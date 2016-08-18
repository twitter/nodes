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
 * A variant of {@code DebugMessageBuilder} that has no effect and could be safely shared.
 */
public final class DebugNoneMessageBuilder extends DebugMessageBuilder {
  public static final DebugNoneMessageBuilder SINGLETON = new DebugNoneMessageBuilder();

  public static DebugMessageBuilder getInstance() {
    return SINGLETON;
  }

  private DebugNoneMessageBuilder() {
    super(DebugLevel.DEBUG_NONE);
  }

  @Override
  public void reset() {
  }

  @Override
  public DebugMessageBuilder basic(String message, Object... args) {
    return this;
  }

  @Override
  public DebugMessageBuilder detailed(String message, Object... args) {
    return this;
  }

  @Override
  public DebugMessageBuilder verbose(String message, Object... args) {
    return this;
  }

  @Override
  public DebugMessageBuilder indent() {
    return this;
  }

  @Override
  public DebugMessageBuilder unindent() {
    return this;
  }

  @Override
  public void setIndentationLevel(int indentationLevel) {
  }

  @Override
  public boolean isEmpty() {
    return true;
  }
}
