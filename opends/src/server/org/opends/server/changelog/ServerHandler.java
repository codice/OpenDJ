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
package org.opends.server.changelog;

import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.synchronization.SynchMessages.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.synchronization.AckMessage;
import org.opends.server.synchronization.ChangeNumber;
import org.opends.server.synchronization.ChangelogStartMessage;
import org.opends.server.synchronization.ServerStartMessage;
import org.opends.server.synchronization.ServerState;
import org.opends.server.synchronization.SynchronizationMessage;
import org.opends.server.synchronization.UpdateMessage;
import org.opends.server.util.TimeThread;

/**
 * This class defines a server handler, which handles all interaction with a
 * changelog server.
 */
public class ServerHandler extends MonitorProvider
{
  private short serverId;
  private ProtocolSession session;
  private MsgQueue msgQueue = new MsgQueue();
  private MsgQueue lateQueue = new MsgQueue();
  private Map<ChangeNumber, AckMessageList> waitingAcks  =
          new HashMap<ChangeNumber, AckMessageList>();;
  private ChangelogCache changelogCache = null;
  private String serverURL;
  private int outCount = 0; // number of update sent to the server
  private int inCount = 0;  // number of updates received from the server
  private int inAckCount = 0;
  private int outAckCount = 0;
  private int maxReceiveQueue = 0;
  private int maxSendQueue = 0;
  private int maxReceiveDelay = 0;
  private int maxSendDelay = 0;
  private int restartReceiveQueue;
  private int restartSendQueue;
  private int restartReceiveDelay;
  private int restartSendDelay;
  private boolean serverIsLDAPserver;
  private boolean following = false;
  private ServerState serverState;
  private boolean active = true;
  private ServerWriter writer = null;
  private DN baseDn = null;

  private static Map<ChangeNumber, ChangelogAckMessageList>
   changelogsWaitingAcks = new HashMap<ChangeNumber, ChangelogAckMessageList>();

  /**
   * Creates a new server handler instance with the provided socket.
   *
   * @param  session The ProtocolSession used by the ServerHandler to
   *                 communicate with the remote entity.
   */
  public ServerHandler(ProtocolSession session)
  {
    super("Server Handler");
    this.session = session;
  }

