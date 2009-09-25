package org.opends.sdk.schema.syntaxes;

import org.opends.messages.MessageBuilder;
import org.opends.sdk.schema.Schema;
import org.opends.server.types.ByteSequence;
import static org.opends.sdk.schema.SchemaConstants.*;

/**
 * This class implements the postal address attribute syntax, which is a list of
 * UCS (Universal Character Set, as defined in the ISO 10646 specification and
 * includes UTF-8 and UTF-16) strings separated by dollar signs.  By default,
 * they will be treated in a case-insensitive manner, and equality and substring
 * matching will be allowed.
 */
public class PostalAddressSyntax extends AbstractSyntaxImplementation
{

  public String getName() {
    return SYNTAX_POSTAL_ADDRESS_NAME;
  }

  public boolean isHumanReadable() {
    return true;
  }

  /**
   * Indicates whether the provided value is acceptable for use in an attribute
   * with this syntax.  If it is not, then the reason may be appended to the
   * provided buffer.
   *
   * @param schema
   *@param  value          The value for which to make the determination.
   * @param  invalidReason  The buffer to which the invalid reason should be
 *                        appended.
 * @return  <CODE>true</CODE> if the provided value is acceptable for use with
   *          this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(Schema schema, ByteSequence value,
                                   MessageBuilder invalidReason)
  {
    // We'll allow any value.
    return true;
  }

  @Override
  public String getEqualityMatchingRule() {
    return EMR_CASE_IGNORE_OID;
  }
  @Override
  public String getSubstringMatchingRule() {
    return SMR_CASE_IGNORE_OID;
  }
}
