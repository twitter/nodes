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

import java.util.function.Function;

/**
 * A function with a name.
 *
 * Use this to create static constant functions to be used in node transformations, etc.
 */
public abstract class NamedFunction<A, B> implements Function<A, B> {
  private final String name;

  public NamedFunction(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public static <A, B> NamedFunction<A, B> create(
      String name, java.util.function.Function<A, B> func) {
    return new NamedFunction<A, B>(name) {
      @Override
      public B apply(A a) {
        return func.apply(a);
      }
    };
  }
}
