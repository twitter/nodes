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

import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import com.twitter.logging.Logger;
import com.twitter.util.Future;

/**
 * Applies a predicate on a given source sourceNode, producing a boolean output.
 *
 * @param <SourceType> Source sourceNode type.
 */
public class PredicateNode<SourceType> extends Node<Boolean> {

  private static final Logger LOG = Logger.get(PredicateNode.class);

  private final Node<SourceType> sourceNode;
  private final Predicate<SourceType> predicate;

  public PredicateNode(Node<SourceType> sourceNode,
                       Predicate<SourceType> predicate) {
    this(sourceNode, predicate, null);
  }

  public PredicateNode(Node<SourceType> sourceNode,
                       Predicate<SourceType> predicate,
                       @Nullable String name) {
    super(name != null ? name : String.format("Predicate[%s]", sourceNode.getName()),
        ImmutableList.<Node>of(sourceNode));
    this.sourceNode = sourceNode;
    this.predicate = Preconditions.checkNotNull(predicate);
  }

  @Override
  protected Future<Boolean> evaluate() throws Exception {
    SourceType sourceValue = sourceNode.emit();

    try {
      return Future.value(predicate.test(sourceValue));
    } catch (Exception e) {
      String msg = String.format("%s threw: sourceNode.emit() => %s", getName(), sourceValue);
      LOG.error(e, msg);
      return Future.exception(new RuntimeException(msg, e));
    }
  }

  public static <SourceType> PredicateNode<SourceType> create(Node<SourceType> node,
                                                              Predicate<SourceType> predicate) {
    return create(node, predicate, null);
  }

  public static <SourceType> PredicateNode<SourceType> create(Node<SourceType> node,
                                                              Predicate<SourceType> predicate,
                                                              @Nullable String name) {
    return new PredicateNode<>(node, predicate, name);
  }
}
