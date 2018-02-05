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

import com.twitter.logging.Logger;

/**
 * A class to build debug messages with different levels. We also duplicate debug messages with a
 * level > 2 to debug logs using slf4j.
 */
public class DebugMessageBuilder {
  /**
   * Same as {@link DebugLevel#DEBUG_NONE}.
   *
   * @deprecated use {@link DebugLevel#DEBUG_NONE} instead.
   */
  @Deprecated
  public static final int DEBUG_NONE = DebugLevel.DEBUG_NONE.getLevel();
  /**
   * Same as {@link DebugLevel#DEBUG_BASIC}.
   *
   * @deprecated use {@link DebugLevel#DEBUG_BASIC} instead.
   */
  @Deprecated
  public static final int DEBUG_BASIC = DebugLevel.DEBUG_BASIC.getLevel();
  /**
   * Same as {@link DebugLevel#DEBUG_DETAILED}.
   *
   * @deprecated use {@link DebugLevel#DEBUG_DETAILED} instead.
   */
  @Deprecated
  public static final int DEBUG_DETAILED = DebugLevel.DEBUG_DETAILED.getLevel();
  /**
   * Same as {@link DebugLevel#DEBUG_VERBOSE}.
   *
   * @deprecated use {@link DebugLevel#DEBUG_VERBOSE} instead.
   */
  @Deprecated
  public static final int DEBUG_VERBOSE = DebugLevel.DEBUG_VERBOSE.getLevel();
  /**
   * Same as {@link DebugLevel#DEBUG_VERBOSE_2}.
   *
   * @deprecated use {@link DebugLevel#DEBUG_VERBOSE_2} instead.
   */
  @Deprecated
  public static final int DEBUG_VERBOSE_2 = DebugLevel.DEBUG_VERBOSE_2.getLevel();

  /**
   * Same as {@link DebugLevel#DEBUG_VERBOSE_3}.
   *
   * @deprecated use {@link DebugLevel#DEBUG_VERBOSE_3} instead.
   */
  @Deprecated
  public static final int DEBUG_VERBOSE_3 = DebugLevel.DEBUG_VERBOSE_3.getLevel();

  // We use an indentation step of 2 blank spaces.
  private static final String INDENTATION_STEP = "  ";

  private static final Logger LOG = Logger.get(DebugMessageBuilder.class);

  /** Returns the Level corresponding to the given integer. */
  public static DebugLevel getDebugLevel(int level) {
    for (DebugLevel l : DebugLevel.values()) {
      if (level <= l.getLevel()) {
        return l;
      }
    }
    return DebugLevel.values()[DebugLevel.values().length - 1];  // return the last one
  }

  private final DebugLevel level;
  private int currentIndentationLevel;
  private final StringBuilder builder;

  /**
   * Creates a new DebugMessageBuilder instance.
   *
   * @deprecated use {@link DebugMessageBuilder#DebugMessageBuilder(DebugLevel)}
   * instead.
   */
  @Deprecated
  public DebugMessageBuilder(int debugLevel) {
    this(new StringBuilder(), debugLevel);
  }

  /**
   * Creates a new DebugMessageBuilder instance.
   *
   * @deprecated use {@link DebugMessageBuilder#DebugMessageBuilder(String,
   * DebugLevel)} instead.
   */
  @Deprecated
  public DebugMessageBuilder(String initString, int debugLevel) {
    this(new StringBuilder(initString), debugLevel);
  }

  /**
   * Creates a new DebugMessageBuilder instance.
   *
   * @deprecated use {@link DebugMessageBuilder#DebugMessageBuilder(StringBuilder,
   * DebugLevel)} instead.
   */
  @Deprecated
  public DebugMessageBuilder(StringBuilder builder, int debugLevel) {
    this(builder, getDebugLevel(debugLevel));
  }

  /** Creates a new DebugMessageBuilder instance. */
  public DebugMessageBuilder() {
    this(new StringBuilder(), DebugLevel.DEBUG_NONE);
  }

  /** Creates a new DebugMessageBuilder instance. */
  public DebugMessageBuilder(DebugLevel debugLevel) {
    this(new StringBuilder(), debugLevel);
  }

