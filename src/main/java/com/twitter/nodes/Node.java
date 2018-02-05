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

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.twitter.logging.Logger;
import com.twitter.nodes.utils.DebugManager;
import com.twitter.nodes.utils.DeciderSupplier;
import com.twitter.nodes.utils.Futures;
import com.twitter.util.Await;
import com.twitter.util.Function0;
import com.twitter.util.Future;
import com.twitter.util.FutureTransformer;
import com.twitter.util.Promise;

/**
 * A node represents the Future computation of a value as the result of evaluating its completed
 * required and optional dependencies.
 */
public abstract class Node<Resp> extends Function0<Future<Resp>> {
  private static final Logger LOG = Logger.get(Node.class);

  // A static dependency map to remember all optional dependencies of all nodes.
  // This starts with an empty map and gradually collect optionality information for different
  // enum classes used in the nodes.
  private static final Map<Class<? extends Enum>, Set<? extends Enum>> OPTIONAL_DEP_MAP =
      Maps.newConcurrentMap();

  public static final Node<Boolean> TRUE = Node.value(true, "trueNode");
  public static final Node<Boolean> FALSE = Node.value(false, "falseNode");
  public static final Node NULL_NODE = Node.value(null, "nullNode");

  protected static final Future<Boolean> TRUE_FUTURE = Future.value(true);
  protected static final Future<Boolean> FALSE_FUTURE = Future.value(false);
  protected static final Future FUTURE_ABSENT = Future.value(Optional.absent());

  private final AtomicBoolean createdFuture = new AtomicBoolean();
  private final Promise<Resp> promise = new Promise<>();

  // Dependent nodes by their name. The names were created in the Builder (or the map passed into
  // the constructor.
  private ImmutableMap<Enum, Node> dependentNodesByName;

  protected List<Node> sinkNodes;

  // Name for this node instance, this is mostly auto-generated with type information
  protected String name;

  // A string key for the node, could be used to distinguish node instances of the same type.
  protected String key;

  private long startTimeMs;
  private long evaluateStartTimeMs;
  private long evaluateStopTimeMs;
  private long stopTimeMs;

  // This will be set if this node is the exposed "output" node of a Subgraph, it's done by
  // Subgraph.markExposedNodes().
  private Subgraph enclosingSubgraph;

  // this is a flag to deal with the java difficulty of differentiating between
  // Node<T> and Node<Optional<T>> via type introspection.
  private final boolean optional;

  // if true, this node's evaluate() method can return Future.value(null) without causing
  // exception and its emit() can return null, otherwise an exception will be thrown.
  private boolean canEmitNull = false;

  /**
   * Decider key that's used to check if this node should be run or not, if:
   * 1. the decider key is present for this node instance
   * 2. the node is optional for the workflow.
   * Otherwise, the decider check is skipped.
   */
  private Optional<DeciderSupplier> deciderSupplier = Optional.absent();

  protected Node() {
    this(null, false, ImmutableMap.<Enum, Node>of(), ImmutableList.<Node>of());
  }

  protected Node(boolean optional) {
    this(null, optional, ImmutableMap.<Enum, Node>of(), ImmutableList.<Node>of());
  }

  protected Node(String name, boolean optional) {
    this(name, optional, ImmutableMap.<Enum, Node>of(), ImmutableList.<Node>of());
  }

  protected Node(Node... nodes) {
    this(null, ImmutableList.copyOf(nodes));
  }

  protected Node(String name, Node... nodes) {
    this(name, ImmutableList.copyOf(nodes));
  }

  protected Node(Collection<Node> dependentNodes) {
    this(null, dependentNodes);
  }

  protected Node(String name, Collection<Node> dependentNodes) {
    this(name, false, dependentNodes, ImmutableList.<Node>of());
  }

  protected Node(Map<Enum, Node> dependentNodesByName) {
    this(null, dependentNodesByName);
  }

  protected Node(String name, Map<Enum, Node> dependentNodesByName) {
    this(name, false, dependentNodesByName, ImmutableList.<Node>of());
  }

  protected Node(String name,
                 boolean optional,
                 Collection<Node> dependentNodes,
                 Collection<Node> sinkNodes) {
    this(name, optional, createNamedDependencies(dependentNodes), sinkNodes);
  }

  /**
   * Constructor
   *
   * @param name The name of the node
   * @param optional Whether this node is optional. If true, this node should use Optional<> as its
   * return type.
   * @param dependentNodesByName A map from enum to dependent nodes, only after all these nodes
   * are ready will this node be running.
   * @param sinkNodes A collection of sink nodes to run after this node finished apply().
   */
  protected Node(@Nullable String name,
                 boolean optional,
                 Map<Enum, Node> dependentNodesByName,
                 Collection<Node> sinkNodes) {
    this.name = name != null && !name.isEmpty() ? name : this.getClass().getSimpleName();
    this.optional = optional;
    // dependent node map could be empty if the default constructor is called, this happens in
    // Builder.build(Class), where dependent node map is set later.
    this.dependentNodesByName = dependentNodesByName.isEmpty()
        ? ImmutableMap.<Enum, Node>of()
        : addOptionalDeps(dependentNodesByName);
    this.sinkNodes = ImmutableList.copyOf(sinkNodes);
  }

  public Subgraph getEnclosingSubgraph() {
    return enclosingSubgraph;
  }

  public void setEnclosingSubgraph(Subgraph enclosingSubgraph) {
    this.enclosingSubgraph = enclosingSubgraph;
  }

  /**
   * Allow this node to emit null, so when its evaluate returns Future.value(null) it won't convert
   * it to an exception but just let the null value fall through.
   */
  final Node<Resp> setAllowNull() {
    this.canEmitNull = true;
    return this;
  }

