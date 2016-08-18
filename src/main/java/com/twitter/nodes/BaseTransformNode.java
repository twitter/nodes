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

import com.google.common.collect.ImmutableList;

import com.twitter.logging.Logger;
import com.twitter.nodes.utils.DeciderSupplier;
import com.twitter.util.Future;

/**
 * Base class for nodes that transforms a node value, producing a new one.
 *
 * @param <SourceType> Source node type.
 * @param <Resp> Resulting node value type.
 */
public abstract class BaseTransformNode<SourceType, Resp> extends Node<Resp> {

  private static final Logger LOG = Logger.get(BaseTransformNode.class);

  protected final Node<SourceType> node;

  protected BaseTransformNode(Node<SourceType> node,
                              @Nullable String name,
                              @Nullable DeciderSupplier deciderSupplier) {
    super(name != null ? name : String.format("Transform[%s]", node.getName()),
        ImmutableList.<Node>of(node));
    this.node = node;
    setDeciderSupplier(deciderSupplier);
  }

  @Override
  public abstract String getResponseClassName();

  @Override
  protected Future<Resp> evaluate() throws Exception {
    SourceType source = node.emit();

    Future<Resp> resp;

    try {
      resp = transform(source);
    } catch (Exception e) {
      String msg = String.format(
          "TransformNode [%s] on [%s] threw an exception while transforming (%s): %s",
          getName(), node.getName(), e, String.valueOf(source));
      LOG.error(e, msg);
      return Future.exception(new RuntimeException(msg, e));
    }

    return resp;
  }

  protected abstract Future<Resp> transform(SourceType source);
}
