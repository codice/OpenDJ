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
 *      Portions copyright 2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import org.testng.annotations.DataProvider;

/**
 * @author sin
 */
@SuppressWarnings("javadoc")
public class CompareRequestTestCase extends RequestTestCase {
    @DataProvider(name = "CompareRequests")
    public Object[][] getCompareRequests() throws Exception {
        return getTestRequests();
    }

    @Override
    protected CompareRequest[] createTestRequests() throws Exception {
        return new CompareRequest[] {
                Requests.newCompareRequest("uid=user.0,ou=people,o=test", "cn", "user.0"),
                Requests.newCompareRequest("uid=user.0,ou=people,o=test", "uid", "user.0")
        };
    }

}