  /** Creates a new DebugMessageBuilder instance. */
  public DebugMessageBuilder(String initString, DebugLevel debugLevel) {
    this(new StringBuilder(initString), debugLevel);
  }

  /** Creates a new DebugMessageBuilder instance. */
  public DebugMessageBuilder(StringBuilder builder, DebugLevel level) {
    this.builder = (builder == null) ? new StringBuilder() : builder;
    this.level = level;
    setIndentationLevel(0);
  }

  /** Returns the integer associated with the debug level. */
  public int getDebugLevel() {
    return level.getLevel();
  }

  /** Returns the current debug level. */
  public DebugLevel getLevel() {
    return level;
  }

  /** Resets this builder. */
  public void reset() {
    builder.setLength(0);
    setIndentationLevel(0);
  }

  private DebugMessageBuilder appendDebug(
      DebugLevel msgLevel,
      final String message,
      Object... args) {
    if (!message.isEmpty()
        && (getDebugLevel() >= msgLevel.getLevel()
        || getDebugLevel() >= DebugLevel.DEBUG_DETAILED.getLevel())) {
      String formattedMessage = args.length == 0 ? message : String.format(message, args);
      if (getDebugLevel() >= msgLevel.getLevel()) {
        appendIndentation();
        builder.append(formattedMessage);
        builder.append("\n");
      }
    }
    return this;
  }

  private void appendIndentation() {
    for (int i = 0; i < currentIndentationLevel; ++i) {
      builder.append(INDENTATION_STEP);
    }
  }

  public DebugMessageBuilder basic(final String message, Object... args) {
    return appendDebug(DebugLevel.DEBUG_BASIC, message, args);
  }

  public DebugMessageBuilder detailed(final String message, Object... args) {
    return appendDebug(DebugLevel.DEBUG_DETAILED, message, args);
  }

  public DebugMessageBuilder verbose(final String message, Object... args) {
    return appendDebug(DebugLevel.DEBUG_VERBOSE, message, args);
  }

  public DebugMessageBuilder verbose2(final String message, Object... args) {
    return appendDebug(DebugLevel.DEBUG_VERBOSE_2, message, args);
  }

  public DebugMessageBuilder verbose3(final String message, Object... args) {
    return appendDebug(DebugLevel.DEBUG_VERBOSE_3, message, args);
  }

  public DebugMessageBuilder indent() {
    setIndentationLevel(currentIndentationLevel + 1);
    return this;
  }

  public DebugMessageBuilder unindent() {
    setIndentationLevel(currentIndentationLevel - 1);
    return this;
  }

  public void setIndentationLevel(int indentationLevel) {
    this.currentIndentationLevel = Math.max(0, indentationLevel);
  }

  public boolean isEmpty() {
    return builder.length() == 0;
  }

  public boolean isAtLeastLevel(DebugLevel cmpLevel) {
    return getDebugLevel() >= cmpLevel.getLevel();
  }

  public boolean isEnabled() {
    return getDebugLevel() >= DebugLevel.DEBUG_BASIC.getLevel();
  }

  public boolean isDetailed() {
    return getDebugLevel() >= DebugLevel.DEBUG_DETAILED.getLevel();
  }

  public boolean isVerbose() {
    return getDebugLevel() >= DebugLevel.DEBUG_VERBOSE.getLevel();
  }

  public boolean isVerbose2() {
    return getDebugLevel() >= DebugLevel.DEBUG_VERBOSE_2.getLevel();
  }

  public boolean isVerbose3() {
    return getDebugLevel() >= DebugLevel.DEBUG_VERBOSE_3.getLevel();
  }

  public static boolean isVerbose(DebugMessageBuilder debugMessageBuilder) {
    return debugMessageBuilder != null && debugMessageBuilder.isVerbose();
  }

  public static void verbose(DebugMessageBuilder debugMessageBuilder, String message) {
    if (debugMessageBuilder != null) {
      debugMessageBuilder.verbose(message);
    }
  }

  @Override
  public String toString() {
    return builder.toString();
  }
}
