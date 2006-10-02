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
package org.opends.server.api;



import java.util.List;

import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.InitializationException;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that can provide usage,
 * performance, availability, or other kinds of monitor information
 * to clients.
 */
public abstract class MonitorProvider
       extends DirectoryThread
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.MonitorProvider";



  // Indicates whether a request has been received to stop running.
  private boolean stopRequested;

  // The thread used to run this monitor provider.
  private Thread monitorThread;



  /**
   * Initializes this monitor provider.  Note that no initialization
   * should be done here, since it should be performed in the
   * <CODE>initializeMonitorProvider</CODE> class.
   *
   * @param  threadName  The name to use for this thread for debugging
   *                     purposes.
   */
  protected MonitorProvider(String threadName)
  {
    super(threadName);

    assert debugConstructor(CLASS_NAME);

    stopRequested = false;
    monitorThread = null;
  }



  /**
   * Initializes this monitor provider based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this
   *                      monitor provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeMonitorProvider(
                            ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Finalizes this monitor provider so that it may be unloaded and
   * taken out of service.  This method should be overridden by any
   * monitor provider that has resources that should be released when
   * the monitor is no longer needed.  Any monitor that does override
   * this method must first invoke this version by calling
   * <CODE>super.finalizeMonitorProvider()</CODE>.
   */
  public void finalizeMonitorProvider()
  {
    assert debugEnter(CLASS_NAME, "finalizeMonitorProvider");

    // Signal the monitor thread that it should stop.
    stopRequested = true;

    try
    {
      if (monitorThread != null)
      {
        monitorThread.interrupt();
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "processServerShutdown", e);
    }
  }



  /**
   * Retrieves the name of this monitor provider.  It should be unique
   * among all monitor providers, including all instances of the same
   * monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  public abstract String getMonitorInstanceName();



  /**
   * Retrieves the length of time in milliseconds that should elapse
   * between calls to the <CODE>updateMonitorData()</CODE> method.  A
   * negative or zero return value indicates that the
   * <CODE>updateMonitorData()</CODE> method should not be
   * periodically invoked.
   *
   * @return  The length of time in milliseconds that should elapse
   *          between calls to the <CODE>updateMonitorData()</CODE>
   *          method.
   */
  public abstract long getUpdateInterval();



  /**
   * Performs any processing periodic processing that may be desired
   * to update the information associated with this monitor.  Note
   * that best-effort attempts will be made to ensure that calls to
   * this method come <CODE>getUpdateInterval()</CODE> milliseconds
   * apart, but no guarantees will be made.
   */
  public abstract void updateMonitorData();



  /**
   * Retrieves a set of attributes containing monitor data that should
   * be returned to the client if the corresponding monitor entry is
   * requested.
   *
   * @return  A set of attributes containing monitor data that should
   *          be returned to the client if the corresponding monitor
   *          entry is requested.
   */
  public abstract List<Attribute> getMonitorData();



  /**
   * Enters a loop, periodically invoking the
   * <CODE>getUpdateInterval()</CODE> method to updates the data
   * associated with this monitor.
   */
  public final void run()
  {
    assert debugEnter(CLASS_NAME, "run");


    monitorThread = Thread.currentThread();


    // If this monitor should not perform any checks to periodically
    // update its information, then there is no need to run this
    // method.
    if (getUpdateInterval() <= 0)
    {
      return;
    }


    // Set the name of this thread for debugging purposes.
    setName(getMonitorInstanceName() + " Monitor Provider");


    // Operate in a loop until it is detected that the server is
    // shutting down.
    while (! stopRequested)
    {
      long stopSleepTime =
           System.currentTimeMillis() + getUpdateInterval();
      updateMonitorData();

      long remainingSleepTime =
           stopSleepTime - System.currentTimeMillis();
      while ((! stopRequested) && (remainingSleepTime > 0))
      {
        if (remainingSleepTime > 1000)
        {
          try
          {
            Thread.sleep(1000);
          } catch (Exception e) {}
        }
        else
        {
          try
          {
            Thread.sleep(remainingSleepTime);
          } catch (Exception e) {}
        }

        remainingSleepTime =
             stopSleepTime - System.currentTimeMillis();
      }
    }
  }



  /**
   * Retrieves the human-readable name for this shutdown listener.
   *
   * @return  The human-readable name for this shutdown listener.
   */
  public final String getShutdownListenerName()
  {
    assert debugEnter(CLASS_NAME, "getShutdownListenerName");

    return "Monitor Provider " + getMonitorInstanceName();
  }



  /**
   * Indicates that the Directory Server has received a request to
   * stop running and that this shutdown listener should take any
   * action necessary to prepare for it.
   *
   * @param  reason  The human-readable reason for the shutdown.
   */
  public final void processServerShutdown(String reason)
  {
    assert debugEnter(CLASS_NAME, "processServerShutdown",
                      String.valueOf(reason));

    finalizeMonitorProvider();
  }
}

