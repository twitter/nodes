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

import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import com.twitter.util.Future;

/**
 * A value node based on supplier
 */
public class SupplierValueNode<Resp> extends ValueNode<Resp> {
  private final Supplier<Resp> supplier;
  private Resp suppliedValue;
  private volatile boolean alreadySupplied = false;

  /**
   * Create a value node from a supplier, the supplier .get() will only will called once!!
   */
  public SupplierValueNode(Supplier<Resp> supplier, String name) {
    super(null, name);
    this.supplier = Preconditions.checkNotNull(supplier);
  }

  @Override
  protected Future<Resp> evaluate() {
    return Future.value(getValue());
  }

  @Override
  public String getResponseClassName() {
    Resp value = getValue();
    return value == null ? "" : value.getClass().getSimpleName();
  }

  @Override
  public Resp emit() {
    return getValue();
  }

  /**
   * Get the value inside this node, if it's from a provider, the provider will only be called
   * once.
   */
  private synchronized Resp getValue() {
    if (!alreadySupplied) {
      suppliedValue = supplier.get();
      alreadySupplied = true;
    }
    return suppliedValue;
  }

  static <T> SupplierValueNode<T> create(Supplier<T> valueSupplier, String name) {
    return new SupplierValueNode<>(valueSupplier, name);
  }

}
