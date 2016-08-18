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

import java.util.function.Function;

import org.junit.Test;

import com.twitter.nodes.utils.Futures;
import com.twitter.util.Await;
import com.twitter.util.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TransformNodeTest extends NodeTestBase {

  private static final Long TWO_VALUE = 2L;

  public static final Function<Long, Long> PLUS_FN =
      new Function<Long, Long>() {
        @Override
        public Long apply(Long term) {
          return term + TWO_VALUE;
        }
      };

  public static final Function<Long, Long> NULL_FN =
      new Function<Long, Long>() {
        @Override
        public Long apply(Long term) {
          return null;
        }
      };

  public static final Function<Long, Long> THROW_FN =
      new Function<Long, Long>() {
        @Override
        public Long apply(Long term) {
          throw new NullPointerException("here ya go!");
        }
      };

  @Test
  public void testTransformNode() throws Exception {
    Long term = 3L;
    Node<Long> termNode = Node.<Long>value(term);
    Node<Long> resultNode = TransformNode.create(termNode, PLUS_FN);
    Long result = resultFromNode(resultNode);
    assertEquals(result, Long.valueOf(term + TWO_VALUE));
  }

  @Test
  public void testTransformNodeNull() throws Exception {
    String name = "this is a transform node";
    Long term = 3L;
    Node<Long> termNode = Node.<Long>value(term);
    Node<Long> resultNode = TransformNode.create(termNode, NULL_FN, name);

    // null result no longer throws in transformer
    assertNull(resultFromNode(resultNode));
  }

  @Test
  public void testTransformNodeException() throws Exception {
    String name = "this is a transform node";
    Long term = 3L;
    Node<Long> termNode = Node.<Long>value(term);
    Node<Long> resultNode = TransformNode.create(termNode, THROW_FN, name);
    Future<Long> graph = resultNode.apply();

    assertTrue(Await.result(graph.liftToTry()).isThrow());
    Throwable e = Futures.getException(graph);
    assertTrue(e instanceof RuntimeException);
    assertTrue(e.getCause() instanceof NullPointerException);
  }
}
