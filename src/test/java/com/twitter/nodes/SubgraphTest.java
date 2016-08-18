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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.twitter.nodes.utils.DebugLevel;
import com.twitter.nodes.utils.DebugManager;

public class SubgraphTest extends NodeTestBase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    DebugManager.resetForTest(DebugLevel.DEBUG_BASIC);
  }

  // Normal subgraph
  static class SimpleGraph extends Subgraph {
    public final Node<Long> longNode;

    SimpleGraph() {
      this.longNode = Node.value(10L);
      markExposedNodes();
    }
  }

  // Subgraph without public field, this will throw exception
  static class BadGraph extends Subgraph {
    private Node<Long> privateLongNode;  // this will fail

    BadGraph() {
      this.privateLongNode = Node.value(10L);
      markExposedNodes();
    }
  }

  @Test
  public void testSimple() throws Exception {
    SimpleGraph subgraph = new SimpleGraph();
    Assert.assertEquals(subgraph, subgraph.longNode.getEnclosingSubgraph());
  }

  @Test(expected = RuntimeException.class)
  public void testNoPublicField() throws Exception {
    new BadGraph();
  }
}
