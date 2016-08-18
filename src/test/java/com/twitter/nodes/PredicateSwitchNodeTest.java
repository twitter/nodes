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

import org.junit.Before;
import org.junit.Test;

import com.twitter.nodes.utils.Futures;
import com.twitter.util.Await;
import com.twitter.util.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PredicateSwitchNodeTest extends NodeTestBase {

  private static final Integer TRUE_NODE_VALUE = 2;
  private static final Integer FALSE_NODE_VALUE = 3;

  private Node<Integer> trueNode;
  private Node<Integer> falseNode;

  @Before
  public void setUp() throws Exception {
    trueNode = new Node<Integer>() {
      @Override
      protected Future<Integer> evaluate() throws Exception {
        return Future.value(TRUE_NODE_VALUE);
      }
    };
    falseNode = new Node<Integer>() {
      @Override
      protected Future<Integer> evaluate() throws Exception {
        return Future.value(FALSE_NODE_VALUE);
      }
    };
  }

  @Test
  public void testTrueSwitch() throws Exception {
    Node<Integer> node = Node.ifThenElse(Node.value(true), trueNode, falseNode);
    assertEquals(TRUE_NODE_VALUE, resultFromNode(node));
  }

  @Test
  public void testFalseSwitch() throws Exception {
    Node<Integer> node = Node.ifThenElse(Node.value(false), trueNode, falseNode);
    assertEquals(FALSE_NODE_VALUE, resultFromNode(node));
  }

  @Test
  public void testOneWaySwitch() throws Exception {
    Node<Integer> node = Node.ifThen(Node.value(false), trueNode);
    assertEquals(null, resultFromNode(node));
  }

  @Test
  public void testNullPredicate() throws Exception {
    Node<Integer> node = Node.ifThenElse(Node.<Boolean>value(null), trueNode, falseNode);

    Future<Integer> future = node.apply();
    assertTrue(Await.result(future.liftToTry()).isThrow());
    Throwable e = Futures.getException(future);
    assertTrue(e instanceof RuntimeException);
  }

  @Test
  public void testPredicateException() throws Exception {
    Node<Boolean> predicateNode = new Node<Boolean>() {
      @Override
      protected Future<Boolean> evaluate() throws Exception {
        throw new IllegalStateException("always throws");
      }
    };
    Node<Integer> node = Node.ifThenElse(predicateNode, trueNode, falseNode);

    Future<Integer> future = node.apply();
    assertTrue(Await.result(future.liftToTry()).isThrow());
    Throwable e = Futures.getException(future);
    assertTrue(e instanceof IllegalStateException);
  }
}
