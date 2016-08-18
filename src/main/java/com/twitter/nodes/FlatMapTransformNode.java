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

import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

import com.twitter.nodes.utils.DeciderSupplier;
import com.twitter.util.Future;

/**
 * Transforms a node using a function that returns a future of a new value. See Node.flatMap.
 */
public class FlatMapTransformNode<SourceType, Resp> extends BaseTransformNode<SourceType, Resp> {

  private final Function<SourceType, Future<Resp>> transform;

  protected FlatMapTransformNode(Node<SourceType> node,
                                 Function<SourceType, Future<Resp>> transform,
                                 @Nullable String name,
                                 @Nullable DeciderSupplier deciderSupplier) {
    super(node, name, deciderSupplier);
    this.transform = Preconditions.checkNotNull(transform);
    setAllowNull();
  }

  @Override
  public String getResponseClassName() {
    return getLastTemplateType(this.transform.getClass());
  }

  @Override
  protected Future<Resp> transform(SourceType source) {
    return transform.apply(source);
  }

  /**
   * Create a new FlatMapTransformNode with decider key.
   * NOTE: try not to use this directly, use Node.flatMap() instead.
   */
  static <SourceType, Resp> FlatMapTransformNode<SourceType, Resp> create(
      Node<SourceType> node,
      Function<SourceType, Future<Resp>> transform,
      String name,
      DeciderSupplier deciderSupplier) {
    return new FlatMapTransformNode<>(node, transform, name, deciderSupplier);
  }
}
