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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * A Generic Extended result indicates the final status of an Generic Extended
 * operation.
 */
public interface GenericExtendedResult extends ExtendedResult {
    /**
     * {@inheritDoc}
     */
    GenericExtendedResult addControl(Control control);

    /**
     * {@inheritDoc}
     */
    GenericExtendedResult addReferralURI(String uri);

    /**
     * {@inheritDoc}
     */
    Throwable getCause();

    /**
     * {@inheritDoc}
     */
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    /**
     * {@inheritDoc}
     */
    List<Control> getControls();

    /**
     * {@inheritDoc}
     */
    String getDiagnosticMessage();

    /**
     * {@inheritDoc}
     */
    String getMatchedDN();

    /**
     * {@inheritDoc}
     */
    String getOID();

    /**
     * {@inheritDoc}
     */
    List<String> getReferralURIs();

    /**
     * {@inheritDoc}
     */
    ResultCode getResultCode();

    /**
     * {@inheritDoc}
     */
    ByteString getValue();

    /**
     * {@inheritDoc}
     */
    boolean hasValue();

    /**
     * {@inheritDoc}
     */
    boolean isReferral();

    /**
     * {@inheritDoc}
     */
    boolean isSuccess();

    /**
     * {@inheritDoc}
     */
    GenericExtendedResult setCause(Throwable cause);

    /**
     * {@inheritDoc}
     */
    GenericExtendedResult setDiagnosticMessage(String message);

    /**
     * {@inheritDoc}
     */
    GenericExtendedResult setMatchedDN(String dn);

    /**
     * Sets the numeric OID, if any, associated with this extended result.
     *
     * @param oid
     *            The numeric OID associated with this extended result, or
     *            {@code null} if there is no value.
     * @return This generic extended result.
     * @throws UnsupportedOperationException
     *             If this generic extended result does not permit the result
     *             name to be set.
     */
    GenericExtendedResult setOID(String oid);

    /**
     * {@inheritDoc}
     */
    GenericExtendedResult setResultCode(ResultCode resultCode);

    /**
     * Sets the value, if any, associated with this extended result. Its format
     * is defined by the specification of this extended result.
     *
     * @param bytes
     *            The value associated with this extended result, or
     *            {@code null} if there is no value.
     * @return This generic extended result.
     * @throws UnsupportedOperationException
     *             If this generic extended result does not permit the result
     *             value to be set.
     */
    GenericExtendedResult setValue(ByteString bytes);

}
