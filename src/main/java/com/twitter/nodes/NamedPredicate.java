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

import java.util.function.Predicate;

import javax.annotation.Nullable;

/**
 * A named predicate
 *
 * Use this to create static constant predicates to be used in node predicate switches.
 * CHECKSTYLE:OFF EqualsHashCode
 */
public abstract class NamedPredicate<A> implements Predicate<A> {
  private final String name;

  public NamedPredicate(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return false;
  }

  public Node<Boolean> apply(Node<A> inputNode) {
    return inputNode.predicate(this);
  }

  public static <A> NamedPredicate<A> create(String name, Predicate<A> predicate) {
    return new NamedPredicate<A>(name) {
      @Override
      public boolean test(@Nullable A a) {
        return predicate.test(a);
      }
    };
  }
}
