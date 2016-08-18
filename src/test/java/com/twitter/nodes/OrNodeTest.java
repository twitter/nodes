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

import com.twitter.util.Future;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrNodeTest extends NodeTestBase {

  @Test
  public void testLeftTrueNode() throws Exception {
    Node<Boolean> a = Node.TRUE;
    Node<Boolean> b = Node.FALSE;
    assertTrue(resultFromNode(OrNode.create(a, b)));
    assertTrue(resultFromNode(OrNode.createLazy(a, b)));
  }

  @Test
  public void testRightTrueNode() throws Exception {
    Node<Boolean> a = Node.FALSE;
    Node<Boolean> b = Node.TRUE;
    assertTrue(resultFromNode(OrNode.create(a, b)));
    assertTrue(resultFromNode(OrNode.createLazy(a, b)));
  }

  @Test
  public void testLeftAndRightTrueNode() throws Exception {
    Node<Boolean> a = Node.TRUE;
    Node<Boolean> b = Node.TRUE;
    assertTrue(resultFromNode(OrNode.create(a, b)));
    assertTrue(resultFromNode(OrNode.createLazy(a, b)));
  }

  @Test
  public void testLeftAndRightFalseNode() throws Exception {
    Node<Boolean> a = Node.FALSE;
    Node<Boolean> b = Node.FALSE;
    assertFalse(resultFromNode(OrNode.create(a, b)));
    assertFalse(resultFromNode(OrNode.createLazy(a, b)));
  }

  @Test
  public void testMultiNode() throws Exception {
    Node<Boolean> a = Node.FALSE;
    Node<Boolean> b = Node.FALSE;
    Node<Boolean> c = Node.TRUE;
    assertTrue(resultFromNode(OrNode.create(a, b, c)));
    assertTrue(resultFromNode(OrNode.createLazy(a, b, c)));
  }

  @Test
  public void testEagerEvaluate() throws Exception {
    final Boolean[] evaluatedA = new Boolean[1];
    final Boolean[] evaluatedB = new Boolean[1];
    evaluatedA[0] = new Boolean(false);
    evaluatedB[0] = new Boolean(false);

    Node<Boolean> a = new Node<Boolean>() {
      @Override
      protected Future<Boolean> evaluate() throws Exception {
        evaluatedA[0] = true;
        return Future.value(false);
      }
    };
    Node<Boolean> b = new Node<Boolean>() {
      @Override
      protected Future<Boolean> evaluate() throws Exception {
        evaluatedB[0] = true;
        return Future.value(false);
      }
    };
    Node<Boolean> node = OrNode.create(a, b);

    assertFalse(resultFromNode(node));
    assertTrue(evaluatedA[0]);
    assertTrue(evaluatedB[0]);
  }

  @Test
  public void testLazyEvaluate() throws Exception {
    final Boolean[] evaluatedA = new Boolean[1];
    final Boolean[] evaluatedB = new Boolean[1];
    evaluatedA[0] = new Boolean(false);
    evaluatedB[0] = new Boolean(false);

    Node<Boolean> a = new Node<Boolean>() {
      @Override
      protected Future<Boolean> evaluate() throws Exception {
        evaluatedA[0] = true;
        return Future.value(true);
      }
    };
    Node<Boolean> b = new Node<Boolean>() {
      @Override
      protected Future<Boolean> evaluate() throws Exception {
        evaluatedB[0] = true;
        return Future.value(false);
      }
    };
    Node<Boolean> node = OrNode.createLazy(a, b);

    assertTrue(resultFromNode(node));
    assertTrue(evaluatedA[0]);
    assertFalse(evaluatedB[0]);
  }
}
