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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.twitter.util.Future;

/**
 * Predicate switch nodes make a choice based on a predicate and subsequently
 * represents the output of the chosen node.
 * <p/>
 * If the predicate is true, then the node set with ifTrue is used,
 * otherwise the node set with ifFalse is used.
 * <p/>
 * The chosen node is lazily executed, so work is not wasted on the unused node.
 */
public class PredicateSwitchNode<Resp> extends Node<Resp> {

  private final Node<Boolean> predicateNode;
  private final Node<Resp> trueNode;
  private final Node<Resp> falseNode;

  public PredicateSwitchNode(
      Node<Boolean> predicateNode,
      Node<Resp> trueNode,
      Node<Resp> falseNode
  ) {
    super(String.format(
        "IF::%s(%s, %s)", predicateNode.getName(), trueNode.getName(), falseNode.getName()),
        ImmutableList.<Node>of(predicateNode));
    this.predicateNode = predicateNode;
    this.trueNode = Preconditions.checkNotNull(trueNode);
    this.falseNode = Preconditions.checkNotNull(falseNode);
    if (this.trueNode.canEmitNull() || this.falseNode.canEmitNull()) {
      this.setAllowNull();
    }
  }

  @Override
  protected void logEnd() {
    super.logEnd();
    debugDetailed("predicate value from [%s] = %s", predicateNode.getName(), predicateNode.emit());
  }

  @Override
  public String getResponseClassName() {
    return this.trueNode.getResponseClassName();
  }

  @Override
  protected final Future<Resp> evaluate() throws Exception {
    return predicateNode.emit()
        ? trueNode.apply()
        : falseNode.apply();
  }

  @Override
  ImmutableMap<String, Node> getInputsByName() {
    return ImmutableMap.<String, Node>of(
        "condition", predicateNode,
        "TRUE", trueNode,
        "FALSE", falseNode);
  }

}