  /**
   * Disallow this node to emit null.
   */
  final Node<Resp> unsetAllowNull() {
    this.canEmitNull = false;
    return this;
  }

  protected void setDeciderSupplier(Optional<DeciderSupplier> optionaldeciderSupplier) {
    this.deciderSupplier = optionaldeciderSupplier;
  }

  protected void setDeciderSupplier(@Nullable DeciderSupplier deciderSupplier) {
    this.deciderSupplier = Optional.fromNullable(deciderSupplier);
  }

  /**
   * Check if this node can emit null value.
   */
  protected final boolean canEmitNull() {
    return canEmitNull;
  }

  private static Map<Enum, Node> createNamedDependencies(Collection<Node> nodes) {
    int maxSize = DefaultDependencyEnum.values().length;
    Preconditions.checkArgument(nodes.size() <= maxSize,
        String.format("You can't have more than %s dependencies for a node.", maxSize));
    Map<Enum, Node> map = new EnumMap(DefaultDependencyEnum.class);
    DefaultDependencyEnum[] values = DefaultDependencyEnum.values();
    int index = 0;
    for (Node node : nodes) {
      map.put(values[index++], node);
    }
    return map;
  }

  /**
   * Add optional nodes to the dependency map if they are not already there.
   */
  private ImmutableMap<Enum, Node> addOptionalDeps(Map<Enum, Node> depMap) {
    Enum firstEnum = depMap.keySet().iterator().next();
    Set<Enum> optionalDeps = getOptionalDependenciesForClass(firstEnum.getClass());
    if (optionalDeps.isEmpty()) {
      return ImmutableMap.copyOf(depMap);
    } else {
      for (Enum e : optionalDeps) {
        if (!depMap.containsKey(e)) {
          Node absentNode = DebugManager.isDebug()
              ? Node.optional(Node.noValue())
              : Node.absent();
          depMap.put(e, absentNode);
        }
      }
      return ImmutableMap.copyOf(depMap);
    }
  }

  public boolean isOptional() {
    return optional;
  }

  /**
   * Get dependency node itself
   */
  protected <T> Node<T> getNodeDep(Enum dependency) {
    Preconditions.checkArgument(name != null && dependentNodesByName.containsKey(dependency),
        "Cannot find node dependency for %s", dependency);
    return dependentNodesByName.get(dependency);
  }

  /**
   * Get a dependent node's emitted value by its name. You can only get named dependency's value. If
   * the node is optional, it will return Optional<> type.
   */
  protected <T> T getRawDep(Enum dependency) {
    Preconditions.checkArgument(name != null && dependentNodesByName.containsKey(dependency),
        "Cannot find raw node dependency value for %s", dependency);
    return (T) dependentNodesByName.get(dependency).emit();
  }

  /**
   * Get a dependent node's emitted value by its name.
   */
  @Nullable
  protected <T> T getDep(Enum dependency) {
    Preconditions.checkArgument(dependency != null && dependentNodesByName.containsKey(dependency),
        "Cannot find node dependency value for %s", dependency);
    return (T) this.<T>getDep(dependentNodesByName.get(dependency));
  }

  /**
   * Get a dependent node's emitted value by its name
   *
   * @param dependency Enum name of dependency
   * @param defaultValue default value to use if dependency is missing, i.e. emitted value is null
   * @param <T> return type of dependency
   */
  protected <T> T getDep(Enum dependency, T defaultValue) {
    Preconditions.checkNotNull(defaultValue, "Cannot have default value for a dependency as null");
    T value = getDep(dependency);
    return value == null ? defaultValue : value;
  }

  /**
   * Get dependency value by their node, if the node is optional, it will strip the Optional class
   * wrapping and returns null if the value is absent.
   */
  protected <T> T getDep(Node<T> depNode) {
    return depNode.isOptional()
        ? ((Optional<T>) depNode.emit()).orNull()
        : depNode.emit();
  }

  public final String getName() {
    return key == null
        ? name
        : name + ":" + key;
  }

  @Nullable
  public final String getKey() {
    return key;
  }

  public final Node setKey(String aKey) {
    this.key = aKey;
    return this;
  }

  public final Node setSinkNodes(List<Node> nodes) {
    Preconditions.checkArgument(!createdFuture.get(), "Node [%s] has been applied.", getName());
    Preconditions.checkNotNull(nodes);
    this.sinkNodes = nodes;
    return this;
  }

  public final Node addSinkNodes(List<Node> nodes) {
    return setSinkNodes(
        ImmutableList.<Node>builder()
            .addAll(this.sinkNodes)
            .addAll(nodes)
            .build());
  }

  public final Node addSinkNodes(Node... nodes) {
    return addSinkNodes(ImmutableList.copyOf(nodes));
  }

  /**
   * Return a set of enum fields that define which named dependencies are optional
   * This is only used to generate DOT graph.
   */
  public final Set<? extends Enum> getOptionalDependencies() {
    if (dependentNodesByName.isEmpty()) {
      return Collections.emptySet();
    }
    Enum firstEnum = dependentNodesByName.keySet().iterator().next();
    return getOptionalDependenciesForClass(firstEnum.getClass());
  }

  /**
   * Convert a node to a future "safely". This will mask all possible failures in the wrapped node,
   * including:
   * - non-NullableNode returning a null value
   * - exception thrown inside node
   * - any of node's further dependency's failure
   * all of them will turn into Future.value(null).
   * <p>
   * If you do not want to mask failures below, you should directly use .apply(). If current node
   * is nullable (inherit from NullableNode, or canEmitNull() returns true), it can return
   * Future.value(null) properly; if current node is not nullable, or there is an exception thrownm
   * this will become a Future exception.
   */
  public final Future<Resp> toFutureSafe() {
    return Node.optional(this).apply().map(new com.twitter.util.Function<Optional<Resp>, Resp>() {
      @Override
      public Resp apply(Optional<Resp> response) {
        return response.orNull();
      }
    });
  }

