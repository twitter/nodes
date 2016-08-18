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

import java.util.Collection;

import com.twitter.util.Future;

/**
 * A node that allows null value output
 *
 * NOTE: right now this has a few constructors, add more here to match the parent Node class
 * if needed.
 */
public abstract class NullableNode<T> extends Node<T> {
  protected NullableNode(String name,
                         Collection<Node> dependentNodes) {
    super(name, dependentNodes);
    this.setAllowNull();
  }

  protected NullableNode(String name, Node... nodes) {
    super(name, nodes);
    this.setAllowNull();
  }

  protected NullableNode(Node... nodes) {
    super(nodes);
    this.setAllowNull();
  }

  protected NullableNode() {
    super();
    this.setAllowNull();
  }

  /**
   * Wrap a Future object into a nullable node.
   */
  public static <T> Node wrapFuture(final Future<T> future) {
    return new NullableNode<T>() {
      @Override
      protected Future<T> evaluate() throws Exception {
        return future;
      }
    };
  }
}
