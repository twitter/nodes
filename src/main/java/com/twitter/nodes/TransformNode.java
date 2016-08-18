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
 * TransformNode applies a function on a node value, producing a new value.
 * <p/>
 * A typical transform is extracting a simple value from a more complex data structure.
 * <p/>
 * This node can return null since the transform function can return null.
 * <p/>
 *
 * See {link:NodeTransforms} for commonly used transforms.
 *
 * @param <SourceType> Source node type.
 * @param <Resp> Resulting node value type.
 */
public class TransformNode<SourceType, Resp> extends BaseTransformNode<SourceType, Resp> {

  private final Function<SourceType, Resp> transform;

  protected TransformNode(Node<SourceType> node,
                          Function<SourceType, Resp> transform,
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
    Resp resp = transform.apply(source);
    if (resp == null && !canEmitNull()) {
      TransformNodeNullException exception =
          new TransformNodeNullException(this, node, source);
      return Future.exception(exception);
    }

    return Future.value(resp);
  }

  /**
   * Create a new TransformNode with decider key.
   * NOTE: try not to use this directly, use Node.map() instead.
   */
  public static <SourceType, Resp> TransformNode<SourceType, Resp> create(
      Node<SourceType> node,
      Function<SourceType, Resp> transform,
      String name,
      DeciderSupplier deciderSupplier) {
    return new TransformNode<>(node, transform, name, deciderSupplier);
  }

  /**
   * Create a new TransformNode with no decider key and no name.
   * NOTE: try not to use this directly, use Node.map() instead.
   */
  public static <SourceType, Resp> TransformNode<SourceType, Resp> create(
      Node<SourceType> node,
      Function<SourceType, Resp> transform) {
    return create(node, transform, null, null);
  }

  /**
   * Create a new TransformNode. with name but no decider key.
   * NOTE: try not to use this directly, use Node.map() instead.
   */
  public static <SourceType, Resp> TransformNode<SourceType, Resp> create(
      Node<SourceType> node,
      Function<SourceType, Resp> transform,
      String name) {
    return new TransformNode<>(node, transform, name, null);
  }
}