  /**
   * Create dot graph for a node, this provides a way to visualize node dependencies
   * and helps debugging.
   *
   * @return A string in DOT syntax, which can be rendered using Graphviz or other software.
   */
  public String toDotGraph() {
    return NodeDotGraphGenerator.createDot(this);
  }

  /**
   * Get class name for the response type using reflection.
   */
  public String getResponseClassName() {
    return getLastTemplateType(this.getClass());
  }

  /**
   * A helper function to get optional dependencies for an enum class and cache the result
   * in a map. If the cached results is available, use it.
   */
  private static Set getOptionalDependenciesForClass(Class<? extends Enum> enumClass) {
    Set optionalEnumSet = OPTIONAL_DEP_MAP.get(enumClass);
    if (optionalEnumSet == null) {
      optionalEnumSet = EnumSet.noneOf(enumClass);
      try {
        for (Object item : enumClass.getEnumConstants()) {
          Annotation[] annotations = enumClass.getField(item.toString()).getAnnotations();
          if (annotations != null) {
            for (Annotation anno : annotations) {
              if (anno instanceof OptionalDep) {
                optionalEnumSet.add(item);
              }
            }
          }
        }
        OPTIONAL_DEP_MAP.put(enumClass, ImmutableSet.copyOf(optionalEnumSet));
      } catch (Exception e) {
        LOG.error(e, "Cannot get fields for enum class " + enumClass);
        Throwables.propagate(e);
      }
    }
    return ImmutableSet.copyOf(optionalEnumSet);
  }

  public enum DefaultDependencyEnum {
    DEP0, DEP1, DEP2, DEP3, DEP4, DEP5, DEP6, DEP7,
    DEP8, DEP9, DEP10, DEP11, DEP12, DEP13, DEP14, DEP15
  }

  /**
   * A convenient method to create a builder for a given target node class.
   * Enum class for the dependencies will be decided when the first dependency is added.
   */
  public static <T> Builder<T> builder(Class<? extends Node<T>> nodeClass) {
    return new Builder<>(nodeClass, null);
  }

  /**
   * A simpler helper merging Node.builder() and Builder.withDependencies() into one call.
   */
  public static <T> Node<T> build(Class<? extends Node<T>> nodeClass, Object... dependencies) {
    return new Builder<>(nodeClass, null).withDependencies(dependencies);
  }

  /**
   * Provides a way of creating a builder from an existing instance of a Node.
   * <p>
   * This method is useful for nodes that require arguments in the constructor or for using mocks.
   */
  public static <T> Builder<T> builder(Node<T> nodeInstance) {
    return new Builder<>(nodeInstance, null);
  }

  /**
   * A general builder to build a node using named dependencies.
   * <p>
   * If the node is created from a class, it will first call the default constructor of the given
   * class (so make sure it has one, since Node already has one, not implementing any constructor
   * gives you one by default).
   * <p>
   * The builder assigns dependencies to the node instance using enum names. For any dependencies
   * marked as @OptionalDel in the enum class, if they don't already exist in the collected
   * dependency map, they will be added as Node.absent().
   *
   * @param <T> The return type of the nodes this Builder builds.
   */
  public static class Builder<T> {
    protected final Node<T> nodeInstance;
    protected Map<Enum, Node> dependentNodesByName;
    protected DeciderSupplier deciderSupplier;
    protected List<Node> sinkNodes = ImmutableList.of();
    protected String nodeKey;

    public Builder(Node<T> nodeInstance, @Nullable Class<? extends Enum> enumClass) {
      this.nodeInstance = nodeInstance;
      if (enumClass != null) {
        initDependencyMap(enumClass);
      }
    }

    public Builder(Class<? extends Node<T>> nodeClass) {
      this(nodeClass, null);
    }

    public Builder(Class<? extends Node<T>> nodeClass,
                   @Nullable Class<? extends Enum> enumClass) {
      this(createInstance(nodeClass), enumClass);
    }

    private static <T> Node<T> createInstance(Class<? extends Node<T>> nodeClass) {
      try {
        return nodeClass.newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
        throw new RuntimeException(
            String.format(
                "Cannot create instance for Node [%s], make sure it has a default constructor",
                nodeClass.getSimpleName()), e);
      }
    }

    /**
     * Create dependency map by enum, this allows the lazy creation of this class.
     */
    private void initDependencyMap(Class<? extends Enum> enumClass) {
      if (dependentNodesByName == null) {
        dependentNodesByName = new EnumMap(enumClass);
      }
    }

    /**
     * Check if a dependency or not by name
     */
    public boolean isDependencyOptional(Enum name) {
      return getOptionalDependenciesForClass(name.getClass()).contains(name);
    }

    /**
     * Add a named dependency. If the dependency is marked as optional, the node will be wrapped
     * so when it fails to emit a valid result, current node's execution won't be affected.
     */
    public Builder<T> dependsOn(Enum name, Node node) {
      initDependencyMap(name.getClass());
      // Wrap this node if its dependency state is optional and the node is not already wrapped.
      // The node.isOptional() is mostly for backward compatibility as we may sometimes pass in
      // an optional-wrapped node using the new-style builder.
      // TODO(wangtian): remove the isOptional() check after we clean up all such cases.
      Node dependency = isDependencyOptional(name) && !node.isOptional()
          ? Node.optional(node)
          : node;
      Preconditions.checkArgument(dependentNodesByName.put(name, dependency) == null,
          "You have already added a dependent node named " + name);
      return this;
    }

