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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import com.twitter.util.Future;

/**
 * AndNode represents a conjunction of a list of boolean nodes.
 * <p>
 * The parallelism model chosen (eager or lazy) determines how the dependencies execute.
 */
public final class AndNode extends BooleanOperationNode {

  private AndNode(List<Node<Boolean>> conjunctionNodes, boolean lazy) {
    this("AND", conjunctionNodes, lazy);
  }

  private AndNode(String name, List<Node<Boolean>> conjunctionNodes, boolean lazy) {
    super(name, conjunctionNodes, lazy);
  }

  @Override
  protected Future<Boolean> evaluate() {
    return evaluate(operands);
  }

  /**
   * Evaluate the operands left to right executing according to the parallelism mode specified.
   * <p>
   * Call apply on each operand in sequence and evaluate the boolean condition.
   * If the evaluation should be continued, then apply() is called on the next operand by
   * recursively
   * calling evaluate on the rest of the operands.  If the evaluation hasn't prematurely terminated,
   * then we arrive at the last operand, the response is the final state of the evaluation.
   * <p>
   * Note, calling apply on a Node is idempotent and will always give you back the same Future.
   * <p>
   * For lazy evaluation, each apply may kick off the task sequentially, causing serial execution
   * of the operands.
   * <p>
   * For eager evaluation, all of the nodes have been kicked-off already, so we are effectively
   * evaluating them left-to-right as they complete.
   */
  @Override
  protected Future<Boolean> evaluate(final List<Node<Boolean>> operands) {
    if (operands.size() == 1) {
      return operands.get(0).apply();
    }

    return operands.get(0).apply().flatMap(
        new com.twitter.util.Function<Boolean, Future<Boolean>>() {
          @Override
          public Future<Boolean> apply(Boolean value) {
            return value
                ? evaluate(operands.subList(1, operands.size()))
                : FALSE_FUTURE;
          }
        });  }

  /**
   * Creates an eagerly evaluated conjunction where after all dependencies complete successfully,
   * the conjunction is evaluated left to right.
   */
  public static final AndNode create(Node<Boolean>... conjunctionNodes) {
    return create("AND", conjunctionNodes);
  }

  public static final AndNode create(String name, Node<Boolean>... conjunctionNodes) {
    Preconditions.checkState(conjunctionNodes.length >= 2);
    return new AndNode(name, ImmutableList.copyOf(conjunctionNodes), false);
  }

  /**
   * Creates a lazily evaluated conjunction where lazily implies that both the async and boolean
   * evaluation of node dependencies occurs sequentially, left to right.
   */
  public static final AndNode createLazy(Node<Boolean>... conjunctionNodes) {
    return createLazy("AND-lazy", conjunctionNodes);
  }

  public static final AndNode createLazy(String name, Node<Boolean>... conjunctionNodes) {
    Preconditions.checkState(conjunctionNodes.length >= 2);
    return new AndNode(name, ImmutableList.copyOf(conjunctionNodes), true);
  }
}
