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

import com.twitter.nodes.utils.DebugManager;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import com.twitter.util.Future;

import static org.junit.Assert.assertTrue;

public abstract class NodeTestBase {
  private static final Duration DEFAULT_WAIT_DURATION = Duration.fromSeconds(30);

  @Before
  public void setUp() throws Exception {
    DebugManager.resetForTest();
  }

  /**
   * Get result from future
   */
  public static <T> T resultFromFuture(Future<T> future) throws Exception {
    T result;
    try {
      result = Await.result(future, DEFAULT_WAIT_DURATION);
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
    return result;
  }

  /**
   * Get result from a node, if the node fails or returns null, an exception will be thrown
   */
  public static <T> T resultFromNode(Node<T> node) throws Exception {
    return resultFromFuture(node.apply());
  }

  public static <T> void assertNodeThrow(Node<T> node) throws Exception {
    Future<T> r = node.apply();
    Await.ready(r, DEFAULT_WAIT_DURATION);
    boolean isThrow = Await.result(r.liftToTry()).isThrow();
    assertTrue("expecting a throw but get: " + r, isThrow);
  }
}