    public Builder<T> withDeciderSupplier(DeciderSupplier aDeciderSupplier) {
      this.deciderSupplier = aDeciderSupplier;
      return this;
    }

    public Builder<T> withNodeKey(String key) {
      this.nodeKey = key;
      return this;
    }

    public Builder<T> withSinkNodes(List<Node> nodes) {
      this.sinkNodes = nodes;
      return this;
    }

    public Builder<T> withSinkNodes(Node... nodes) {
      return withSinkNodes(ImmutableList.copyOf(nodes));
    }

    /**
     * A convenient wrapper (ImmutableMap.of() style) to add all dependencies.
     *
     * @param deps All dependencies with enum key and node object alternating. There must be even
     * number of items in this list.
     * @return A node built with given dependencies.
     */
    public Node<T> withDependencies(Object... deps) {
      Preconditions.checkArgument(deps.length > 0 && deps.length % 2 == 0,
          "There must be even number of arguments in Node.Builder.withDependencies()");
      try {
        for (int i = 0; i < deps.length; i += 2) {
          Enum key = (Enum) deps[i];
          Node node = (Node) deps[i + 1];
          dependsOn(key, node);
        }
      } catch (ClassCastException e) {
        LOG.error(e, "Casting exception while creating node");
        throw new RuntimeException(e);
      }
      return build();
    }

    /**
     * Get all dependent nodes in a map. If user uses Builder or subclasses it themselves, the enum
     * class would be provided by the user; if they use AnonymousBuilder, or simply pass in a
     * collection of unnamed dependencies, the key will be DefaultDependencyEnum.
     */
    protected Map<Enum, Node> getDependencyMap() {
      return dependentNodesByName;
    }

    public Node<T> build() {
      nodeInstance.setAllDependencies(getDependencyMap());
      nodeInstance.setDeciderSupplier(deciderSupplier);
      nodeInstance.setKey(nodeKey);
      nodeInstance.setSinkNodes(sinkNodes);
      return nodeInstance;
    }
  }

  /**
   * Optional wrapper for turning required nodes into Optional nodes.
   */
  static final class OptionalNodeWrapper<T> extends Node<Optional<T>> {

    private final Node<T> wrappedNode;

    OptionalNodeWrapper(Node<T> node) {
      super(String.format("~%s", node.getName()),
          true, ImmutableList.<Node>of(node), ImmutableList.<Node>of());
      this.wrappedNode = node;
      this.setDeciderSupplier(node.deciderSupplier);
    }

    Node<T> getWrappedNode() {
      return wrappedNode;
    }

    @Override
    protected Future<Optional<T>> evaluate() {
      T emitted = wrappedNode.emit();
      return Future.value(emitted == null ? Optional.<T>absent() : Optional.of(emitted));
    }
  }

  /**
   * Wrap a node which has an optional generic type and return an Optional wrapped value.
   * <p/>
   * This node will always succeed, and will return Optional.absent() if the underlying node fails.
   */
  public static <T> Node<Optional<T>> optional(final Node<T> node) {
    if (node == null) {
      return absent();
    }
    return new OptionalNodeWrapper<>(node);
  }

  /**
   * This statically shared node represents an optional node with an absent value.
   * Any node that depends on this node will always get Optional.absent from emit.
   */
  private static final Node NODE_OPTIONAL_ABSENT = new Node("ABSENT", true) {
    @Override
    protected Future evaluate() {
      return FUTURE_ABSENT;
    }
  };

  /**
   * Gets an Node with no contained reference.
   */
  public static <T> Node<Optional<T>> absent() {
    return (Node<Optional<T>>) NODE_OPTIONAL_ABSENT;
  }

  /**
   * Get all of the node's dependencies.
   *
   * @return a collection of dependent nodes.
   */
  public final Collection<Node> getAllDependencies() {
    return dependentNodesByName.values();
  }

  /**
   * Get dependencies by name
   */
  final ImmutableMap<Enum, Node> getDependenciesByName() {
    return dependentNodesByName;
  }

  /**
   * Get all inputs by name, for some nodes (like PredicateSwitchNode, BooleaNode), input is more
   * than just dependencies.
   */
  ImmutableMap<String, Node> getInputsByName() {
    ImmutableMap.Builder<String, Node> builder = ImmutableMap.builder();
    for (Map.Entry<Enum, Node> entry : getDependenciesByName().entrySet()) {
      builder.put(entry.getKey().name(), entry.getValue());
    }
    return builder.build();
  }

  /**
   * Set dependencies after a Node is constructed using the default constructor,
   * this should only be called by builder. We add all unset optional dependencies and check
   * if all required dependencies are set, if not the Preconditions check will fail.
   */
  private void setAllDependencies(Map<Enum, Node> depsMap) {
    Preconditions.checkArgument(depsMap != null && !depsMap.isEmpty(),
        "You can set with empty dependency map");
    ImmutableMap<Enum, Node> allDependencies = addOptionalDeps(depsMap);
    // check if all dependencies are provided
    EnumSet unsetEnums = EnumSet.complementOf(EnumSet.copyOf(allDependencies.keySet()));
    Preconditions.checkArgument(unsetEnums.isEmpty(),
        "Required dependencies not set for node [" + getName() + "]: " + unsetEnums);
    this.dependentNodesByName = allDependencies;
  }

