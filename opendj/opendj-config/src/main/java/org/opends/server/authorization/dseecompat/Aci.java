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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2013 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

/**
 * The Aci class represents ACI strings.
 */
public class Aci implements Comparable<Aci> {
    // TODO : to complete when implementing Aci support.

    @Override
    public int compareTo(Aci o) {
        throw new RuntimeException("This class is not implemented");
    }

    /**
     * Decode an ACI byte string.
     *
     * @param byteString
     *            The ByteString containing the ACI string.
     * @param dn
     *            DN of the ACI entry.
     * @return Returns a decoded ACI representing the string argument.
     * @throws LocalizedIllegalArgumentException
     *             If the parsing of the ACI string fails.
     */
    public static Aci decode(ByteString byteString, DN dn) {
        throw new RuntimeException("This class is not implemented");
    }
}
