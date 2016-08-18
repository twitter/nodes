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

import com.twitter.nodes.utils.Futures;
import com.twitter.util.Await;
import com.twitter.util.Future;

import static org.junit.Assert.assertTrue;

public class PredicateNodeTest extends NodeTestBase {

  @Test
  public void testPredicate() throws Exception {
    Long value = 3L;
    Node<Boolean> resultNode = PredicateNode.create(Node.value(value), input -> input == 3L);
    assertTrue(resultFromNode(resultNode));
  }

  @Test
  public void testNullInput() throws Exception {
    Node<Boolean> resultNode = PredicateNode.create(Node.<Long>value(null), input -> input == 3L);

    Future<Boolean> future = resultNode.apply();
    assertTrue(Await.result(future.liftToTry()).isThrow());
    Throwable e = Futures.getException(future);
    assertTrue(e instanceof RuntimeException);
  }

  @Test
  public void testPredicateException() throws Exception {
    Node<Boolean> resultNode = PredicateNode.create(Node.<Long>value(3L), input -> {
        throw new IllegalStateException("always throws");
      });

    Future<Boolean> future = resultNode.apply();
    assertTrue(Await.result(future.liftToTry()).isThrow());
    Throwable e = Futures.getException(future);
    assertTrue(e instanceof RuntimeException);
    assertTrue(e.getCause() instanceof IllegalStateException);
  }
}
