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
 *      Portions copyright 2011 ForgeRock AS
 */

package com.forgerock.opendj.ldap;

import java.io.IOException;

import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;

/**
 * The default {@link TCPNIOTransport} which all {@code LDAPConnectionFactory}s
 * and {@code LDAPListener}s will use unless otherwise specified in their
 * options.
 */
final class DefaultTCPNIOTransport {
    private static TCPNIOTransport defaultTransport = null;

    /**
     * Returns the default {@link TCPNIOTransport} which all
     * {@code LDAPConnectionFactory}s and {@code LDAPListener}s will use unless
     * otherwise specified in their options.
     *
     * @return The default {@link TCPNIOTransport}.
     */
    static synchronized TCPNIOTransport getInstance() {
        if (defaultTransport == null) {
            final TCPNIOTransportBuilder builder = TCPNIOTransportBuilder.newInstance();

            // Determine which threading strategy to use, and total number of
            // threads.
            final String useWorkerThreadsStr =
                    System.getProperty("org.forgerock.opendj.transport.useWorkerThreads");
            final boolean useWorkerThreadStrategy;
            if (useWorkerThreadsStr != null) {
                useWorkerThreadStrategy = Boolean.parseBoolean(useWorkerThreadsStr);
            } else {
                // The most best performing strategy to use is the
                // SameThreadIOStrategy,
                // however it can only be used in cases where result listeners
                // will not
                // block.
                useWorkerThreadStrategy = true;
            }

            if (useWorkerThreadStrategy) {
                builder.setIOStrategy(WorkerThreadIOStrategy.getInstance());
            } else {
                builder.setIOStrategy(SameThreadIOStrategy.getInstance());
            }

            // Calculate thread counts.
            final int cpus = Runtime.getRuntime().availableProcessors();

            // Calculate the number of selector threads.
            final String selectorsStr =
                    System.getProperty("org.forgerock.opendj.transport.selectors");
            final int selectorThreadCount;

            if (selectorsStr != null) {
                selectorThreadCount = Integer.parseInt(selectorsStr);
            } else {
                selectorThreadCount =
                        useWorkerThreadStrategy ? Math.max(2, cpus / 4) : Math.max(5,
                                (cpus / 2) - 1);
            }

            builder.getSelectorThreadPoolConfig().setCorePoolSize(selectorThreadCount)
                    .setMaxPoolSize(selectorThreadCount).setPoolName(
                            "OpenDJ LDAP SDK Grizzly selector thread");

            // Calculate the number of worker threads.
            if (builder.getWorkerThreadPoolConfig() != null) {
                final String workersStr =
                        System.getProperty("org.forgerock.opendj.transport.workers");
                final int workerThreadCount;

                if (workersStr != null) {
                    workerThreadCount = Integer.parseInt(workersStr);
                } else {
                    workerThreadCount = useWorkerThreadStrategy ? Math.max(5, (cpus * 2)) : 0;
                }

                builder.getWorkerThreadPoolConfig().setCorePoolSize(workerThreadCount)
                        .setMaxPoolSize(workerThreadCount).setPoolName(
                                "OpenDJ LDAP SDK Grizzly worker thread");
            }

            // Parse IO related options.
            final String lingerStr = System.getProperty("org.forgerock.opendj.transport.linger");
            if (lingerStr != null) {
                // Disabled by default.
                builder.setLinger(Integer.parseInt(lingerStr));
            }

            final String tcpNoDelayStr =
                    System.getProperty("org.forgerock.opendj.transport.tcpNoDelay");
            if (tcpNoDelayStr != null) {
                // Enabled by default.
                builder.setTcpNoDelay(Boolean.parseBoolean(tcpNoDelayStr));
            }

            final String reuseAddressStr =
                    System.getProperty("org.forgerock.opendj.transport.reuseAddress");
            if (reuseAddressStr != null) {
                // Enabled by default.
                builder.setReuseAddress(Boolean.parseBoolean(reuseAddressStr));
            }

            defaultTransport = builder.build();

            // FIXME: raise bug in Grizzly. We should not need to do this, but
            // failure
            // to do so causes many deadlocks.
            defaultTransport.setSelectorRunnersCount(selectorThreadCount);

            try {
                defaultTransport.start();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        return defaultTransport;
    }

    private DefaultTCPNIOTransport() {
        // Prevent instantiation.
    }

}
