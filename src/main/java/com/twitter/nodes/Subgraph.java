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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.Lists;

import com.twitter.nodes.utils.DebugManager;

/**
 * A subgraph is a subset of a node graph, it takes a bunch of nodes as input and create a node
 * graph. It exposes one or more internal nodes of this graph via public member variables.
 *
 * To implement a subclass, you should:
 * 1. add public member variables of Node type for all the nodes you want to expose. If you
 *    just want something like old GraphNode, you only need one.
 * 2. implement your own constructor, with one or more Node inputs and other input, create all
 *    node wiring in the constructor.
 * 3. at the end of constructor, call markExposedNodes().
 */
public abstract class Subgraph {
  private static final Logger LOG = Logger.getLogger(Subgraph.class.getName());

  /**
   * ALWAYS REMEMBER TO CALL THIS AT THE END OF SUBCLASS CONSTRUCTOR.
   *
   * Mark all exposed public Node member variables with current Subgraph instance using reflection.
   * This is for debugging and DOT graph generation purpose. If this method is not called,
   * there's no impact on the executions of nodes, it just affect the generated DOT graph.
   *
   * This is only done when current debug level is > 0, so we don't need to run this for every
   * production query.
   */
  protected void markExposedNodes() {
    if (DebugManager.isDebug()) {
      List<Node> exposedNodes = getExposedNodes();
      if (exposedNodes.isEmpty()) {
        throw new RuntimeException("You don't have any public Node field in subgraph class "
            + this.getClass().getSimpleName());
      }
      for (Node node : exposedNodes) {
        node.setEnclosingSubgraph(this);
      }
    }
  }

  /**
   * Find all exposed public node member variables by reflection.
   */
  List<Node> getExposedNodes() {
    List<Node> nodes = Lists.newArrayList();
    Field[] fields = this.getClass().getDeclaredFields();
    for (Field f : fields) {
      if (Modifier.isPublic(f.getModifiers()) && Node.class.isAssignableFrom(f.getType())) {
        try {
          nodes.add((Node) f.get(this));
        } catch (IllegalAccessException e) {
          LOG.warning("Cannot access field [" + f.getName() + "] in subgraph "
              + this.getClass().getSimpleName());
        }
      }
    }
    return nodes;
  }

  /**
   * Mark all nodes in the list with current subgraph, a simpler non-reflection based version.
   */
  protected void markExposedNodes(Node... nodes) {
    for (Node node : nodes) {
      node.setEnclosingSubgraph(this);
    }
  }

  public String toDotGraph() {
    return NodeDotGraphGenerator.createDot(this);
  }
}
