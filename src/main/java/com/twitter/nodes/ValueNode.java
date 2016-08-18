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

import com.google.common.collect.ImmutableList;

import com.twitter.util.Future;

/**
 * ValueNode represents a node with a fixed value.
 */
public class ValueNode<Resp> extends NullableNode<Resp> {
  private final Resp value;

  /**
   * Creates a node with a fixed value.
   */
  /**
   * Creates a node with a fixed value.
   */
  public ValueNode(Resp value, String name) {
    super(name != null
            ? name
            : String.format("value[%s]", valueStringInName(value)),
        ImmutableList.<Node>of());
    this.value = value;
  }

  private static <Resp> String valueStringInName(Resp value) {
    if (value instanceof Boolean
        || value instanceof Number
        || value instanceof Enum) {
      return String.valueOf(value);
    }
    return value == null ? "null" : value.getClass().getSimpleName();
  }

  @Override
  protected Future<Resp> evaluate() {
    return Future.value(value);
  }

  @Override
  public String getResponseClassName() {
    return value == null ? "" : value.getClass().getSimpleName();
  }

  @Override
  public Resp emit() {
    return value;
  }

  static <T> ValueNode<T> create(T value) {
    return new ValueNode<>(value, null);
  }

  static <T> ValueNode<T> create(T value, String name) {
    return new ValueNode<>(value, name);
  }

  @Override
  protected void logStart() {
    // print nothing as value node is too simple
  }

  @Override
  protected void logEnd() {
    // print nothing as value node is too simple
  }
}