  /**
   * Do the exchange of start messages to know if the remote
   * server is an LDAP or changelog server and to exchange serverID.
   * Then create the reader and writer thread.
   *
   * @param baseDn baseDn of the ServerHandler when this is an outgoing conn.
   *               null if this is an incoming connection.
   */
  public void start(DN baseDn)
  {
    try
    {
      if (baseDn != null)
      {
        this.baseDn = baseDn;
        changelogCache = Changelog.getChangelogCache(baseDn);
        ServerState localServerState = changelogCache.getDbServerState();
        ChangelogStartMessage msg =
          new ChangelogStartMessage(Changelog.getServerId(),
                                    Changelog.getServerURL(),
                                    baseDn, localServerState);

        session.publish(msg);
      }

      SynchronizationMessage msg = session.receive();
      if (msg instanceof ServerStartMessage)
      {
        ServerStartMessage receivedMsg = (ServerStartMessage) msg;
        serverId = receivedMsg.getServerId();
        serverURL = receivedMsg.getServerURL();
        this.baseDn = receivedMsg.getBaseDn();
        this.serverState = receivedMsg.getServerState();

        maxReceiveDelay = receivedMsg.getMaxReceiveDelay();
        maxReceiveQueue = receivedMsg.getMaxReceiveQueue();
        maxSendDelay = receivedMsg.getMaxSendDelay();
        maxSendQueue = receivedMsg.getMaxSendQueue();

        if (maxReceiveQueue > 0)
          restartReceiveQueue = (maxReceiveQueue > 1000 ?
                                  maxReceiveQueue - 200 :
                                  maxReceiveQueue*8/10);
        else
          restartReceiveQueue = 0;

        if (maxSendQueue > 0)
          restartSendQueue = (maxSendQueue  > 1000 ? maxSendQueue - 200 :
            maxSendQueue*8/10);
        else
          restartSendQueue = 0;

        if (maxReceiveDelay > 0)
          restartReceiveDelay = (maxReceiveDelay > 10 ? maxReceiveDelay -1 :
            maxReceiveDelay);
        else
          restartReceiveDelay = 0;

        if (maxSendDelay > 0)
          restartSendDelay = (maxSendDelay > 10 ?
                              maxSendDelay -1 :
                              maxSendDelay);
        else
          restartSendDelay = 0;
        serverIsLDAPserver = true;

        changelogCache = Changelog.getChangelogCache(this.baseDn);
        ServerState localServerState = changelogCache.getDbServerState();
        ChangelogStartMessage myStartMsg =
          new ChangelogStartMessage(Changelog.getServerId(),
                                    Changelog.getServerURL(),
                                    this.baseDn, localServerState);
        session.publish(myStartMsg);
      }
      else if (msg.getClass() == Class.forName(
      "org.opends.server.synchronization.ChangelogStartMessage"))
      {
        ChangelogStartMessage receivedMsg = (ChangelogStartMessage) msg;
        serverId = receivedMsg.getServerId();
        serverURL = receivedMsg.getServerURL();
        serverIsLDAPserver = false;
        this.baseDn = receivedMsg.getBaseDn();
        if (baseDn == null)
        {
          changelogCache = Changelog.getChangelogCache(this.baseDn);
          ServerState serverState = changelogCache.getDbServerState();
          ChangelogStartMessage outMsg =
            new ChangelogStartMessage(Changelog.getServerId(),
                                      Changelog.getServerURL(),
                                      this.baseDn, serverState);
          session.publish(outMsg);
        }
        else
          this.baseDn = baseDn;
        this.serverState = receivedMsg.getServerState();
      }
      else
      {
        // TODO : log error
        return;   // we did not recognize the message, ignore it
      }

      changelogCache = Changelog.getChangelogCache(this.baseDn);

      if (serverIsLDAPserver)
      {
        changelogCache.startServer(this);
      }
      else
      {
        changelogCache.startChangelog(this);
      }

      writer = new ServerWriter(session, serverId, this, changelogCache);

      ServerReader reader = new ServerReader(session, serverId, this,
                                             changelogCache);

      reader.start();
      writer.start();
      DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());
      DirectoryServer.registerMonitorProvider(this);
    }
    catch (Exception e)
    {
      // some problem happened, reject the connection
      int    msgID   = MSGID_CHANGELOG_CONNECTION_ERROR;
      String message = getMessage(msgID, this.toString())
                                  + stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
               ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      try
      {
        session.close();
      } catch (IOException e1)
      {
        // ignore
      }
    }

  }

  /**
   * get the Server Id.
   *
   * @return the ID of the server to which this object is linked
   */
  public short getServerId()
  {
    return serverId;
  }

  /**
   * Retrieves the URL for this server handler.
   *
   * @return  The URL for this server handler, in the form of an address and
   *          port separated by a colon.
   */
  public String getServerURL()
  {
    return serverURL;
  }

  /**
   * Increase the counter of updates sent to the server.
   */
  public void incrementOutCount()
  {
    outCount++;
  }

  /**
   * Increase the counter of update received from the server.
   */
  public void incrementInCount()
  {
    inCount++;
  }

  /**
   * Get the count of updates received from the server.
   * @return the count of update received from the server.
   */
  public int getInCount()
  {
    return inCount;
  }

  /**
   * Get the count of updates sent to this server.
   * @return  The count of update sent to this server.
   */
  public int getOutCount()
  {
    return outCount;
  }

  /**
   * Get the number of Ack received from the server managed by this handler.
   *
   * @return Returns the inAckCount.
   */
  public int getInAckCount()
  {
    return inAckCount;
  }

  /**
   * Get the number of Ack sent to the server managed by this handler.
   *
   * @return Returns the outAckCount.
   */
  public int getOutAckCount()
  {
    return outAckCount;
  }

  /**
   * Check is this server is saturated (this server has already been
   * sent a bunch of updates and has not processed them so they are staying
   * in the message queue for this server an the size of the queue
   * for this server is above the configured limit.
   *
   * The limit can be defined in number of updates or with a maximum delay
   *
   * @param changeNumber The changenumber to use to make the delay calculations.
   * @param sourceHandler The ServerHandler which is sending the update.
   * @return true is saturated false if not saturated.
   */
  public boolean isSaturated(ChangeNumber changeNumber,
                             ServerHandler sourceHandler)
  {
    synchronized (msgQueue)
    {
      int size = msgQueue.size();

      if ((maxReceiveQueue > 0) && (size >= maxReceiveQueue))
        return true;

      if ((sourceHandler.maxSendQueue > 0) &&
          (size >= sourceHandler.maxSendQueue))
        return true;

      if (!msgQueue.isEmpty())
      {
        UpdateMessage firstUpdate = msgQueue.first();

        if (firstUpdate != null)
        {
          long timeDiff = changeNumber.getTimeSec() -
          firstUpdate.getChangeNumber().getTimeSec();

          if ((maxReceiveDelay > 0) && (timeDiff >= maxReceiveDelay))
            return true;

          if ((sourceHandler.maxSendDelay > 0) &&
              (timeDiff >= sourceHandler.maxSendDelay))
            return true;
        }
      }
      return false;
    }
  }

  /**
   * Check that the size of the Server Handler messages Queue has lowered
   * below the limit and therefore allowing the reception of messages
   * from other servers to restart.
   * @param source The ServerHandler which was sending the update.
   *        can be null.
   * @return true if the processing can restart
   */
  public boolean restartAfterSaturation(ServerHandler source)
  {
    synchronized (msgQueue)
    {
      if (msgQueue == null)
        return true;

      synchronized (msgQueue)
      {
        int queueSize = msgQueue.size();
        if ((maxReceiveQueue > 0) && (queueSize >= restartReceiveQueue))
          return false;
        if ((source != null) && (source.maxSendQueue > 0) &&
            (queueSize >= source.restartSendQueue))
          return false;

        if (!msgQueue.isEmpty())
        {
          UpdateMessage firstUpdate = msgQueue.first();
          UpdateMessage lastUpdate = msgQueue.last();

          if ((firstUpdate != null) && (lastUpdate != null))
          {
            long timeDiff = lastUpdate.getChangeNumber().getTimeSec() -
            firstUpdate.getChangeNumber().getTimeSec();
            if ((maxReceiveDelay > 0) && (timeDiff >= restartReceiveDelay))
              return false;
            if ((source != null) && (source.maxSendDelay > 0)
                && (timeDiff >= source.restartSendDelay))
              return false;
          }
        }
      }
      return true;
    }
  }

  /**
   * Check if the server associated to this ServerHandler is a changelog server.
   * @return true if the server associated to this ServerHandler is a
   *         changelog server.
   */
  public boolean isChangelogServer()
  {
    return (!serverIsLDAPserver);
  }

  /**
   * Get the number of message in the receive message queue.
   * @return Size of the receive message queue.
   */
  public int getRcvMsgQueueSize()
  {
   synchronized (msgQueue)
   {
     /*
      * TODO : When the server  is not able to follow, the msgQueue
      * may become too large and therefore won't contain all the
      * changes. Some changes may only be stored in the backing DB
      * of the servers.
      * The calculation should be done by asking to the each dbHandler
      * how many changes need to be replicated and making the sum
      * For now just return maxint in this case
      */
     if (isFollowing())
       return msgQueue.size();
     else
       return Integer.MAX_VALUE;
   }
  }

  /**
   * Get an approximation of the delay by looking at the age of the odest
   * message that has not been sent to this server.
   * This is an approximation because the age is calculated using the
   * clock of the servee where the changelog is currently running
   * while it should be calculated using the clock of the server
   * that originally processed the change.
   *
   * The approximation error is therefore the time difference between
   *
   * @return the approximate delay for the connected server.
   */
  public long getApproxDelay()
  {
    long olderUpdateTime = getOlderUpdateTime();
    if (olderUpdateTime == 0)
      return 0;

    long currentTime = TimeThread.getTime();
    return ((currentTime - olderUpdateTime)/1000);
  }

  /**
   * Get the age of the older change that has not yet been replicated
   * to the server handled by this ServerHandler.
   *
   * @return The age if the older change has not yet been replicated
   *         to the server handled by this ServerHandler.
   */
  public long getOlderUpdateTime()
  {
    synchronized (msgQueue)
    {
      if (isFollowing())
      {
        if (msgQueue.isEmpty())
          return 0;

        UpdateMessage msg = msgQueue.first();
        return msg.getChangeNumber().getTime();
      }
      else
      {
        if (lateQueue.isEmpty())
          return 0;

        UpdateMessage msg = lateQueue.first();
        return msg.getChangeNumber().getTime();
      }
    }
  }

  /**
   * Check if the LDAP server can follow the speed of the other servers.
   * @return true when the server has all the not yet sent changes
   *         in its queue.
   */
  public boolean isFollowing()
  {
    return following;
  }

  /**
   * Set the following flag of this server.
   * @param following the value that should be set.
   */
  public void setFollowing(boolean following)
  {
    this.following = following;
  }

  /**
   * Add an update the list of updates that must be sent to the server
   * managed by this ServerHandler.
   *
   * @param update The update that must be added to the list of updates.
   */
  public void add(UpdateMessage update)
  {
    synchronized (msgQueue)
    {
      /*
       * If queue was empty the writer thread was probably asleep
       * waiting for some changes, wake it up
       */
      if (msgQueue.isEmpty())
        msgQueue.notify();

      msgQueue.add(update);

      /* TODO : size should be configurable
       * and larger than max-receive-queue-size
       */
      while (msgQueue.size() > 10000)
      {
        following = false;
        msgQueue.removeFirst();
      }
    }
  }

  /**
   * Select the next update that must be sent to the server managed by this
   * ServerHandler.
   *
   * @return the next update that must be sent to the server managed by this
   *         ServerHandler.
   */
  public UpdateMessage take()
  {
    UpdateMessage msg;
    do
    {
      if (following == false)
      {
        /* this server is late with regard to some other masters
         * in the topology or just joined the topology.
         * In such cases, we can't keep all changes in the queue
         * without saturating the memory, we therefore use
         * a lateQueue that is filled with a few changes from the changelogDB
         * If this server is able to close the gap, it will start using again
         * the regular msgQueue later.
         */
        if (lateQueue.isEmpty())
        {
          /*
           * Start from the server State
           * Loop until the queue high mark or until no more changes
           *   for each known LDAP master
           *      get the next CSN after this last one :
           *         - try to get next from the file
           *         - if not found in the file
           *             - try to get the next from the queue
           *   select the smallest of changes
           *   check if it is in the memory tree
           *     yes : lock memory tree.
           *           check all changes from the list, remove the ones that
           *           are already sent
           *           unlock memory tree
           *           restart as usual
           *   load this change on the delayList
           *
           */
          ChangelogIteratorComparator comparator =
            new ChangelogIteratorComparator();
          SortedSet<ChangelogIterator> iteratorSortedSet =
            new TreeSet<ChangelogIterator>(comparator);
          /* fill the lateQueue */
          for (short serverId : changelogCache.getServers())
          {
            ChangeNumber lastCsn = serverState.getMaxChangeNumber(serverId);
            ChangelogIterator iterator =
              changelogCache.getChangelogIterator(serverId, lastCsn);
            if ((iterator != null) && (iterator.getChange() != null))
            {
              iteratorSortedSet.add(iterator);
            }
          }
          while (!iteratorSortedSet.isEmpty() && (lateQueue.size()<100))
          {
            ChangelogIterator iterator = iteratorSortedSet.first();
            iteratorSortedSet.remove(iterator);
            lateQueue.add(iterator.getChange());
            if (iterator.next())
              iteratorSortedSet.add(iterator);
            else
              iterator.releaseCursor();
          }
          for (ChangelogIterator iterator : iteratorSortedSet)
          {
            iterator.releaseCursor();
          }
          /*
           * Check if the first change in the lateQueue is also on the regular
           * queue
           */
          if (lateQueue.isEmpty())
          {
            synchronized (msgQueue)
            {
              if (msgQueue.size() < 10000)
              {
                following = true;
              }
            }
          }
          else
          {
            msg = lateQueue.first();
            synchronized (msgQueue)
            {
              if (msgQueue.contains(msg))
              {
                /* we finally catched up with the regular queue */
                following = true;
                lateQueue.clear();
                UpdateMessage msg1;
                do
                {
                  msg1 = msgQueue.removeFirst();
                } while (!msg.getChangeNumber().equals(msg1.getChangeNumber()));
                this.updateServerState(msg);
                this.incrementOutCount();
                return msg;
              }
            }
          }
        }
        else
        {
          /* get the next change from the lateQueue */
          msg = lateQueue.removeFirst();
          this.updateServerState(msg);
          this.incrementOutCount();
          return msg;
        }
      }
      synchronized (msgQueue)
      {
        if (following == true)
        {
          try
          {
            while (msgQueue.isEmpty())
            {
              msgQueue.wait(500);
              if (!active)
                return null;
            }
          } catch (InterruptedException e)
          {
            return null;
          }
          msg = msgQueue.removeFirst();
          if (this.updateServerState(msg))
          {
            /*
             * Only push the message if it has not yet been seen
             * by the other server.
             * Otherwise just loop to select the next message.
             */
            this.incrementOutCount();
            return msg;
          }
        }
      }
      /*
       * Need to loop because following flag may have gone to false between
       * the first check at the beginning of this method
       * and the second check just above.
       */
    } while (active == true);
    return null;
  }

  /**
   * Update the serverState with the last message sent.
   *
   * @param msg the last update sent.
   * @return boolean indicating if the update was meaningfull.
   */
  public boolean  updateServerState(UpdateMessage msg)
  {
    return serverState.update(msg.getChangeNumber());
  }

  /**
   * Stop this server handler processing.
   */
  public void stopHandler()
  {
    active = false;
    synchronized (msgQueue)
    {
      /* wake up the writer thread on an empty queue so that it disappear */
      msgQueue.clear();
      msgQueue.notify();
      msgQueue.notifyAll();
    }

    DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());
  }

  /**
   * Send the ack to the server that did the original modification.
   *
   * @param changeNumber The ChangeNumber of the update that is acked.
   * @throws IOException In case of Exception thrown sending the ack.
   */
  public void sendAck(ChangeNumber changeNumber) throws IOException
  {
    AckMessage ack = new AckMessage(changeNumber);
    session.publish(ack);
    outAckCount++;
  }

  /**
   * Do the work when an ack message has been received from another server.
   *
   * @param message The ack message that was received.
   * @param ackingServerId The  id of the server that acked the change.
   */
  public void ack(AckMessage message, short ackingServerId)
  {
    ChangeNumber changeNumber = message.getChangeNumber();
    AckMessageList ackList;
    boolean completedFlag;
    synchronized (waitingAcks)
    {
      ackList = waitingAcks.get(changeNumber);
      if (ackList == null)
        return;
      ackList.addAck(ackingServerId);
      completedFlag = ackList.completed();
      if (completedFlag)
      {
        waitingAcks.remove(changeNumber);
      }
    }
    if (completedFlag)
    {
      changelogCache.sendAck(changeNumber, true);
    }
  }

  /**
   * Process reception of an for an update that was received from a
   * Changelog Server.
   *
   * @param message the ack message that was received.
   * @param ackingServerId The  id of the server that acked the change.
   */
  public static void ackChangelog(AckMessage message, short ackingServerId)
  {
    ChangeNumber changeNumber = message.getChangeNumber();
    ChangelogAckMessageList ackList;
    boolean completedFlag;
    synchronized (changelogsWaitingAcks)
    {
      ackList = changelogsWaitingAcks.get(changeNumber);
      if (ackList == null)
        return;
      ackList.addAck(ackingServerId);
      completedFlag = ackList.completed();
      if (completedFlag)
      {
        changelogsWaitingAcks.remove(changeNumber);
      }
    }
    if (completedFlag)
    {
      ChangelogCache changelogCache = ackList.getChangelogCache();
      changelogCache.sendAck(changeNumber, false,
                             ackList.getChangelogServerId());
    }
  }

  /**
   * Add an update to the list of update waiting for acks.
   *
   * @param update the update that must be added to the list
   * @param nbWaitedAck  The number of ack that must be received before
   *               the update is fully acked.
   */
  public void addWaitingAck(UpdateMessage update, int nbWaitedAck)
  {
    AckMessageList ackList = new AckMessageList(update.getChangeNumber(),
                                                nbWaitedAck);
    synchronized(waitingAcks)
    {
      waitingAcks.put(update.getChangeNumber(), ackList);
    }
  }

  /**
   * Add an update to the list of update received from a changelog server and
   * waiting for acks.
   *
   * @param update The update that must be added to the list.
   * @param ChangelogServerId The identifier of the changelog that sent the
   *                          update.
   * @param changelogCache The ChangelogCache from which the change was
   *                       processed and to which the ack must later be sent.
   * @param nbWaitedAck The number of ack that must be received before
   *                    the update is fully acked.
   */
  public static void addWaitingAck(UpdateMessage update,
      short ChangelogServerId, ChangelogCache changelogCache, int nbWaitedAck)
  {
    ChangelogAckMessageList ackList =
          new ChangelogAckMessageList(update.getChangeNumber(),
                                      nbWaitedAck,
                                      ChangelogServerId, changelogCache);
    synchronized(changelogsWaitingAcks)
    {
      changelogsWaitingAcks.put(update.getChangeNumber(), ackList);
    }
  }

  /**
   * Get the size of the list of update waiting for acks.
   *
   * @return the size of the list of update waiting for acks.
   */
  public int getWaitingAckSize()
  {
    synchronized (waitingAcks)
    {
      return waitingAcks.size();
    }
  }

  /**
   * Increment the count of Acks received from this server.
   */
  public void incrementInAckCount()
  {
    inAckCount++;
  }

  /**
   * Check type of server handled.
   *
   * @return true if the handled server is an LDAP server.
   *         false if the handled server is a changelog server
   */
  public boolean isLDAPserver()
  {
    return serverIsLDAPserver;
  }

  /**
   * Initializes this monitor provider based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this monitor provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  @Override
  public void initializeMonitorProvider(ConfigEntry configEntry)
                          throws ConfigException,InitializationException
  {
    // Nothing to do for now
  }

  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    String str = changelogCache.getBaseDn().toString() +
                 " " + serverURL + " " + String.valueOf(serverId);

    if (serverIsLDAPserver)
      return "LDAP Server " + str;
    else
      return "Changelog Server " + str;
  }

  /**
   * Retrieves the length of time in milliseconds that should elapse between
   * calls to the <CODE>updateMonitorData()</CODE> method.  A negative or zero
   * return value indicates that the <CODE>updateMonitorData()</CODE> method
   * should not be periodically invoked.
   *
   * @return  The length of time in milliseconds that should elapse between
   *          calls to the <CODE>updateMonitorData()</CODE> method.
   */
  @Override
  public long getUpdateInterval()
  {
    /* we don't wont to do polling on this monitor */
    return 0;
  }

  /**
   * Performs any processing periodic processing that may be desired to update
   * the information associated with this monitor.  Note that best-effort
   * attempts will be made to ensure that calls to this method come
   * <CODE>getUpdateInterval()</CODE> milliseconds apart, but no guarantees will
   * be made.
   */
  @Override
  public void updateMonitorData()
  {
    // As long as getUpdateInterval() returns 0, this will never get called

  }

  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  @Override
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();
    if (serverIsLDAPserver)
      attributes.add(new Attribute("LDAP-Server", serverURL));
    else
      attributes.add(new Attribute("Changelog-Server", serverURL));
    attributes.add(new Attribute("server-id",
                                 String.valueOf(serverId)));
    attributes.add(new Attribute("base-dn",
                                 changelogCache.getBaseDn().toString()));
    attributes.add(new Attribute("waiting-changes",
                                 String.valueOf(getRcvMsgQueueSize())));
    attributes.add(new Attribute("update-waiting-acks",
                                 String.valueOf(getWaitingAckSize())));
    attributes.add(new Attribute("update-sent",
                                 String.valueOf(getOutCount())));
    attributes.add(new Attribute("update-received",
                                 String.valueOf(getInCount())));
    attributes.add(new Attribute("ack-sent", String.valueOf(getOutAckCount())));
    attributes.add(new Attribute("ack-received",
                                 String.valueOf(getInAckCount())));
    attributes.add(new Attribute("approximate-delay",
                                 String.valueOf(getApproxDelay())));
    long olderUpdateTime = getOlderUpdateTime();
    if (olderUpdateTime != 0)
    {
      Date date = new Date(getOlderUpdateTime());
      attributes.add(new Attribute("older-change-not-synchronized",
                                 String.valueOf(date.toString())));
    }

    /* get the Server State */
    final String ATTR_SERVER_STATE = "server-state";
    AttributeType type =
      DirectoryServer.getDefaultAttributeType(ATTR_SERVER_STATE);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    for (String str : serverState.toStringSet())
    {
      values.add(new AttributeValue(type,str));
    }
    Attribute attr = new Attribute(type, ATTR_SERVER_STATE, values);
    attributes.add(attr);
    return attributes;
  }

  /**
   * Shutdown This ServerHandler.
   */
  public void shutdown()
  {
    try
    {
      session.close();
    } catch (IOException e)
    {
      // Service is closing.
    }
    stopHandler();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    String localString;
    if (serverId != 0)
    {
      if (serverIsLDAPserver)
        localString = "Directory Server ";
      else
        localString = "Changelog Server ";


      localString += serverId + " " + serverURL + " " + baseDn;
    }
    else
      localString = "Unknown server";

    return localString;
  }
}
