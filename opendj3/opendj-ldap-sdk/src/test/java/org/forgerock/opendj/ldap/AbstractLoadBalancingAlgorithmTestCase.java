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
 *      Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import static java.util.Arrays.asList;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.opendj.ldap.Connections.newLoadBalancer;
import static org.forgerock.opendj.ldap.ErrorResultException.newErrorResult;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.CompletedFutureResult;

@SuppressWarnings("javadoc")
public class AbstractLoadBalancingAlgorithmTestCase extends SdkTestCase {
    private static ConnectionFactory mockAsync(final ConnectionFactory mock) {
        return new ConnectionFactory() {
            @Override
            public void close() {
                mock.close();
            }

            @Override
            public Connection getConnection() throws ErrorResultException {
                return mock.getConnection();
            }

            @Override
            public FutureResult<Connection> getConnectionAsync(
                    final ResultHandler<? super Connection> handler) {
                try {
                    final Connection connection = mock.getConnection();
                    if (handler != null) {
                        handler.handleResult(connection);
                    }
                    return new CompletedFutureResult<Connection>(connection);
                } catch (final ErrorResultException e) {
                    if (handler != null) {
                        handler.handleErrorResult(e);
                    }
                    return new CompletedFutureResult<Connection>(e);
                }
            }

            @Override
            public String toString() {
                return mock.toString();
            }
        };
    }

    /**
     * Disables logging before the tests.
     */
    @BeforeClass()
    public void disableLogging() {
        setDefaultLogLevel(Level.SEVERE);
    }

    /**
     * Re-enable logging after the tests.
     */
    @AfterClass()
    public void enableLogging() {
        setDefaultLogLevel(Level.INFO);
    }

    /**
     * Tests fix for OPENDJ-1112: when a load balancer fails completely the
     * connection exception should include the last error that occurred.
     */
    @Test
    public void testFinalFailureExposedAsCause() throws Exception {
        /*
         * Create load-balancer with two failed connection factories.
         */
        final ConnectionFactory first = mock(ConnectionFactory.class, "first");
        final ErrorResultException firstError = newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        when(first.getConnection()).thenThrow(firstError);

        final ConnectionFactory second = mock(ConnectionFactory.class, "second");
        final ErrorResultException secondError = newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        when(second.getConnection()).thenThrow(secondError);

        final ConnectionFactory loadBalancer =
                newLoadBalancer(new RoundRobinLoadBalancingAlgorithm(asList(first, second)));

        /*
         * Belt and braces check to ensure that factory methods don't return
         * same instance and fool this test.
         */
        assertThat(firstError).isNotSameAs(secondError);

        try {
            loadBalancer.getConnection().close();
            fail("Unexpectedly obtained a connection");
        } catch (final ErrorResultException e) {
            assertThat(e.getCause()).isSameAs(secondError);
        } finally {
            loadBalancer.close();
        }
    }

    /**
     * Tests fix for OPENDJ-1112: event listener should be notified when a
     * factory changes state from online to offline or vice versa.
     */
    @Test
    public void testLoadBalancerEventListenerNotification() throws Exception {
        /*
         * Create load-balancer with two failed connection factories and a mock
         * listener and scheduler. The first factory will succeed on the second
         * attempt.
         */
        final ConnectionFactory first = mock(ConnectionFactory.class, "first");
        final ConnectionFactory firstAsync = mockAsync(first);
        final ErrorResultException firstError = newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        when(first.getConnection()).thenThrow(firstError).thenReturn(mock(Connection.class));

        final ConnectionFactory second = mock(ConnectionFactory.class, "second");
        final ConnectionFactory secondAsync = mockAsync(second);
        final ErrorResultException secondError = newErrorResult(ResultCode.CLIENT_SIDE_SERVER_DOWN);
        when(second.getConnection()).thenThrow(secondError);

        final LoadBalancerEventListener listener = mock(LoadBalancerEventListener.class);
        final MockScheduler scheduler = new MockScheduler();
        final ConnectionFactory loadBalancer =
                newLoadBalancer(new RoundRobinLoadBalancingAlgorithm(
                        asList(firstAsync, secondAsync), listener, 1, TimeUnit.SECONDS, scheduler));

        /*
         * Belt and braces check to ensure that factory methods don't return
         * same instance and fool this test.
         */
        assertThat(firstError).isNotSameAs(secondError);

        try {
            loadBalancer.getConnection().close();
            fail("Unexpectedly obtained a connection");
        } catch (final ErrorResultException e) {
            // Check that the event listener has been fired for both factories.
            verify(listener).handleConnectionFactoryOffline(firstAsync, firstError);
            verify(listener).handleConnectionFactoryOffline(secondAsync, secondError);
            verifyNoMoreInteractions(listener);

            // Check that the factories are being monitored.
            assertThat(scheduler.isScheduled()).isTrue();

            // Forcefully run the monitor task and check that the first factory is online.
            scheduler.runFirstTask();
            verify(listener).handleConnectionFactoryOnline(firstAsync);
            verifyNoMoreInteractions(listener);
        } finally {
            loadBalancer.close();
        }
    }
}
