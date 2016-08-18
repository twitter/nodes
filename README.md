[![Build Status](https://img.shields.io/travis/twitter/twitter-nodes/master.svg)](https://travis-ci.org/twitter/twitter-text) [![Maven Central](https://img.shields.io/maven-central/v/com.twitter/twitter-nodes.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.twitter%22%20AND%20a%3A%22twitter-nodes%22)

nodes
============================

Dependency graph implementation for async services in Java.

## Basics
Node is an asyncrhonous, reusable processing unit in Blender. It takes multiple inputs (dependencies) in form of Nodes, and produces an output value asyncrhonously. You can think it as a mixture of a Function and a Future (actually, Node extends Function0 Scala class). It's an asychronous function call with a given set of inputs.

## Tutorials

Here are some quick examples to create nodes.

```java
// A node that produces an integer as output 
public class MyNode extends Node<Integer> {  // NOTE: extend "NullableNode" if you may return Future.value(null) in evalute()
  public enum D {  // "D" is a convention
    DEP1,
    DEP2,
    @OptionalDep DEP3  // this dependency is optional, may be omitted
  }

  @Override
  protected Future<Integer> evaluate() throws Exception { 
    // To get the value of a dependency nodes, use getDependencyValue(key)
    Type1 value1 = getDependencyValue(D.DEP1);
    Type2 value2 = getDependencyValue(D.DEP2);

    // if it's an optional dependency as told by the @OptionalDep annotation above,
    // this getDepedencyValue() would return null. 
    Type3 value3 = getDependencyValueWithDefault(D.DEP3, someDefault);
    Integer result = compute(value1, value2, value3);
    return Future.value(result);
  }
}
```
To instantiate your node:

```java
Node<Type1> node1 = ...
Node<Type2> node2 = ...
Node<Type3> node3 = ...

// You can create a node and specify the dependencies one by one: 
Node<Integer> resultNode = Node.build(
    MyNode.class,
    MyNode.D.DEP1, node1,
    MyNode.D.DEP2, node2,
    MyNode.D.DEP3, node3);

// Or you can even omit some optional dependency:
Node<Integer> resultNode = Node.build(MyNode.class,
    MyNode.D.DEP1, node1,
    MyNode.D.DEP2, node2);
 
// You can add deciders and sinks as well
Node<Integer> resultNode = Node.builder(MyNode.class)
  .withDeciderSupplier(BlenderDecider.createDeciderSupplier("decider_key"))  // gate by decider
  .withSinkNodes(s1, s2, ...)     // calls .apply() on these nodes upon finishing resultNode
  .withDependencies(
    MyNode.D.DEP1, node1,
    MyNode.D.DEP2, node2,
    MyNode.D.DEP3, node3);
```

### Subgraph

Subgraph is a subset of the node dependency graph. Remember that a node is a unit that does direct computation using all its dependencies, but if you don't really want to use the dependencies, but just wiring them together, you need Subgraph. Here is an example.

```java
public class MySubgraph extends Subgraph {
  public Node<Type1> exposedNode1; 
  public Node<Type2> exposedNode2; 

  public MySubgraph(Node<A> inputNode1, Node<B> inputNode2, Node<C> inputNode3) {
    // ... all the wiring
    
    this.exposedNode1 = ...
 
    this.exposedNode2 = ...
 
    markExposedNodes();  // remember to always call this at the end of the constructor
  }
}
 
// To use the subgraph, you do this:
MySubgraph s = new MySubgraph(node1, node2, node3);
// you can use its produced nodes by:
//   s.exposedNode1
//   s.exposedNode2
```

So the common code pattern is:

* Subclass from Subgraph
* Have one or more public member variables of Node type, these are your exposed nodes, outsider access them directly through these references.
* Have a constructor that takes all the input nodes (and maybe other parameters) you want, construct the subgraph tree inside.

Note that subgraph is only a way to organize the node graph construction and make them reusable, it has no impact on the execution of the node tree. It's just a method of organizing code. You can always flatten a subgraph in the caller without changing the semantics.

The call to the markExposedNodes() method at the end is purely for bookkeeping, this will help you generate better visualization/debug message, but it has no impact on the execution. Even if you forget to call it, it not a big deal.

### Node Transformation

You can transform nodes from one type to another, by providing either a Java 8 Function object, or a NamedFunction or NamedPredicate that wraps a function.

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

// Boolean nodes
Node<Boolean> booleaNode = AndNode.create(b1Node, b2Node, b3Node, ...);
Node<Boolean> booleaNode = AndNode.createLazy(b1Node, b2Node, b3Node, ...);  // short-circuiting, will not evaluate later nodes
Node<Boolean> booleaNode = OrNode.create(b1Node, b2Node, b3Node, ...);
Node<Boolean> booleaNode = OrNode.createLazy(b1Node, b2Node, b3Node, ...);  // short-circuiting, ditto
Node<Boolean> booleaNode = NotNode.create(b1Node);
 
// NodeTransforms class: this class has a lot of convenient helper functions for node manipulations:
Node<A> nodeA = ...;
Node<B> nodeB = ...;
Node<List<A>> listNode = NodeUtils.asList(nodeA);
Node<Pair<A, B>> pairNode = NodeUtils.asPair(nodeA, nodeB);
Node<C> nodeC = NodeUtils.mapAsPair(nodeA, nodeB, "mapMethodName", (a, b) -> ```

### Control Flow with Nodes

You can implement if-then-else logic with Nodes. There is also syntactic sugar to represent some other common condition checks, like whether a node has finished running successfully (more on "successful nodes" later).

```java
 
// An if-else switch between two nodes:
Node<C> cNode = Node.ifThenElse(booleanNode, cNode1, cNode2);
Node<C> cNode = Node.ifThen(booleanNode, cNode1);  // equivalent to cNode2 being Node.noValue()
// Simpler if-else
Node<C> cNode = xNode.when(booleanNode);     // gets xNode if booleanNode emits true, or Node.noValue()
Node<C> cNode = xNode.unless(booleanNode);   // gets xNode if booleanNode emits false, or Node.noValue()
// On success
Node<C> cNode = xNode.whenSuccess(anyNode);  // gets xNode if anyNode is success, or Node.noValue();
Node<C> cNode = xNode.orElse(yNode);  // gets xNode if xNode is a success, otherwise gets yNode
{ ... });
...
```

### Under the Hood

TBD

### Suggested Practices

#### Naming Conventions
Start your node class and subgraph class name with a verb, like DoThisThingNode
name your node object as a noun, describing the product of the exectuion, like someResultNode.

#### Nullness
Try your best not to return null value (in form of Future.value(null)) in your node's *evaluate()* method. If you have to, the class has to inherit from *NullableNode*. Try not to return null value (in the form of Future.value(null)) in your node's evaluate() method. However, if you create your node from some helpers, they are already NullableNode: ValueNode, ServiceNode, TransformNode.

#### Dependencies
A node was constructed at the beginning of the program, but the execution of its *evaluate()* method is blocked until all dependencies are ready. So make sure you are actually using all dependencies. Node doesn't detect circular dependency.

Also, make use of *Subpgrah* whenever you find necessary, it provides modularity like a methods call in the synchronously executed code.

### Testing

Make sure that any nodes you are adding or modifying has a corresponding unit test for your change. 
For regular processing nodes, test as if it's a method, with different inputs and verify outputs.

* if you have optional input, make sure you have some tests without these inputs.
* this is the base of all our functionalities, try hard to really test things thoroughly at this level, so we don't need to repeat some test at some upper level.
* for subgraphs, test the wiring, or the control flow implemented in the node graph form. Sometimes it's hard to write a test without knowing any of the underlying logic, but we probably want to keep that a minimum.

If any service is involved (unavoidable when you work with subgraph), utilize ServiceDirectoryTestUtils to set up services with fixed responses or failures. If you want, you can also implement your own through a simple interface.

### Visualzation

You can visualize your node by generating a DOT graph text for it.

```java
// You can create a dummy unittest and run the code below. In each node/subgraph's unittests,
// it's likely we have already constructed an instance of it, so you can just call toDotGraph() directly.
 
// for nodes
String dot = mynode.toDotGraph();

// for subgraphs
String dot = mySubgraph.toDotGraph();

// a trick to save to file
FileUtils.writeStringToFile(new File("/path/to/file.dot"), dot);
```

Then you can just render the generated file using Graphviz.

* Download: http://www.graphviz.org/Download_macos.php
* DOT language reference: http://www.graphviz.org/Documentation.php

Each node in the graph is a node, the shape indicates its type, the edges connecting them indicates dependencies.

Some hints to read the graph:

* pink box: value nodes
* green-yellow trapezoid: transform node
* green-blue inverted trapezoid: predicate switch node (with "condition" and true/false branch)
* white square box: normal node
* gray double-edged box: service node

For edges:

* solid: required dependency
* dashed: optional dependency
* label: dependency name, useful especially if you use enum style suggested above.

To make your graph look nice, always remember naming TransformNode (give it method-like name) and ValueNode (give it variable like name).


## Copyright and License

Copyright 2016 Twitter, Inc and other contributors

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