  /**
   * Initiates the computation of the node value.
   * <p/>
   * Calling apply results in bottom-up evaluation of apply on all dependent nodes, their
   * dependencies, and so on, resulting in a future-chain that evaluates dependent node success.
   * <p/>
   * A node will only succeed in computing its value if and only if, all of the required
   * dependencies successfully complete and have non-null values.
   * <p/>
   * If the dependents do succeed, then the {link:evaluate} will be called on this node, at which
   * point accessing of the required dependent nodes via {@link #emit()} is guaranteed to produce a
   * non-null value.
   * <p/>
   * Optional dependencies may or may not have succeeded, but will have at least completed.
   * <p/>
   * The Future for a node is only created once and is shared amongst all dependent nodes.
   * <p/>
   *
   * @return Future of the node value.
   */
  public final Future<Resp> apply() {
    if (!createdFuture.compareAndSet(false, true)) {
      return promise;
    }

    // Capture when the node started waiting on dependencies
    startTimeMs = System.currentTimeMillis();

    Future<Resp> response = futureFromDependencies().flatMap(
        new com.twitter.util.Function<Object, Future<Resp>>() {
          @Override
          public Future<Resp> apply(Object value) {

            Future<Resp> result;

            if (!isOptional()) {
              logStart();
            }
            try {
              evaluateStartTimeMs = System.currentTimeMillis();

              if (deciderSupplier.isPresent()
                  && !deciderSupplier.get().isFeatureAvailable()) {
                debugVerbose("is decidered off for this request, decider key: %s",
                    deciderSupplier.get().getDeciderKey());
                result = isOptional() ? FUTURE_ABSENT : Future.value(null);
              } else {
                result = evaluate();
                if (result == null) {
                  result = Future.exception(new RuntimeException(
                      String.format("evaluate() returned null Future object!")));
                }
              }
            } catch (Exception e) {
              String message = String.format("evaluate threw an exception");
              debugDetailed("%s\n%s", message, Throwables.getStackTraceAsString(e));
              LOG.error(e, message);
              result = Future.exception(e);
            }

            evaluateStopTimeMs = System.currentTimeMillis();
            return result;
          }
        }
    ).flatMap(
        new com.twitter.util.Function<Resp, Future<Resp>>() {
          @Override
          public Future<Resp> apply(Resp value) {
            if (value == null && !canEmitNull) {
              return Future.exception(new RuntimeException(
                  String.format("evaluate() returned Future.value(null) "
                      + "but the node is not marked as Nullable.")));
            } else {
              return Future.value(value);
            }
          }
        }).transformedBy(
            new FutureTransformer<Resp, Resp>() {
              @Override
              public Future<Resp> flatMap(Resp value) {
                stopTimeMs = System.currentTimeMillis();
                if (!isOptional()) {
                  logResponse(value);
                  logEnd();
                }
                return Future.value(value);
              }

              @Override
              public Future<Resp> rescue(Throwable throwable) {
                stopTimeMs = System.currentTimeMillis();
                if (!isOptional()) {
                  logError(throwable);
                }
                return isOptional()
                    ? (Future<Resp>) FUTURE_ABSENT : Future.exception(throwable);
              }
            });

    applySinkNodes();

    promise.become(response);

    return promise;
  }

  protected void logStart() {
    debugDetailed("Start");
  }

  protected void logEnd() {
    debugDetailed("End (%d/%d ms)",
        stopTimeMs - startTimeMs, evaluateStopTimeMs - evaluateStartTimeMs);
  }

  protected void logError(Throwable t) {
    debugDetailed("Failed (%d/%d ms): %s",
        stopTimeMs - startTimeMs, evaluateStopTimeMs - evaluateStartTimeMs, t.getMessage());
    debugVerbose("Detailed failure: %s", Throwables.getStackTraceAsString(t));
  }

  /**
   * Wait on a bunch of nodes before returning current node's result. This is convenient in creating
   * some temporary dependencies.
   */
  public Node<Resp> waitOn(Node... nodesToWait) {
    Preconditions.checkArgument(nodesToWait.length <= DefaultDependencyEnum.values().length,
        "Too many nodes to wait on");
    List<Node> deps = Lists.newArrayList(nodesToWait);
    final Node<Resp> outerNode = this;
    return new NullableNode<Resp>(this.getName() + "_waited", deps) {
      @Override
      protected Future<Resp> evaluate() throws Exception {
        return outerNode.apply();
      }
    };
  }

  /**
   * Creates the future used to determine when the node's dependencies are able to be
   * evaluate()'ed.
   * <p/>
   * The default implementation is to join all dependencies so that evaluate() is only called when
   * all dependencies are complete and successful.
   */
  Future futureFromDependencies() {
    final List<Future<Object>> dependencies =
        Lists.newArrayListWithExpectedSize(dependentNodesByName.size());
    for (Node node : dependentNodesByName.values()) {
      dependencies.add(node.apply());
    }
    return com.twitter.util.Futures.join(dependencies);
  }


  /**
   * Calls apply on all sink nodes.
   */
  private void applySinkNodes() {
    for (Node node : sinkNodes) {
      node.apply();
    }
  }

  /**
   * Callback that fires when all of the required dependencies succeeded and have non-null values.
   * <p/>
   * Either this method or {link:rescue} is called.
   * <p/>
   *
   * @return a future of the computed node's value; may be a future of null if it failed.
   */
  protected abstract Future<Resp> evaluate() throws Exception;

  /**
   * Log response string, by default it doesn't print much information
   */
  protected void logResponse(@Nullable Resp response) {
    String str = response == null ? null : printResponse(response);
    if (str != null && !isOptional()) {
      debugDetailed("response: %s", str);
    }
  }

  /**
   * Print response into a string for logging/debugging purpose.
   */
  @Nullable
  protected String printResponse(Resp response) {
    if (DebugManager.isAtLeastVerbose2()) {
      return String.valueOf(response);
    }
    return null;
  }

