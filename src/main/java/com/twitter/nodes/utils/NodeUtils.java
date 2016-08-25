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

package com.twitter.nodes.utils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import scala.Tuple3;
import scala.Tuple4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import com.twitter.nodes.IfSuccessfulNode;
import com.twitter.nodes.NamedFunction;
import com.twitter.nodes.Node;
import com.twitter.nodes.PredicateSwitchNode;
import com.twitter.nodes.TransformNode;
import com.twitter.util.Future;

/**
 * NodeTransforms are convenience functions for use in {@link TransformNode}s.
 * A typical transform is extracting a simpler value from a more complex data structure.
 */
public final class NodeUtils {
  private NodeUtils() { }

  public static <T> NamedFunction<T, List<T>> asList() {
    return NamedFunction.create("asList", ImmutableList::of);
  }

  public static <T> TransformNode<T, List<T>> asList(Node<T> node) {
    return TransformNode.create(node, NodeUtils.<T>asList());
  }

  private static class ToNodePair<S, T> extends Node<Pair<S, T>> {
    private final Node<S> node1;
    private final Node<T> node2;

    private ToNodePair(Node<S> node1, Node<T> node2) {
      super(ImmutableList.<Node>of(node1, node2));
      this.node1 = node1;
      this.node2 = node2;
    }

    @Override
    protected Future<Pair<S, T>> evaluate() throws Exception {
      return Future.value(Pair.of(node1.emit(), node2.emit()));
    }
  }

  public static <S, T> Node<Pair<S, T>> asPair(final Node<S> node1, final Node<T> node2) {
    return new ToNodePair<>(node1, node2);
  }

  public static <S, T> Pair<Node<S>, Node<T>> splitPair(Node<Pair<S, T>> pairNode) {
    return Pair.of(
        pairNode.mapOnSuccess("getFirst", Pair::getFirst),
        pairNode.mapOnSuccess("getSecond", Pair::getSecond));
  }

  private static class ToNodeTuple3<T1, T2, T3> extends Node<Tuple3<T1, T2, T3>> {
    private final Node<T1> node1;
    private final Node<T2> node2;
    private final Node<T3> node3;

    private ToNodeTuple3(Node<T1> node1, Node<T2> node2, Node<T3> node3) {
      super(ImmutableList.<Node>of(node1, node2, node3));
      this.node1 = node1;
      this.node2 = node2;
      this.node3 = node3;
    }

    @Override
    protected Future<Tuple3<T1, T2, T3>> evaluate() throws Exception {
      return Future.<scala.Tuple3<T1, T2, T3>>value(
          scala.Tuple3$.MODULE$.<T1, T2, T3>apply(node1.emit(), node2.emit(), node3.emit()));
    }
  }

  public static <T1, T2, T3> Node<Tuple3<T1, T2, T3>> asTuple3(final Node<T1> node1,
                                                               final Node<T2> node2,
                                                               final Node<T3> node3) {
    return new ToNodeTuple3<>(node1, node2, node3);
  }

  private static class ToNodeTuple4<T1, T2, T3, T4> extends Node<Tuple4<T1, T2, T3, T4>> {
    private final Node<T1> node1;
    private final Node<T2> node2;
    private final Node<T3> node3;
    private final Node<T4> node4;

    private ToNodeTuple4(Node<T1> node1, Node<T2> node2, Node<T3> node3, Node<T4> node4) {
      super(ImmutableList.<Node>of(node1, node2, node3, node4));
      this.node1 = node1;
      this.node2 = node2;
      this.node3 = node3;
      this.node4 = node4;
    }

    @Override
    protected Future<Tuple4<T1, T2, T3, T4>> evaluate() throws Exception {
      return Future.<scala.Tuple4<T1, T2, T3, T4>>value(
          scala.Tuple4$.MODULE$.<T1, T2, T3, T4>apply(node1.emit(), node2.emit(), node3.emit(),
              node4.emit()));
    }
  }

  public static <T1, T2, T3, T4> Node<Tuple4<T1, T2, T3, T4>> asTuple4(final Node<T1> node1,
                                                                       final Node<T2> node2,
                                                                       final Node<T3> node3,
                                                                       final Node<T4> node4) {
    return new ToNodeTuple4<>(node1, node2, node3, node4);
  }

  public static <T, S extends Iterable<T>> NamedFunction<S, Set<T>> toSet() {
    return NamedFunction.create("toSet", input ->
        input != null ? ImmutableSet.copyOf(input) : Collections.<T>emptySet());
  }
}
