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

package com.twitter.nodes.examples.search;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.twitter.util.Future;
import com.twitter.util.Promise;

/**
 * A class to wrap an immediate value into a Future that delays its materialization by some random
 * time. This is just used to simulate the asynchronous computing environment. In the real server,
 * this delay could be caused by calls to external services, and computation in a different thread.
 */
public class DelayedResponse {
  private final ExecutorService executor = Executors.newScheduledThreadPool(5);
  private Random random = new Random();

  private static final DelayedResponse DELAY = new DelayedResponse();
  public static DelayedResponse get() {
    return DELAY;
  }

  public final <T> Future<T> delay(final T value) {
    final Promise<T> promise = new Promise<T>();
    executor.submit(() -> {
      try {
        Thread.sleep(500 + random.nextInt(500));  // somewhere between 0.5 ~ 1.0 second
        promise.become(Future.value(value));
      } catch (InterruptedException e) {
        Thread.interrupted();
      }
    });
    return promise;
  }
}
