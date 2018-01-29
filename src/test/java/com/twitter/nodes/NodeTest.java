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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.junit.Test;

import com.twitter.nodes.utils.DeciderSupplier;
import com.twitter.util.Await;
import com.twitter.util.Future;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NodeTest extends NodeTestBase {

  /**
   * A simple node that sums the value of three other nodes in a specific way
   */
  static class SumNode extends Node<Integer> {
    public enum D {
      FIRST,
      SECOND,
      THIRD,
      @OptionalDep FOURTH_OP
    }

    @Override
    protected Future<Integer> evaluate() throws Exception {
      int sum = ((Integer) getRawDep(D.FIRST))
          + 10 * ((Integer) getRawDep(D.SECOND))
          + 100 * ((Integer) getRawDep(D.THIRD));
      Optional<Integer> fourth = getRawDep(D.FOURTH_OP);
      if (fourth.isPresent()) {
        sum += 1000 * fourth.get();
      }
      return Future.value(sum);
    }
  }

  @Test
  public void testSumNodeSimple() throws Exception {
    Node<Integer> dep1 = Node.value(1);
    Node<Integer> dep2 = Node.value(2);
    Node<Integer> dep3 = Node.value(3);
    Node<Integer> dep4 = Node.value(4);
    Node<Integer> sumNode = Node.builder(SumNode.class)  // works without D.class given!
        .withDependencies(
            SumNode.D.FIRST, dep1,
            SumNode.D.SECOND, dep2,
            SumNode.D.THIRD, dep3,
            SumNode.D.FOURTH_OP, dep4);
    Await.result(sumNode.apply());

    assertEquals(4, sumNode.getAllDependencies().size());
    assertEquals(1, (int) sumNode.getDep(SumNode.D.FIRST));
    assertEquals(2, (int) sumNode.getDep(SumNode.D.SECOND));
    assertEquals(3, (int) sumNode.getDep(SumNode.D.THIRD));
    assertEquals(4, (int) sumNode.getDep(SumNode.D.FOURTH_OP));
    assertEquals(4321, (int) sumNode.emit());
  }

  @Test
  public void testSumNodeSimpleFailure() throws Exception {
    Node<Integer> dep1 = Node.value(1);
    Node<Integer> dep2 = Node.value(2);
    Node<Integer> dep3 = Node.fail(new IOException());
    Node<Integer> dep4 = Node.value(4);
    Node<Integer> sumNode = Node.builder(SumNode.class)  // works without D.class given!
        .withDependencies(
            SumNode.D.FIRST, dep1,
            SumNode.D.SECOND, dep2,
            SumNode.D.THIRD, dep3,
            SumNode.D.FOURTH_OP, dep4);
    assertNodeThrow(sumNode);
  }

  @Test
  public void testSumNodeSimpleWithPrewrapping() throws Exception {
    Node<Integer> dep1 = Node.value(1);
    Node<Integer> dep2 = Node.value(2);
    Node<Integer> dep3 = Node.value(3);
    Node<Optional<Integer>> dep4 = Node.optional(Node.value(4));
    Node<Integer> sumNode = Node.builder(SumNode.class)
        .withDependencies(
            SumNode.D.FIRST, dep1,
            SumNode.D.SECOND, dep2,
            SumNode.D.THIRD, dep3,
            SumNode.D.FOURTH_OP, dep4);
    Await.result(sumNode.apply());
    assertEquals(4321, (int) sumNode.emit());
  }

  @Test
  public void testMissingDependency() throws Exception {
    // Missing required one is not OK
    try {
      Node.builder(SumNode.class)
          .withDependencies(
              SumNode.D.FIRST, Node.value(1),
              SumNode.D.FOURTH_OP, Node.value(4));
      fail();
    } catch (Exception e) {
      assertTrue(e instanceof IllegalArgumentException);
      assertTrue(e.getMessage().contains("SECOND, THIRD"));
    }

    // Missing optional one is ok
    Node<Integer> resultNode = Node.builder(SumNode.class)
        .withDependencies(
            SumNode.D.FIRST, Node.value(1),
            SumNode.D.SECOND, Node.value(2),
            SumNode.D.THIRD, Node.value(3));
    assertEquals(321, (int) resultFromNode(resultNode));
  }

  @Test
  public void testSumNodeWithOptional() throws Exception {
    Node<Integer> dep1 = Node.value(1);
    Node<Integer> dep2 = Node.value(2);
    Node<Integer> dep3 = Node.value(3);
    Node<Integer> dep4 = Node.noValue();
    Node<Integer> sumNode = Node.builder(SumNode.class)
        .dependsOn(SumNode.D.FIRST, dep1)
        .dependsOn(SumNode.D.SECOND, dep2)
        .dependsOn(SumNode.D.THIRD, dep3)
        .dependsOn(SumNode.D.FOURTH_OP, dep4)
        .build();
    Await.result(sumNode.apply());

    assertEquals(4, sumNode.getAllDependencies().size());
    assertEquals(1, (int) sumNode.getDep(SumNode.D.FIRST));
    assertEquals(2, (int) sumNode.getDep(SumNode.D.SECOND));
    assertEquals(3, (int) sumNode.getDep(SumNode.D.THIRD));
    assertNull(sumNode.getDep(SumNode.D.FOURTH_OP));
    assertEquals(321, (int) sumNode.emit());
  }

  @Test
  public void testSumNodeWithOptionalMissing() throws Exception {
    Node<Integer> dep1 = Node.value(1);
    Node<Integer> dep2 = Node.value(2);
    Node<Integer> dep3 = Node.value(3);
    Node<Integer> sumNode = Node.builder(SumNode.class)
        .dependsOn(SumNode.D.FIRST, dep1)
        .dependsOn(SumNode.D.SECOND, dep2)
        .dependsOn(SumNode.D.THIRD, dep3)
        .build();
    Await.result(sumNode.apply());

    assertEquals(4, sumNode.getAllDependencies().size());
    assertEquals(1, (int) sumNode.getDep(SumNode.D.FIRST));
    assertEquals(2, (int) sumNode.getDep(SumNode.D.SECOND));
    assertEquals(3, (int) sumNode.getDep(SumNode.D.THIRD));
    assertNull(sumNode.getDep(SumNode.D.FOURTH_OP));
    assertEquals(321, (int) sumNode.emit());
  }

  @Test
  public void testOptional() throws Exception {
    Node<Integer> node = new PlusOneNode(Node.optional(Node.<Integer>noValue()));
    Integer result = Await.result(node.apply());
    System.out.println("-- result = " + result);
  }

  static class PlusOneNode extends Node<Integer> {
    Node<Optional<Integer>> anotherNode;

    PlusOneNode(Node<Optional<Integer>> anotherNode) {
      super(anotherNode);
      this.anotherNode = anotherNode;
    }

    @Override
    protected Future<Integer> evaluate() throws Exception {
      Optional<Integer> another = anotherNode.emit();
      if (another.isPresent()) {
        return Future.value(another.get() + 1);
      } else {
        return Future.value(0);
      }
    }
  }

  public static final DeciderSupplier ALWAYS_TRUE = new DeciderSupplier("always_true") {
    @Override
    public Boolean get() {
      return true;
    }
  };
  public static final DeciderSupplier ALWAYS_FALSE = new DeciderSupplier("always_true") {
    @Override
    public Boolean get() {
      return false;
    }
  };

  @Test
  public void testDecider() throws Exception {
    {
      // Required node, with decider
      Node<Boolean> requiredNode = Node.value(true);
      requiredNode.setDeciderSupplier(DeciderSupplier.ALWAYS_FALSE);
      assertNull(resultFromNode(requiredNode));
    }

    {
      // Optional node, absent decider supplier
      Node<Optional<Boolean>> optionalNode = Node.optional(Node.value(true));
      optionalNode.setDeciderSupplier(Optional.<DeciderSupplier>absent());
      assertEquals(Optional.of(true), resultFromNode(optionalNode));
    }

    {
      // Optional node with false decider
      Node<Optional<Boolean>> optionalNode = Node.optional(Node.value(true));
      optionalNode.setDeciderSupplier(DeciderSupplier.ALWAYS_FALSE);
      assertEquals(Optional.<Boolean>absent(), resultFromNode(optionalNode));
    }

    {
      // Optional node with true decider
      Node<Optional<Boolean>> optionalNode = Node.optional(Node.value(true));
      optionalNode.setDeciderSupplier(DeciderSupplier.ALWAYS_TRUE);
      assertEquals(Optional.of(true), resultFromNode(optionalNode));
    }

    {
      // Map with true decider
      Node<String> resultNode = Node.value("x")
          .mapWithDeciderSupplier("map", DeciderSupplier.ALWAYS_TRUE, x -> "[" + x + "]");
      assertEquals("[x]", resultFromNode(resultNode));
    }

    {
      // Map with false decider
      Node<String> resultNode = Node.value("x")
          .mapWithDeciderSupplier("map", DeciderSupplier.ALWAYS_FALSE, x -> "[" + x + "]");
      assertNull(resultFromNode(resultNode));
    }

    {
      // flatMap with true decider
      Node<String> resultNode = Node.value("x")
          .flatMapWithDeciderSupplier(DeciderSupplier.ALWAYS_TRUE,
              NamedFunction.create("map", x -> Future.value("[" + x + "]")));
      assertEquals("[x]", resultFromNode(resultNode));
    }

    {
      // flatMap with false decider
      Node<String> resultNode = Node.value("x")
          .flatMapWithDeciderSupplier(DeciderSupplier.ALWAYS_FALSE,
              NamedFunction.create("map", x -> Future.value("[" + x + "]")));
      assertNull(resultFromNode(resultNode));
    }
  }

  @Test
  public void testMap() throws Exception {
    final AtomicInteger functionRuns = new AtomicInteger(0);
    NamedFunction<Integer, String> func = new NamedFunction<Integer, String>("name") {
      @Override
      public String apply(Integer i) {
        functionRuns.incrementAndGet();
        Preconditions.checkNotNull(i, "Input to map function cannot be null");
        return "number " + i;
      }
    };
    Node<String> mappedNode = Node.value(100).map(func);
    assertEquals("number 100", resultFromNode(mappedNode));
    assertEquals(1, functionRuns.get());

    // Function should run for null values since ValueNode is a nullable node
    mappedNode = Node.<Integer>noValue().map(func);
    try {
      resultFromNode(mappedNode);
      fail();
    } catch (Exception e) {
      assertTrue(e instanceof RuntimeException);
      assertTrue(Await.result(mappedNode.apply().liftToTry()).isThrow());
      assertEquals(2, functionRuns.get());
    }

    // Function should NOT run for null values when mapOnSuccess is used
    mappedNode = Node.<Integer>noValue().mapOnSuccess(func);
    assertNull(resultFromNode(mappedNode));
    assertEquals(2, functionRuns.get());

    // Function should not run for non-nullable nodes that return null
    mappedNode = new Node<Integer>() {
      @Override
      protected Future<Integer> evaluate() throws Exception {
        return Future.value(null);
      }
    }.map(func);
    try {
      resultFromNode(mappedNode);
      fail();
    } catch (Exception e) {
      assertTrue(e.getMessage().contains(
          "evaluate() returned Future.value(null) but the node is not marked as Nullable"));
      assertTrue(Await.result(mappedNode.apply().liftToTry()).isThrow());
      assertEquals(2, functionRuns.get());
    }

    // Function should not run on node with an exception
    mappedNode = Node.wrapFuture(Future.<Integer>exception(new Exception("Test Exception"))).map(func);
    try {
      resultFromNode(mappedNode);
      fail();
    } catch (Exception e) {
      assertEquals("Test Exception", e.getMessage());
      assertTrue(Await.result(mappedNode.apply().liftToTry()).isThrow());
      assertEquals(2, functionRuns.get());
    }
  }

  @Test
  public void testFlatMap() throws Exception {
    Node<Integer> first = Node.value(100);
    Node<String> second = first.flatMap(new NamedFunction<Integer, Future<String>>("func") {
      @Override
      public Future<String> apply(Integer i) {
        return Future.value("future " + i);
      }
    });
    assertEquals("future 100", resultFromNode(second));
  }

  @Test
  public void testFlatMap2() throws Exception {
    Node<Integer> first = Node.value(100);
    Node<Integer> second = Node.value(200);
    Node<Integer> flatMappedNode = Node.flatMap2("FlatMap2", first, second,
            (result1, result2) -> Future.value(result1 + result2));

    assertEquals(new Integer(300), resultFromNode(flatMappedNode));
  }

  @Test
  public void testFlatMap3() throws Exception {
    Node<Integer> first = Node.value(100);
    Node<Integer> second = Node.value(200);
    Node<Integer> third = Node.value(300);
    Node<Integer> flatMappedNode = Node.flatMap3("FlatMap3", first, second, third,
            (result1, result2, result3) -> Future.value(result1 + result2 + result3));

    assertEquals(new Integer(600), resultFromNode(flatMappedNode));
  }

  @Test
  public void testFlatMap4() throws Exception {
    Node<Integer> first = Node.value(100);
    Node<Integer> second = Node.value(200);
    Node<Integer> third = Node.value(300);
    Node<Integer> fourth = Node.value(400);
    Node<Integer> flatMappedNode = Node.flatMap4("FlatMap4", first, second, third, fourth,
            (result1, result2, result3, result4) -> Future.value(result1 + result2 + result3 + result4));

    assertEquals(new Integer(1000), resultFromNode(flatMappedNode));
  }

  @Test
  public void testWhen() throws Exception {
    Node<String> node = Node.value("condition was true");
    assertEquals("condition was true", resultFromNode(node.when(Node.TRUE)));
    assertNull(resultFromNode(node.when(Node.FALSE)));
  }

  @Test
  public void testUnless() throws Exception {
    Node<String> node = Node.value("condition was false");
    assertEquals("condition was false", resultFromNode(node.unless(Node.FALSE)));
    assertNull(resultFromNode(node.unless(Node.TRUE)));
  }

  @Test
  public void testWhenUnless() throws Exception {
    Node<String> node = Node.value("when was true and unless was false");
    assertEquals("when was true and unless was false", resultFromNode(node.when(Node.TRUE).unless(Node.FALSE)));
    assertNull(resultFromNode(node.when(Node.TRUE).unless(Node.TRUE)));
    assertNull(resultFromNode(node.when(Node.FALSE).unless(Node.FALSE)));
    assertNull(resultFromNode(node.when(Node.FALSE).unless(Node.TRUE)));
  }

  @Test
  public void testOrElse() throws Exception {
    Node<String> node = Node.value("foo");
    Node<String> elseNode = Node.value("node was null");

    assertEquals("foo", resultFromNode(node.orElse(elseNode)));

    assertEquals("node was null", resultFromNode(Node.<String>noValue().orElse(elseNode)));
    assertEquals("node was null",
        resultFromNode(Node.<String>fail(new Exception()).orElse(elseNode)));
    assertEquals("node was null", resultFromNode(node.when(Node.FALSE).orElse(elseNode)));
  }

  @Test
  public void testValueNode() throws Exception {
    Node<String> strNode = Node.value("test");
    assertEquals("test", strNode.emit());

    Node<String> supplierNode = Node.valueFromSupplier(new Supplier<String>() {
      private boolean called = false;

      @Override
      public String get() {
        if (called) {
          fail();
        }
        called = true;
        return "test";
      }
    }, "stringSupplierNode");
    assertEquals("test", supplierNode.emit());
    assertEquals("test", supplierNode.emit());
    assertEquals("test", supplierNode.emit());
  }

  @Test
  public void testNodeMapCollect() throws Exception {
    Map<Integer, Node<Double>> intToNodeDoubleMap = Maps.newHashMap();
    intToNodeDoubleMap.put(1, Node.value(100.0));
    intToNodeDoubleMap.put(2, Node.value(200.0));
    intToNodeDoubleMap.put(3, Node.value(300.0));

    Node<Map<Integer, Double>> intToDoubleMapNode = Node.collect(intToNodeDoubleMap);
    Map<Integer, Double> integerDoubleMap = resultFromNode(intToDoubleMapNode);

    assertEquals(integerDoubleMap.get(1), 100.0, 0);
    assertEquals(integerDoubleMap.get(2), 200.0, 0);
    assertEquals(integerDoubleMap.get(3), 300.0, 0);
  }

  @Test
  public void testNodeListCollect() throws Exception {
    List<Node<Integer>> intNodeList = Lists.newArrayList();
    intNodeList.add(Node.value(1));
    intNodeList.add(Node.value(2));
    intNodeList.add(Node.value(3));

    Node<List<Integer>> intListNode = Node.collect(intNodeList);
    List<Integer> intList = resultFromNode(intListNode);

    assertEquals(intList.get(0), 1, 0);
    assertEquals(intList.get(1), 2, 0);
    assertEquals(intList.get(2), 3, 0);

    // Test splitAndCollect()
    Node<List<String>> stringListNode = Node.splitAndCollect(
        intListNode,
        new NamedFunction<Integer, Node<String>>("toString") {
          @Override
          public Node<String> apply(Integer i) {
            return Node.value("str" + i);
          }
        });

    List<String> stringList = resultFromNode(stringListNode);

    assertEquals(stringList.get(0), "str1");
    assertEquals(stringList.get(1), "str2");
    assertEquals(stringList.get(2), "str3");
  }

  @Test
  public void testWaitOn() throws Exception {
    final List<Integer> store = Lists.newArrayList();

    final NamedFunction<Integer, Integer> STORE_INTEGER =
        new NamedFunction<Integer, Integer>("store-integer") {
          @Override
          public Integer apply(Integer value) {
            store.add(value);
            return value;
          }
        };

    Node<Integer> mark1 = Node.value(1).map(STORE_INTEGER);
    Node<Integer> mark2 = Node.value(2).map(STORE_INTEGER);
    Node<Integer> mark3 = Node.value(3).map(STORE_INTEGER);
    Node<Integer> last = Node.value(999).map(STORE_INTEGER);

    Node<Integer> afterWait = last.waitOn(mark1, mark2, mark3);
    Integer value = resultFromNode(afterWait);

    assertEquals(999, value.intValue());
    assertEquals(4, store.size());
    assertEquals(999, store.get(3).intValue());  // the last node should insert its value last
  }

  @Test
  public void testMapMultiple() throws Exception {
    Node<Integer> aNode = Node.value(1);
    Node<Integer> bNode = Node.value(2);
    Node<Integer> cNode = Node.value(3);
    Node<Integer> dNode = Node.value(4);

    Node<Integer> sum2 = Node.map2("add", aNode, bNode, (a, b) -> a + b);
    assertEquals(3, (int) resultFromNode(sum2));

    Node<Integer> sum3 = Node.map3("add", aNode, bNode, cNode, (a, b, c) -> a + b + c);
    assertEquals(6, (int) resultFromNode(sum3));

    Node<Integer> sum4 = Node.map4("add", aNode, bNode, cNode, dNode,
        (a, b, c, d) -> a + b + c + d);
    assertEquals(10, (int) resultFromNode(sum4));
  }
}
