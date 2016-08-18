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

import com.google.common.base.Optional;

import com.twitter.util.Future;

/**
 * A simple wrapper node converting success state of a node to a boolean.
 */
public class IfSuccessfulNode extends Node<Boolean> {
  private final Node node;

  IfSuccessfulNode(Node node) {
    super(String.format("SUCCESS::%s", node.getName()), node);
    this.node = node;
  }

  @Override
  protected Future<Boolean> evaluate() throws Exception {
    Optional optional = (Optional) node.emit();
    return Future.value(optional.isPresent());
  }

  /**
   * Create an IfSuccessNode based any give node.
   */
  public static <T> IfSuccessfulNode create(Node<T> node) {
    return new IfSuccessfulNode(Node.optional(node));
  }
}
