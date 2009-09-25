package org.opends.sdk.schema;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.opends.sdk.DecodeException;
import org.opends.sdk.schema.AbstractSchemaElement;
import org.opends.sdk.schema.AttributeType;
import org.opends.sdk.schema.AttributeUsage;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.schema.SchemaBuilder;
import org.opends.sdk.schema.SchemaException;
import org.opends.sdk.schema.Syntax;
import org.opends.server.types.CommonSchemaElements;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: boli
 * Date: Aug 24, 2009
 * Time: 1:49:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class AttributeTypeTest extends AbstractSchemaElementTestCase
{
  private Schema schema;

  public AttributeTypeTest() throws Exception
  {
    SchemaBuilder builder = new SchemaBuilder();
    builder.addAttributeType("1.2.1",
        EMPTY_NAMES,
        "",
        true,
        null,
        null,
        null,
        null,
        null,
        "1.3.6.1.4.1.1466.115.121.1.27",
        true,
        false,
        false,
        AttributeUsage.USER_APPLICATIONS,
        EMPTY_PROPS, false);
    builder.addAttributeType(
        "( 1.2.2 OBSOLETE SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
            " COLLECTIVE)",
        false);
    builder.addAttributeType("1.2.3",
        Collections.singletonList("testType"),
        "",
        false,
        "1.2.2",
        null,
        null,
        null,
        null,
        "1.3.6.1.4.1.1466.115.121.1.27",
        false,
        true,
        false,
        AttributeUsage.USER_APPLICATIONS,
        EMPTY_PROPS, false);
    builder.addAttributeType(
        "( 1.2.4 NAME 'testType' SUP 1.2.3 SINGLE-VALUE COLLECTIVE )", false);
    List<String> names = new LinkedList<String>();
    names.add("testType");
    names.add("testnamealias");
    names.add("anothernamealias");
    builder.addAttributeType("1.2.5",
        names,
        "",
        false,
        null,
        EMR_CASE_IGNORE_LIST_OID,
        null,
        SMR_CASE_IGNORE_LIST_OID,
        AMR_DOUBLE_METAPHONE_OID,
        SYNTAX_INTEGER_OID,
        false,
        false,
        true,
        AttributeUsage.DSA_OPERATION,
        EMPTY_PROPS, false);
    builder.addAttributeType(
        "( 1.2.6 NAME ( 'testType' 'testnamealias' 'anothernamealias1' ) " +
            " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SUP anothernamealias" +
            " USAGE dSAOperation NO-USER-MODIFICATION )",
        false);
    schema = builder.toSchema();
  }
  protected AbstractSchemaElement getElement(String description,
                                             Map<String, List<String>> extraProperties)
      throws SchemaException
  {
    SchemaBuilder builder = new SchemaBuilder();
    builder.addAttributeType("1.2.3",
        Collections.singletonList("testType"),
        description,
        false,
        null,
        null,
        null,
        null,
        null,
        "1.3.6.1.4.1.1466.115.121.1.27",
        false,
        false,
        false,
        AttributeUsage.DSA_OPERATION,
        extraProperties, false);
    return builder.toSchema().getAttributeType("1.2.3");
  }

  @DataProvider(name = "equalsTestData")
  public Object[][] createEqualsTestData()
      throws SchemaException, DecodeException
  {
    return new Object[][] {
        { schema.getAttributeType("1.2.3"),
            schema.getAttributeType("1.2.3"), true },
        { schema.getAttributeType("1.2.4"),
            schema.getAttributeType("1.2.3"), false } };  }



  /**
   * Check that the simple constructor throws an NPE when mandatory
   * parameters are not specified.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNoSupNorSyntax1() throws Exception {
    SchemaBuilder builder = new SchemaBuilder();
    builder.addAttributeType("1.2.1",
        EMPTY_NAMES,
        "",
        true,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        false,
        AttributeUsage.DSA_OPERATION,
        EMPTY_PROPS, false);
    builder.addAttributeType(
        "( 1.2.2 OBSOLETE SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE )",
        false);
  }

  /**
   * Check that the simple constructor throws an NPE when mandatory
   * parameters are not specified.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNoSupNorSyntax2() throws Exception {
    SchemaBuilder builder = new SchemaBuilder();
    builder.addAttributeType(
        "( 1.2.2 OBSOLETE SINGLE-VALUE )", false);
  }

  /**
   * Check that the primary name is added to the set of names.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testConstructorPrimaryName() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.3");

    Assert.assertTrue(d.hasName("testType"));
    Assert.assertFalse(d.hasName("xxx"));

    d = schema.getAttributeType("1.2.4");

    Assert.assertTrue(d.hasName("testType"));
    Assert.assertFalse(d.hasName("xxx"));

  }



  /**
   * Check that the type names are accessible.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testConstructorTypeNames() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.5");

    Assert.assertTrue(d.hasName("testType"));
    Assert.assertTrue(d.hasName("testnamealias"));
    Assert.assertTrue(d.hasName("anothernamealias"));

    d = schema.getAttributeType("1.2.6");

    Assert.assertTrue(d.hasName("testType"));
    Assert.assertTrue(d.hasName("testnamealias"));
    Assert.assertTrue(d.hasName("anothernamealias1"));
  }

  /**
   * Check that the {@link CommonSchemaElements#getNameOrOID()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNameOrOIDReturnsOID() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.1");

    Assert.assertEquals(d.getNameOrOID(), "1.2.1");

    d = schema.getAttributeType("1.2.2");

    Assert.assertEquals(d.getNameOrOID(), "1.2.2");
  }



  /**
   * Check that the {@link CommonSchemaElements#getNameOrOID()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNameOrOIDReturnsPrimaryName()
      throws Exception {
    AttributeType d = schema.getAttributeType("1.2.3");
    Assert.assertEquals(d.getNameOrOID(), "testType");
    d = schema.getAttributeType("1.2.4");
    Assert.assertEquals(d.getNameOrOID(), "testType");
  }



  /**
   * Check that the {@link CommonSchemaElements#getNormalizedNames()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetNormalizedNames() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.5");
    Iterator<String> i = d.getNames().iterator();
    Assert.assertEquals(i.next(), "testType");
    Assert.assertEquals(i.next(), "testnamealias");
    Assert.assertEquals(i.next(), "anothernamealias");

    d = schema.getAttributeType("1.2.6");
    i = d.getNames().iterator();
    Assert.assertEquals(i.next(), "testType");
    Assert.assertEquals(i.next(), "testnamealias");
    Assert.assertEquals(i.next(), "anothernamealias1");
  }


  /**
   * Check that the {@link CommonSchemaElements#getOID()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testGetOID() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.3");
    Assert.assertEquals(d.getOID(), "1.2.3");
    d = schema.getAttributeType("1.2.4");
    Assert.assertEquals(d.getOID(), "1.2.4");

  }



  /**
   * Check that the {@link CommonSchemaElements#hasNameOrOID(String)}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testHasNameOrOID() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.3");

    Assert.assertTrue(d.hasNameOrOID("testType"));
    Assert.assertTrue(d.hasNameOrOID("1.2.3"));
    Assert.assertFalse(d.hasNameOrOID("x.y.z"));
    d = schema.getAttributeType("1.2.4");

    Assert.assertTrue(d.hasNameOrOID("testType"));
    Assert.assertTrue(d.hasNameOrOID("1.2.4"));
    Assert.assertFalse(d.hasNameOrOID("x.y.z"));
  }



  /**
   * Check that the {@link CommonSchemaElements#isObsolete()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public final void testIsObsolete() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.3");
    Assert.assertFalse(d.isObsolete());
    d = schema.getAttributeType("1.2.4");
    Assert.assertFalse(d.isObsolete());

    d = schema.getAttributeType("1.2.1");
    Assert.assertTrue(d.isObsolete());
    d = schema.getAttributeType("1.2.2");
    Assert.assertTrue(d.isObsolete());
  }

  /**
   * Check constructor sets the default usage correctly.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorDefaultUsage() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.2");

    Assert.assertEquals(d.getUsage(),
        AttributeUsage.USER_APPLICATIONS);
  }


  /**
   * Check constructor sets the syntax correctly.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorSyntax() throws Exception {
    AttributeType d = schema.getAttributeType("1.2.2");

    Assert.assertEquals(d.getSyntax().getOID(),
        "1.3.6.1.4.1.1466.115.121.1.15");
  }



  /**
   * Check constructor inherits the syntax from the parent type when
   * required.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testConstructorSyntax")
  public void testConstructorInheritsSyntax() throws Exception {
    AttributeType parent = schema.getAttributeType("1.2.3");


    AttributeType child = schema.getAttributeType("1.2.4");

    Assert.assertEquals(parent.getSyntax(), child.getSyntax());

    parent = schema.getAttributeType("1.2.2");


    child = schema.getAttributeType("1.2.3");
    Assert.assertFalse(parent.getSyntax().equals(child.getSyntax()));

    // Make sure paren't s syntax was not inherited in this case
    child = schema.getAttributeType("1.2.6");
    Assert.assertEquals(child.getSyntax().getOID(),
        SYNTAX_DIRECTORY_STRING_OID);
  }

  /**
   * Check constructor sets the default matching rules correctly.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorDefaultMatchingRules() throws Exception {
    AttributeType type = schema.getAttributeType("1.2.1");

    Syntax syntax = schema.getSyntax("1.3.6.1.4.1.1466.115.121.1.27");
    Assert.assertEquals(type.getApproximateMatchingRule(), syntax
        .getApproximateMatchingRule());
    Assert.assertEquals(type.getEqualityMatchingRule(), syntax
        .getEqualityMatchingRule());
    Assert.assertEquals(type.getOrderingMatchingRule(), syntax
        .getOrderingMatchingRule());
    Assert.assertEquals(type.getSubstringMatchingRule(), syntax
        .getSubstringMatchingRule());
  }



  /**
   * Check constructor sets the matching rules correctly.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorMatchingRules() throws Exception {
    AttributeType type = schema.getAttributeType("1.2.5");

    Assert.assertEquals(type.getEqualityMatchingRule().getOID(),
        EMR_CASE_IGNORE_LIST_OID);
    Assert.assertEquals(type.getOrderingMatchingRule().getOID(),
        type.getSyntax().getOrderingMatchingRule().getOID());
    Assert.assertEquals(type.getSubstringMatchingRule().getOID(),
        SMR_CASE_IGNORE_LIST_OID);
    Assert.assertEquals(type.getApproximateMatchingRule().getOID(),
        AMR_DOUBLE_METAPHONE_OID);
  }



  /**
   * Check constructor inherits the matching rules from the parent
   * type when required.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(dependsOnMethods = "testConstructorMatchingRules")
  public void testConstructorInheritsMatchingRules() throws Exception {
    AttributeType parent = schema.getAttributeType("1.2.5");


    AttributeType child = schema.getAttributeType("1.2.6");

    Assert.assertEquals(parent.getApproximateMatchingRule(), child
        .getApproximateMatchingRule());
    Assert.assertEquals(parent.getEqualityMatchingRule(), child
        .getEqualityMatchingRule());
    // It should inherit ordering rule from parent's syntax since parent
    // didn't specify an ordering matching rule.
    Assert.assertEquals(parent.getSyntax().getOrderingMatchingRule(), child
        .getOrderingMatchingRule());
    Assert.assertEquals(parent.getSubstringMatchingRule(), child
        .getSubstringMatchingRule());
  }





  /**
   * Check that the {@link AttributeType#isCollective()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsCollective() throws Exception {
    AttributeType type = schema.getAttributeType("1.2.2");
    Assert.assertTrue(type.isCollective());
    type = schema.getAttributeType("1.2.3");
    Assert.assertTrue(type.isCollective());
    type = schema.getAttributeType("1.2.6");
    Assert.assertFalse(type.isCollective());
    type = schema.getAttributeType("1.2.5");
    Assert.assertFalse(type.isCollective());
  }



  /**
   * Check that the {@link AttributeType#isNoUserModification()}
   * method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsNoUserModification() throws Exception {
    AttributeType type = schema.getAttributeType("1.2.5");
    Assert.assertTrue(type.isNoUserModification());
    type = schema.getAttributeType("1.2.6");
    Assert.assertTrue(type.isNoUserModification());
    type = schema.getAttributeType("1.2.3");
    Assert.assertFalse(type.isNoUserModification());
    type = schema.getAttributeType("1.2.4");
    Assert.assertFalse(type.isNoUserModification());
  }





  /**
   * Check that the {@link AttributeType#isSingleValue()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testIsSingleValue() throws Exception {
    AttributeType type = schema.getAttributeType("1.2.1");
    Assert.assertTrue(type.isSingleValue());
    type = schema.getAttributeType("1.2.2");
    Assert.assertTrue(type.isSingleValue());
    type = schema.getAttributeType("1.2.5");
    Assert.assertFalse(type.isSingleValue());
    type = schema.getAttributeType("1.2.6");
    Assert.assertFalse(type.isSingleValue());
  }

  /**
   * Check that the {@link AttributeType#getUsage()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetAttributeUsage() throws Exception {
    AttributeType type = schema.getAttributeType("1.2.1");
    Assert.assertEquals(type.getUsage(), AttributeUsage.USER_APPLICATIONS);
    type = schema.getAttributeType("1.2.6");
    Assert.assertEquals(type.getUsage(), AttributeUsage.DSA_OPERATION);
  }

  /**
   * Check that the {@link AttributeType#getSuperiorType()} method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetSuperiorType() throws Exception {
    AttributeType type = schema.getAttributeType("1.2.3");
    Assert.assertEquals(type.getSuperiorType().getOID(), "1.2.2");
    type = schema.getAttributeType("1.2.4");
    Assert.assertEquals(type.getSuperiorType().getOID(), "1.2.3");
  }

  @Test(expectedExceptions = SchemaException.class)
  public void testInheritFromNonCollective() throws Exception
  {
    // Collective can't inherit from non-collective
    SchemaBuilder builder = new SchemaBuilder(schema);
    builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' " +
        " OBSOLETE SUP 1.2.5 " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " COLLECTIVE USAGE userApplications )", false);
    builder.toSchema();
  }

  @Test(expectedExceptions = SchemaException.class)
  public void testCollectiveOperational() throws Exception
  {
    // Collective can't be operational
    SchemaBuilder builder = new SchemaBuilder(schema);
    builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " COLLECTIVE USAGE directoryOperation )", false);
    builder.toSchema();
  }

  @Test(expectedExceptions = SchemaException.class)
  public void testInheritFromUserAppUsage() throws Exception
  {
    // directoryOperation can't inherit from userApplications
    SchemaBuilder builder = new SchemaBuilder(schema);
    builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' " +
        " OBSOLETE SUP 1.2.1 " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " NO-USER-MODIFICATION USAGE directoryOperation )", false);
    builder.toSchema();
  }

  @Test(expectedExceptions = SchemaException.class)
  public void testNoUserModNonOperational() throws Exception
  {
    // NO-USER-MODIFICATION can't have non-operational usage
    SchemaBuilder builder = new SchemaBuilder(schema);
    builder.addAttributeType("(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " NO-USER-MODIFICATION USAGE userApplications )", false);
    builder.toSchema();
  }
}
