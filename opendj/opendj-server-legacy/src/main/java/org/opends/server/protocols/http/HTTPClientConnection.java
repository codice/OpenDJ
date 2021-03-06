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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.protocols.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.spi.LdapPromiseImpl;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.responses.Result;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.HTTPAccessLogger;
import org.opends.server.loggers.HTTPRequestInfo;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.CompareResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.protocols.ldap.SearchResultReferenceProtocolOp;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.IntermediateResponse;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.forgerock.opendj.adapter.server3x.Converters.*;
import static org.forgerock.opendj.ldap.LdapException.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.AccessLogger.*;

/**
 * This class defines an HTTP client connection, which is a type of client
 * connection that will be accepted by an instance of the HTTP connection
 * handler.
 */
final class HTTPClientConnection extends ClientConnection implements
    HTTPRequestInfo
{

  // TODO JNR Confirm with Matt that persistent searches are inapplicable to
  // Rest2LDAP.
  // TODO JNR Should I override getIdleTime()?

  /**
   * Class grouping together an {@link Operation} and its associated
   * {@link LdapPromiseImpl} to ensure they are both atomically added
   * and removed from the {@link HTTPClientConnection#operationsInProgress} Map.
   */
  private static class OperationWithPromise
  {
    final Operation operation;
    final LdapPromiseImpl<Result> promise;

    public OperationWithPromise(Operation operation, LdapPromiseImpl<Result> promise)
    {
      this.operation = operation;
      this.promise = promise;
    }

    @Override
    public String toString()
    {
      return operation.toString();
    }
  }

  /** {@inheritDoc} */
  private static final class SearchOperationWithPromise extends OperationWithPromise
  {

    final SearchResultHandler entryHandler;

    public SearchOperationWithPromise(Operation operation, LdapPromiseImpl<Result> promise,
        SearchResultHandler entryHandler)
    {
      super(operation, promise);
      this.entryHandler = entryHandler;
    }
  }

  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Official servlet property giving access to the SSF (Security Strength
   * Factor) used to encrypt the current connection.
   */
  private static final String SERVLET_SSF_CONSTANT =
      "javax.servlet.request.key_size";

  /**
   * Indicates whether the Directory Server believes this connection to be valid
   * and available for communication.
   */
  private volatile boolean connectionValid = true;

  /**
   * Indicates whether this connection is about to be closed. This will be used
   * to prevent accepting new requests while a disconnect is in progress.
   */
  private boolean disconnectRequested;

  /**
   * Indicates whether the connection should keep statistics regarding the
   * operations that it is performing.
   */
  private final boolean keepStats;

  /**
   * The Map (messageID => {@link OperationWithPromise}) of all operations
   * currently in progress on this connection.
   */
  private final Map<Integer, OperationWithPromise> operationsInProgress = new ConcurrentHashMap<>();

  /**
   * The number of operations performed on this connection. Used to compare with
   * the resource limits of the network group.
   */
  private final AtomicLong operationsPerformed = new AtomicLong(0);

  /**
   * The lock used to provide threadsafe access to the map of operations in
   * progress. This is used when we want to prevent puts on this map while we
   * are removing all operations in progress.
   */
  private final Object opsInProgressLock = new Object();

  /** The connection ID assigned to this connection. */
  private final long connectionID;

  /** The reference to the connection handler that accepted this connection. */
  private final HTTPConnectionHandler connectionHandler;

  /** The statistics tracker associated with this client connection. */
  private final HTTPStatistics statTracker;
  private boolean useNanoTime;

  /** Total execution time for this request. */
  private final AtomicLong totalProcessingTime = new AtomicLong();

  /** The protocol in use for this client connection. */
  private final String protocol;

  /** The HTTP method/verb used for this request. */
  private final String method;
  /** The query issued by the client. */
  private final String query;
  /** The user agent used by the client. */
  private final String userAgent;

  /** The username that was used to authenticate. */
  private String authUser;
  /**
   * The HTTP status code returned to the client. Using 0 to say no status code
   * was set since it is not .
   */
  private final AtomicInteger statusCode = new AtomicInteger(0);

  /** The client (remote) address. */
  private final String clientAddress;

  /** The client (remote) host name. */
  private String clientHost;

  /** The client (remote) port. */
  private final int clientPort;

  /** The remote (client) address. */
  private final InetAddress remoteAddress;

  /** The server (local) address. */
  private final String serverAddress;

  /** The server (local) host name. */
  private String serverHost;

  /** The server (local) port. */
  private final int serverPort;

  /** The local (server) address. */
  private final InetAddress localAddress;

  /** Whether this connection is secure. */
  private final boolean isSecure;

  /** Security-Strength Factor extracted from the request attribute. */
  private final int securityStrengthFactor;

  /**
   * Constructs an instance of this class.
   *
   * @param connectionHandler
   *          the connection handler that accepted this connection
   * @param request
   *          represents this client connection.
   */
  public HTTPClientConnection(HTTPConnectionHandler connectionHandler,
      HttpServletRequest request)
  {
    this.connectionHandler = connectionHandler;

    // memoize all the fields we need from the request before Grizzly decides to
    // recycle it
    this.clientAddress = request.getRemoteAddr();
    this.clientPort = request.getRemotePort();
    this.serverAddress = request.getLocalAddr();
    this.serverPort = request.getLocalPort();
    this.remoteAddress = toInetAddress(request.getRemoteAddr());
    this.localAddress = toInetAddress(request.getLocalAddr());
    this.isSecure = request.isSecure();
    this.securityStrengthFactor =
        calcSSF(request.getAttribute(SERVLET_SSF_CONSTANT));
    this.method = request.getMethod();
    this.query = computeQuery(request);
    this.protocol = request.getProtocol();
    this.userAgent = request.getHeader("User-Agent");

    this.statTracker = this.connectionHandler.getStatTracker();

    this.keepStats = connectionHandler.keepStats();
    if (this.keepStats)
    {
      this.statTracker.updateConnect();
      this.useNanoTime = DirectoryServer.getUseNanoTime();
    }

    this.connectionID = DirectoryServer.newConnectionAccepted(this);
  }

  private String computeQuery(HttpServletRequest request)
  {
    if (request.getQueryString() != null)
    {
      return request.getRequestURI() + "?" + request.getQueryString();
    }
    return request.getRequestURI();
  }

  /** {@inheritDoc} */
  @Override
  public String getAuthUser()
  {
    return this.authUser;
  }

  /** {@inheritDoc} */
  @Override
  public long getConnectionID()
  {
    return connectionID;
  }

  /** {@inheritDoc} */
  @Override
  public HTTPConnectionHandler getConnectionHandler()
  {
    return connectionHandler;
  }

  /** {@inheritDoc} */
  @Override
  public long getTotalProcessingTime()
  {
    return totalProcessingTime.get();
  }

  /** {@inheritDoc} */
  @Override
  public String getProtocol()
  {
    return protocol;
  }

  /** {@inheritDoc} */
  @Override
  public String getClientAddress()
  {
    return clientAddress;
  }

  /** {@inheritDoc} */
  @Override
  public String getClientHost()
  {
    return clientHost;
  }

  /** {@inheritDoc} */
  @Override
  public int getClientPort()
  {
    return clientPort;
  }

  /** {@inheritDoc} */
  @Override
  public String getServerAddress()
  {
    return serverAddress;
  }

  /** {@inheritDoc} */
  @Override
  public String getServerHost()
  {
    return serverHost;
  }

  /** {@inheritDoc} */
  @Override
  public int getServerPort()
  {
    return serverPort;
  }

  /** {@inheritDoc} */
  @Override
  public InetAddress getRemoteAddress()
  {
    return remoteAddress;
  }

  /** {@inheritDoc} */
  @Override
  public InetAddress getLocalAddress()
  {
    return localAddress;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isSecure()
  {
    return isSecure;
  }

  /** {@inheritDoc} */
  @Override
  public void sendResponse(Operation operation)
  {
    final long time = getProcessingTime(operation);
    this.totalProcessingTime.addAndGet(time);

    if (keepStats)
    {
      this.statTracker.updateRequestMonitoringData(getMethod(), time);
      this.statTracker.updateOperationMonitoringData(operation
          .getOperationType(), time);
    }

    OperationWithPromise op = this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      try
      {
        op.promise.handleResult(getResponseResult(operation));

        if (keepStats)
        {
          this.statTracker.updateMessageWritten(new LDAPMessage(operation
              .getMessageID(), toResponseProtocolOp(operation)));
        }
      }
      catch (LdapException e)
      {
        op.promise.handleException(e);
      }
    }
  }

  private long getProcessingTime(Operation operation)
  {
    if (useNanoTime)
    {
      return operation.getProcessingNanoTime();
    }
    return operation.getProcessingTime();
  }

  private ProtocolOp toResponseProtocolOp(Operation operation)
  {
    final int resultCode = operation.getResultCode().intValue();
    if (operation instanceof AddOperation)
    {
      return new AddResponseProtocolOp(resultCode);
    }
    else if (operation instanceof BindOperation)
    {
      return new BindResponseProtocolOp(resultCode);
    }
    else if (operation instanceof CompareOperation)
    {
      return new CompareResponseProtocolOp(resultCode);
    }
    else if (operation instanceof DeleteOperation)
    {
      return new DeleteResponseProtocolOp(resultCode);
    }
    else if (operation instanceof ExtendedOperation)
    {
      return new ExtendedResponseProtocolOp(resultCode);
    }
    else if (operation instanceof ModifyDNOperation)
    {
      return new ModifyDNResponseProtocolOp(resultCode);
    }
    else if (operation instanceof ModifyOperation)
    {
      return new ModifyResponseProtocolOp(resultCode);
    }
    else if (operation instanceof SearchOperation)
    {
      return new SearchResultDoneProtocolOp(resultCode);
    }
    throw new RuntimeException("Not implemented for operation " + operation);
  }

  /** {@inheritDoc} */
  @Override
  public void sendSearchEntry(SearchOperation operation,
      SearchResultEntry searchEntry) throws DirectoryException
  {
    SearchOperationWithPromise op =
        (SearchOperationWithPromise) this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      op.entryHandler.handleEntry(from(searchEntry));
      if (keepStats)
      {
        this.statTracker.updateMessageWritten(new LDAPMessage(operation
            .getMessageID(), new SearchResultEntryProtocolOp(searchEntry)));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean sendSearchReference(SearchOperation operation,
      SearchResultReference searchReference) throws DirectoryException
  {
    SearchOperationWithPromise op =
        (SearchOperationWithPromise) this.operationsInProgress.get(operation.getMessageID());
    if (op != null)
    {
      op.entryHandler.handleReference(from(searchReference));
      if (keepStats)
      {
        this.statTracker.updateMessageWritten(new LDAPMessage(operation.getMessageID(),
            new SearchResultReferenceProtocolOp(searchReference)));
      }
    }

    return connectionValid;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean sendIntermediateResponseMessage(
      IntermediateResponse intermediateResponse)
  {
    // if (keepStats)
    // {
    // this.statTracker.updateMessageWritten(new LDAPMessage(
    // intermediateResponse.getOperation().getMessageID(),
    // new IntermediateResponseProtocolOp(intermediateResponse.getOID())));
    // }
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void setAuthUser(String authUser)
  {
    this.authUser = authUser;
  }

  /**
   * {@inheritDoc}
   *
   * @param sendNotification
   *          not used with HTTP.
   */
  @Override
  public void disconnect(DisconnectReason disconnectReason,
      boolean sendNotification, LocalizableMessage message)
  {
    // Set a flag indicating that the connection is being terminated so
    // that no new requests will be accepted. Also cancel all operations
    // in progress.
    synchronized (opsInProgressLock)
    {
      // If we are already in the middle of a disconnect, then don't
      // do anything.
      if (disconnectRequested)
      {
        return;
      }

      disconnectRequested = true;
    }

    if (keepStats)
    {
      statTracker.updateDisconnect();
    }

    if (connectionID >= 0)
    {
      DirectoryServer.connectionClosed(this);
    }

    // Indicate that this connection is no longer valid.
    connectionValid = false;

    if (message != null)
    {
      LocalizableMessageBuilder msgBuilder = new LocalizableMessageBuilder();
      msgBuilder.append(disconnectReason.getClosureMessage());
      msgBuilder.append(": ");
      msgBuilder.append(message);
      cancelAllOperations(new CancelRequest(true, msgBuilder.toMessage()));
    }
    else
    {
      cancelAllOperations(new CancelRequest(true, disconnectReason
          .getClosureMessage()));
    }
    finalizeConnectionInternal();


    this.connectionHandler.removeClientConnection(this);
    logDisconnect(this, disconnectReason, message);
  }

  /** {@inheritDoc} */
  @Override
  public String getMethod()
  {
    return this.method;
  }

  /** {@inheritDoc} */
  @Override
  public String getQuery()
  {
    return this.query;
  }

  /** {@inheritDoc} */
  @Override
  public int getStatusCode()
  {
    return this.statusCode.get();
  }

  /** {@inheritDoc} */
  @Override
  public String getUserAgent()
  {
    return this.userAgent;
  }

  /** {@inheritDoc} */
  @Override
  public Collection<Operation> getOperationsInProgress()
  {
    Collection<OperationWithPromise> values = operationsInProgress.values();
    Collection<Operation> results = new ArrayList<>(values.size());
    for (OperationWithPromise op : values)
    {
      results.add(op.operation);
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public Operation getOperationInProgress(int messageID)
  {
    OperationWithPromise op = operationsInProgress.get(messageID);
    if (op != null)
    {
      return op.operation;
    }
    return null;
  }

  /**
   * Adds the passed in search operation to the in progress list along with the
   * associated promise and the {@code SearchResultHandler}.
   *
   * @param operation
   *          the operation to add to the in progress list
   * @param promise
   *          the promise associated to the operation
   * @param searchResultHandler
   *          the search result handler associated to the promise result
   * @throws DirectoryException
   *           If an error occurs
   */
  void addOperationInProgress(Operation operation, LdapPromiseImpl<Result> promise,
      SearchResultHandler searchResultHandler) throws DirectoryException
  {
    if (searchResultHandler != null)
    {
      addOperationWithPromise(new SearchOperationWithPromise(operation, promise, searchResultHandler));
    }
    else
    {
      addOperationWithPromise(new OperationWithPromise(operation, promise));
    }
  }

  private void addOperationWithPromise(OperationWithPromise opPromise) throws DirectoryException
  {
    synchronized (opsInProgressLock)
    {
      // If we're already in the process of disconnecting the client, then reject the operation.
      if (disconnectRequested)
      {
        LocalizableMessage message = WARN_CLIENT_DISCONNECT_IN_PROGRESS.get();
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      operationsInProgress.put(opPromise.operation.getMessageID(), opPromise);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeOperationInProgress(int messageID)
  {
    final OperationWithPromise previousValue = operationsInProgress.remove(messageID);
    if (previousValue != null)
    {
      operationsPerformed.incrementAndGet();

      final Operation operation = previousValue.operation;
      if (operation.getOperationType() == OperationType.ABANDON
          && keepStats
          && operation.getResultCode() == ResultCode.CANCELLED)
      {
        statTracker.updateAbandonedOperation();
      }
    }
    return previousValue != null;
  }

  /** {@inheritDoc} */
  @Override
  public CancelResult cancelOperation(int messageID, CancelRequest cancelRequest)
  {
    OperationWithPromise op = operationsInProgress.remove(messageID);
    if (op != null)
    {
      op.promise.handleException(newLdapException(ResultCode.CANCELLED));
      return op.operation.cancel(cancelRequest);
    }
    return new CancelResult(ResultCode.NO_SUCH_OPERATION, null);
  }

  private int calcSSF(Object ssf)
  {
    if (ssf instanceof Number)
    {
      return ((Number) ssf).intValue();
    }
    else if (ssf instanceof String)
    {
      try
      {
        return Integer.parseInt((String) ssf);
      }
      catch (IllegalArgumentException ignored)
      {
        // We cannot do much about it. Just log it.
        logger.traceException(ignored);
      }
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void cancelAllOperations(CancelRequest cancelRequest)
  {
    synchronized (opsInProgressLock)
    {
      try
      {
        for (OperationWithPromise op : operationsInProgress.values())
        {
          try
          {
            op.promise.handleException(newLdapException(ResultCode.CANCELLED));
            op.operation.abort(cancelRequest);

            if (keepStats)
            {
              statTracker.updateAbandonedOperation();
            }
          }
          catch (Exception e)
          { // make sure all operations are cancelled, no matter what
            logger.traceException(e);
          }
        }

        operationsInProgress.clear();
      }
      catch (Exception e)
      { // TODO JNR should I keep this catch?
        logger.traceException(e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void cancelAllOperationsExcept(CancelRequest cancelRequest,
      int messageID)
  {
    synchronized (opsInProgressLock)
    {
      OperationWithPromise toKeep = operationsInProgress.remove(messageID);
      try
      {
        cancelAllOperations(cancelRequest);
      }
      finally
      { // Ensure we always put back this operation
        operationsInProgress.put(messageID, toKeep);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfOperations()
  {
    return this.operationsPerformed.get();
  }

  /** {@inheritDoc} */
  @Override
  public String getMonitorSummary()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("connID=\"");
    buffer.append(getConnectionID());
    buffer.append("\" connectTime=\"");
    buffer.append(getConnectTimeString());
    buffer.append("\" source=\"");
    buffer.append(getClientAddress());
    buffer.append(":");
    buffer.append(getClientPort());
    buffer.append("\" destination=\"");
    buffer.append(getServerAddress());
    buffer.append(":");
    buffer.append(connectionHandler.getListenPort());
    buffer.append("\" authDN=\"");
    DN authDN = getAuthenticationInfo().getAuthenticationDN();
    if (authDN != null)
    {
      authDN.toString(buffer);
    }
    return buffer.toString();
  }

  private InetAddress toInetAddress(String address)
  {
    try
    {
      return InetAddress.getByName(address);
    }
    catch (UnknownHostException e)
    {
      throw new RuntimeException("Should never happen", e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("HTTP client connection from ");
    buffer.append(getClientAddress()).append(":").append(getClientPort());
    buffer.append(" to ");
    buffer.append(getServerAddress()).append(":").append(getServerPort());
  }

  /**
   * Returns the statTracker for this connection handler.
   *
   * @return the statTracker for this connection handler
   */
  public HTTPStatistics getStatTracker()
  {
    return statTracker;
  }

  /** {@inheritDoc} */
  @Override
  public int getSSF()
  {
    return securityStrengthFactor;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConnectionValid()
  {
    return connectionValid;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInnerConnection()
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void log(int statusCode)
  {
    if (this.statusCode.compareAndSet(0, statusCode))
    { // this request was not logged before
      HTTPAccessLogger.logRequestInfo(this);
    }
  }
}
