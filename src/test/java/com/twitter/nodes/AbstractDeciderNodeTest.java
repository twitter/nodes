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

public class AbstractDeciderNodeTest extends NodeTestBase {

  private class TestDeciderNode extends AbstractDeciderNode {
    private Boolean value;

    public TestDeciderNode(Boolean value) {
      super("TestDecider", "feature");
      this.value = value;
    }

    @Override
    protected boolean isAvailable(String featureName) {
      return value;
    }
  }

  @Test
  public void testNodeTrue() throws Exception {
    TestDeciderNode node = new TestDeciderNode(true);
    assertTrue(resultFromNode(node));
  }

  @Test
  public void testNodeFalse() throws Exception {
    TestDeciderNode node = new TestDeciderNode(false);
    assertFalse(resultFromNode(node));
  }
}
