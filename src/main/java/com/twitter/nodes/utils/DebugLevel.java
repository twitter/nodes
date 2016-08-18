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

/**
 * Enumerating the supported debug levels.
 *
 * The non-enum int levels are deprecated, and still in use in legacy code.
 */
public enum DebugLevel {
  DEBUG_NONE(0),
  DEBUG_BASIC(1),
  DEBUG_DETAILED(2),
  DEBUG_VERBOSE(3),
  DEBUG_VERBOSE_2(4),
  DEBUG_VERBOSE_3(5);

  private final int level;

  DebugLevel(int level) {
    this.level = level;
  }

  public int getLevel() {
    return level;
  }
}
