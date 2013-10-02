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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Fail.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.StaticUtils;

/**
 * An abstract class that all types unit tests should extend. A type represents
 * the classes found directly under the package org.forgerock.opendj.ldap.
 */
@Test(groups = { "precommit", "types", "sdk" })
public abstract class SdkTestCase extends ForgeRockTestCase {

    /**
     * Fail with precise message giving the exception that was expected.
     *
     * @param exceptionClass expected exception
     */
    void failWasExpected(Class<? extends Throwable> exceptionClass) {
        fail("should throw an exception " + exceptionClass.getSimpleName());
    }

    /**
     * Dynamically change log level using java.util.logging framework.
     * <p>
     * slf4j ERROR maps to java.util.logging SEVERE
     * slf4j INFO maps to java.util.logging INFO
     * slf4j DEBUG maps to java.util.logging FINE
     * slf4j TRACE maps to java.util.logging FINEST
     *
     * @param level logging level to use
     */
    void setDefaultLogLevel(Level level) {
        Logger.getLogger(StaticUtils.DEFAULT_LOG.getName()).setLevel(level);
    }

}