  /**
   * Gets the node value.
   * <p/>
   * The node will only emit a non-null value if it completed successfully.
   * <p/>
   * Since a node's required dependencies must succeed for {@link #evaluate()} to be called, a
   * node's implementation should only be able to call {@link #emit()} when it's guaranteed to
   * return successfully.
   */
  public Resp emit() {
    if (Futures.completedWithSuccess(promise)) {
      try {
        return Await.result(promise);
      } catch (Exception e) {
        LOG.error(e, "Exception during emit()");
        throw new RuntimeException("Could not read promise", e);
      }
    }

    // It's logically impossible to get here if:
    //    apply() was called on the terminating graph node
    //    AND the promise's node was added as a dependency.
    // So it's possible to get here if you just create a node and then call emit() on it w/o
    // calling apply() on the terminating graph node.
    // Assuming the graph was used properly, then it's possible to get here if the node wasn't added
    //
    // Report back to the user which state the node was in, but also remind them to add the node
    // as a dependency.

    if (Futures.completedWithFailure(promise)) {
      throw new IllegalStateException(
          String.format("NODE[%s]: Attempting to call emit() on failed required node.  "
              + "Did you forget to add this node as a required dependency?", getName()));
    }

    throw new IllegalStateException(
        String.format("NODE[%s]: Attempting to call emit() on an incomplete required node.  "
            + "Did you forget to add this node as a required dependency?", getName()));
  }

  public void debugBasic(final String message, Object... args) {
    if (DebugManager.isAtLeastBasic()) {
      DebugManager.basic(getDebugPrefix() + message, args);
    }
  }

  public void debugDetailed(final String message, Object... args) {
    if (DebugManager.isAtLeastDetailed()) {
      DebugManager.detailed(getDebugPrefix() + message, args);
    }
  }

  public void debugVerbose(final String message, Object... args) {
    if (DebugManager.isAtLeastVerbose()) {
      DebugManager.verbose(getDebugPrefix() + message, args);
    }
  }

  public void debugVerbose2(final String message, Object... args) {
    if (DebugManager.isAtLeastVerbose2()) {
      DebugManager.verbose2(getDebugPrefix() + message, args);
    }
  }

  public void debugVerbose3(final String message, Object... args) {
    if (DebugManager.isAtLeastVerbose3()) {
      DebugManager.verbose3(getDebugPrefix() + message, args);
    }
  }

  private volatile String debugPrefix = null;

  private String getDebugPrefix() {
    if (debugPrefix == null) {
      debugPrefix = "NODE [" + getName() + "]: ";
    }
    return DebugManager.isAtLeastDetailed()
        ? String.format("[%04d] %s", System.currentTimeMillis() % 10000, debugPrefix)
        : debugPrefix;
  }

  // ------------------------------- Transformations -------------------------------
  // Common transformations on current node

  /**
   * Map the output of current node to a new type T.
   */
  public <T> Node<T> map(NamedFunction<Resp, T> func) {
    return mapWithDeciderSupplier(null, func);
  }

  public <T> Node<T> map(String functionName, Function<Resp, T> func) {
    return mapWithDeciderSupplier(null, NamedFunction.create(functionName, func));
  }

  /**
   * Mapping with multiple inputs
   */
  @FunctionalInterface
  public interface Function2<X, A, B> {
    X apply(A a, B b);
  }

  @FunctionalInterface
  public interface Function3<X, A, B, C> {
    X apply(A a, B b, C c);
  }

  @FunctionalInterface
  public interface Function4<X, A, B, C, D> {
    X apply(A a, B b, C c, D d);
  }

  @FunctionalInterface
  public interface Function5<X, A, B, C, D, E> {
    X apply(A a, B b, C c, D d, E e);
  }

  @FunctionalInterface
  public interface Function6<X, A, B, C, D, E, F> {
    X apply(A a, B b, C c, D d, E e, F f);
  }

  @FunctionalInterface
  public interface Function7<X, A, B, C, D, E, F, G> {
    X apply(A a, B b, C c, D d, E e, F f, G g);
  }

  @FunctionalInterface
  public interface Function8<X, A, B, C, D, E, F, G, H> {
    X apply(A a, B b, C c, D d, E e, F f, G g, H h);
  }

