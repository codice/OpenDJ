package org.opends.sdk.schema.matchingrules;

import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.StaticUtils;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;

/**
 * This class implements the telephoneNumberMatch matching rule defined in X.520
 * and referenced in RFC 2252.  Note that although the specification calls for a
 * very rigorous format, this is widely ignored so this matching will compare
 * only numeric digits and strip out everything else.
 */
public class TelephoneNumberEqualityMatchingRule
    extends AbstractMatchingRuleImplementation
{
  public ByteString normalizeAttributeValue(Schema schema, ByteSequence value)
  {
    String valueString = value.toString();
    int    valueLength = valueString.length();
    StringBuilder buffer = new StringBuilder(valueLength);


    // Iterate through the characters in the value and filter out everything
    // that isn't a digit.
    for (int i=0; i < valueLength; i++)
    {
      char c = valueString.charAt(i);
      if (StaticUtils.isDigit(c))
      {
        buffer.append(c);
      }
    }


    return ByteString.valueOf(buffer.toString());
  }
}
