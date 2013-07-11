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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.types;

import static org.opends.server.loggers.debug.DebugLogger.*;

import java.util.*;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.ClientConnection;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.operation.PostResponseOperation;
import org.opends.server.types.operation.PreParseOperation;
import org.opends.server.util.Validator;


/**
 * This class defines a generic operation that may be processed by the
 * Directory Server.  Specific subclasses should implement specific
 * functionality appropriate for the type of operation.
 * <BR><BR>
 * Note that this class is not intended to be subclassed by any
 * third-party code outside of the OpenDJ project.  It should only be
 * extended by the operation types included in the
 * {@code org.opends.server.core} package.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public abstract class AbstractOperation
       implements Operation, PreParseOperation, PostResponseOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The set of response controls that will always be returned for
   * an abandon operation.
   */
  protected static final List<Control> NO_RESPONSE_CONTROLS =
       new ArrayList<Control>(0);

  /**
   * The client connection with which this operation is associated.
   */
  protected final ClientConnection clientConnection;

  /**
   * The message ID for this operation.
   */
  protected final int messageID;

  /**
   * The operation ID for this operation.
   */
  protected final long operationID;

  /**
   * Whether nanotime was used for this operation.
   */
  protected final boolean useNanoTime;

  /**
   * The cancel request for this operation.
   */
  protected CancelRequest cancelRequest;


  /**
   * The cancel result for this operation.
   */
  protected CancelResult cancelResult;

  /**
   * Indicates whether this is an internal operation triggered within the server
   * itself rather than requested by an external client.
   */
  private boolean isInternalOperation;
  private Boolean isInnerOperation;

  /**
   * Indicates whether this operation is involved in data synchronization
   * processing.
   */
  private boolean isSynchronizationOperation;

  /** The matched DN for this operation. */
  private DN matchedDN;

  /** The entry for the authorization identify for this operation. */
  private Entry authorizationEntry;

  /**
   * A set of attachments associated with this operation that might be used by
   * various components during its processing.
   */
  private Map<String,Object> attachments;

  /** The set of controls included in the request from the client. */
  private List<Control> requestControls;

  /** The set of referral URLs for this operation. */
  private List<String> referralURLs;

  /** The result code for this operation. */
  private ResultCode resultCode;

  /**
   * Additional information that should be included in the log but not sent to
   * the client.
   */
  private List<AdditionalLogItem> additionalLogItems;

  /**
   * The error message for this operation that should be included in the log and
   * in the response to the client.
   */
  private MessageBuilder errorMessage;

  /**
   * Indicates whether this operation needs to be synchronized to other copies
   * of the data.
   */
  private boolean dontSynchronizeFlag;

  /** The time that processing started on this operation in milliseconds. */
  private long processingStartTime;

  /** The time that processing ended on this operation in milliseconds. */
  private long processingStopTime;

  /** The time that processing started on this operation in nanoseconds. */
  private long processingStartNanoTime;

  /** The time that processing ended on this operation in nanoseconds. */
  private long processingStopNanoTime;

  /** The callbacks to be invoked once a response has been sent. */
  private List<Runnable> postResponseCallbacks = null;

  /**
   * Creates a new operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this
   *                           operation is associated.
   * @param  operationID       The identifier assigned to this
   *                           operation for the client connection.
   * @param  messageID         The message ID of the request with
   *                           which this operation is associated.
   * @param  requestControls   The set of controls included in the
   *                           request.
   */
  protected AbstractOperation(ClientConnection clientConnection,
                      long operationID,
                      int messageID, List<Control> requestControls)
  {
    this.clientConnection = clientConnection;
    this.operationID      = operationID;
    this.messageID        = messageID;
    this.useNanoTime = DirectoryServer.getUseNanoTime();

    if (requestControls == null)
    {
      this.requestControls = new ArrayList<Control>(0);
    }
    else
    {
      this.requestControls  = requestControls;
    }

    resultCode                 = ResultCode.UNDEFINED;
    additionalLogItems         = null;
    errorMessage               = new MessageBuilder();
    attachments                = new HashMap<String,Object>();
    matchedDN                  = null;
    referralURLs               = null;
    cancelResult               = null;
    isInternalOperation        = false;
    isSynchronizationOperation = false;
    authorizationEntry         =
         clientConnection.getAuthenticationInfo().
          getAuthorizationEntry();
  }



  /**
   * Terminates the client connection being used to process this
   * operation.  If this is called by a plugin, then that plugin must
   * return a result indicating that the  client connection has been
   * terminated.
   *
   * @param  disconnectReason  The disconnect reason that provides the
   *                           generic cause for the disconnect.
   * @param  sendNotification  Indicates whether to try to provide
   *                           notification
   *                           to the client that the connection will
   *                           be closed.
   * @param  message           The message to send to the client.  It
   *                           may be {@code null} if no notification
   *                           is to be sent.
   */
  @Override
  public void disconnectClient(DisconnectReason disconnectReason,
                               boolean sendNotification,
                               Message message)
  {
    clientConnection.disconnect(disconnectReason, sendNotification,
            message);
  }



  /**
   * Retrieves the client connection with which this operation is
   * associated.
   *
   * @return  The client connection with which this operation is
   *          associated.
   */
  @Override
  public final ClientConnection getClientConnection()
  {
    return clientConnection;
  }



  /**
   * Retrieves the unique identifier that is assigned to the client
   * connection that submitted this operation.
   *
   * @return  The unique identifier that is assigned to the client
   *          connection that submitted this operation.
   */
  @Override
  public final long getConnectionID()
  {
    return clientConnection.getConnectionID();
  }

  /** {@inheritDoc} */
  @Override
  public final long getOperationID()
  {
    return operationID;
  }

  /** {@inheritDoc} */
  @Override
  public final int getMessageID()
  {
    return messageID;
  }

  /** {@inheritDoc} */
  @Override
  public final List<Control> getRequestControls()
  {
    return requestControls;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public final <T extends Control> T getRequestControl(
      ControlDecoder<T> d) throws DirectoryException
  {
    String oid = d.getOID();
    for(int i = 0; i < requestControls.size(); i++)
    {
      Control c = requestControls.get(i);
      if(c.getOID().equals(oid))
      {
        if(c instanceof LDAPControl)
        {
          T decodedControl = d.decode(c.isCritical(),
              ((LDAPControl) c).getValue());
          requestControls.set(i, decodedControl);
          return decodedControl;
        }
        else
        {
          return (T)c;
        }
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public final void addRequestControl(Control control)
  {
    requestControls.add(control);
  }

  /** {@inheritDoc} */
  @Override
  public final void removeRequestControl(Control control)
  {
    requestControls.remove(control);
  }



  /** {@inheritDoc} */
  @Override
  public final ResultCode getResultCode()
  {
    return resultCode;
  }

  /** {@inheritDoc} */
  @Override
  public final void setResultCode(ResultCode resultCode)
  {
    this.resultCode = resultCode;
  }

  /** {@inheritDoc} */
  @Override
  public final MessageBuilder getErrorMessage()
  {
    return errorMessage;
  }

  /** {@inheritDoc} */
  @Override
  public final void setErrorMessage(MessageBuilder errorMessage)
  {
    if (errorMessage == null)
    {
      this.errorMessage = new MessageBuilder();
    }
    else
    {
      this.errorMessage = errorMessage;
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void appendErrorMessage(Message message)
  {
    if (errorMessage == null)
    {
      errorMessage = new MessageBuilder(message);
    }
    else
    {
      if (errorMessage.length() > 0)
      {
        errorMessage.append("  ");
      }

      errorMessage.append(message);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public List<AdditionalLogItem> getAdditionalLogItems()
  {
    if (additionalLogItems == null)
    {
      return Collections.emptyList();
    }
    else
    {
      return Collections.unmodifiableList(additionalLogItems);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void addAdditionalLogItem(AdditionalLogItem item)
  {
    Validator.ensureNotNull(item);
    if (additionalLogItems == null)
    {
      additionalLogItems = new LinkedList<AdditionalLogItem>();
    }
    additionalLogItems.add(item);
  }

  /** {@inheritDoc} */
  @Override
  public final DN getMatchedDN()
  {
    return matchedDN;
  }

  /** {@inheritDoc} */
  @Override
  public final void setMatchedDN(DN matchedDN)
  {
    this.matchedDN = matchedDN;
  }

  /** {@inheritDoc} */
  @Override
  public final List<String> getReferralURLs()
  {
    return referralURLs;
  }

  /** {@inheritDoc} */
  @Override
  public final void setReferralURLs(List<String> referralURLs)
  {
    this.referralURLs = referralURLs;
  }

  /** {@inheritDoc} */
  @Override
  public final void setResponseData(
                         DirectoryException directoryException)
  {
    this.resultCode   = directoryException.getResultCode();
    this.matchedDN    = directoryException.getMatchedDN();
    this.referralURLs = directoryException.getReferralURLs();

    appendErrorMessage(directoryException.getMessageObject());
  }



  /** {@inheritDoc} */
  @Override
  public final boolean isInternalOperation()
  {
    return isInternalOperation;
  }

  /** {@inheritDoc} */
  @Override
  public final void setInternalOperation(boolean isInternalOperation)
  {
    this.isInternalOperation = isInternalOperation;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isInnerOperation()
  {
    if (this.isInnerOperation != null)
    {
      return this.isInnerOperation;
    }
    return isInternalOperation();
  }

  /** {@inheritDoc} */
  @Override
  public void setInnerOperation(boolean isInnerOperation)
  {
    this.isInnerOperation = isInnerOperation;
  }


  /** {@inheritDoc} */
  @Override
  public final boolean isSynchronizationOperation()
  {
    return isSynchronizationOperation;
  }

  /** {@inheritDoc} */
  @Override
  public final void setSynchronizationOperation(
                         boolean isSynchronizationOperation)
  {
    this.isSynchronizationOperation = isSynchronizationOperation;
  }



  /**
   * Indicates whether this operation needs to be synchronized to
   * other copies of the data.
   *
   * @return  {@code true} if this operation should not be
   *          synchronized, or {@code false} if it should be
   *          synchronized.
   */
  @Override
  public boolean dontSynchronize()
  {
    return dontSynchronizeFlag;
  }

  /** {@inheritDoc} */
  @Override
  public final void setDontSynchronize(boolean dontSynchronize)
  {
    this.dontSynchronizeFlag = dontSynchronize;
  }

  /** {@inheritDoc} */
  @Override
  public final Entry getAuthorizationEntry()
  {
    return authorizationEntry;
  }

  /** {@inheritDoc} */
  @Override
  public final void setAuthorizationEntry(Entry authorizationEntry)
  {
    this.authorizationEntry = authorizationEntry;
  }

  /** {@inheritDoc} */
  @Override
  public final DN getAuthorizationDN()
  {
    if (authorizationEntry == null)
    {
      return DN.nullDN();
    }
    else
    {
      return authorizationEntry.getDN();
    }
  }

  /** {@inheritDoc} */
  @Override
  public final Map<String,Object> getAttachments()
  {
    return attachments;
  }



  /**
   * Set the attachments to the operation.
   *
   * @param attachments - Attachments to register within the
   *                      operation
   */
  @Override
  public final void setAttachments(Map<String, Object> attachments)
  {
    this.attachments = attachments;
  }

  /** {@inheritDoc} */
  @Override
  public final Object getAttachment(String name)
  {
    return attachments.get(name);
  }

  /** {@inheritDoc} */
  @Override
  public final Object removeAttachment(String name)
  {
    return attachments.remove(name);
  }

  /** {@inheritDoc} */
  @Override
  public final Object setAttachment(String name, Object value)
  {
    return attachments.put(name, value);
  }

  /** {@inheritDoc} */
  @Override
  public final void operationCompleted()
  {
    // Notify the client connection that this operation is complete
    // and that it no longer needs to be retained.
    clientConnection.removeOperationInProgress(messageID);
  }

  /** {@inheritDoc} */
  @Override
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    abort(cancelRequest);

    long stopWaitingTime = System.currentTimeMillis() + 5000;
    while ((cancelResult == null) &&
        (System.currentTimeMillis() < stopWaitingTime))
    {
      try
      {
        Thread.sleep(50);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    if (cancelResult == null)
    {
      // This can happen in some rare cases (e.g., if a client
      // disconnects and there is still a lot of data to send to
      // that client), and in this case we'll prevent the cancel
      // thread from blocking for a long period of time.
      cancelResult = new CancelResult(ResultCode.CANNOT_CANCEL, null);
    }

    return cancelResult;
  }



  /**
   * Attempts to cancel this operation before processing has
   * completed without waiting for a cancel result.
   *
   * @param  cancelRequest  Information about the way in which the
   *                        operation should be canceled.
   */
  @Override
  public synchronized void abort(CancelRequest cancelRequest)
  {
    if(cancelResult == null && this.cancelRequest == null)
    {
      this.cancelRequest = cancelRequest;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized final void
    checkIfCanceled(boolean signalTooLate)
      throws CanceledOperationException {
    if(cancelRequest != null)
    {
      throw new CanceledOperationException(cancelRequest);
    }

    if(signalTooLate && cancelResult != null)
    {
      cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final CancelResult getCancelResult()
  {
    return cancelResult;
  }

  /** {@inheritDoc} */
  @Override
  public final String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /** {@inheritDoc} */
  @Override
  public final long getProcessingStartTime()
  {
    return processingStartTime;
  }



  /**
   * Set the time at which the processing started for this operation.
   */
  public final void setProcessingStartTime()
  {
    processingStartTime = System.currentTimeMillis();
    if(useNanoTime)
    {
      processingStartNanoTime = System.nanoTime();
    }
  }

  /** {@inheritDoc} */
  @Override
  public final long getProcessingStopTime()
  {
    return processingStopTime;
  }



  /**
   * Set the time at which the processing stopped for this operation.
   * This will actually hold a time immediately before the response
   * was sent to the client.
   */
  public final void setProcessingStopTime()
  {
    this.processingStopTime = System.currentTimeMillis();
    if(useNanoTime)
    {
      this.processingStopNanoTime = System.nanoTime();
    }
  }



  /**
   * Retrieves the length of time in milliseconds that
   * the server spent processing this operation.  This should not be
   * called until after the server has sent the response to the
   * client.
   *
   * @return  The length of time in milliseconds that
   *          the server spent processing this operation.
   */
  @Override
  public final long getProcessingTime()
  {
    return (processingStopTime - processingStartTime);
  }

  /** {@inheritDoc} */
  @Override
  public final long getProcessingNanoTime()
  {
    if(useNanoTime)
    {
      return (processingStopNanoTime - processingStartNanoTime);
    }
    else
    {
      return -1;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final void registerPostResponseCallback(Runnable callback)
  {
    if (postResponseCallbacks == null)
    {
      postResponseCallbacks = new LinkedList<Runnable>();
    }
    postResponseCallbacks.add(callback);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final int hashCode()
  {
    return clientConnection.hashCode() * (int) operationID;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }

    if (obj instanceof Operation)
    {
      Operation other = (Operation) obj;
      if (other.getClientConnection().equals(clientConnection))
      {
        return other.getOperationID() == operationID;
      }
    }

    return false;
  }



  /**
   * Invokes the post response callbacks that were registered with
   * this operation.
   */
  protected final void invokePostResponseCallbacks()
  {
    if (postResponseCallbacks != null)
    {
      for (Runnable callback : postResponseCallbacks)
      {
        try
        {
          callback.run();
        }
        catch (Exception e)
        {
          // Should not happen.
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
  }
}