  public static <T, A, B> Node<T> map2(
      String name,
      final Node<A> aNode, final Node<B> bNode, Function2<T, A, B> func) {
    return new Node<T>(name, aNode, bNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return Future.value(func.apply(aNode.emit(), bNode.emit()));
      }
    };
  }

  public static <T, A, B, C> Node<T> map3(
      String name,
      Node<A> aNode, Node<B> bNode, Node<C> cNode, Function3<T, A, B, C> func) {
    return new Node<T>(name, aNode, bNode, cNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return Future.value(func.apply(aNode.emit(), bNode.emit(), cNode.emit()));
      }
    };
  }

  public static <T, A, B, C, D> Node<T> map4(
      String name,
      Node<A> aNode, Node<B> bNode, Node<C> cNode, Node<D> dNode,
      Function4<T, A, B, C, D> func) {
    return new Node<T>(name, aNode, bNode, cNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return Future.value(func.apply(aNode.emit(), bNode.emit(), cNode.emit(), dNode.emit()));
      }
    };
  }

  public static <T, A, B> Node<T> flatMap2(
      String name,
      final Node<A> aNode, final Node<B> bNode, Function2<Future<T>, A, B> func) {
    return new Node<T>(name, aNode, bNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return func.apply(aNode.emit(), bNode.emit());
      }
    };
  }

  public static <T, A, B, C> Node<T> flatMap3(
      String name,
      Node<A> aNode, Node<B> bNode, Node<C> cNode, Function3<Future<T>, A, B, C> func) {
    return new Node<T>(name, aNode, bNode, cNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return func.apply(aNode.emit(), bNode.emit(), cNode.emit());
      }
    };
  }

  public static <T, A, B, C, D> Node<T> flatMap4(
      String name,
      Node<A> aNode, Node<B> bNode, Node<C> cNode, Node<D> dNode,
      Function4<Future<T>, A, B, C, D> func) {
    return new Node<T>(name, aNode, bNode, cNode, dNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return func.apply(aNode.emit(), bNode.emit(), cNode.emit(), dNode.emit());
      }
    };
  }

  public static <T, A, B, C, D, E> Node<T> flatMap5(
      String name,
      Node<A> aNode, Node<B> bNode, Node<C> cNode, Node<D> dNode, Node<E> eNode,
      Function5<Future<T>, A, B, C, D, E> func) {
    return new Node<T>(name, aNode, bNode, cNode, dNode, eNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return func.apply(aNode.emit(), bNode.emit(), cNode.emit(), dNode.emit(), eNode.emit());
      }
    };
  }

  public static <T, A, B, C, D, E, F> Node<T> flatMap6(
      String name,
      Node<A> aNode, Node<B> bNode, Node<C> cNode, Node<D> dNode, Node<E> eNode, Node<F> fNode,
      Function6<Future<T>, A, B, C, D, E, F> func) {
    return new Node<T>(name, aNode, bNode, cNode, dNode, eNode, fNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return func.apply(
            aNode.emit(), bNode.emit(), cNode.emit(), dNode.emit(), eNode.emit(), fNode.emit());
      }
    };
  }

  public static <T, A, B, C, D, E, F, G> Node<T> flatMap7(
      String name,
      Node<A> aNode,
      Node<B> bNode,
      Node<C> cNode,
      Node<D> dNode,
      Node<E> eNode,
      Node<F> fNode,
      Node<G> gNode,
      Function7<Future<T>, A, B, C, D, E, F, G> func) {
    return new Node<T>(name, aNode, bNode, cNode, dNode, eNode, fNode, gNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return func.apply(
            aNode.emit(), bNode.emit(), cNode.emit(), dNode.emit(), eNode.emit(), fNode.emit(),
            gNode.emit());
      }
    };
  }

  public static <T, A, B, C, D, E, F, G, H> Node<T> flatMap8(
      String name,
      Node<A> aNode,
      Node<B> bNode,
      Node<C> cNode,
      Node<D> dNode,
      Node<E> eNode,
      Node<F> fNode,
      Node<G> gNode,
      Node<H> hNode,
      Function8<Future<T>, A, B, C, D, E, F, G, H> func) {
    return new Node<T>(name, aNode, bNode, cNode, dNode, eNode, fNode, gNode, hNode) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return func.apply(
            aNode.emit(), bNode.emit(), cNode.emit(), dNode.emit(), eNode.emit(), fNode.emit(),
            gNode.emit(), hNode.emit());
      }
    };
  }

  /**
   * Maps the value of current node to a new type T by applying the provided function, but only when
   * the value is present. For null values, the function is not even run and the transformed value
   * is null. Exceptions will be convert to null.
   * <p>
   * This means the function doesn't have to handle nullable inputs when run on Nullable nodes.
   */
  public <T> Node<T> mapOnSuccess(NamedFunction<Resp, T> func) {
    return ifSuccessThen(this, map(func));
  }

  public <T> Node<T> mapOnSuccess(String functionName, Function<Resp, T> func) {
    return ifSuccessThen(this, map(functionName, func));
  }

  public <T> Node<T> mapWithDeciderSupplier(
      DeciderSupplier deciderKey, NamedFunction<Resp, T> func) {
    return TransformNode.<Resp, T>create(this, func, func.getName(), deciderKey);
  }

  public <T> Node<T> mapWithDeciderSupplier(
      String functionName, DeciderSupplier deciderKey, Function<Resp, T> func) {
    return mapWithDeciderSupplier(deciderKey, NamedFunction.create(functionName, func));
  }

  public <T> Node<T> flatMap(NamedFunction<Resp, Future<T>> func) {
    return flatMapWithDeciderSupplier(null, func);
  }

  public <T> Node<T> flatMapWithDeciderSupplier(
      DeciderSupplier deciderKey, NamedFunction<Resp, Future<T>> func) {
    return FlatMapTransformNode.create(this, func, func.getName(), deciderKey);
  }

  public <T> Node<T> flatMapWithDeciderSupplier(
      String functionName,
      DeciderSupplier deciderKey,
      java.util.function.Function<Resp, Future<T>> func) {
    return flatMapWithDeciderSupplier(deciderKey, NamedFunction.create(functionName, func));
  }

  /**
   * Collect results from a map of nodes into a node of the map.
   */
  public static <A, B> Node<Map<A, B>> collect(Map<A, Node<B>> nodeMap) {
    Preconditions.checkNotNull(nodeMap);
    Map<A, Future<B>> futures = Maps.newHashMap();
    for (Map.Entry<A, Node<B>> mapEntry : nodeMap.entrySet()) {
      futures.put(mapEntry.getKey(), mapEntry.getValue().apply());
    }
    return Node.wrapFuture(Futures.collect(futures));
  }

  /**
   * Collect results from a list of nodes into a node of list.
   */
  public static <T> Node<List<T>> collect(List<Node<T>> nodeList) {
    Preconditions.checkNotNull(nodeList);
    List<Future<T>> futures = Lists.newArrayList();
    for (Node<T> node : nodeList) {
      futures.add(node.apply());
    }
    return Node.wrapFuture(Future.collect(futures));
  }

  /**
   * Splits and transforms a Node of a list of elements A
   * and then collects as a Node of lists of element B
   */
  public static <A, B> Node<List<B>> splitAndCollect(
      Node<List<A>> list, final NamedFunction<A, Node<B>> transformFunction) {
    return list.flatMap(
        NamedFunction.create("splitAndCollectList", items -> {
          List<Node<B>> newlist = Lists.newArrayList();
          for (A item : items) {
            newlist.add(transformFunction.apply(item));
          }
          return Node.collect(newlist).apply();
        }))
        .whenSuccess(list);
  }

  /**
   * Split and collect with java function
   */
  public static <A, B> Node<List<B>> splitAndCollect(
      Node<List<A>> list, String name, Function<A, Node<B>> func) {
    return splitAndCollect(list, NamedFunction.create(name, func));
  }

  /**
   * Returns the value of the current node if the condition node is evaluated as true. Otherwise,
   * returns a node with a null value.
   */
  public Node<Resp> when(Node<Boolean> conditionNode) {
    return ifThen(conditionNode, this);
  }

  /**
   * Returns the value of the current node if the condition node is evaluated as false. Otherwise,
   * returns a node with a null value.
   */
  public Node<Resp> unless(Node<Boolean> conditionNode) {
    return ifThen(NotNode.of(conditionNode), this);
  }

  /**
   * Returns the value of the current node if the condition node is successful. Otherwise,
   * returns a node with a null value.
   */
  public Node<Resp> whenSuccess(Node conditionNode) {
    return ifSuccessThen(conditionNode, this);
  }

  /**
   * Returns the value of the other node if the current one has failed or has null value,
   * otherwise it returns the current node.
   */
  public Node<Resp> orElse(Node<Resp> otherNode) {
    return ifSuccessThenElse(this, this, otherNode);
  }

  /**
   * Create a predicate out of this node
   */
  public Node<Boolean> predicate(String predicateName, Predicate<Resp> predicate) {
    return PredicateNode.create(this, predicate, predicateName);
  }

  public Node<Boolean> predicate(NamedPredicate<Resp> predicate) {
    return PredicateNode.create(this, predicate, predicate.getName());
  }

  public Node<Boolean> isNull() {
    return this.predicate(getName() + "_isNull", i -> i == null);
  }

  public Node<Boolean> isNotNull() {
    return this.predicate(getName() + "_isNotNull", i -> i != null);
  }

  // ------------------------------- Convenient Helpers -------------------------------
  // These convenient static helpers creates some nodes of common type from other nodes
  // or non-node objects.

  /**
   * Create a fixed value Node.
   *
   * @param value value of the node
   * @param <T> type of the node
   */
  public static <T> Node<T> value(T value) {
    return ValueNode.<T>create(value);
  }

  /**
   * Create a fixed value Node.
   *
   * @param value value of the node
   * @param name name of the node used in graph serialization
   * @param <T> type of the node
   */
  public static <T> Node<T> value(T value, String name) {
    return ValueNode.create(value, name);
  }

  /**
   * Create a node from a value supplier. The supplier will be called at most once.
   * this is only called when the value node is actually used
   * (have its emit() or apply() called, not during its creation)
   */
  public static <T> Node<T> valueFromSupplier(Supplier<T> valueSupplier, String name) {
    return SupplierValueNode.create(valueSupplier, name);
  }

  /**
   * Gets a Node with a null value.
   * <p/>
   * Any node that depends on this noValue node will not succeed.
   */
  public static <K> Node<K> noValue() {
    if (DebugManager.isDebug()) {
      return Node.value(null, "null");
    } else {
      // non-debug time optimization
      return (Node<K>) NULL_NODE;
    }
  }

  /**
   * Create a failure node with the given exception
   */
  public static <T> Node<T> fail(final Exception e) {
    return new Node<T>() {
      @Override
      protected Future<T> evaluate() throws Exception {
        return Future.exception(e);
      }
    };
  }

  public static <T> PredicateSwitchNode<T> ifThenElse(
      Node<Boolean> predicateNode,
      Node<T> trueNode,
      Node<T> falseNode
  ) {
    return new PredicateSwitchNode<>(predicateNode, trueNode, falseNode);
  }

  public static <T> PredicateSwitchNode<T> ifThen(
      Node<Boolean> predicateNode,
      Node<T> trueNode) {
    return new PredicateSwitchNode<>(predicateNode, trueNode, Node.<T>noValue());
  }

  public static <T> PredicateSwitchNode<T> ifSuccessThenElse(
      Node testNode,
      Node<T> trueNode,
      Node<T> falseNode) {
    return ifThenElse(IfSuccessfulNode.create(testNode), trueNode, falseNode);
  }

  public static <T> PredicateSwitchNode<T> ifSuccessThen(
      Node testNode,
      Node<T> trueNode) {
    return ifThen(IfSuccessfulNode.create(testNode), trueNode);
  }

  /**
   * Wrap a Future object into a node.
   */
  public static <T> Node<T> wrapFuture(final Future<T> future) {
    return wrapFuture(future, "wrappedFuture[" + getLastTemplateType(future.getClass()) + "]");
  }

  /**
   * Wrap a Future object into a node with a name
   */
  public static <T> Node<T> wrapFuture(final Future<T> future, String name) {
    // Create a dummy wrapping node, not optional, no dependencies or sinks
    return new NullableNode<T>(name) {
      @Override
      protected Future<T> evaluate() throws Exception {
        return future;
      }
    };
  }

  protected static String getLastTemplateType(Class clazz) {
    Type t = clazz.getGenericSuperclass();
    if (t instanceof ParameterizedType) {
      Type[] argTypes = ((ParameterizedType) t).getActualTypeArguments();
      if (argTypes.length > 0) {
        return argTypes[argTypes.length - 1].toString();
      }
    }
    return "";
  }

}
