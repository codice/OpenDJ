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
package org.opends.server.api.plugin;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a data structure that holds information about
 * the result of processing by a plugin invoked during the Directory
 * Server startup process.
 */
public class StartupPluginResult
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.plugin.StartupPluginResult";



  /**
   * A startup plugin result instance that indicates all processing
   * was successful.
   */
  public static final StartupPluginResult SUCCESS =
       new StartupPluginResult();



  // Indicates whether the startup plugin completed successfully.
  private final boolean completedSuccessfully;

  // Indicates whether the server should continue with the startup
  // process.
  private final boolean continueStartup;

  // The unique identifier for any error message generated by the
  // startup plugin.
  private final int errorID;

  // A human-readable message that explains any error that might have
  // occurred.
  private final String errorMessage;



  /**
   * Creates a new startup plugin result with the default settings.
   * In this case, it will indicate that the plugin completed
   * successfully, that the startup process should continue, and that
   * there is no error message.
   */
  private StartupPluginResult()
  {
    this(true, true, 0, null);
  }



  /**
   * Creates a new startup plugin result with the provided
   * information.
   *
   * @param  completedSuccessfully  Indicates whether the startup
   *                                plugin completed its processing
   *                                successfully.
   * @param  continueStartup        Indicates whether the Directory
   *                                Server should continue with its
   *                                startup process.
   * @param  errorID                The unique ID assigned to any
   *                                error message that might have been
   *                                generated by the startup plugin.
   * @param  errorMessage           A human-readable error message
   *                                that explains any error that might
   *                                have occurred.
   */
  public StartupPluginResult(boolean completedSuccessfully,
                             boolean continueStartup, int errorID,
                             String errorMessage)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(completedSuccessfully),
                            String.valueOf(continueStartup),
                            String.valueOf(errorID),
                            String.valueOf(errorMessage));

    this.completedSuccessfully = completedSuccessfully;
    this.continueStartup       = continueStartup;
    this.errorID               = errorID;
    this.errorMessage          = errorMessage;
  }



  /**
   * Indicates whether the startup plugin completed its processing
   * successfully.
   *
   * @return  <CODE>true</CODE> if the startup plugin completed its
   *          processing successfully, or <CODE>false</CODE> if not.
   */
  public boolean completedSuccessfully()
  {
    assert debugEnter(CLASS_NAME, "completedSuccessfully");

    return completedSuccessfully;
  }



  /**
   * Indicates whether the Directory Server should continue with its
   * startup process.
   *
   * @return  <CODE>true</CODE> if the Directory Server should
   *          continue with its startup process, or <CODE>false</CODE>
   *          if not.
   */
  public boolean continueStartup()
  {
    assert debugEnter(CLASS_NAME, "continueStartup");

    return continueStartup;
  }



  /**
   * Retrieves the unique ID for the error message generated by the
   * startup plugin.
   *
   * @return  The unique ID for the error message generated by the
   *          startup plugin, or 0 if there is no error message.
   */
  public int getErrorID()
  {
    assert debugEnter(CLASS_NAME, "getErrorID");

    return errorID;
  }



  /**
   * Retrieves the human-readable error message generated by the
   * startup plugin.
   *
   * @return  The human-readable error message generated by the
   *          startup plugin, or <CODE>null</CODE> if there is no
   *          error message.
   */
  public String getErrorMessage()
  {
    assert debugEnter(CLASS_NAME, "getErrorMessage");

    return errorMessage;
  }



  /**
   * Retrieves a string representation of this startup plugin result.
   *
   * @return  A string representation of this startup plugin result.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this startup plugin result to
   * the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

    buffer.append("StartupPluginResult(completedSuccessfully=");
    buffer.append(completedSuccessfully);
    buffer.append(", continueStartup=");
    buffer.append(continueStartup);
    buffer.append(", errorID=");
    buffer.append(errorID);
    buffer.append(", errorMessage=\"");
    buffer.append(errorMessage);
    buffer.append("\")");
  }
}

