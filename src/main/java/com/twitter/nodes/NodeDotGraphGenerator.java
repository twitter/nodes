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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.twitter.nodes.utils.Pair;

/**
 * Create DOT graph text for a node. This recursively traverses the node dependencies and create
 * the node dependency graph.
 * - http://www.graphviz.org/Documentation.php
 * - http://en.wikipedia.org/wiki/DOT_(graph_description_language)
 */
public final class NodeDotGraphGenerator {
  private static final String DEFAULT_PROPERTIES =
      "  rankdir=TB;\n"
      + "  node [shape=box fontname=\"menlo\" fontsize=11];\n"
      + "  edge [fontname=\"menlo\" fontsize=9];\n";

  /**
   * Types of node we care about from rendering perspective.
   */
  enum NodeType {
    NORMAL("shape=box"),
    VALUE("style=filled,color=\"#efccef\",shape=box"),
    PREDICATE("style=filled,color=\"#ddefef\",shape=polygon,sides=4,distortion=.05"),
    TRANSFORM("style=filled,color=\"#efefdd\",shape=polygon,sides=4,distortion=-.05"),
    BOOLEAN("style=filled,color=\"#efddef\",shape=polygon,sides=4"),
    SERVICE("style=filled,shape=box,peripheries=2");

    private final String renderingStyle;

    NodeType(String renderingStyle) {
      this.renderingStyle = renderingStyle;
    }

    public String getRenderingStyle() {
      return renderingStyle;
    }
  }

  /**
   * Edge Info for rendering
   */
  private static class EdgeInfo {
    boolean optional;
    String label;  // by default this is just the dep name, but may have special name

    EdgeInfo(boolean optional, String label) {
      this.optional = optional;
      this.label = label;
    }
  }

  private static Node unwrapOptional(Node node) {
    return node.isOptional()
        ? ((Node.OptionalNodeWrapper) node).getWrappedNode() : node;
  }

  /**
   * Node Info for rendering
   */
  private static class NodeInfo {
    Node node;  // the reference to the node
    String keyName;  // key used in the DOT graph file
    String nodeName;  // name from node itself
    NodeType type;
    // A map for all dependency nodes, from each dependency node's keyName to its dot information,
    // which is a pair of NodeInfo and EdgeInfo. NodeInfo is a reference to that node's data,
    // EdgeInfo has information for the connection between that dependency and this node.
    Map<String, Pair<NodeInfo, EdgeInfo>> depInfo = Maps.newHashMap();

    NodeInfo(String keyName, Node node) {
      this.node = node;
      this.keyName = keyName;
      Node underlyingNode = unwrapOptional(node);
      this.nodeName = underlyingNode.getName();
      this.type = extractType(underlyingNode);
    }

    /**
     * Generate the DOT node string for this node
     */
    public String getDOTString() {
      String label;
      String returnType = getReturnTypeForDisplay(node);
      if (returnType != null && !"Resp".equals(returnType)) {
        label = String.format("%s\\n<%s>", nodeName, returnType);
      } else {
        label = nodeName;
      }
      boolean subgraphExit = node.getEnclosingSubgraph() != null;
      String renderingFormat = type.getRenderingStyle() + (subgraphExit ? ",fontcolor=blue" : "");
      return String.format("%s [label=\"%s\" %s];", keyName, label, renderingFormat);
    }

    /**
     * Generate DOT edge string for all incoming edges for this node
     */
    public List<String> getEdgeStrings() {
      List<String> edges = Lists.newArrayList();
      Set<Pair<String, String>> seenPairs = Sets.newHashSet();
      for (Map.Entry<String, Pair<NodeInfo, EdgeInfo>> entry : depInfo.entrySet()) {
        NodeInfo dep = entry.getValue().getFirst();
        EdgeInfo edge = entry.getValue().getSecond();
        if (dep.keyName.equals(this.keyName)) {
          continue;  // don't add edge to itself
        }
        Pair<String, String> pair = Pair.of(dep.keyName, this.keyName);
        if (!seenPairs.contains(pair)) {
          edges.add(String.format("%s -> %s [style=%s label=\"%s\" color=\"%s\"];",
              dep.keyName, this.keyName,
              edge.optional ? "dashed" : "solid",
              edge.label.startsWith("DEP") ? "" : edge.label,
              "condition".equals(edge.label) ? "grey" : "black"));
          seenPairs.add(pair);
        }
      }

      // Beautify predicate edges, align true and false node.
      // TODO(wangtian): Temporarily commented out for now as it sometimes mess up the display,
      // find a better way to do this.
      // if (this.type == NodeType.PREDICATE) {
      //   Iterator<Pair<NodeInfo, EdgeInfo>> iter = depInfo.values().iterator();
      //   String name1 = iter.next().getFirst().keyName;
      //   String name2 = iter.next().getFirst().keyName;
      //   edges.add(String.format("{ rank = same; %s %s }", name1, name2));
      // }
      return edges;
    }
  }

  private NodeDotGraphGenerator() { }

  /**
   * Create dot for a subgraph
   */
  public static String createDot(Subgraph subgraph) {
    return createDot(subgraph.getExposedNodes());
  }

  public static String createDot(Node node) {
    return createDot(Collections.singletonList(node));
  }

