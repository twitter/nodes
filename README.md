[![Build Status](https://img.shields.io/travis/twitter/nodes/master.svg)](https://travis-ci.com/twitter/nodes) [![Maven Central](https://img.shields.io/maven-central/v/com.twitter/twitter-nodes.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.twitter%22%20AND%20a%3A%22nodes%22)

Nodes
============================

Nodes is a library to implement asynchronous dependency graphs for services in Java.

## Background

When you write a service, like an RPC server based on Thrift, especially if you use [Finagle](https://twitter.github.io/finagle/), you may have some interface like:

```java
Future<Response> processRequest(Request req)
```

You business logic goes inside `process()`, you may do some local computation here, call some external services, or run some code in some other threads. The logic dependencies in these code could be complicated, but Finagle provides a good paradigm for [concurrent programming with Futures](http://twitter.github.io/finagle/guide/Futures.html). The `Future` class is a good building block, but it's not exactly convenient, nor Java friendly. Usually it involves lots of nested callbacks and repeated function signatures. When it comes to waiting on multiple Futures, the code gets ugly very soon. Nodes is a Java library that aims to solve these problems, making the asynchronous code easier to read, to maintain and to test in Java.

## Basic Concepts
A node is an asyncrhonous processing unit. It takes multiple asyhcrounous input nodes (dependency nodes), and produces a single output, asyncrhonously. It will only start executing when all inputs are ready. A node object is also a handle to its output: like `Future<A>` in Finagle, `Node<A>` represents an asychronously computed data of type A. Actually `Node` and `Future` are mutually convertible. The tutorials below will show you how to create nodes and assemble them into a dependency graph.

Another way to understand nodes is to consider them Asynchronous Functions, every node with input of type A, B, C and return type of X can be thought as a function:

```java
Future<X> process(Future<A> a, Future<B> b, Future<C> c)
```

Actually this is very close to how its implemented, except that the input arguments have to be declared as dependency enums.

## Tutorials

### Creating a Node

The most common way to create a node is as follows:

```java
// A node that produces an integer as output 
public class MyNode extends Node<Integer> {
  public enum D {  // "D" is a convention
    DEP1,
    DEP2,
    @OptionalDep DEP3  // this dependency is optional, may be omitted
  }

  @Override
  protected Future<Integer> evaluate() throws Exception { 
    // To get the value of a dependency nodes, use getDep(key)
    Type1 value1 = getDep(D.DEP1);
    Type2 value2 = getDep(D.DEP2);
    Type3 value3 = getDep(D.DEP3, someDefault);
    Integer result = compute(value1, value2, value3);
    return Future.value(result);
  }
}
```

You write a `public` class extending `Node` with a certain type (Note that all Node's succlasses have to be `public`, we will explain this later), inside which you define enums for all your dependencies (the name convention is `D`).

A dependency is by default required, which means seeing an exception in them would fail current node directly and cause it not to execute at all. Optional dependencies should be marked as `@OptionalDep`, allowing them to be omitted or to fail.

You need to implement the `evaluate()` method, which will only be called when all inputs are ready. You can get values for each of the dependencies and do you computation. At the end, you are supposed to return a `Future` with desired return type. If you don't have any asynchronous processing inside, you can just wrap your output with `Future.value()`. However, if you have any asynchronous processing at all, like calling a server, or submit task to another thread, you will have a `Future` object in hand and you can just return that.

#### NullableNode

A node shouldn't return a null value, an exception would be thrown if it encounters a null output. You should try to use the control flow methods (see below) to manage the execution and make `null` value unnecessary, or utilize `Optional<A>` to represent return values that can be null. However, we also provides `NullableNode` which you can extend from, they can return `null` without causing any exception. The reason we make default `Node` class null-unfriendly is to make it easy to reason what went wrong during the execution, and not to confuse an error with a non-existent value, but overall this is a matter of style. The `NullableNode` is convenient but you should use it with care.

#### ServiceNode

Most of the nodes are just doing local computations. For nodes that calls any asynchronous service, be it an external Thrift RPC server, an HTTP server, or just a in-process scheduler that runs tasks in a thread pool, you can implement in `ServiceNode`. Rather than implementing `evaluate()`, you need to implement `buildRequest()` and `getService()`.

The method `buildRequst()` builds the request for current service call, in the parent class it's called in `evaluate()` so all dependencies should already be ready.

The method `getService()` gets a Finagle `Service` object, its interface is mostly about taking a `Request` and return a `Future<Response>`.

```java
public class NodeServiceNode extends ServiceNode<Response> {
  private final Node<A> 

  @Override
  public Service<Request, Response> getService() {
     // get service from somewhere
  }
  
  @Override
  public Request buildRequest() {
    // this is called inside evaluate(), you can get all your dependencies
    // the same way, or just get them from your own member variable.
  }
}
```

(more on this)

### Instantiating a Node

To instantiate your node:

```java
Node<Integer> resultNode = Node.build(
    MyNode.class,
    MyNode.D.DEP1, node1,
    MyNode.D.DEP2, node2,
    MyNode.D.DEP3, node3);
```

You can omit the optional dependency without causing any runtime exception.

```java
Node<Integer> resultNode = Node.build(
    MyNode.class,
    MyNode.D.DEP1, node1,
    MyNode.D.DEP2, node2);
```

Or the input can even be a failure, for example:

```java
Node<Integer> resultNode = Node.build(
    MyNode.class,
    MyNode.D.DEP1, node1,
    MyNode.D.DEP2, node2,
    MyNode.D.DEP3, Node.fail(new Exception()));
```

You can always implement a raw node without these enum based dependencies, and instantiate it with a normal constructor. You can see many such examples in the "Boolean operations" section of "Node Transformations".

```java
Node<A> aNode = new SomeSpecialNode(inputNode1, inputNode2);
```

### Executing the Dependency Graph

A node (or a dependency tree of nodes) doesn't get executed automatically after they are constructed. The instantiation of the node only defines the logic dependency, but nothing has executed yet. To start running, you need to call `apply()` on nodes:

```java
Future<A> aFuture = aNode.apply();
```

This creates a Future of the node, which you can wait on. This will trigger a recursive execution (an `apply()` call) of all its dependencies and their dependencies. You start from the root of a tree and it eventually reaches all leaves, which are inputs. Only the nodes reachable by walking the dependency links will be executed.

You can just pass this `Future` object to anywhere it's needed, like inside the `processRequest()` method of your server in the example at the beginning. If you want to block on it and get its value, you can call:

```java
A a = Await.result(aNode.apply(), Duration.ofSeconds(2));
```

Another way is to directly call `emit()` on node object, this is same as two steps above combined, but you can't specify a timeout value.

```java
A a = aNode.emit();
```

### Sinks

As mentioned above, only the node reachable by the dependency link from an executing node will ever be executed. However, there are situations where we want a node to execute but no one is waiting for its result (like you want to start and forget a logging process after a request has been processed, but you can respond without waiting for that logging work to be done). You can implement this logic with Sinks, a set of nodes you attach to another node which gets executed *after* it has finished. It's like a reverse dependency.

```java 
Node<Integer> resultNode = Node.builder(MyNode.class)
  .withSinkNodes(s1, s2, ...)
  .withDependencies(
    MyNode.D.DEP1, node1,
    MyNode.D.DEP2, node2,
    MyNode.D.DEP3, node3);
    
// You can also set the sink after the node is created
resultNode.setSinkNodes(s1, s2, ...);
```

### Deciders

Nodes can be selectively turned on and off by "Deciders", which is just a simple `Supplier` object that produces a true/false value. You can use this to implement runtime control of your nodes. Having supplier return `false` would cause node not to execute and return failure.

```java 
// You can add deciders and sinks as well
Node<Integer> resultNode = Node.builder(MyNode.class)
  .withDeciderSupplier(someDeciderSupplier)
  .withDependencies(
    MyNode.D.DEP1, node1,
    MyNode.D.DEP2, node2,
    MyNode.D.DEP3, node3);
```

### Nodes without a Body

You node doesn't need to always have a computation class, it can directly wrap a value or a `Future` object.

```java
// To wrap node around an immediate value, without any computation process:
Node<A> nodeA = Node.value(somethingOfTypeA);

// To wrap node around a Finagle Future of type A
Future<A> futureA = ...;
Node<A> nodeA = Node.wrapFuture(futureA);
```

As we mentioned above, a `Node` can be converted to a `Future` by just calling `apply()`:

```java
Node<A> nodeA = ...;
Future<A> futureA = nodeA.apply();
```

A future may fail and return exception, if it's used as a required dependency of another node, it would fail that node. Of course you can make it an optional dependency, but it may not always be feasible when you are calling some existing node code. You can make create a "safe" node masking `Future`'s failure. All failures will simply become `null` result.

```java
Node<A> nodeA = ...;  // this may fail
Future<A> futureA = nodeA.toFutureSafe();
```

#### Node Literals

There are some node literals for your convenience.

```java
Node.TRUE           // Node<Boolean> with True value
Node.FALSE          // Node<Boolean> with False value
Node.NULL_NODE      // typeless Node with a null value
Node.<T>noValue()   // typed Node with a null value

// To create a literal failed node
Node.fail(Throwable t)
```

### Subgraph

Putting your whole graph in a single file is probably not convenient. Subgraph  provides a way to organize your graph. Subgraph is a subset of the graph (duh), its inputs/outputs consist of the union of all inputs/outputs of the nodes at the boundary, so it can have more than 1 input and more than 1 output.

Subgraph doesn't have any effect on the execution of the nodes, it's just a way to organize nodes into a more reusable way. Below is a recommended style to implement Subgraphs.

```java
public class MySubgraph extends Subgraph {
  public final Node<Type1> exposedNode1; 
  public final Node<Type2> exposedNode2; 

  public MySubgraph(Node<A> inputNode1, 
                    Node<B> inputNode2,
                    Node<C> inputNode3) {
                    
    // ... all the wiring
    
    this.exposedNode1 = ... 
    this.exposedNode2 = ...
 
    // only needed if you need to generate DOT graph
    markExposedNodes();
  }
}
 
// Constructing two copies of the same subgraph, using different inputs:
MySubgraph s1 = new MySubgraph(node1, node2, node3);
MySubgraph s2 = new MySubgraph(node4, node5, node6);

// Access its produced outputs referencing the public members:
//   s1.exposedNode1
//   s2.exposedNode2
```

The suggested style is:

* Subclass from Subgraph
* All input nodes as constructor arguments
* All output nodes as public member variables
* You can also have other non-node input to configure the subgraph and provide other information.

The existence of subgraphs is transparent to the final dependency graph. You can always flatten a subgraph at its caller side, or split a subgraph into more smaller subgraphs if that helps with your modularity.

The call to the `markExposedNodes()` method at the end is purely for bookkeeping, this will help you generate better visualization/debug message, but it has no impact on the execution. Even if you forget to call it, it not a big deal.

### Node Transformations

For nodes with only a single input/dependency, it's cumbersome to create a whole new node class. We provide many syntactic sugar for you to do this kind of transformations easily.

#### map and flatMap

You can map a node of type A to another type B or even the Future of B `Future<B>` using vairous mapping methods. You can provide either a `NamedFunction` or a Java 8 lambda method for the logic:

```java
// To convert the node of one type to another, synchronously
Node<B> bNode = aNode.map(namedFunction);      // map with a NamedFunction<A,B>

Node<B> bNode = aNode.mapOnSuccess(func);   // only when aNode is successfully done, or emit null
Node<B> bNode = aNode.mapWithDeciderSupplier(ds, func);   // only when decider is on
 
// To convert the node of one type to another, asynchronously
Node<B> bNode = aNode.flatMap(func);   // where func is an instance of NamedFunction<A, Future<B>>
Node<B> bNode = aNode.flatMapOnSuccess(func);   // only when aNode is successfully done, or emit null
Node<B> bNode = aNode.flatMapWithDeciderSupplier(ds, func);   // only when aNode is successfully done, or emit null
 
// To get a boolean value out of node:
Node<Boolean> booleanNode = aNode.predicate(pred);  // where pred is a NamedPredicate<A>
 
// Java 8 support: you can also use Java 8 function in all above cases, except that you need to provide a name
// as the first argument. The name is only used for tracking and debugging, and ignored in production.
// For example:
Node<B> bNode = aNode.map("name", a -> doSomethingTo(a));
Node<B> bNode = aNode.map("name", a -> { ... });
Node<B> bNode = aNode.map("name", SomeClass::staticMethodOnA);
Node<B> bNode = aNode.map("name", someObject::instanceMethodOnA);
// same applies to .mapOnSuccess(), .flatMap(), etc
```

#### Bolean operations

We provide some convenient methods for manipulating nodes wiht boolean values:

```java
// Boolean nodes
Node<Boolean> booleaNode = AndNode.create(b1Node, b2Node, b3Node, ...);
Node<Boolean> booleaNode = AndNode.createLazy(b1Node, b2Node, b3Node, ...);  // short-circuiting, will not evaluate later nodes
Node<Boolean> booleaNode = OrNode.create(b1Node, b2Node, b3Node, ...);
Node<Boolean> booleaNode = OrNode.createLazy(b1Node, b2Node, b3Node, ...);  // short-circuiting, ditto
Node<Boolean> booleaNode = NotNode.create(b1Node);
```

Note that the "lazy" version would short-circuit the execution, not all input nodes are to be executed, only the first input is its real dependency.


#### Other transformations

We also have some other transformations that deal with 2 nodes.
 
```java
Node<A> nodeA = ...;
Node<B> nodeB = ...;

Node<List<A>> listNode = NodeUtils.asList(nodeA);
Node<Pair<A, B>> pairNode = NodeUtils.asPair(nodeA, nodeB);
Node<C> nodeC = NodeUtils.mapAsPair(
    nodeA, nodeB, "mapMethodName", (a, b) -> {...})
```

### Control Flow with Nodes

You can implement if-then-else logic with Nodes. There is also syntactic sugar to represent some other common condition checks, like whether a node has finished running successfully (more on "successful nodes" later).

Simple if-then-else control flow

```java 
// An if-else switch between two nodes:
Node<C> cNode = Node.ifThenElse(booleanNode, cNode1, cNode2);
Node<C> cNode = Node.ifThen(booleanNode, cNode1);  // equivalent to cNode2 being Node.noValue()

// Simpler if-else
Node<C> cNode = xNode.when(booleanNode);     // gets xNode if booleanNode emits true, or Node.noValue()
Node<C> cNode = xNode.unless(booleanNode);   // gets xNode if booleanNode emits false, or Node.noValue()
```
Success-based control flow

```java
// On success
Node<C> cNode = xNode.whenSuccess(anyNode);  // gets xNode if anyNode is success, or Node.noValue();
Node<C> cNode = xNode.orElse(yNode);  // gets xNode if xNode is a success, otherwise gets yNode
```

### Logging and Debug Messages

Nodes provides a simple framework for collecting debug messages in the asychrounous execution of the dependency graph.

(to be finished)

### Naming Conventions

There are some naming conventions for node code we'd like to suggest:

* Start your node class and subgraph class name with a verb, like `CreateRecordNode` or `ResolveGeoLocationSubgraph`.
* Name your node object as a noun and always end with `Node`, describing the product rather than the process, like `recordNode`.

### Testing

It's easy to write tests for nodes. Just instantiate them and test their output with specific inputs.

* if you have any optional input, make sure you have some tests without these inputs.
* for subgraphs, try to test the wiring, or the control flow implemented in the graph, rather than the logic of nodes inside. 


### Visualzation

You can visualize your node by generating a DOT graph text for it.

```java
// You can create a dummy unittest and run the code below. In each node/subgraph's unittests,
// it's likely we have already constructed an instance of it, so you can just call toDotGraph() directly.
 
// for nodes
String dot = mynode.toDotGraph();

// for subgraphs
String dot = mySubgraph.toDotGraph();

// You can save these strings to a file to be rendered by other tools.
FileUtils.writeStringToFile(new File("/path/to/file.dot", dot));
```

You can render the generated file using Graphviz ([download here](http://www.graphviz.org/Download_macos.php)). If you want to understand the format of the generated file, take a look at the [DOT language reference](http://www.graphviz.org/Documentation.php).

In the generated diagram, the type of the node is indicated by its shape, while the optionality of dependencies are represented by the edges connecting them.

For nodes:

* pink box: value nodes
* green-yellow trapezoid: transform node
* green-blue inverted trapezoid: predicate switch node (with "condition" and true/false branch)
* white square box: normal node
* gray double-edged box: service node

For edges (dependencies):

* solid: required dependency
* dashed: optional dependency
* label: dependency name, useful especially if you use enum style suggested above.

To make your graph look nice, always remember naming TransformNode (give it method-like name) and ValueNode (give it variable like name).


## Copyright and License

Copyright 2016 Twitter, Inc and other contributors

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
