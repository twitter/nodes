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

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

import com.twitter.util.Future;

/**
 * A node that runs one or two nodes, and selectively returns the result based on the condition.
 * Different from PredicateSwitchNode, this ALWAYS calls apply to both true and false nodes (false
 * nodes could be null though), and the condition only controls which result to return.
 */
public final class LightDarkSwitchNode<T> extends Node<T> {
  private Node<Boolean> shouldDarkReadNode;
  private Node<T> darkNode;
  private Node<T> lightNode;

  private LightDarkSwitchNode(
      Node<Boolean> shouldDarkReadNode,
      @Nullable Node<T> darkNode,
      @Nullable Node<T> lightNode) {
    super(shouldDarkReadNode);  // similar to predicate switch node, only depends on the condition
    this.shouldDarkReadNode = shouldDarkReadNode;
    this.darkNode = darkNode;
    this.lightNode = lightNode;
    if ((darkNode != null && darkNode.canEmitNull())
        || (lightNode != null && lightNode.canEmitNull())) {
      setAllowNull();
    }
  }

  @Override
  protected Future<T> evaluate() throws Exception {
    boolean shouldDarkRead = shouldDarkReadNode.emit();
    Future<T> darkResultFuture = darkNode != null
        ? darkNode.apply() : Future.<T>value(null);
    Future<T> lightResultFuture = lightNode != null
        ? lightNode.apply() : Future.<T>value(null);
    return shouldDarkRead ? darkResultFuture : lightResultFuture;
  }

  @Override
  ImmutableMap<String, Node> getInputsByName() {
    ImmutableMap.Builder<String, Node> builder = ImmutableMap.builder();
    builder.put("condition", shouldDarkReadNode);
    if (darkNode != null) {
      builder.put("TRUE", darkNode);
    }
    if (lightNode != null) {
      builder.put("FALSE", lightNode);
    }
    return builder.build();
  }

  @Override
  public String getResponseClassName() {
    return darkNode != null
        ? darkNode.getResponseClassName()
        : (lightNode != null
           ? lightNode.getResponseClassName()
           : "");
  }

  /**
   * Use a condition to darkread a response node, which is always applied.
   * If the condition is true, null will be returned, if false, the result of responseNode will
   * be returned.
   */
  public static <T> Node<T> create(
      Node<Boolean> shouldDarkReadNode, Node<T> responseNode) {
    return new LightDarkSwitchNode<>(shouldDarkReadNode, null, responseNode);
  }

  /**
   * Use a condition to choose between two nodes (always both applied),
   * if the condition is true, return the result of first node, otherwise the second.
   */
  public static <T> Node<T> create(
      Node<Boolean> conditionsNode, Node<T> trueNode, Node<T> falseNode) {
    return new LightDarkSwitchNode<>(conditionsNode, trueNode, falseNode);
  }
}
