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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import static org.opends.sdk.CoreMessages.INFO_SASL_UNSUPPORTED_CALLBACK;

import java.io.IOException;

import javax.security.auth.callback.*;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Writer;



/**
 * SASL bind client implementation.
 */
class SASLBindClientImpl extends BindClientImpl implements CallbackHandler
{
  /**
   * The name of the default protocol used.
   */
  static final String SASL_DEFAULT_PROTOCOL = "ldap";

  private final String saslMechanism;



  /**
   * Creates a new abstract SASL bind client. The next bind request will be a
   * copy of the provided initial bind request which should be updated in
   * subsequent bind requests forming part of this authentication.
   *
   * @param initialBindRequest
   *          The initial bind request.
   */
  SASLBindClientImpl(final SASLBindRequest initialBindRequest)
  {
    super(initialBindRequest);
    this.saslMechanism = initialBindRequest.getSASLMechanism();
  }



  public final void handle(final Callback[] callbacks) throws IOException,
      UnsupportedCallbackException
  {
    for (final Callback callback : callbacks)
    {
      if (callback instanceof NameCallback)
      {
        handle((NameCallback) callback);
      }
      else if (callback instanceof PasswordCallback)
      {
        handle((PasswordCallback) callback);
      }
      else if (callback instanceof AuthorizeCallback)
      {
        handle((AuthorizeCallback) callback);
      }
      else if (callback instanceof RealmCallback)
      {
        handle((RealmCallback) callback);
      }
      else if (callback instanceof RealmChoiceCallback)
      {
        handle((RealmChoiceCallback) callback);
      }
      else if (callback instanceof ChoiceCallback)
      {
        handle((ChoiceCallback) callback);
      }
      else if (callback instanceof ConfirmationCallback)
      {
        handle((ConfirmationCallback) callback);
      }
      else if (callback instanceof LanguageCallback)
      {
        handle((LanguageCallback) callback);
      }
      else if (callback instanceof TextInputCallback)
      {
        handle((TextInputCallback) callback);
      }
      else if (callback instanceof TextOutputCallback)
      {
        handle((TextOutputCallback) callback);
      }
      else
      {
        final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
            .get(saslMechanism, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
      }
    }
  }



  void handle(final AuthorizeCallback callback)
      throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final ChoiceCallback callback)
      throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final ConfirmationCallback callback)
      throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final LanguageCallback callback)
      throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final NameCallback callback) throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final PasswordCallback callback)
      throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final RealmCallback callback) throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final RealmChoiceCallback callback)
      throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final TextInputCallback callback)
      throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  void handle(final TextOutputCallback callback)
      throws UnsupportedCallbackException
  {
    final org.forgerock.i18n.LocalizableMessage message = INFO_SASL_UNSUPPORTED_CALLBACK
        .get(saslMechanism, String.valueOf(callback));
    throw new UnsupportedCallbackException(callback, message.toString());
  }



  /**
   * Sets the SASL credentials to be used in the next bind request.
   *
   * @param saslCredentials
   *          The SASL credentials to be used in the next bind request.
   * @return A reference to this SASL bind client.
   */
  final BindClient setNextSASLCredentials(final byte[] saslCredentials)
  {
    final ByteString value = (saslCredentials != null) ? ByteString
        .wrap(saslCredentials) : null;
    return setNextSASLCredentials(value);
  }



  /**
   * Sets the SASL credentials to be used in the next bind request.
   *
   * @param saslCredentials
   *          The SASL credentials to be used in the next bind request.
   * @return A reference to this SASL bind client.
   */
  final BindClient setNextSASLCredentials(final ByteString saslCredentials)
  {
    final ByteStringBuilder builder = new ByteStringBuilder();
    final ASN1Writer writer = ASN1.getWriter(builder);

    try
    {
      writer.writeOctetString(saslMechanism);
      if (saslCredentials != null)
      {
        writer.writeOctetString(saslCredentials);
      }
    }
    catch (final IOException ioe)
    {
      throw new RuntimeException("Error encoding SaslCredentials");
    }

    return setNextAuthenticationValue(builder.toByteString());
  }
}
