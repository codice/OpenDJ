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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server to another server in order to
 * request this other server to do an export to the server sender
 * of this message.
 */
public class InitializeRequestMsg extends RoutableMsg
{
  private String baseDn = null;

  /**
   * Creates a InitializeRequestMsg message.
   *
   * @param baseDn The base DN of the replication domain.
   * @param destination destination of this message
   * @param serverID serverID of the server that will send this message
   */
  public InitializeRequestMsg(String baseDn, int serverID, int destination)
  {
    super(serverID, destination);
    this.baseDn = baseDn;
  }

  /**
   * Creates a new InitializeRequestMsg by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the Message
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded InitializeMessage.
   */
  public InitializeRequestMsg(byte[] in) throws DataFormatException
  {
    super();
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_INITIALIZE_REQUEST)
        throw new DataFormatException(
            "input is not a valid InitializeRequestMessage");
      int pos = 1;

      // baseDn
      int length = getNextLength(in, pos);
      baseDn = new String(in, pos, length, "UTF-8");
      pos += length +1;

      // sender
      length = getNextLength(in, pos);
      String sourceServerIdString = new String(in, pos, length, "UTF-8");
      senderID = Integer.valueOf(sourceServerIdString);
      pos += length +1;

      // destination
      length = getNextLength(in, pos);
      String destinationServerIdString = new String(in, pos, length, "UTF-8");
      destination = Integer.valueOf(destinationServerIdString);
      pos += length +1;


    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the base DN from this InitializeRequestMsg.
   *
   * @return the base DN from this InitializeRequestMsg.
   */
  public DN getBaseDn()
  {
    if (baseDn == null)
      return null;
    try
    {
      return DN.decode(baseDn);
    } catch (DirectoryException e)
    {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    try {
      byte[] baseDNBytes = baseDn.getBytes("UTF-8");
      byte[] senderBytes = String.valueOf(senderID).getBytes("UTF-8");
      byte[] destinationBytes = String.valueOf(destination).
      getBytes("UTF-8");

      int length = 1 + baseDNBytes.length + 1 + senderBytes.length + 1
        + destinationBytes.length + 1;

      byte[] resultByteArray = new byte[length];

      // type of the operation
      resultByteArray[0] = MSG_TYPE_INITIALIZE_REQUEST;
      int pos = 1;

      // baseDN
      pos = addByteArray(baseDNBytes, resultByteArray, pos);

      // sender
      pos = addByteArray(senderBytes, resultByteArray, pos);

      // destination
      pos = addByteArray(destinationBytes, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  public String toString()
  {
    return "InitializeRequestMessage: baseDn="+baseDn+" senderId="+senderID +
    " destination=" + destination;
  }
}
