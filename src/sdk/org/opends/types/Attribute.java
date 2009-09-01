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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.types;



import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.opends.server.types.ByteString;



/**
 * An attribute, comprising of an attribute description and zero or more
 * attribute values.
 * <p>
 * Any methods which perform comparisons between attribute values use
 * the equality matching rule associated with the attribute description.
 * <p>
 * Any methods which accept {@code Object} based attribute values
 * convert the attribute values to instances of {@code ByteString} as
 * follows:
 *
 * <pre>
 * Object object = ...;
 * ByteString value = null;
 * if (object instanceof ByteSequence)
 * {
 *   value = ((ByteSequence)object).toByteString();
 * }
 * else
 * {
 *   value = ByteString.valueOf(object.toString());
 * }
 * </pre>
 * <p>
 * TODO: matching against attribute value assertions.
 * <p>
 * TODO: methods for retrieving a single value as a specific type (e.g.
 * boolean, integer, etc) using Function objects?
 */
public interface Attribute extends AttributeValueSequence,
    Set<ByteString>
{
  /**
   * Adds {@code value} to this attribute if it is not already present
   * (optional operation). If this attribute already contains {@code
   * value}, the call leaves the attribute unchanged and returns {@code
   * false}.
   *
   * @param value
   *          The attribute value to be added to this attribute.
   * @return {@code true} if this attribute changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this attribute does not support addition of attribute
   *           values.
   * @throws NullPointerException
   *           If {@code value} was {@code null}.
   */
  boolean add(ByteString value) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * Adds all of the attribute values contained in {@code values} to
   * this attribute if they are not already present (optional
   * operation).
   *
   * @param values
   *          The attribute values to be added to this attribute.
   * @return {@code true} if this attribute changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this attribute does not support addition of attribute
   *           values.
   * @throws NullPointerException
   *           If {@code values} was {@code null}.
   */
  boolean addAll(Collection<? extends ByteString> values)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Adds all of the attribute values contained in {@code objects} to
   * this attribute if they are not already present (optional
   * operation).
   * <p>
   * Any attribute value contained in {@code objects} which is not an
   * instances of {@code ByteString} will be converted to one using its
   * string representation.
   *
   * @param objects
   *          The attribute values to be added to this attribute.
   * @return {@code true} if this attribute changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this attribute does not support addition of attribute
   *           values.
   * @throws NullPointerException
   *           If {@code objects} was {@code null}.
   */
  boolean addAllObjects(Collection<?> objects)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Adds {@code object} to this attribute if it is not already present
   * (optional operation). If this attribute already contains {@code
   * value}, the call leaves the attribute unchanged and returns {@code
   * false}.
   * <p>
   * If {@code object} is not an instance of {@code ByteString} then it
   * will be converted to one using its string representation.
   *
   * @param object
   *          The attribute value to be added to this attribute.
   * @return {@code true} if this attribute changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this attribute does not support addition of attribute
   *           values.
   * @throws NullPointerException
   *           If {@code object} was {@code null}.
   */
  boolean addObject(Object object)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes all of the attribute values from this attribute (optional
   * operation). This attribute will be empty after this call returns.
   */
  void clear() throws UnsupportedOperationException;



  /**
   * Returns {@code true} if this attribute contains {@code value}.
   *
   * @param value
   *          The attribute value whose presence in this attribute is to
   *          be tested.
   * @return {@code true} if this attribute contains {@code value}, or
   *         {@code false} if not.
   * @throws NullPointerException
   *           If {@code value} was {@code null}.
   */
  boolean contains(ByteString value) throws NullPointerException;



  /**
   * Returns {@code true} if this attribute contains {@code object}.
   * <p>
   * If {@code object} is not an instance of {@code ByteString} then it
   * will be converted to one using its string representation.
   *
   * @param object
   *          The attribute value whose presence in this attribute is to
   *          be tested.
   * @return {@code true} if this attribute contains {@code object}, or
   *         {@code false} if not.
   * @throws NullPointerException
   *           If {@code object} was {@code null}.
   */
  boolean contains(Object object) throws NullPointerException;



  /**
   * Returns {@code true} if this attribute contains all of the
   * attribute values contained in {@code objects}.
   * <p>
   * Any attribute value contained in {@code objects} which is not an
   * instances of {@code ByteString} will be converted to one using its
   * string representation.
   *
   * @param objects
   *          The objects whose presence in this attribute is to be
   *          tested.
   * @return {@code true} if this attribute contains all of the
   *         attribute values contained in {@code objects}, or {@code
   *         false} if not.
   * @throws NullPointerException
   *           If {@code objects} was {@code null}.
   */
  boolean containsAll(Collection<?> objects)
      throws NullPointerException;



  /**
   * Returns {@code true} if {@code object} is an attribute which is
   * equal to this attribute. Two attributes are considered equal if
   * their attribute descriptions are equal, they both have the same
   * number of attribute values, and every attribute value contained in
   * the first attribute is also contained in the second attribute.
   *
   * @param object
   *          The object to be tested for equality with this attribute.
   * @return {@code true} if {@code object} is an attribute which is
   *         equal to this attribute, or {@code false} if not.
   */
  boolean equals(Object object);



  /**
   * Returns the first attribute value in this attribute.
   *
   * @return The first attribute value in this attribute.
   * @throws NoSuchElementException
   *           If this attribute is empty.
   */
  ByteString firstValue() throws NoSuchElementException;



  /**
   * Returns the first attribute value in this attribute decoded as a
   * UTF-8 string.
   *
   * @return The first attribute value in this attribute decoded as a
   *         UTF-8 string.
   * @throws NoSuchElementException
   *           If this attribute is empty.
   */
  String firstValueAsString();



  /**
   * Returns the attribute description of this attribute, which includes
   * its attribute type and any options.
   *
   * @return The attribute description of this attribute.
   */
  AttributeDescription getAttributeDescription();



  /**
   * Returns the string representation of the attribute description of
   * this attribute, which includes its attribute type and any options.
   *
   * @return The string representation of the attribute description of
   *         this attribute.
   */
  String getAttributeDescriptionAsString();



  /**
   * Returns the hash code for this attribute. It will be calculated as
   * the sum of the hash codes of the attribute description and all of
   * the attribute values.
   *
   * @return The hash code for this attribute.
   */
  int hashCode();



  /**
   * Returns {@code true} if this attribute contains no attribute
   * values.
   *
   * @return {@code true} if this attribute contains no attribute
   *         values.
   */
  boolean isEmpty();



  /**
   * Returns an iterator over the attribute values in this attribute.
   * The attribute values are returned in no particular order, unless
   * the implementation of this attribute provides such a guarantee.
   *
   * @return An iterator over the attribute values in this attribute.
   */
  Iterator<ByteString> iterator();



  /**
   * Removes {@code value} from this attribute if it is present
   * (optional operation). If this attribute does not contain {@code
   * value}, the call leaves the attribute unchanged and returns {@code
   * false}.
   *
   * @param value
   *          The attribute value to be removed from this attribute.
   * @return {@code true} if this attribute changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this attribute does not support removal of attribute
   *           values.
   * @throws NullPointerException
   *           If {@code value} was {@code null}.
   */
  boolean remove(ByteString value)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes {@code object} from this attribute if it is present
   * (optional operation). If this attribute does not contain {@code
   * object}, the call leaves the attribute unchanged and returns
   * {@code false}.
   * <p>
   * If {@code object} is not an instance of {@code ByteString} then it
   * will be converted to one using its string representation.
   *
   * @param object
   *          The attribute value to be removed from this attribute.
   * @return {@code true} if this attribute changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this attribute does not support removal of attribute
   *           values.
   * @throws NullPointerException
   *           If {@code object} was {@code null}.
   */
  boolean remove(Object object) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * Removes all of the attribute values contained in {@code objects}
   * from this attribute if they are present (optional operation).
   * <p>
   * Any attribute value contained in {@code objects} which is not an
   * instances of {@code ByteString} will be converted to one using its
   * string representation.
   *
   * @param objects
   *          The attribute values to be removed from this attribute.
   * @return {@code true} if this attribute changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this attribute does not support removal of attribute
   *           values.
   * @throws NullPointerException
   *           If {@code objects} was {@code null}.
   */
  boolean removeAll(Collection<?> objects)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Retains only the attribute values in this attribute which are
   * contained in {@code objects} (optional operation).
   * <p>
   * Any attribute value contained in {@code objects} which is not an
   * instances of {@code ByteString} will be converted to one using its
   * string representation.
   *
   * @param objects
   *          The attribute values to be retained in this attribute.
   * @return {@code true} if this attribute changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this attribute does not support removal of attribute
   *           values.
   * @throws NullPointerException
   *           If {@code objects} was {@code null}.
   */
  boolean retainAll(Collection<?> objects)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Returns the number of attribute values in this attribute.
   *
   * @return The number of attribute values in this attribute.
   */
  int size();



  /**
   * Returns an array containing all of the atribute values contained in
   * this attribute.
   * <p>
   * If this attribute makes any guarantees as to what order its
   * attribute values are returned by its iterator, this method must
   * return the attribute values in the same order.
   * <p>
   * The returned array will be "safe" in that no references to it are
   * maintained by this attribute. The caller is thus free to modify the
   * returned array.
   */
  ByteString[] toArray();



  /**
   * Returns an array containing all of the attribute values in this
   * attribute; the runtime type of the returned array is that of the
   * specified array.
   * <p>
   * If the set fits in the specified array, it is returned therein.
   * Otherwise, a new array is allocated with the runtime type of the
   * specified array and the size of this attribute. If this attribute
   * fits in the specified array with room to spare (i.e., the array has
   * more elements than this attribute), the elements in the array
   * immediately following the end of the set is set to {@code null}.
   * <p>
   * If this attribute makes any guarantees as to what order its
   * attribute values are returned by its iterator, this method must
   * return the attribute values in the same order.
   *
   * @throws ArrayStoreException
   *           If the runtime type of {@code array} is not a supertype
   *           of {@code ByteString}.
   * @throws NullPointerException
   *           If {@code array} was {@code null}.
   */
  <T> T[] toArray(T[] array) throws ArrayStoreException,
      NullPointerException;



  /**
   * Returns a string representation of this attribute.
   *
   * @return The string representation of this attribute.
   */
  String toString();
}
