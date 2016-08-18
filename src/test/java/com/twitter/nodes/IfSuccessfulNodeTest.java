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

public class IfSuccessfulNodeTest extends NodeTestBase {

  @Test
  public void testSuccess() throws Exception {
    Node<Boolean> node = IfSuccessfulNode.create(Node.value(100));
    assertTrue(resultFromNode(node));
  }

  @Test
  public void testNull() throws Exception {
    Node<Boolean> node = IfSuccessfulNode.create(Node.noValue());
    assertFalse(resultFromNode(node));
  }

  @Test
  public void testFailure() throws Exception {
    Node<Boolean> node = IfSuccessfulNode.create(new Node<String>() {
      @Override
      protected Future<String> evaluate() throws Exception {
        throw new Exception("bad!");
      }
    });
    assertFalse(resultFromNode(node));
  }
}
