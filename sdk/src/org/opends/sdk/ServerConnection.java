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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import org.opends.sdk.requests.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.CompareResult;
import org.opends.sdk.responses.ExtendedResult;
import org.opends.sdk.responses.Result;



/**
 * A handler interface for processing requests from clients.
 * <p>
 * Implementations should always return results via the provided
 * {@code RequestHandler} when processing requests unless otherwise indicated by
 * the component passing requests to the {@code ServerConnection}. For example,
 * an {@link LDAPListener} does not require {@code ServerConnection}
 * implementations to return results, which may be useful when implementing
 * abandon operation functionality.
 * <p>
 * Note that {@code ServerConnection}s may be stacked, and that a lower
 * {@code ServerConnection} implementation, such as a logger, may require upper
 * {@code ServerConnection} implementations to always return results.
 *
 * @param <C>
 *          The type of request context.
 * @see ServerConnectionFactory
 */
public interface ServerConnection<C>
{

  /**
   * Invoked when an abandon request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The abandon request.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle abandon requests.
   */
  void handleAbandon(C requestContext, AbandonRequest request)
      throws UnsupportedOperationException;



  /**
   * Invoked when an add request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The add request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle add requests.
   */
  void handleAdd(C requestContext, AddRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a bind request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param version
   *          The protocol version included with the bind request.
   * @param request
   *          The bind request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle bind requests.
   */
  void handleBind(C requestContext, int version, BindRequest request,
      ResultHandler<? super BindResult> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a compare request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The compare request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle compare requests.
   */
  void handleCompare(C requestContext, CompareRequest request,
      ResultHandler<? super CompareResult> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when the client closes the connection, possibly using an unbind
   * request.
   *
   * @param requestContext
   *          The request context which should be ignored if there was no
   *          associated unbind request.
   * @param request
   *          The unbind request, which may be {@code null} if one was not sent
   *          before the connection was closed.
   */
  void handleConnectionClosed(C requestContext, UnbindRequest request);



  /**
   * Invoked when the server disconnects the client connection, possibly using a
   * disconnect notification.
   *
   * @param resultCode
   *          The result code which was included with the disconnect
   *          notification, or {@code null} if no disconnect notification was
   *          sent.
   * @param message
   *          The diagnostic message, which may be empty or {@code null}
   *          indicating that none was provided.
   */
  void handleConnectionDisconnected(ResultCode resultCode, String message);



  /**
   * Invoked when an error occurs on the connection and it is no longer usable.
   *
   * @param error
   *          The exception describing the problem that occurred.
   */
  void handleConnectionError(Throwable error);



  /**
   * Invoked when a delete request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The delete request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle delete requests.
   */
  void handleDelete(C requestContext, DeleteRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when an extended request is received from a client.
   *
   * @param <R>
   *          The type of result returned by the extended request.
   * @param requestContext
   *          The request context.
   * @param request
   *          The extended request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle extended requests.
   */
  <R extends ExtendedResult> void handleExtendedRequest(C requestContext,
      ExtendedRequest<R> request, ResultHandler<? super R> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a modify request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The modify request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle modify requests.
   */
  void handleModify(C requestContext, ModifyRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a modify DN request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The modify DN request.
   * @param resultHandler
   *          The handler which should be used to send back the result to the
   *          client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle modify DN requests.
   */
  void handleModifyDN(C requestContext, ModifyDNRequest request,
      ResultHandler<? super Result> resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;



  /**
   * Invoked when a search request is received from a client.
   *
   * @param requestContext
   *          The request context.
   * @param request
   *          The search request.
   * @param resultHandler
   *          The handler which should be used to send back the search results
   *          to the client.
   * @param intermediateResponseHandler
   *          The handler which should be used to send back any intermediate
   *          responses to the client.
   * @throws UnsupportedOperationException
   *           If this server connection does not handle search requests.
   */
  void handleSearch(C requestContext, SearchRequest request,
      SearchResultHandler resultHandler,
      IntermediateResponseHandler intermediateResponseHandler)
      throws UnsupportedOperationException;
}
