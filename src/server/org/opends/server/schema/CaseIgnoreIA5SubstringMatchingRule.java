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
package org.opends.server.schema;



import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.messages.Message;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.ServerConstants;



/**
 * This class implements the caseIgnoreIA5SubstringsMatch matching rule defined
 * in RFC 2252.
 */
class CaseIgnoreIA5SubstringMatchingRule
       extends SubstringMatchingRule
{
  /**
   * Creates a new instance of this caseIgnoreSubstringsMatch matching rule.
   */
  public CaseIgnoreIA5SubstringMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getAllNames()
  {
    return Collections.singleton(getName());
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  @Override
  public String getName()
  {
    return SMR_CASE_IGNORE_IA5_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return SMR_CASE_IGNORE_IA5_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  @Override
  public String getSyntaxOID()
  {
    return SYNTAX_SUBSTRING_ASSERTION_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeValue(ByteSequence value)
         throws DirectoryException
  {
    StringBuilder buffer = new StringBuilder();
    toLowerCase(value, buffer, true);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (value.length() > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return ServerConstants.SINGLE_SPACE_VALUE;
      }
      else
      {
        // The value is empty, so it is already normalized.
        return ByteString.empty();
      }
    }


    // Replace any consecutive spaces with a single space, and watch out for
    // non-ASCII characters.
    boolean logged = false;
    for (int pos = bufferLength-1; pos > 0; pos--)
    {
      char c = buffer.charAt(pos);
      if (c == ' ')
      {
        if (buffer.charAt(pos-1) == ' ')
        {
          buffer.delete(pos, pos+1);
        }
      }
      else if ((c & 0x7F) != c)
      {
        // This is not a valid character for an IA5 string.  If strict syntax
        // enforcement is enabled, then we'll throw an exception.  Otherwise,
        // we'll get rid of the character.
        Message message = WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER.get(
                value.toString(), String.valueOf(c));

        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          case WARN:
            if (! logged)
            {
              ErrorLogger.logError(message);
              logged = true;
            }

            buffer.delete(pos, pos+1);
            break;

          default:
            buffer.delete(pos, pos+1);
            break;
        }
      }
    }

    return ByteString.valueOf(buffer.toString());
  }



  /**
   * Normalizes the provided value fragment into a form that can be used to
   * efficiently compare values.
   *
   * @param  substring  The value fragment to be normalized.
   *
   * @return  The normalized form of the value fragment.
   *
   * @throws  DirectoryException  If the provided value fragment is not
   *                              acceptable according to the associated syntax.
   */
  @Override
  public ByteString normalizeSubstring(ByteSequence substring)
         throws DirectoryException
  {
    // In this case, the process for normalizing a substring is the same as
    // normalizing a full value with the exception that it may include an
    // opening or trailing space.
    StringBuilder buffer = new StringBuilder();
    toLowerCase(substring, buffer, false);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (substring.length() > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return ServerConstants.SINGLE_SPACE_VALUE;
      }
      else
      {
        // The value is empty, so it is already normalized.
        return substring.toByteString();
      }
    }


    // Replace any consecutive spaces with a single space, and watch out for
    // non-ASCII characters.
    boolean logged = false;
    for (int pos = bufferLength-1; pos > 0; pos--)
    {
      char c = buffer.charAt(pos);
      if (c == ' ')
      {
        if (buffer.charAt(pos-1) == ' ')
        {
          buffer.delete(pos, pos+1);
        }
      }
      else if ((c & 0x7F) != c)
      {
        // This is not a valid character for an IA5 string.  If strict syntax
        // enforcement is enabled, then we'll throw an exception.  Otherwise,
        // we'll get rid of the character.
        Message message = WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER.get(
                substring.toString(), String.valueOf(c));

        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          case WARN:
            if (! logged)
            {
              ErrorLogger.logError(message);
              logged = true;
            }

            buffer.delete(pos, pos+1);
            break;

          default:
            buffer.delete(pos, pos+1);
            break;
        }
      }
    }

    return ByteString.valueOf(buffer.toString());
  }
}

