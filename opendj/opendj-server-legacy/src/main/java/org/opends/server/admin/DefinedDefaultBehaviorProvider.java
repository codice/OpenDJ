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
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.admin;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;



/**
 * A default behavior provider which represents a well-defined set of default
 * values. It should be used by properties which have default value(s) which are
 * valid value(s) according to the constraints of the property's definition.
 *
 * @param <T>
 *          The type of values represented by this provider.
 */
public final class DefinedDefaultBehaviorProvider<T> extends
    DefaultBehaviorProvider<T> {

  /** The collection of default values. */
  private final Collection<String> values;



  /**
   * Create a new defined default behavior provider associated with the
   * specified list of values.
   *
   * @param values
   *          The list of values (must be non-<code>null</code> and not
   *          empty) in their string representation.
   * @throws IllegalArgumentException
   *           If the list of values was <code>null</code> or empty.
   */
  public DefinedDefaultBehaviorProvider(String... values)
      throws IllegalArgumentException {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException(
          "Null or empty list of default values");
    }
    this.values = Arrays.asList(values);
  }

  /** {@inheritDoc} */
  public <R, P> R accept(DefaultBehaviorProviderVisitor<T, R, P> v, P p) {
    return v.visitDefined(this, p);
  }

  /**
   * Get a copy of the default values.
   *
   * @return Returns a newly allocated collection containing a copy of the
   *         default values.
   */
  public Collection<String> getDefaultValues() {
    return new ArrayList<>(values);
  }
}
