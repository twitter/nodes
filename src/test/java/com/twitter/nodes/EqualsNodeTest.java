/**
 * Copyright 2016 Twitter, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.nodes;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EqualsNodeTest extends NodeTestBase {
  @Test
  public void testEvaluateEquals() throws Exception {
    Object someGuy = new Object();
    Node<Object> firstNode = Node.value(someGuy);
    Node<Object> secondNode = Node.value(someGuy);

    Node<Boolean> equalsNode = EqualsNode.create(firstNode, secondNode);

    assertTrue(resultFromNode(equalsNode));
  }

  @Test
  public void testEvaluateNotEquals() throws Exception {
    Object someGuy = new Object();
    Object someOtherGuy = new Object();
    Node<Object> firstNode = Node.value(someGuy);
    Node<Object> secondNode = Node.value(someOtherGuy);

    Node<Boolean> equalsNode = EqualsNode.create(firstNode, secondNode);

    assertFalse(resultFromNode(equalsNode));
  }
}
