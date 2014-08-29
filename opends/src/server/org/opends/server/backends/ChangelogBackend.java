/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.replication.protocol.StartECLSessionMsg.ECLRequestType.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.util.LDIFWriter.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.text.SimpleDateFormat;
import java.util.*;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.admin.Configuration;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.EntryChangelogNotificationControl;
import org.opends.server.controls.ExternalChangelogRequestControl;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyCommonMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.StartECLSessionMsg.ECLRequestType;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.replication.server.changelog.je.ECLEnabledDomainPredicate;
import org.opends.server.replication.server.changelog.je.ECLMultiDomainDBCursor;
import org.opends.server.replication.server.changelog.je.MultiDomainDBCursor;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;

/**
 * A backend that provides access to the changelog, ie the "cn=changelog"
 * suffix. It is a read-only backend that is created by a
 * {@code ReplicationServer} and is not configurable.
 * <p>
 * There are two modes to search the changelog:
 * <ul>
 * <li>Cookie mode: when a "ECL Cookie Exchange Control" is provided with the
 * request. The cookie provided in the control is used to retrieve entries from
 * the ReplicaDBs. The <code>changeNumber</code> attribute is not returned with
 * the entries.</li>
 * <li>Draft compat mode: when no "ECL Cookie Exchange Control" is provided with
 * the request. The entries are retrieved using the ChangeNumberIndexDB (or
 * DraftDB, hence the name) and their attributes are set with the information
 * from the ReplicasDBs. The <code>changeNumber</code> attribute value is set
 * from the content of ChangeNumberIndexDB.</li>
 * </ul>
 *
 * @see ReplicationServer
 */
public class ChangelogBackend extends Backend<Configuration>
{
  private static final DebugTracer TRACER = getTracer();

  /** The id of this backend. */
  public static final String BACKEND_ID = "changelog";

  private static final long CHANGE_NUMBER_FOR_EMPTY_CURSOR = 0L;

  private static final String CHANGE_NUMBER_ATTR = "changeNumber";
  private static final String CHANGE_NUMBER_ATTR_LC = CHANGE_NUMBER_ATTR.toLowerCase();

  /** The set of objectclasses that will be used in root entry. */
  private static final Map<ObjectClass, String>
    CHANGELOG_ROOT_OBJECT_CLASSES = new LinkedHashMap<ObjectClass, String>(2);

  static
  {
    CHANGELOG_ROOT_OBJECT_CLASSES.put(DirectoryServer.getObjectClass(OC_TOP, true), OC_TOP);
    CHANGELOG_ROOT_OBJECT_CLASSES.put(DirectoryServer.getObjectClass("container", true), "container");
  }

  /** The set of objectclasses that will be used in ECL entries. */
  private static final Map<ObjectClass, String>
    CHANGELOG_ENTRY_OBJECT_CLASSES = new LinkedHashMap<ObjectClass, String>(2);

  static
  {
    CHANGELOG_ENTRY_OBJECT_CLASSES.put(DirectoryServer.getObjectClass(OC_TOP, true), OC_TOP);
    CHANGELOG_ENTRY_OBJECT_CLASSES.put(DirectoryServer.getObjectClass(OC_CHANGELOG_ENTRY, true), OC_CHANGELOG_ENTRY);
  }

  /** The attribute type for the "creatorsName" attribute. */
  private static final AttributeType CREATORS_NAME_TYPE =
      DirectoryConfig.getAttributeType(OP_ATTR_CREATORS_NAME_LC, true);

  /** The attribute type for the "modifiersName" attribute. */
  private static final AttributeType MODIFIERS_NAME_TYPE =
      DirectoryConfig.getAttributeType(OP_ATTR_MODIFIERS_NAME_LC, true);

  /** The DN for the base changelog entry. */
  private DN baseChangelogDN;

  /** The set of base DNs for this backend. */
  private DN[] baseDNs;

  /** The set of supported controls for this backend. */
  private final Set<String> supportedControls = Collections.singleton(OID_ECL_COOKIE_EXCHANGE_CONTROL);

  /** The replication server on which the changelog is read. */
  private final ReplicationServer replicationServer;

  private final ECLEnabledDomainPredicate domainPredicate;

  /**
   * Creates a new backend with the provided repication server.
   *
   * @param replicationServer
   *          The replication server on which the changes are read.
   * @param domainPredicate
   *          Returns whether a domain is enabled for the external changelog.
   */
  public ChangelogBackend(final ReplicationServer replicationServer, final ECLEnabledDomainPredicate domainPredicate)
  {
    this.replicationServer = replicationServer;
    this.domainPredicate = domainPredicate;
    setBackendID(BACKEND_ID);
    setWritabilityMode(WritabilityMode.DISABLED);
    setPrivateBackend(true);
  }

  /** {@inheritDoc} */
  @Override
  public void configureBackend(final Configuration config) throws ConfigException
  {
    throw new UnsupportedOperationException("The changelog backend is not configurable");
  }

