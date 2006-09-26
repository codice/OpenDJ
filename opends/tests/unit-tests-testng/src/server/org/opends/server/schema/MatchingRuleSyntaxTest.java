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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import org.opends.server.api.AttributeSyntax;
import org.testng.annotations.DataProvider;

/**
 * Test the MatchingRuleSyntax.
 */
public class MatchingRuleSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeSyntax getRule()
  {
    return new MatchingRuleSyntax();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
          {"( 1.2.3.4 NAME 'full matching rule' "
              + " DESC 'description of matching rule' OBSOLETE "
              + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.17 "
              + " ( this is an extension ) )", true},
          {"( 1.2.3.4 NAME 'missing closing parenthesis' "
                + " DESC 'description of matching rule' "
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.17 "
                + " ( this is an extension ) ", false},
    };
  }

}
