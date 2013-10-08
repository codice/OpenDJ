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
 *      Copyright 2013 ForgeRock AS.
 */
package com.forgerock.opendj.ldap;

import java.io.IOException;
import java.net.SocketAddress;

import org.forgerock.opendj.ldap.LDAPClientContext;
import org.forgerock.opendj.ldap.LDAPListenerOptions;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.ServerConnectionFactory;
import org.forgerock.opendj.ldap.spi.LDAPConnectionFactoryImpl;
import org.forgerock.opendj.ldap.spi.LDAPListenerImpl;
import org.forgerock.opendj.ldap.spi.TransportProvider;

/**
 * Provides an implementation of {@code LDAPListener} using Grizzly as
 * transport.
 * <p>
 * To be used, this implementation must be declared in the
 * provider-configuration file
 * {@code META-INF/services/org.forgerock.opendj.ldap.spi.LDAPListenerProvider}
 * with this single line:
 *
 * <pre>
 * com.forgerock.opendj.ldap.GrizzlyLDAPListenerProvider
 * </pre>.
 * <p>
 * To require that this implementation is used, you must set the transport
 * provider to {@code Grizzly} using
 * {@code LDAPListenerOptions#setTransportProvider(String)} method. Otherwise
 * there is no guarantee that this implementation will be used.
 */
public class GrizzlyTransportProvider implements TransportProvider {

    /** {@inheritDoc} */
    @Override
    public LDAPConnectionFactoryImpl getLDAPConnectionFactory(SocketAddress address, LDAPOptions options) {
        return new GrizzlyLDAPConnectionFactory(address, options);
    }

    /** {@inheritDoc} */
    @Override
    public LDAPListenerImpl getLDAPListener(
            SocketAddress address,
            ServerConnectionFactory<LDAPClientContext, Integer> factory,
            LDAPListenerOptions options)
            throws IOException {
        return new GrizzlyLDAPListener(address, factory, options);
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Grizzly";
    }

}
