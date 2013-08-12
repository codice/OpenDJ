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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.messages.MessageBuilder;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.server.DraftCNDB.DraftCNDBCursor;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class is used for managing the replicationServer database for each
 * server in the topology.
 * It is responsible for efficiently saving the updates that is received from
 * each master server into stable storage.
 * This class is also able to generate a ReplicationIterator that can be
 * used to read all changes from a given ChangeNumber.
 *
 * This class publish some monitoring information below cn=monitor.
 */
public class DraftCNDbHandler implements Runnable
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();
  private static int NO_KEY = 0;

  private DraftCNDB db;
  private int firstkey = NO_KEY;
  private int lastkey = NO_KEY;
  private DbMonitorProvider dbMonitor = new DbMonitorProvider();
  private boolean shutdown = false;
  private boolean trimDone = false;
  /**
   * A dedicated thread loops trim().
   * <p>
   * trim() : deletes from the DB a number of changes that are older than a
   * certain date.
   */
  private DirectoryThread thread;
  /**
   * The trim age in milliseconds. Changes record in the change DB that are
   * older than this age are removed.
   * <p>
   * FIXME it never gets updated even when the replication server purge delay is
   * updated
   */
  private long trimAge;

  private ReplicationServer replicationServer;


  /**
   * Creates a new dbHandler associated to a given LDAP server.
   *
   * @param replicationServer The ReplicationServer that creates this dbHandler.
   * @param dbenv the Database Env to use to create the ReplicationServer DB.
   * server for this domain.
   * @throws ChangelogException If a database problem happened
   */
  public DraftCNDbHandler(ReplicationServer replicationServer,
      ReplicationDbEnv dbenv) throws ChangelogException
  {
    this.replicationServer = replicationServer;
    this.trimAge = replicationServer.getTrimAge();

    // DB initialization
    db = new DraftCNDB(replicationServer, dbenv);
    firstkey = db.readFirstDraftCN();
    lastkey = db.readLastDraftCN();

    // Trimming thread
    thread = new DirectoryThread(this, "Replication DraftCN db");
    thread.start();

    // Monitoring registration
    DirectoryServer.deregisterMonitorProvider(dbMonitor);
    DirectoryServer.registerMonitorProvider(dbMonitor);
  }

  /**
   * Add an update to the list of messages that must be saved to the db
   * managed by this db handler.
   * This method is blocking if the size of the list of message is larger
   * than its maximum.
   * @param key The key for this record in the db.
   * @param value The associated value.
   * @param baseDN The associated baseDN.
   * @param cn The associated replication change number.
   */
  public synchronized void add(int key, String value, String baseDN,
      ChangeNumber cn)
  {
    db.addEntry(key, value, baseDN, cn);

    if (debugEnabled())
      TRACER.debugInfo(
          "In DraftCNDbhandler.add, added: "
        + " key=" + key
        + " value=" + value
        + " baseDN=" + baseDN
        + " cn=" + cn);
  }

  /**
   * Get the firstChange.
   * @return Returns the firstChange.
   */
  public int getFirstKey()
  {
    return db.readFirstDraftCN();
  }

  /**
   * Get the lastChange.
   * @return Returns the lastChange.
   */
  public int getLastKey()
  {
    return db.readLastDraftCN();
  }

  /**
   * Get the number of changes.
   * @return Returns the number of changes.
   */
  public long count()
  {
    return db.count();
  }

  /**
   * Returns whether this database is empty.
   * <p>
   * FIXME Find a way to implement this method in a more efficient manner.
   * {@link com.sleepycat.je.Database#count()} javadoc mentions:
   * <blockquote>Note that this method does scan a significant portion of the
   * database and should be considered a fairly expensive
   * operation.</blockquote>
   * <p>
   * It could be faster to:
   * <ul>
   * <li>open a cursor, check if the next entry exits, then close the cursor
   * </li>
   * <li>call <code>db.readFirstDraftCN() != 0</code></li>
   * </ul>
   *
   * @return <code>true</code> if this database is empty, <code>false</code>
   *         otherwise
   */
  public boolean isEmpty()
  {
    return count() == 0;
  }

  /**
   * Get a read cursor on the database from a provided key.
   * The cursor MUST be released after use.
   * @param key The provided key.
   * @return the new cursor.
   */
  public DraftCNDBCursor getReadCursor(int key)
  {
    try
    {
      return db.openReadCursor(key);
    }
    catch(Exception e)
    {
      return null;
    }
  }

  /**
   * Release a provided read cursor.
   * @param cursor The provided read cursor.
   */
  public void releaseReadCursor(DraftCNDBCursor cursor)
  {
    close(cursor);
  }

  /**
   * Generate a new ReplicationIterator that allows to browse the db
   * managed by this dbHandler and starting at the position defined
   * by a given changeNumber.
   *
   * @param  startDraftCN The position where the iterator must start.
   *
   * @return a new ReplicationIterator that allows to browse the db
   *         managed by this dbHandler and starting at the position defined
   *         by a given changeNumber.
   *
   * @throws ChangelogException if a database problem happened.
   */
  public DraftCNDbIterator generateIterator(int startDraftCN)
      throws ChangelogException
  {
    return new DraftCNDbIterator(db, startDraftCN);
  }

  /**
   * Shutdown this dbHandler.
   */
  public void shutdown()
  {
    if (shutdown)
    {
      return;
    }

    shutdown  = true;
    synchronized (this)
    {
      notifyAll();
    }

    synchronized (this)
    { /* Can we just do a thread.join() ? */
      while (!trimDone)
      {
        try
        {
          wait();
        } catch (InterruptedException e)
        { /* do nothing */ }
      }
    }

    db.shutdown();
    DirectoryServer.deregisterMonitorProvider(dbMonitor);
  }

  /**
   * Run method for this class.
   * Periodically Flushes the ReplicationServerDomain cache from memory to the
   * stable storage and trims the old updates.
   */
  @Override
  public void run()
  {
    while (!shutdown)
    {
      try {
        trim();

        synchronized (this)
        {
          try
          {
            wait(1000);
          } catch (InterruptedException e)
          {
            Thread.currentThread().interrupt();
          }
        }
      } catch (Exception end)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH.get());
        mb.append(stackTraceToSingleLineString(end));
        logError(mb.toMessage());
        if (replicationServer != null)
          replicationServer.shutdown();
        break;
      }
    }

    synchronized (this)
    {
      trimDone = true;
      notifyAll();
    }
  }

  /**
   * Trim old changes from this database.
   * @throws ChangelogException In case of database problem.
   */
  public void trim() throws ChangelogException
  {
    if (trimAge == 0)
      return;

    clear(null);
  }

  /**
   * Clear the changes from this DB (from both memory cache and DB storage)
   * for the provided baseDN.
   * @param baseDNToClear The baseDN for which we want to remove
   *         all records from the DraftCNDb - null means all.
   * @throws ChangelogException When an exception occurs while removing the
   * changes from the DB.
   */
  public void clear(String baseDNToClear) throws ChangelogException
  {
    if (isEmpty())
    {
      return;
    }

    ChangeNumber crossDomainEligibleCN = replicationServer.getEligibleCN();

    for (int i = 0; i < 100; i++)
    {
      DraftCNDBCursor cursor = db.openDeleteCursor();
      try
      {
        for (int j = 0; j < 50; j++)
        {
          // let's traverse the DraftCNDb
          if (!cursor.next())
          {
            cursor.close();
            return;
          }

          ChangeNumber cn = cursor.currentChangeNumber();

          // From the draftCNDb change record, get the domain and changeNumber
          String baseDN = cursor.currentBaseDN();

          if ((baseDNToClear != null)
              && (baseDNToClear.equalsIgnoreCase(baseDN)))
          {
            cursor.delete();
            continue;
          }

          ReplicationServerDomain domain = replicationServer
              .getReplicationServerDomain(baseDN, false);

          if (domain == null)
          {
            // the domain has been removed since the record was written in the
            // draftCNDb, thus it makes no sense to keep the record in the
            // draftCNDb.
            cursor.delete();
            continue;
          }

          ServerState startState = domain.getStartState();

          // We don't use the returned endState but it's updating CN as
          // reading
          domain.getEligibleState(crossDomainEligibleCN);

          ChangeNumber fcn = startState.getChangeNumber(cn.getServerId());

          int currentKey = cursor.currentKey();

          if (cn.older(fcn))
          {
            cursor.delete();
            continue;
          }

          ServerState cnVector;
          try
          {
            Map<String,ServerState> cnStartStates =
                MultiDomainServerState.splitGenStateToServerStates(
                        cursor.currentValue());
            cnVector = cnStartStates.get(baseDN);

            if (debugEnabled())
              TRACER.debugInfo("DraftCNDBHandler:clear() - ChangeVector:" +
                      cnVector + " -- StartState:" + startState);
          }
          catch(Exception e)
          {
            // We couldn't parse the mdss from the DraftCNData Value
            cursor.delete();
            continue;
          }

          if ((cnVector == null)
                  || (cnVector.getChangeNumber(cn.getServerId()) != null
                      && !cnVector.cover(startState)))
          {
            cursor.delete();
            if (debugEnabled())
              TRACER.debugInfo("DraftCNDBHandler:clear() - deleted " +
                      cn + "Not covering startState");
            continue;
          }

          firstkey = currentKey;
          cursor.close();
          return;
        }

        cursor.close();
      }
      catch (ChangelogException e)
      {
        // mark shutdown for this db so that we don't try again to
        // stop it from cursor.close() or methods called by cursor.close()
        cursor.abort();
        shutdown = true;
        throw e;
      }
      catch (Exception e)
      {
        // mark shutdown for this db so that we don't try again to
        // stop it from cursor.close() or methods called by cursor.close()
        cursor.abort();
        shutdown = true;
        throw new ChangelogException(e);
      }
    }
  }

  /**
   * This internal class is used to implement the Monitoring capabilities
   * of the dbHandler.
   */
  private class DbMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    /**
     * {@inheritDoc}
     */
    @Override
    public List<Attribute> getMonitorData()
    {
      List<Attribute> attributes = new ArrayList<Attribute>();
      attributes.add(Attributes.create("first-draft-changenumber",
          Integer.toString(db.readFirstDraftCN())));
      attributes.add(Attributes.create("last-draft-changenumber",
          Integer.toString(db.readLastDraftCN())));
      attributes.add(Attributes.create("count",
          Long.toString(count())));
      return attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMonitorInstanceName()
    {
      return "Draft Changelog";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeMonitorProvider(MonitorProviderCfg configuration)
                            throws ConfigException,InitializationException
    {
      // Nothing to do for now
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "draftCNdb:" + " " + firstkey + " " + lastkey;
  }

  /**
   * Set the Purge delay for this db Handler.
   * @param delay The purge delay in Milliseconds.
   */
  public void setPurgeDelay(long delay)
  {
    trimAge = delay;
  }

  /**
   * Clear the changes from this DB (from both memory cache and DB storage).
   * @throws ChangelogException When an exception occurs while removing the
   * changes from the DB.
   */
  public void clear() throws ChangelogException
  {
    db.clear();
    firstkey = db.readFirstDraftCN();
    lastkey = db.readLastDraftCN();
  }

  private ReentrantLock lock = new ReentrantLock();

  /**
   * Tests if the current thread has the lock on this object.
   * @return True if the current thread has the lock.
   */
  public boolean hasLock()
  {
    return (lock.getHoldCount() > 0);
  }

  /**
   * Takes the lock on this object (blocking until lock can be acquired).
   * @throws InterruptedException If interrupted.
   */
   public void lock() throws InterruptedException
  {
    lock.lockInterruptibly();
  }

  /**
   * Releases the lock on this object.
   */
  public void release()
  {
    lock.unlock();
  }

  /**
   * Get the value associated to a provided key.
   * @param key the provided key.
   * @return the associated value, null when none.
   */
  public String getValue(int key)
  {
    DraftCNDBCursor draftCNDBCursor = null;
    try
    {
      draftCNDBCursor = db.openReadCursor(key);
      return draftCNDBCursor.currentValue();
    }
    catch(Exception e)
    {
      if (debugEnabled())
        TRACER.debugInfo("In DraftCNDbHandler.getValue, read: " +
          " key=" + key + " value returned is null" +
          " first=" + db.readFirstDraftCN() +
          " last=" + db.readLastDraftCN() +
          " count=" + db.count() +
          " exception " + e + " " + e.getMessage());
      return null;
    }
    finally
    {
      close(draftCNDBCursor);
    }
  }

  /**
   * Get the CN associated to a provided key.
   * @param key the provided key.
   * @return the associated CN, null when none.
   */
  public ChangeNumber getChangeNumber(int key)
  {
    DraftCNDBCursor draftCNDBCursor = null;
    try
    {
      draftCNDBCursor = db.openReadCursor(key);
      return draftCNDBCursor.currentChangeNumber();
    }
    catch(Exception e)
    {
      if (debugEnabled())
        TRACER.debugInfo("In DraftCNDbHandler.getChangeNumber, read: " +
          " key=" + key + " changeNumber returned is null" +
          " first=" + db.readFirstDraftCN() +
          " last=" + db.readLastDraftCN() +
          " count=" + db.count() +
          " exception" + e + " " + e.getMessage());
      return null;
    }
    finally
    {
      close(draftCNDBCursor);
    }
  }

  /**
   * Get the baseDN associated to a provided key.
   * @param key the provided key.
   * @return the baseDN, null when none.
   */
  public String getBaseDN(int key)
  {
    DraftCNDBCursor draftCNDBCursor = null;
    try
    {
      draftCNDBCursor = db.openReadCursor(key);
      return draftCNDBCursor.currentBaseDN();
    }
    catch(Exception e)
    {
      if (debugEnabled())
        TRACER.debugInfo("In DraftCNDbHandler.getBaseDN(), read: " +
          " key=" + key + " baseDN returned is null" +
          " first=" + db.readFirstDraftCN() +
          " last=" + db.readLastDraftCN() +
          " count=" + db.count() +
          " exception" + e + " " + e.getMessage());
      return null;
    }
    finally
    {
      close(draftCNDBCursor);
    }
  }
}
