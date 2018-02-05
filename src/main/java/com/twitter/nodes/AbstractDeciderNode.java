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

import com.twitter.util.Future;

/**
 * Base class for implementation of predicate nodes which make use of Decider.
 */
public abstract class AbstractDeciderNode extends Node<Boolean> {
  private final String featureName;

  protected AbstractDeciderNode(String name,
                                String featureName) {
    super(name);
    this.featureName = Preconditions.checkNotNull(featureName);
  }

  @Override
  protected final Future<Boolean> evaluate() {
    return isAvailable(featureName) ? Node.TRUE_FUTURE : Node.FALSE_FUTURE;
  }

  /**
   * Retrieves decider state for the given feature
   *
   * @param featureName name of decider.
   * @return decider result for given feature and recipient.
   */
  protected abstract boolean isAvailable(String featureName);
}
