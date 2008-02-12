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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;

import java.util.Comparator;
import java.util.Set;
import java.util.List;

/**
 * This class attempts to abstract the generation and comparison of keys
 * for an index. It is subclassed for the specific type of indexing.
 */
public abstract class Indexer
{
  /**
   * Get the comparator that must be used to compare index keys
   * generated by this class.
   *
   * @return A byte array comparator.
   */
  public abstract Comparator<byte[]> getComparator();

  /**
   * Generate the set of index keys for an entry.
   *
   * @param txn A database transaction to be used if the database need to be
   * accessed in the course of generating the index keys.
   * @param entry The entry.
   * @param keys The set into which the generated keys will be inserted.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public abstract void indexEntry(Transaction txn, Entry entry,
                                Set<byte[]> keys)
       throws DatabaseException;

  /**
   * Generate the set of index keys to be added and the set of index keys
   * to be deleted for an entry that has been replaced.
   *
   * @param txn A database transaction to be used if the database need to be
   * accessed in the course of generating the index keys.
   * @param oldEntry The original entry contents.
   * @param newEntry The new entry contents.
   * @param addKeys The set into which the keys to be added will be inserted.
   * @param delKeys The set into which the keys to be deleted will be inserted.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public abstract void replaceEntry(Transaction txn,
                                    Entry oldEntry, Entry newEntry,
                                    Set<byte[]> addKeys,
                                    Set<byte[]> delKeys)
       throws DatabaseException;

  /**
   * Generate the set of index keys to be added and the set of index keys
   * to be deleted for an entry that was modified.
   *
   * @param txn A database transaction to be used if the database need to be
   * accessed in the course of generating the index keys.
   * @param oldEntry The original entry contents.
   * @param newEntry The new entry contents.
   * @param mods The set of modifications that were applied to the entry.
   * @param addKeys The set into which the keys to be added will be inserted.
   * @param delKeys The set into which the keys to be deleted will be inserted.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public abstract void modifyEntry(Transaction txn,
                                   Entry oldEntry, Entry newEntry,
                                   List<Modification> mods,
                                   Set<byte[]> addKeys,
                                   Set<byte[]> delKeys)
       throws DatabaseException;
}
