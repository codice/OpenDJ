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
package org.opends.server.replication.server.changelog.api;

import org.opends.server.replication.common.ChangeNumber;

/**
 * This class stores the changelog information into a database.
 *
 * @see <a href=
 * "https://wikis.forgerock.org/confluence/display/OPENDJ/OpenDJ+Domain+Names"
 * >OpenDJ Domain Names</a> for more information about the changelog.
 */
public interface ChangelogDB extends Runnable
{

  /**
   * Get the CN associated to a provided draft change number.
   *
   * @param draftCN
   *          the provided draft change number.
   * @return the associated CN, null when none.
   */
  public ChangeNumber getChangeNumber(int draftCN);

  /**
   * Get the baseDN associated to a provided draft change number.
   *
   * @param draftCN
   *          the provided draft change number.
   * @return the baseDN, null when none.
   */
  public String getBaseDN(int draftCN);

  /**
   * Get the previous cookie associated to a provided draft change number.
   *
   * @param draftCN
   *          the provided draft change number.
   * @return the previous cookie, null when none.
   */
  String getPreviousCookie(int draftCN);

  /**
   * Get the firstChange.
   *
   * @return Returns the first draftCN in the DB.
   */
  int getFirstDraftCN();

  /**
   * Get the lastChange.
   *
   * @return Returns the last draftCN in the DB
   */
  int getLastDraftCN();

  /**
   * Add an update to the list of messages that must be saved to the db managed
   * by this db handler.
   * <p>
   * This method is blocking if the size of the list of message is larger than
   * its maximum.
   *
   * @param draftCN
   *          The draft change number for this record in the db.
   * @param previousCookie
   *          The value of the previous cookie.
   * @param baseDN
   *          The associated baseDN.
   * @param changeNumber
   *          The associated replication change number.
   */
  void add(int draftCN, String previousCookie, String baseDN,
      ChangeNumber changeNumber);

  /**
   * Generate a new {@link ChangelogDBIterator} that allows to browse the db
   * managed by this dbHandler and starting at the position defined by a given
   * changeNumber.
   *
   * @param startDraftCN
   *          The position where the iterator must start.
   * @return a new ReplicationIterator that allows to browse the db managed by
   *         this dbHandler and starting at the position defined by a given
   *         changeNumber.
   * @throws ChangelogException
   *           if a database problem happened.
   */
  ChangelogDBIterator generateIterator(int startDraftCN)
      throws ChangelogException;

  /**
   * Returns whether this database is empty.
   *
   * @return <code>true</code> if this database is empty, <code>false</code>
   *         otherwise
   */
  boolean isEmpty();

  /**
   * Clear the changes from this DB (from both memory cache and DB storage).
   *
   * @throws ChangelogException
   *           When an exception occurs while removing the changes from the DB.
   */
  void clear() throws ChangelogException;

  /**
   * Clear the changes from this DB (from both memory cache and DB storage) for
   * the provided baseDN.
   *
   * @param baseDNToClear
   *          The baseDN for which we want to remove all records from the
   *          DraftCNDb - null means all.
   * @throws ChangelogException
   *           When an exception occurs while removing the changes from the DB.
   */
  void clear(String baseDNToClear) throws ChangelogException;

  /**
   * Shutdown this dbHandler.
   */
  void shutdown();

}
