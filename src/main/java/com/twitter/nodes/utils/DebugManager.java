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

import com.google.common.annotations.VisibleForTesting;

import com.twitter.util.Local;

/**
 * DebugManager controls debug message building (via DebugMessageBuilder) on a per-request basis.
 */
public final class DebugManager {

  private static final Local<DebugMessageBuilder> DEBUG_MSG_BUILDER = new Local<>();

  static {
    resetForTest();
  }

  private DebugManager() {
  }

  /**
   * Update the request thread local with the builder to use for this request.
   */
  public static void update(DebugMessageBuilder builder) {
    DEBUG_MSG_BUILDER.update(builder);
  }

  /**
   * Clear all request specific thread locals. This should be called after the request is complete.
   */
  public static void clearLocals() {
    if (DEBUG_MSG_BUILDER.apply().isDefined()) {
      DEBUG_MSG_BUILDER.apply().get().reset();
    }
  }

  /**
   * This builder is a Finagle thread-local, which will be automatically preserved across Finagle
   * future callbacks.
   *
   * @return the {@code DebugMessageBuilder} for the current thread.
   */
  public static DebugMessageBuilder getDebugMessageBuilder() {
    return DEBUG_MSG_BUILDER.apply().get();
  }

  // Some helpers for convenience
  public static  DebugMessageBuilder basic(final String message, Object... args) {
    return getDebugMessageBuilder().basic(message, args);
  }

  public static DebugMessageBuilder detailed(final String message, Object... args) {
    return getDebugMessageBuilder().detailed(message, args);
  }

  public static DebugMessageBuilder verbose(final String message, Object... args) {
    return getDebugMessageBuilder().verbose(message, args);
  }

  public static DebugMessageBuilder verbose2(final String message, Object... args) {
    return getDebugMessageBuilder().verbose2(message, args);
  }

  public static DebugMessageBuilder verbose3(final String message, Object... args) {
    return getDebugMessageBuilder().verbose3(message, args);
  }

  private static boolean isDebugLevelAtLeast(DebugLevel level) {
    return getDebugMessageBuilder().getDebugLevel() >= level.getLevel();
  }

  public static boolean isAtLeastBasic() {
    return isDebugLevelAtLeast(DebugLevel.DEBUG_BASIC);
  }

  public static boolean isAtLeastDetailed() {
    return isDebugLevelAtLeast(DebugLevel.DEBUG_DETAILED);
  }

  public static boolean isAtLeastVerbose() {
    return isDebugLevelAtLeast(DebugLevel.DEBUG_VERBOSE);
  }

  public static boolean isAtLeastVerbose2() {
    return isDebugLevelAtLeast(DebugLevel.DEBUG_VERBOSE_2);
  }

  public static boolean isAtLeastVerbose3() {
    return isDebugLevelAtLeast(DebugLevel.DEBUG_VERBOSE_3);
  }

  public static String getDebugMessage() {
    return getDebugMessageBuilder().toString();
  }

  public static boolean isDebug() {
    return getDebugMessageBuilder().getLevel() != DebugLevel.DEBUG_NONE;
  }

  public static boolean isDebug(int debugLevel) {
    return debugLevel <= getDebugMessageBuilder().getDebugLevel();
  }

  public static int getDebugLevel() {
    return getDebugMessageBuilder().getDebugLevel();
  }

  public static boolean isDetailedEnabled() {
    return getDebugLevel() >= DebugMessageBuilder.DEBUG_DETAILED;
  }

  @VisibleForTesting
  public static void resetForTest() {
    update(DebugNoneMessageBuilder.getInstance());
  }

  @VisibleForTesting
  public static void resetForTest(DebugLevel level) {
    update(new DebugMessageBuilder(level));
  }
}
