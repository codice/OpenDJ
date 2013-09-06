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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.util;


/**
 * Ordered pair of various objects.
 *
 * @param <F>
 *          type of the first pair element
 * @param <S>
 *          type of the second pair element
 */
public class Pair<F, S>
{

  /** An empty Pair. */
  public static final Pair<?, ?> EMPTY = Pair.of(null, null);

  /** The first pair element. */
  private final F first;
  /** The second pair element. */
  private final S second;

  /**
   * Default ctor.
   *
   * @param first
   *          the first element of the constructed pair
   * @param second
   *          the second element of the constructed pair
   */
  private Pair(F first, S second)
  {
    this.first = first;
    this.second = second;
  }

  /**
   * Factory method to build a new Pair.
   *
   * @param first
   *          the first element of the constructed pair
   * @param second
   *          the second element of the constructed pair
   * @param <F>
   *          type of the first pair element
   * @param <S>
   *          type of the second pair element
   * @return A new Pair built with the provided elements
   */
  public static <F, S> Pair<F, S> of(F first, S second)
  {
    return new Pair<F, S>(first, second);
  }

  /**
   * Returns an empty Pair matching the required types.
   *
   * @param <F>
   *          type of the first pair element
   * @param <S>
   *          type of the second pair element
   * @return An empty Pair matching the required types
   */
  @SuppressWarnings("unchecked")
  public static <F, S> Pair<F, S> empty()
  {
    return (Pair<F, S>) EMPTY;
  }

  /**
   * Returns the first element of this pair.
   *
   * @return the first element of this pair
   */
  public F getFirst()
  {
    return first;
  }

  /**
   * Returns the second element of this pair.
   *
   * @return the second element of this pair
   */
  public S getSecond()
  {
    return second;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((first == null) ? 0 : first.hashCode());
    result = prime * result + ((second == null) ? 0 : second.hashCode());
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Pair<?, ?> other = (Pair<?, ?>) obj;
    if (first == null)
    {
      if (other.first != null)
        return false;
    }
    else if (!first.equals(other.first))
      return false;
    if (second == null)
    {
      if (other.second != null)
        return false;
    }
    else if (!second.equals(other.second))
      return false;
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "Pair [" + first + ", " + second + "]";
  }

}