  public static String createDot(List<Node> nodes) {
    StringBuilder sb = new StringBuilder();
    sb.append("digraph G {\n");
    sb.append(DEFAULT_PROPERTIES);

    // Extract all node graph information recursively.
    Map<String, NodeInfo> nodeInfoMap = Maps.newLinkedHashMap();

    GraphContext context = new GraphContext();
    for (Node node : nodes) {
      extractNodeInfo(node, nodeInfoMap, context);
    }

    // Merge all keys and dedupe.
    Map<Node, String> seenNodeKeyMap = new IdentityHashMap<>();
    for (NodeInfo info : nodeInfoMap.values()) {
      Node unwrappedNode = unwrapOptional(info.node);
      String seenKey = seenNodeKeyMap.get(unwrappedNode);
      if (seenKey != null) {
        info.keyName = seenKey;  // merge all keys and use a canonical version for each node object.
      } else {
        seenNodeKeyMap.put(unwrappedNode, info.keyName);
      }
    }

    // Output all node properties, labels, etc
    Set<String> seenKeys = Sets.newHashSet();
    for (NodeInfo nodeInfo : nodeInfoMap.values()) {
      if (!seenKeys.contains(nodeInfo.keyName)) {
        sb.append("  ").append(nodeInfo.getDOTString()).append("\n");
        seenKeys.add(nodeInfo.keyName);
      }
    }
    sb.append("\n");

    // Output all edges
    for (String key : seenKeys) {
      NodeInfo nodeInfo = nodeInfoMap.get(key);
      for (String edge : nodeInfo.getEdgeStrings()) {
        sb.append("  ").append(edge).append("\n");
      }
    }

    sb.append("}\n");
    return sb.toString();
  }

  /**
   * A class to store the context informaiton while we recursively traverse the node dependency
   * tree, currently it has only keyid info.
   * TODO(wangtian): add subgraph cluster support.
   */
  static class GraphContext {
    private int keyId = 0;
    private Map<Node, NodeInfo> seenNodeInfo = Maps.newIdentityHashMap();

    public int getKeyIdAndInc() {
      return keyId++;
    }

    public NodeInfo getNodeInfo(Node node) {
      return seenNodeInfo.get(node);
    }

    public boolean updateNodeInfo(Node node, NodeInfo info) {
      if (!seenNodeInfo.containsKey(node)) {
        seenNodeInfo.put(node, info);
        return true;
      }
      return false;
    }
  }

  /**
   * A recursive function to extract node information into nodeInfoMap.
   * @return a pair of NodeInfo for current node, and an integer, which is the next keyId to use.
   */
  private static NodeInfo extractNodeInfo(
      Node node, Map<String, NodeInfo> nodeInfoMap, GraphContext context) {

    // Check if we have seen this before
    NodeInfo seenInfo = context.getNodeInfo(node);
    if (seenInfo != null) {
      return seenInfo;
    }

    NodeInfo info = new NodeInfo("n" + context.getKeyIdAndInc(), node);
    nodeInfoMap.put(info.keyName, info);  // put into the map first

    Node baseNode = unwrapOptional(node);

    // create dependency map, some dependencies are special and need to be added specially
    Map<String, Node> depsByStr = baseNode.getInputsByName();

    // Add all dependencies and semi-dependencies to the NodeInfo map.
    Set<? extends Enum> depEnums = node.getOptionalDependencies();
    Set<String> optionalDeps = depEnums.stream().map(e -> e.name()).collect(Collectors.toSet());
    for (Map.Entry<String, Node> depEntry : depsByStr.entrySet()) {
      Node dep = depEntry.getValue();
      // Recursively get information for all dependencies
      Node unwrappedNode = unwrapOptional(dep);
      NodeInfo depNodeInfo = extractNodeInfo(unwrappedNode, nodeInfoMap, context);
      EdgeInfo edgeInfo = new EdgeInfo(
          optionalDeps.contains(depEntry.getKey()) || dep.isOptional(),
          depEntry.getKey());
      info.depInfo.put(depEntry.getKey(), Pair.of(depNodeInfo, edgeInfo));
    }
    context.updateNodeInfo(node, info);
    return info;
  }

  private static final Pattern CLASS_NAME_PATTERN =
      Pattern.compile("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*");

  /**
   * Get a return type to display, shorten all full qualified class names
   * (there could be other templates in the type) in the type to their simple name.
   */
  @Nullable
  private static String getReturnTypeForDisplay(Node node) {
    String type = node.getResponseClassName();
    if (type.startsWith("class ")) {
      type = type.substring(6);
    }
    Matcher matcher = CLASS_NAME_PATTERN.matcher(type);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group();  // found full qualified class name
      String[] items = name.split("\\.");
      String shortened = items.length > 0 ? items[items.length - 1] : name;
      matcher.appendReplacement(sb, shortened);
    }
    matcher.appendTail(sb);
    String finalType = sb.toString();
    return finalType.isEmpty() ? null : finalType;
  }

  private static NodeType extractType(Node node) {
    if (node instanceof ValueNode) {
      return NodeType.VALUE;
    } else if (node instanceof PredicateSwitchNode) {
      return NodeType.PREDICATE;
    } else if (node instanceof TransformNode
        || node instanceof PredicateNode) {
      return NodeType.TRANSFORM;
    } else if (node instanceof AndNode
        || node instanceof OrNode
        || node instanceof NotNode) {
      return  NodeType.BOOLEAN;
    } else if (node instanceof ServiceNode) {
      return  NodeType.SERVICE;
    } else {
      return NodeType.NORMAL;
    }
  }
}
