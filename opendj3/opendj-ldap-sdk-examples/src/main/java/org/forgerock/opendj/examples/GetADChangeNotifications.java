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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.examples;

import java.io.IOException;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 * An example client application which searches Microsoft Active Directory
 * synchronously, using {@link GenericControl} to pass the <a
 * href="http://msdn.microsoft.com/en-us/library/windows/desktop/ms676877(v=vs.85).aspx"
 * >Microsoft LDAP Notification control</a>.
 *
 * <p>This example is a near copy of {@link Search}, but works only with
 * directory servers like Active Directory that support the control with OID
 * 1.2.840.113556.1.4.528.
 *
 * <p>This example takes the following command line parameters:
 *
 * <pre>
 *  &lt;host> &lt;port> &lt;username> &lt;password> &lt;baseDN>
 * </pre>
 *
 * <p>The {@code baseDN} must be the root of a naming context in this example.
 */
@SuppressWarnings("resource")
public final class GetADChangeNotifications {
    /**
     * Main method.
     *
     * @param args
     *            The command line arguments: host, port, username, password,
     *            base DN, where the base DN is the root of a naming context.
     */
    public static void main(final String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: host port username password baseDN");
            System.exit(1);
        }

        // Parse command line arguments.
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        final String userName = args[2];
        final String password = args[3];
        final String baseDN = args[4];

        // See http://msdn.microsoft.com/en-us/library/windows/desktop/aa772153(v=vs.85).aspx
        // --- JCite ---
        final SearchScope scope = SearchScope.WHOLE_SUBTREE;
        final String filter = "(objectclass=*)";
        final String[] attributes = {
            "objectclass", "objectGUID", "isDeleted", "uSNChanged"
        };

        // Create an LDIF writer which will write the search results to stdout.
        final LDIFEntryWriter writer = new LDIFEntryWriter(System.out);

        // Connect and bind to the server.
        final LDAPConnectionFactory factory =
                new LDAPConnectionFactory(hostName, port);
        Connection connection = null;

        try {
            connection = factory.getConnection();
            connection.bind(userName, password.toCharArray());

            // Read the entries and output them as LDIF.
            final SearchRequest request =
                    Requests
                            .newSearchRequest(baseDN, scope, filter, attributes)
                            .addControl(
                                    GenericControl
                                            .newControl(
                                                    "1.2.840.113556.1.4.528",
                                                    true));
            final ConnectionEntryReader reader = connection.search(request);
            while (reader.hasNext()) {
                if (!reader.isReference()) {
                    final SearchResultEntry entry = reader.readEntry();
                    writer.writeComment("Search result entry: "
                            + entry.getName().toString());
                    writer.writeEntry(entry);
                    writer.flush();
                } else {
                    final SearchResultReference ref = reader.readReference();

                    // Got a continuation reference.
                    writer.writeComment("Search result reference: "
                            + ref.getURIs().toString());
                }
            }
        } catch (final ErrorResultException e) {
            System.err.println(e.getMessage());
            System.exit(e.getResult().getResultCode().intValue());
        } catch (final ErrorResultIOException e) {
            System.err.println(e.getMessage());
            System.exit(e.getCause().getResult().getResultCode().intValue());
        } catch (final IOException e) {
            System.err.println(e.getMessage());
            System.exit(ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        // --- JCite ---
    }

    private GetADChangeNotifications() {
        // Not used.
    }
}
