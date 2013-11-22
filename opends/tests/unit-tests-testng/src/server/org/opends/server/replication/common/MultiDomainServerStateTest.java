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
package org.opends.server.replication.common;

import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.types.DN;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class MultiDomainServerStateTest extends ReplicationTestCase
{

  private static final CSN csn1 = new CSN(1, 2, 3);
  private static final CSN csn2 = new CSN(4, 5, 6);
  private static final CSN csn3 = new CSN(7, 8, 3);

  @Test
  public void testDecodeAndEncode1() throws Exception
  {
    final String cookie = "o=test1:" + csn1 + ";o=test2:" + csn2 + ";o=test6:";
    final MultiDomainServerState state = new MultiDomainServerState(cookie);
    assertEquals(state.toString(), cookie + ";");
  }

  @Test
  public void testDecodeAndEncode2() throws Exception
  {
    final String cookie = "o=test1:" + csn1 + ";o=test2:" + csn2 + ";;o=test6:";
    final MultiDomainServerState state = new MultiDomainServerState(cookie);
    final String expected = ":;o=test1:" + csn1 + ";o=test2:" + csn2 + ";o=test6:;";
    assertEquals(state.toString(), expected);
  }

  @Test
  public void testUpdateCSN() throws Exception
  {
    final DN dn1 = DN.decode("o=test1");
    final DN dn2 = DN.decode("o=test2");

    final MultiDomainServerState state = new MultiDomainServerState();
    assertTrue(state.update(dn1, csn1));
    assertTrue(state.update(dn2, csn2));

    assertFalse(state.update(dn1, (CSN) null));
    assertFalse(state.update(dn1, csn1));
    assertTrue(state.update(dn1, csn3));
    final String expected = "o=test1:" + csn3 + ";o=test2:" + csn2 + ";";
    assertEquals(state.toString(), expected);
  }

  @Test
  public void testUpdateServerState() throws Exception
  {
    final DN dn1 = DN.decode("o=test1");
    final DN dn2 = DN.decode("o=test2");

    final MultiDomainServerState state = new MultiDomainServerState();
    final ServerState ss1 = new ServerState();
    assertTrue(ss1.update(csn3));
    final ServerState ss2 = new ServerState();
    assertTrue(ss2.update(csn2));
    state.update(dn1, ss1);
    state.update(dn2, ss2);

    final String expected = "o=test1:" + csn3 + ";o=test2:" + csn2 + ";";
    assertEquals(state.toString(), expected);
  }

  @Test
  public void testUpdateMultiDomainServerState() throws Exception
  {
    final DN dn1 = DN.decode("o=test1");
    final DN dn2 = DN.decode("o=test2");

    final MultiDomainServerState state1 = new MultiDomainServerState();
    state1.update(dn1, csn3);
    state1.update(dn2, csn2);
    final MultiDomainServerState state2 = new MultiDomainServerState();
    state2.update(state1);

    assertSame(csn3, state2.getCSN(dn1, csn3.getServerId()));
    assertSame(csn2, state2.getCSN(dn2, csn2.getServerId()));
    assertTrue(state1.equalsTo(state2));
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testEqualsTo() throws Exception
  {
    final DN dn1 = DN.decode("o=test1");
    final DN dn2 = DN.decode("o=test2");

    final MultiDomainServerState state1 = new MultiDomainServerState();
    assertTrue(state1.update(dn1, csn3));

    final MultiDomainServerState state2 = new MultiDomainServerState();
    assertFalse(state1.equalsTo(state2));

    assertTrue(state2.update(dn1, csn1));
    assertFalse(state1.equalsTo(state2));

    assertTrue(state2.update(dn1, csn3));
    assertTrue(state1.equalsTo(state2));

    assertTrue(state2.update(dn2, csn2));
    assertFalse(state1.equalsTo(state2));
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testIsEmpty() throws Exception
  {
    final DN dn1 = DN.decode("o=test1");
    final DN dn2 = DN.decode("o=test2");

    final MultiDomainServerState state = new MultiDomainServerState();
    assertTrue(state.isEmpty());

    assertTrue(state.update(dn1, csn1));
    assertFalse(state.isEmpty());
    assertThat(state).containsOnly(dn1);

    assertTrue(state.update(dn2, csn2));
    assertFalse(state.isEmpty());
    assertThat(state).containsOnly(dn1, dn2);

    state.clear();
    assertTrue(state.isEmpty());
  }

  @Test(dependsOnMethods = { "testUpdateCSN" })
  public void testRemoveCSN() throws Exception
  {
    final DN dn1 = DN.decode("o=test1");
    final DN dn2 = DN.decode("o=test2");
    final DN dn3 = DN.decode("o=test3");

    final MultiDomainServerState state = new MultiDomainServerState();

    assertTrue(state.update(dn1, csn1));
    assertTrue(state.update(dn2, csn1));
    assertTrue(state.update(dn2, csn2));
    assertNull(state.getCSN(dn3, 42));

    assertFalse(state.removeCSN(dn3, csn1));
    assertSame(csn1, state.getCSN(dn1, csn1.getServerId()));
    assertSame(csn1, state.getCSN(dn2, csn1.getServerId()));
    assertSame(csn2, state.getCSN(dn2, csn2.getServerId()));

    assertFalse(state.removeCSN(dn1, csn2));
    assertSame(csn1, state.getCSN(dn1, csn1.getServerId()));
    assertSame(csn1, state.getCSN(dn2, csn1.getServerId()));
    assertSame(csn2, state.getCSN(dn2, csn2.getServerId()));

    assertTrue(state.removeCSN(dn2, csn1));
    assertSame(csn1, state.getCSN(dn1, csn1.getServerId()));
    assertNull(state.getCSN(dn2, csn1.getServerId()));
    assertSame(csn2, state.getCSN(dn2, csn2.getServerId()));
  }
}
