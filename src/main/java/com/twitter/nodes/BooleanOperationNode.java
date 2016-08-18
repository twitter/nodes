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

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.twitter.util.Future;

/**
 * Boolean operation node
 */
public abstract class BooleanOperationNode extends Node<Boolean> {
  protected final List<Node<Boolean>> operands;
  protected final boolean lazy;

  protected BooleanOperationNode(String name, List<Node<Boolean>> operandNodes, boolean lazy) {
    super(name + "::(" + mergeName(operandNodes) + ")",
        lazy ? getFirstNodeAsList(operandNodes) : (List<Node>) (List) operandNodes);
    operands = ImmutableList.copyOf(operandNodes);
    this.lazy = lazy;
  }

  private static String mergeName(List<Node<Boolean>> operands) {
    return operands.stream()
        .map(node -> node == null ? "null" : node.getName())
        .collect(Collectors.joining(", "));
  }

  private static List<Node> getFirstNodeAsList(List<Node<Boolean>> nodes) {
    return ImmutableList.<Node>of(nodes.get(0));
  }

  public List<Node<Boolean>> getOperands() {
    return operands;
  }

  @Override
  ImmutableMap<String, Node> getInputsByName() {
    ImmutableMap.Builder<String, Node> builder = ImmutableMap.builder();
    int index = 0;
    for (Node<Boolean> operands : getOperands()) {
      builder.put("OP" + index, operands);
      ++index;
    }
    return builder.build();
  }

  public boolean isLazy() {
    return lazy;
  }

  @Override
  Future futureFromDependencies() {
    if (!lazy) {
      // kick-off all the dependent nodes so they execute async.
      for (Node<Boolean> operand : operands) {
        operand.apply();
      }
    }

    // Note: calling apply on a Node is idempotent and will always give you back the same Future.
    return operands.get(0).apply();
  }

  @Override
  protected Future<Boolean> evaluate() {
    return evaluate(operands);
  }

  protected abstract Future<Boolean> evaluate(final List<Node<Boolean>> operands);
}