  /** {@inheritDoc} */
  @Override
  public void initializeBackend() throws InitializationException
  {
    try
    {
      baseChangelogDN = DN.decode(DN_EXTERNAL_CHANGELOG_ROOT);
      baseDNs = new DN[] { baseChangelogDN };
    }
    catch (final DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new InitializationException(
          ERR_BACKEND_CANNOT_DECODE_BACKEND_ROOT_DN.get(getBackendID(), getExceptionMessage(e)), e);
    }

    try
    {
      DirectoryServer.registerBaseDN(baseChangelogDN, this, true);
    }
    catch (final DirectoryException e)
    {
      throw new InitializationException(
          ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(baseChangelogDN.toString(), getExceptionMessage(e)), e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeBackend()
  {
    try
    {
      DirectoryServer.deregisterBaseDN(baseChangelogDN);
    }
    catch (final DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /** {@inheritDoc} */
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException("Operation not supported.");
  }

  /** {@inheritDoc} */
  @Override
  public boolean isLocal()
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIndexed(final AttributeType attributeType, final IndexType indexType)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(final DN entryDN) throws DirectoryException
  {
    if (entryDN == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKEND_GET_ENTRY_NULL.get(getBackendID()));
    }
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult hasSubordinates(final DN entryDN)
      throws DirectoryException
  {
    final long num = numSubordinates(entryDN, false);
    if (num < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    else if (num == 0)
    {
      return ConditionResult.FALSE;
    }
    else
    {
      return ConditionResult.TRUE;
    }
  }

  /** Specific search operation to count number of entries. */
  private final class NumSubordinatesSearchOperation extends SearchOperationWrapper
  {
    private long numSubordinates = -1;

    private NumSubordinatesSearchOperation()
    {
      super(null);
    }

    @Override
    public boolean returnEntry(Entry entry, List<Control> controls)
    {
      numSubordinates++;
      return true;
    }

    @Override
    public DN getBaseDN()
    {
      return baseChangelogDN;
    }

    @Override
    public SearchFilter getFilter()
    {
      return LDAPURL.DEFAULT_SEARCH_FILTER;
    }

    @Override
    public SearchScope getScope()
    {
      return SearchScope.WHOLE_SUBTREE;
    }
  }

  /** {@inheritDoc} */
  @Override
  public long numSubordinates(final DN entryDN, final boolean subtree) throws DirectoryException
  {
    // Compute the num subordinates only for the base DN
    if (entryDN == null || !baseChangelogDN.equals(entryDN))
    {
      return -1;
    }
    if (!subtree)
    {
      return 1;
    }
    // Search with cookie mode to count all update messages
    final Set<String> excludedDomains = MultimasterReplication.getECLDisabledDomains();
    excludedDomains.add(DN_EXTERNAL_CHANGELOG_ROOT);
    SearchParams params = new SearchParams("0", excludedDomains);
    params.requestType = REQUEST_TYPE_FROM_COOKIE;
    params.multiDomainServerState = new MultiDomainServerState();
    NumSubordinatesSearchOperation searchOp = new NumSubordinatesSearchOperation();
    try
    {
      search0(params, searchOp);
    }
    catch (ChangelogException e)
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_CHANGELOG_BACKEND_NUM_SUBORDINATES.get(
          baseChangelogDN.toString(), stackTraceToSingleLineString(e)));
    }
    return searchOp.numSubordinates;
  }

  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_ADD_NOT_SUPPORTED.get(String.valueOf(entry.getDN()), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_DELETE_NOT_SUPPORTED.get(String.valueOf(entryDN), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException,
      CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_NOT_SUPPORTED.get(String.valueOf(newEntry.getDN()), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry,
      ModifyDNOperation modifyDNOperation) throws DirectoryException,
      CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(String.valueOf(currentDN), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void search(final SearchOperation searchOperation) throws DirectoryException
  {
    checkChangelogReadPrivilege(searchOperation);

    final SearchParams params = buildSearchParameters(searchOperation);

    optimizeSearchParameters(params, searchOperation.getBaseDN(), searchOperation.getFilter());
    try
    {
      search0(params, searchOperation);
    }
    catch (ChangelogException e)
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_CHANGELOG_BACKEND_SEARCH.get(
          searchOperation.getBaseDN().toString(),
          searchOperation.getFilter().toString(),
          stackTraceToSingleLineString(e)));
    }
  }

  private SearchParams buildSearchParameters(final SearchOperation searchOperation) throws DirectoryException
  {
    final Set<String> excludedDomains = MultimasterReplication.getECLDisabledDomains();
    excludedDomains.add(DN_EXTERNAL_CHANGELOG_ROOT);
    final SearchParams params = new SearchParams(searchOperation.toString(), excludedDomains);
    final ExternalChangelogRequestControl eclRequestControl =
        searchOperation.getRequestControl(ExternalChangelogRequestControl.DECODER);
    if (eclRequestControl == null)
    {
      params.requestType = REQUEST_TYPE_FROM_CHANGE_NUMBER;
    }
    else
    {
      params.requestType = REQUEST_TYPE_FROM_COOKIE;
      params.multiDomainServerState = eclRequestControl.getCookie();
    }
    return params;
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedControls()
  {
    return supportedControls;
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsLDIFExport()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void exportLDIF(final LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsLDIFImport()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsBackup()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsBackup(BackupConfig backupConfig, StringBuilder unsupportedReason)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsRestore()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    try
    {
      return numSubordinates(baseChangelogDN, true) + 1;
    }
    catch (DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return -1;
    }
  }

  /**
   * Represent the search parameters specific to the changelog.
   *
   * This class should be visible for tests.
   */
  static class SearchParams
  {
    private ECLRequestType requestType;
    private final String operationId;
    private final Set<String> excludedBaseDNs;
    private long lowestChangeNumber = -1;
    private long highestChangeNumber = -1;
    private CSN csn = new CSN(0, 0, 0);
    private MultiDomainServerState multiDomainServerState;

    /**
     * Creates search parameters.
     */
    SearchParams()
    {
      operationId = "";
      excludedBaseDNs = Collections.emptySet();
    }

    /**
     * Creates search parameters with provided id and excluded domain DNs.
     *
     * @param operationId
     *          The id of the operation.
     * @param excludedBaseDNs
     *          Set of DNs to exclude from search.
     */
    SearchParams(final String operationId, final Set<String> excludedBaseDNs)
    {
      this.operationId = operationId;
      this.excludedBaseDNs = excludedBaseDNs;
    }

    /**
     * Indicates if provided change number is compatible with last change
     * number.
     *
     * @param changeNumber
     *          The change number to test.
     * @return {@code true} if and only if the provided change number is in the
     *         range of the last change number.
     */
    boolean changeNumberIsInRange(long changeNumber)
    {
      return highestChangeNumber == -1 || changeNumber <= highestChangeNumber;
    }

    /**
     * Returns the lowest change number to retrieve (inclusive).
     *
     * @return the lowest change number
     */
    long getLowestChangeNumber()
    {
      return lowestChangeNumber;
    }

    /**
     * Returns the highest change number to retrieve (inclusive).
     *
     * @return the highest change number
     */
    long getHighestChangeNumber()
    {
      return highestChangeNumber;
    }

    /**
     * Returns the CSN to retrieve.
     *
     * @return the CSN, which may be the default CSN with zero values.
     */
    CSN getCSN()
    {
      return csn;
    }

    /**
     * Returns the set of DNs to exclude from the search.
     *
     * @return the DNs corresponding to domains to exclude from the search.
     * @throws DirectoryException
     *           If a DN can't be decoded.
     */
    Set<DN> getExcludedBaseDNs() throws DirectoryException
    {
      final Set<DN> excludedDNs = new HashSet<DN>();
      for (String dn : excludedBaseDNs)
      {
        excludedDNs.add(DN.decode(dn));
      }
      return excludedDNs;
    }

  }

  /**
   * Optimize the search parameters by analyzing the DN and filter.
   * Populate the provided SearchParams with optimizations found.
   *
   * @param params the search parameters that are specific to external changelog
   * @param baseDN the provided search baseDN.
   * @param userFilter the provided search filter.
   * @throws DirectoryException when an exception occurs.
   */
   void optimizeSearchParameters(final SearchParams params, final DN baseDN, final SearchFilter userFilter)
       throws DirectoryException
  {
    SearchFilter equalityFilter = null;
    switch (baseDN.getNumComponents())
    {
    case 1:
      // "cn=changelog" : use user-provided search filter.
      break;
    case 2:
      // It is probably "changeNumber=xxx,cn=changelog", use equality filter
      // But it also could be "<service-id>,cn=changelog" so need to check on attribute
      equalityFilter = buildSearchFilterFrom(baseDN, CHANGE_NUMBER_ATTR_LC, CHANGE_NUMBER_ATTR);
      break;
    default:
      // "replicationCSN=xxx,<service-id>,cn=changelog" : use equality filter
      equalityFilter = buildSearchFilterFrom(baseDN, "replicationcsn", "replicationCSN");
      break;
    }

    final SearchParams optimized = optimizeSearchUsingFilter(equalityFilter != null ? equalityFilter : userFilter);
    params.lowestChangeNumber = optimized.lowestChangeNumber;
    params.highestChangeNumber = optimized.highestChangeNumber;
    params.csn = optimized.csn;
  }

  /**
   * Build a search filter from given DN and attribute.
   *
   * @return the search filter or {@code null} if attribute is not present in
   *         the provided DN
   */
  private SearchFilter buildSearchFilterFrom(final DN baseDN, final String lowerCaseAttr, final String upperCaseAttr)
  {
    final RDN rdn = baseDN.getRDN();
    AttributeType attrType = DirectoryServer.getAttributeType(lowerCaseAttr);
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(upperCaseAttr);
    }
    final AttributeValue attrValue = rdn.getAttributeValue(attrType);
    if (attrValue != null)
    {
      return SearchFilter.createEqualityFilter(attrType, attrValue);
    }
    return null;
  }

  private SearchParams optimizeSearchUsingFilter(final SearchFilter filter) throws DirectoryException
  {
    final SearchParams params = new SearchParams();
    if (filter == null)
    {
      return params;
    }

    if (matches(filter, FilterType.GREATER_OR_EQUAL, CHANGE_NUMBER_ATTR))
    {
      params.lowestChangeNumber = decodeChangeNumber(filter.getAssertionValue());
    }
    else if (matches(filter, FilterType.LESS_OR_EQUAL, CHANGE_NUMBER_ATTR))
    {
      params.highestChangeNumber = decodeChangeNumber(filter.getAssertionValue());
    }
    else if (matches(filter, FilterType.EQUALITY, CHANGE_NUMBER_ATTR))
    {
      final long number = decodeChangeNumber(filter.getAssertionValue());
      params.lowestChangeNumber = number;
      params.highestChangeNumber = number;
    }
    else if (matches(filter, FilterType.EQUALITY, "replicationcsn"))
    {
      // == exact CSN
      params.csn = new CSN(filter.getAssertionValue().toString());
    }
    else if (filter.getFilterType() == FilterType.AND)
    {
      // TODO: it looks like it could be generalized to N components, not only two
      final Collection<SearchFilter> components = filter.getFilterComponents();
      final SearchFilter filters[] = components.toArray(new SearchFilter[0]);
      long last1 = -1;
      long first1 = -1;
      long last2 = -1;
      long first2 = -1;
      if (filters.length > 0)
      {
        SearchParams msg1 = optimizeSearchUsingFilter(filters[0]);
        last1 = msg1.highestChangeNumber;
        first1 = msg1.lowestChangeNumber;
      }
      if (filters.length > 1)
      {
        SearchParams msg2 = optimizeSearchUsingFilter(filters[1]);
        last2 = msg2.highestChangeNumber;
        first2 = msg2.lowestChangeNumber;
      }
      if (last1 == -1)
      {
        params.highestChangeNumber = last2;
      }
      else if (last2 == -1)
      {
        params.highestChangeNumber = last1;
      }
      else
      {
        params.highestChangeNumber = Math.min(last1, last2);
      }

      params.lowestChangeNumber = Math.max(first1, first2);
    }
    return params;
  }

  private static long decodeChangeNumber(final AttributeValue assertionValue)
      throws DirectoryException
  {
    try
    {
      return Long.decode(assertionValue.getNormalizedValue().toString());
    }
    catch (NumberFormatException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
          Message.raw("Could not convert value '%s' to long", assertionValue.getNormalizedValue().toString()));
    }
  }

  private boolean matches(SearchFilter filter, FilterType filterType, String primaryName)
  {
    return filter.getFilterType() == filterType
           && filter.getAttributeType() != null
           && filter.getAttributeType().getPrimaryName().equalsIgnoreCase(primaryName);
  }

  private void search0(final SearchParams searchParams, final SearchOperation searchOperation)
      throws DirectoryException, ChangelogException
  {
    switch (searchParams.requestType)
    {
      case REQUEST_TYPE_FROM_CHANGE_NUMBER:
        searchFromChangeNumber(searchParams, searchOperation);
        break;
      case REQUEST_TYPE_FROM_COOKIE:
        searchFromCookie(searchParams, searchOperation);
        break;
      default:
        // not handled
    }
  }

  /**
   * Search the changelog when a cookie control is provided.
   */
  private void searchFromCookie(final SearchParams searchParams, final SearchOperation searchOperation)
      throws DirectoryException, ChangelogException
  {
    final ReplicationDomainDB replicationDomainDB = replicationServer.getChangelogDB().getReplicationDomainDB();
    validateProvidedCookie(searchParams);

    boolean hasReturnedBaseEntry = false;
    ECLMultiDomainDBCursor replicaUpdatesCursor = null;
    try
    {
      final MultiDomainDBCursor cursor = replicationDomainDB.getCursorFrom(
          searchParams.multiDomainServerState, AFTER_MATCHING_KEY, searchParams.getExcludedBaseDNs());
      replicaUpdatesCursor = new ECLMultiDomainDBCursor(domainPredicate, cursor);

      MultiDomainServerState cookie = searchParams.multiDomainServerState;
      boolean continueSearch = true;
      while (continueSearch && replicaUpdatesCursor.next())
      {
        // Handle creation of base changelog entry on first update message found
        if (!hasReturnedBaseEntry)
        {
          if (!returnBaseChangelogEntry(searchOperation, true))
          {
            return;
          }
          hasReturnedBaseEntry = true;
        }
        // Handle the update message
        final UpdateMsg updateMsg = replicaUpdatesCursor.getRecord();
        final DN domainBaseDN = replicaUpdatesCursor.getData();
        cookie.update(domainBaseDN, updateMsg.getCSN());
        final Entry entry = createEntryFromMsg(domainBaseDN, 0L, cookie.toString(), updateMsg);
        if (matchBaseAndScopeAndFilter(entry, searchOperation))
        {
          Control control = new EntryChangelogNotificationControl(true, cookie.toString());
          continueSearch = searchOperation.returnEntry(entry, Arrays.asList(control));
        }
      }
      // Handle creation of base changelog entry when no update message is found
      if (!hasReturnedBaseEntry)
      {
        returnBaseChangelogEntry(searchOperation, false);
      }
    }
    finally
    {
      StaticUtils.close(replicaUpdatesCursor);
    }
  }

  /**
   * Validates the cookie contained in search parameters by checking its content
   * with the actual replication server state.
   *
   * @throws DirectoryException
   *           If the state is not valid
   */
  private void validateProvidedCookie(final SearchParams searchParams) throws DirectoryException
  {
    final MultiDomainServerState state = searchParams.multiDomainServerState;
    if (state != null && !state.isEmpty())
    {
      replicationServer.validateServerState(state, searchParams.getExcludedBaseDNs());
    }
  }

  /**
   * Search the changelog using change number(s).
   */
  private void searchFromChangeNumber(final SearchParams params, final SearchOperation searchOperation)
      throws ChangelogException, DirectoryException
  {
    boolean hasReturnedBaseEntry = false;
    final ChangelogDB changelogDB = replicationServer.getChangelogDB();
    DBCursor<ChangeNumberIndexRecord> cnIndexDBCursor = null;
    MultiDomainDBCursor replicaUpdatesCursor = null;
    try {
      cnIndexDBCursor = getCNIndexDBCursor(changelogDB, params.lowestChangeNumber);
      boolean continueSearch = true;
      while (continueSearch && cnIndexDBCursor.next())
      {
        // Handle creation of base changelog entry on cnIndex record found
        if (!hasReturnedBaseEntry)
        {
          if (!returnBaseChangelogEntry(searchOperation, true))
          {
            return;
          }
          hasReturnedBaseEntry = true;
        }
        // Handle the current cnIndex record
        final ChangeNumberIndexRecord cnIndexRecord = cnIndexDBCursor.getRecord();
        if (replicaUpdatesCursor == null)
        {
          replicaUpdatesCursor = initializeReplicaUpdatesCursor(changelogDB, cnIndexRecord);
        }
        continueSearch = params.changeNumberIsInRange(cnIndexRecord.getChangeNumber());
        if (continueSearch)
        {
           UpdateMsg updateMsg = findReplicaUpdateMessage(cnIndexRecord, replicaUpdatesCursor);
           if (updateMsg != null)
           {
             continueSearch = returnEntryForUpdateMessage(searchOperation, cnIndexRecord, updateMsg);
             replicaUpdatesCursor.next();
           }
        }
      }
      // Handle creation of base changelog entry when no update message is found
      if (!hasReturnedBaseEntry)
      {
        returnBaseChangelogEntry(searchOperation, false);
      }
    }
    finally {
      StaticUtils.close(cnIndexDBCursor, replicaUpdatesCursor);
    }
  }

  /**
   * Create and returns the base changelog entry to provided search operation.
   *
   * @return {@code true} if search should continue, {@code false} otherwise
   */
  private boolean returnBaseChangelogEntry(final SearchOperation searchOperation, boolean hasSubordinates)
      throws DirectoryException
  {
    final DN baseDN = searchOperation.getBaseDN();
    final SearchFilter filter = searchOperation.getFilter();
    final SearchScope scope = searchOperation.getScope();

    if (baseChangelogDN.matchesBaseAndScope(baseDN, scope))
    {
      final Entry entry = buildBaseChangelogEntry(hasSubordinates);
      if (filter.matchesEntry(entry) && !searchOperation.returnEntry(entry, null))
      {
        // Abandon, size limit reached.
        return false;
      }
    }
    if (baseDN.equals(baseChangelogDN) && scope.equals(SearchScope.BASE_OBJECT))
    {
      // Only the change log root entry was requested
      return false;
    }
    return true;
  }

  /**
   * @return {@code true} if search should continue, {@code false} otherwise
   */
  private boolean returnEntryForUpdateMessage(
      final SearchOperation searchOperation,
      final ChangeNumberIndexRecord cnIndexRecord,
      final UpdateMsg updateMsg)
          throws DirectoryException
  {
    final MultiDomainServerState cookie = new MultiDomainServerState(cnIndexRecord.getPreviousCookie());
    final DN changeDN = cnIndexRecord.getBaseDN();
    cookie.update(changeDN, cnIndexRecord.getCSN());
    final Entry entry = createEntryFromMsg(changeDN, cnIndexRecord.getChangeNumber(), cookie.toString(), updateMsg);
    if (matchBaseAndScopeAndFilter(entry, searchOperation))
    {
      return searchOperation.returnEntry(entry, null);
    }
    return true;
  }

  private MultiDomainDBCursor initializeReplicaUpdatesCursor(final ChangelogDB changelogDB,
      final ChangeNumberIndexRecord cnIndexRecord) throws ChangelogException
  {
    final MultiDomainServerState state = new MultiDomainServerState();
    state.update(cnIndexRecord.getBaseDN(), cnIndexRecord.getCSN());

    // No need for ECLMultiDomainDBCursor in this case
    // as updateMsg will be matched with cnIndexRecord
    final MultiDomainDBCursor replicaUpdatesCursor =
        changelogDB.getReplicationDomainDB().getCursorFrom(state, ON_MATCHING_KEY);
    replicaUpdatesCursor.next();
    return replicaUpdatesCursor;
  }

  /**
   * Returns the replica update message corresponding to the provided
   * cnIndexRecord.
   *
   * @return the update message, which may be {@code null} if the update message
   *         could not be found because it was purged or because corresponding
   *         baseDN was removed from the changelog
   * @throws DirectoryException
   *           If inconsistency is detected between the available update
   *           messages and the provided cnIndexRecord
   */
  private UpdateMsg findReplicaUpdateMessage(
      final ChangeNumberIndexRecord cnIndexRecord,
      final MultiDomainDBCursor replicaUpdatesCursor)
          throws DirectoryException, ChangelogException
  {
    while (true)
    {
      final UpdateMsg updateMsg = replicaUpdatesCursor.getRecord();
      final int compareIndexWithUpdateMsg = cnIndexRecord.getCSN().compareTo(updateMsg.getCSN());
      if (compareIndexWithUpdateMsg < 0) {
        // Either update message has been purged or baseDN has been removed from changelogDB,
        // ignore current index record and go to the next one
        return null;
      }
      else if (compareIndexWithUpdateMsg == 0)
      {
        // Found the matching update message
        return updateMsg;
      }
      // Case compareIndexWithUpdateMsg > 0 : the update message has not bean reached yet
      if (!replicaUpdatesCursor.next())
      {
        // Should never happen, as it means some messages have disappeared
        // TODO : put the correct I18N message
        throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
            Message.raw("Could not find replica update message matching index record. " +
                "No more replica update messages with a csn newer than " + updateMsg.getCSN() + " exist."));
      }
    }
  }

  /** Returns a cursor on CNIndexDB for the provided first change number. */
  private DBCursor<ChangeNumberIndexRecord> getCNIndexDBCursor(final ChangelogDB changelogDB,
      final long firstChangeNumber) throws ChangelogException
  {
    final ChangeNumberIndexDB cnIndexDB = changelogDB.getChangeNumberIndexDB();
    long changeNumberToUse = firstChangeNumber;
    if (changeNumberToUse <= 1)
    {
      final ChangeNumberIndexRecord oldestRecord = cnIndexDB.getOldestRecord();
      changeNumberToUse = oldestRecord == null ? CHANGE_NUMBER_FOR_EMPTY_CURSOR : oldestRecord.getChangeNumber();
    }
    return cnIndexDB.getCursorFrom(changeNumberToUse);
  }

  /** Indicates if the provided entry matches the filter, base and scope. */
  private boolean matchBaseAndScopeAndFilter(Entry entry, SearchOperation searchOp) throws DirectoryException
  {
    return entry.matchesBaseAndScope(searchOp.getBaseDN(), searchOp.getScope())
        && searchOp.getFilter().matchesEntry(entry);
  }

  /**
   * Retrieves the base changelog entry.
   */
  private Entry buildBaseChangelogEntry(boolean hasSubordinates)
  {
    final Map<AttributeType, List<Attribute>> userAttrs = new LinkedHashMap<AttributeType,List<Attribute>>();
    final Map<AttributeType, List<Attribute>> operationalAttrs = new LinkedHashMap<AttributeType,List<Attribute>>();

    addAttributeByUppercaseName(ATTR_COMMON_NAME, ATTR_COMMON_NAME, BACKEND_ID, userAttrs, operationalAttrs);
    addAttributeByUppercaseName(ATTR_SUBSCHEMA_SUBENTRY_LC, ATTR_SUBSCHEMA_SUBENTRY,
        ConfigConstants.DN_DEFAULT_SCHEMA_ROOT, userAttrs, operationalAttrs);
    addAttributeByUppercaseName("hassubordinates", "hasSubordinates", Boolean.toString(hasSubordinates),
        userAttrs, operationalAttrs);
    addAttributeByUppercaseName("entrydn", "entryDN", baseChangelogDN.toString(),
        userAttrs, operationalAttrs);
    return new Entry(baseChangelogDN, CHANGELOG_ROOT_OBJECT_CLASSES, userAttrs, operationalAttrs);
  }

  /**
   * Creates a changelog entry.
   */
  private Entry createEntryFromMsg(final DN baseDN, final long changeNumber, final String cookie, final UpdateMsg msg)
      throws DirectoryException
  {
    if (msg instanceof AddMsg)
    {
      return createAddMsg(baseDN, changeNumber, cookie, msg);
    }
    else if (msg instanceof ModifyCommonMsg)
    {
      return createModifyMsg(baseDN, changeNumber, cookie, msg);
    }
    else if (msg instanceof DeleteMsg)
    {
      final DeleteMsg delMsg = (DeleteMsg) msg;
      return createChangelogEntry(baseDN, changeNumber, cookie, delMsg, null, "delete", delMsg.getInitiatorsName());
    }
    throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
        Message.raw("Unexpected message type when trying to create changelog entry for dn %s : %s", baseDN.toString(),
            msg.getClass().toString()));
  }

  /**
   * Creates an entry from an add message.
   * <p>
   * Map addMsg to an LDIF string for the 'changes' attribute, and pull out
   * change initiators name if available which is contained in the creatorsName
   * attribute.
   */
  private Entry createAddMsg(final DN baseDN, final long changeNumber, final String cookie, final UpdateMsg msg)
      throws DirectoryException
  {
    final AddMsg addMsg = (AddMsg) msg;
    String changeInitiatorsName = null;
    String ldifChanges = null;
    try
    {
      final StringBuilder builder = new StringBuilder(256);
      for (Attribute attr : addMsg.getAttributes())
      {
        if (attr.getAttributeType().equals(CREATORS_NAME_TYPE) && !attr.isEmpty())
        {
          // This attribute is not multi-valued.
          changeInitiatorsName = attr.iterator().next().toString();
        }
        final String attrName = attr.getNameWithOptions();
        for (AttributeValue value : attr)
        {
          builder.append(attrName);
          appendLDIFSeparatorAndValue(builder, value.getValue());
          builder.append('\n');
        }
      }
      ldifChanges = builder.toString();
    }
    catch (Exception e)
    {
      logEncodingMessageError("add", addMsg.getDN(), e);
    }

    return createChangelogEntry(baseDN, changeNumber, cookie, addMsg, ldifChanges, "add", changeInitiatorsName);
  }

  /**
   * Creates an entry from a modify message.
   * <p>
   * Map the modifyMsg to an LDIF string for the 'changes' attribute, and pull
   * out change initiators name if available which is contained in the
   * modifiersName attribute.
   */
  private Entry createModifyMsg(final DN baseDN, final long changeNumber, final String cookie, final UpdateMsg msg)
      throws DirectoryException
  {
    final ModifyCommonMsg modifyMsg = (ModifyCommonMsg) msg;
    String changeInitiatorsName = null;
    String ldifChanges = null;
    try
    {
      final StringBuilder builder = new StringBuilder(128);
      for (Modification mod : modifyMsg.getMods())
      {
        final Attribute attr = mod.getAttribute();
        if (mod.getModificationType() == ModificationType.REPLACE
            && attr.getAttributeType().equals(MODIFIERS_NAME_TYPE)
            && !attr.isEmpty())
        {
          // This attribute is not multi-valued.
          changeInitiatorsName = attr.iterator().next().toString();
        }
        final String attrName = attr.getNameWithOptions();
        builder.append(mod.getModificationType().getLDIFName());
        builder.append(": ");
        builder.append(attrName);
        builder.append('\n');

        for (AttributeValue value : attr)
        {
          builder.append(attrName);
          appendLDIFSeparatorAndValue(builder, value.getValue());
          builder.append('\n');
        }
        builder.append("-\n");
      }
      ldifChanges = builder.toString();
    }
    catch (Exception e)
    {
      logEncodingMessageError("modify", modifyMsg.getDN(), e);
    }

    final boolean isModifyDNMsg = modifyMsg instanceof ModifyDNMsg;
    final Entry entry = createChangelogEntry(baseDN, changeNumber, cookie, modifyMsg, ldifChanges,
        isModifyDNMsg ? "modrdn" : "modify", changeInitiatorsName);

    if (isModifyDNMsg)
    {
      final ModifyDNMsg modDNMsg = (ModifyDNMsg) modifyMsg;
      addAttribute(entry, "newrdn", modDNMsg.getNewRDN());
      if (modDNMsg.getNewSuperior() != null)
      {
        addAttribute(entry, "newsuperior", modDNMsg.getNewSuperior());
      }
      addAttribute(entry, "deleteoldrdn", String.valueOf(modDNMsg.deleteOldRdn()));
    }
    return entry;
  }

  /**
   * Log an encoding message error.
   *
   * @param messageType
   *            String identifying type of message. Should be "add" or "modify".
   * @param entryDN
   *            DN of original entry
   */
  private void logEncodingMessageError(String messageType, DN entryDN,  Exception exception)
  {
    TRACER.debugCaught(DebugLogLevel.ERROR, exception);
    logError(Message.raw(Category.SYNC, Severity.MILD_ERROR,
        "An exception was encountered while trying to encode a replication " + messageType + " message for entry \""
        + entryDN + "\" into an External Change Log entry: " + exception.getMessage()));
  }

  private void checkChangelogReadPrivilege(SearchOperation searchOp) throws DirectoryException
  {
    if (!searchOp.getClientConnection().hasPrivilege(Privilege.CHANGELOG_READ, searchOp))
    {
      throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
          NOTE_SEARCH_CHANGELOG_INSUFFICIENT_PRIVILEGES.get());
    }
  }

  /**
   * Create a changelog entry from a set of provided information. This is the part of
   * entry creation common to all types of msgs (ADD, DEL, MOD, MODDN).
   */
  private static Entry createChangelogEntry(final DN baseDN, final long changeNumber, final String cookie,
      final LDAPUpdateMsg msg, final String ldifChanges, final String changeType,
      final String changeInitiatorsName) throws DirectoryException
  {
    final CSN csn = msg.getCSN();
    String dnString;
    if (changeNumber == 0)
    {
      // Cookie mode
      dnString = "replicationCSN=" + csn + "," + baseDN.toString() + "," + DN_EXTERNAL_CHANGELOG_ROOT;
    }
    else
    {
      // Draft compat mode
      dnString = "changeNumber=" + changeNumber + "," + DN_EXTERNAL_CHANGELOG_ROOT;
    }

    final Map<AttributeType, List<Attribute>> userAttrs = new LinkedHashMap<AttributeType, List<Attribute>>();
    final Map<AttributeType, List<Attribute>> opAttrs = new LinkedHashMap<AttributeType, List<Attribute>>();

    // Operational standard attributes
    addAttributeByType(ATTR_SUBSCHEMA_SUBENTRY_LC, ATTR_SUBSCHEMA_SUBENTRY_LC,
        ConfigConstants.DN_DEFAULT_SCHEMA_ROOT, userAttrs, opAttrs);
    addAttributeByType("numsubordinates", "numSubordinates", "0", userAttrs, opAttrs);
    addAttributeByType("hassubordinates", "hasSubordinates", "false", userAttrs, opAttrs);
    addAttributeByType("entrydn", "entryDN", dnString, userAttrs, opAttrs);

    // REQUIRED attributes
    if (changeNumber != 0)
    {
      addAttributeByType("changenumber", "changeNumber", String.valueOf(changeNumber), userAttrs, opAttrs);
    }
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // ??
    final String format = dateFormat.format(new Date(csn.getTime()));
    addAttributeByType("changetime", "changeTime", format, userAttrs, opAttrs);
    addAttributeByType("changetype", "changeType", changeType, userAttrs, opAttrs);
    addAttributeByType("targetdn", "targetDN", msg.getDN().toString(), userAttrs, opAttrs);

    // NON REQUESTED attributes
    addAttributeByType("replicationcsn", "replicationCSN", csn.toString(), userAttrs, opAttrs);
    addAttributeByType("replicaidentifier", "replicaIdentifier", Integer.toString(csn.getServerId()),
        userAttrs, opAttrs);

    if (ldifChanges != null)
    {
      addAttributeByType("changes", "changes", ldifChanges, userAttrs, opAttrs);
    }
    if (changeInitiatorsName != null)
    {
      addAttributeByType("changeinitiatorsname", "changeInitiatorsName", changeInitiatorsName, userAttrs, opAttrs);
    }

    final String targetUUID = msg.getEntryUUID();
    if (targetUUID != null)
    {
      addAttributeByType("targetentryuuid", "targetEntryUUID", targetUUID, userAttrs, opAttrs);
    }
    addAttributeByType("changelogcookie", "changeLogCookie", cookie, userAttrs, opAttrs);

    final List<RawAttribute> includedAttributes = msg.getEclIncludes();
    if (includedAttributes != null && !includedAttributes.isEmpty())
    {
      final StringBuilder builder = new StringBuilder(256);
      for (final RawAttribute includedAttribute : includedAttributes)
      {
        final String name = includedAttribute.getAttributeType();
        for (final ByteString value : includedAttribute.getValues())
        {
          builder.append(name);
          appendLDIFSeparatorAndValue(builder, value);
          builder.append('\n');
        }
      }
      final String includedAttributesLDIF = builder.toString();
      addAttributeByType("includedattributes", "includedAttributes", includedAttributesLDIF, userAttrs, opAttrs);
    }

    return new Entry(DN.decode(dnString), CHANGELOG_ENTRY_OBJECT_CLASSES, userAttrs, opAttrs);
  }

  private static void addAttribute(final Entry e, final String attrType, final String attrValue)
  {
    e.addAttribute(Attributes.create(attrType, attrValue), null);
  }

  private static void addAttributeByType(String attrNameLowercase,
      String attrNameUppercase, String attrValue,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    addAttribute(attrNameLowercase, attrNameUppercase, attrValue, userAttrs, operationalAttrs, true);
  }

  private void addAttributeByUppercaseName(String attrNameLowercase,
      String attrNameUppercase,  String attrValue,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    addAttribute(attrNameLowercase, attrNameUppercase, attrValue, userAttrs, operationalAttrs, false);
  }

  private static void addAttribute(final String attrNameLowercase,
      final String attrNameUppercase, final String attrValue,
      final Map<AttributeType, List<Attribute>> userAttrs,
      final Map<AttributeType, List<Attribute>> operationalAttrs, final boolean addByType)
  {
    AttributeType attrType = DirectoryServer.getAttributeType(attrNameLowercase);
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(attrNameUppercase);
    }
    final Attribute a = addByType ?
        Attributes.create(attrType, attrValue) : Attributes.create(attrNameUppercase, attrValue);
    final List<Attribute> attrList = Collections.singletonList(a);
    if (attrType.isOperational())
    {
      operationalAttrs.put(attrType, attrList);
    }
    else
    {
      userAttrs.put(attrType, attrList);
    }
  }

}
