/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.
            SubjectDNToUserAttributeCertificateMapperCfg;
import org.opends.server.api.CertificateMapper;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements a very simple Directory Server certificate mapper that
 * will map a certificate to a user only if that user's entry contains an
 * attribute with the subject of the client certificate.  There must be exactly
 * one matching user entry for the mapping to be successful.
 */
public class SubjectDNToUserAttributeCertificateMapper
       extends CertificateMapper<
                    SubjectDNToUserAttributeCertificateMapperCfg>
       implements ConfigurationChangeListener<
                       SubjectDNToUserAttributeCertificateMapperCfg>
{
  // The attribute type that will be used to map the certificate's subject.
  private AttributeType subjectAttributeType;

  // The DN of the configuration entry for this certificate mapper.
  private DN configEntryDN;

  // The current configuration for this certificate mapper.
  private SubjectDNToUserAttributeCertificateMapperCfg currentConfig;



  /**
   * Creates a new instance of this certificate mapper.  Note that all actual
   * initialization should be done in the
   * <CODE>initializeCertificateMapper</CODE> method.
   */
  public SubjectDNToUserAttributeCertificateMapper()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeCertificateMapper(
                   SubjectDNToUserAttributeCertificateMapperCfg
                        configuration)
         throws ConfigException, InitializationException
  {
    configuration.addSubjectDNToUserAttributeChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();


    // Get the attribute type that will be used to hold the fingerprint.
    String attrName = configuration.getSubjectAttribute();
    subjectAttributeType =
         DirectoryServer.getAttributeType(toLowerCase(attrName), false);
    if (subjectAttributeType == null)
    {
      int    msgID   = MSGID_SDTUACM_NO_SUCH_ATTR;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  attrName);
      throw new ConfigException(msgID, message);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeCertificateMapper()
  {
    currentConfig.removeSubjectDNToUserAttributeChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    SubjectDNToUserAttributeCertificateMapperCfg config =
         currentConfig;
    AttributeType subjectAttributeType = this.subjectAttributeType;


    // Make sure that a peer certificate was provided.
    if ((certificateChain == null) || (certificateChain.length == 0))
    {
      int    msgID   = MSGID_SDTUACM_NO_PEER_CERTIFICATE;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   msgID);
    }


    // Get the first certificate in the chain.  It must be an X.509 certificate.
    X509Certificate peerCertificate;
    try
    {
      peerCertificate = (X509Certificate) certificateChain[0];
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SDTUACM_PEER_CERT_NOT_X509;
      String message =
           getMessage(msgID, String.valueOf(certificateChain[0].getType()));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   msgID);
    }


    // Get the subject from the peer certificate and use it to create a search
    // filter.
    X500Principal peerPrincipal = peerCertificate.getSubjectX500Principal();
    String peerName = peerPrincipal.getName(X500Principal.RFC2253);
    AttributeValue value = new AttributeValue(subjectAttributeType, peerName);
    SearchFilter filter =
         SearchFilter.createEqualityFilter(subjectAttributeType, value);


    // If we have an explicit set of base DNs, then use it.  Otherwise, use the
    // set of public naming contexts in the server.
    Collection<DN> baseDNs = config.getUserBaseDN();
    if ((baseDNs == null) || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }


    // For each base DN, issue an internal search in an attempt to map the
    // certificate.
    Entry userEntry = null;
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    for (DN baseDN : baseDNs)
    {
      InternalSearchOperation searchOperation =
           conn.processSearch(baseDN, SearchScope.WHOLE_SUBTREE, filter);
      for (SearchResultEntry entry : searchOperation.getSearchEntries())
      {
        if (userEntry == null)
        {
          userEntry = entry;
        }
        else
        {
          int    msgID   = MSGID_SDTUACM_MULTIPLE_MATCHING_ENTRIES;
          String message = getMessage(msgID, peerName,
                                      String.valueOf(userEntry.getDN()),
                                      String.valueOf(entry.getDN()));
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                       msgID);
        }
      }
    }


    // If we've gotten here, then we either found exactly one user entry or we
    // didn't find any.  Either way, return the entry or null to the caller.
    return userEntry;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      SubjectDNToUserAttributeCertificateMapperCfg
                           configuration,
                      List<String> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Make sure that the subject attribute is defined in the server schema.
    String attrName = configuration.getSubjectAttribute();
    AttributeType newSubjectType =
                       DirectoryServer.getAttributeType(toLowerCase(attrName),
                                       false);
    if (newSubjectType == null)
    {
      unacceptableReasons.add(getMessage(MSGID_SDTUACM_NO_SUCH_ATTR,
                                         String.valueOf(configEntryDN),
                                         attrName));
      configAcceptable = false;
    }


    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              SubjectDNToUserAttributeCertificateMapperCfg
                   configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Make sure that the fingerprint attribute is defined in the server schema.
    String attrName = configuration.getSubjectAttribute();
    AttributeType newSubjectType =
                       DirectoryServer.getAttributeType(toLowerCase(attrName),
                                       false);
    if (newSubjectType == null)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.NO_SUCH_ATTRIBUTE;
      }

      messages.add(getMessage(MSGID_SDTUACM_NO_SUCH_ATTR,
                              String.valueOf(configEntryDN), attrName));
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      subjectAttributeType = newSubjectType;
      currentConfig        = configuration;
    }


   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

