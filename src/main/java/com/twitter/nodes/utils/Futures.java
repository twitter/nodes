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

import java.util.List;
import java.util.Map;

import scala.Tuple2;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.twitter.util.Await;
import com.twitter.util.Function;
import com.twitter.util.Function0;
import com.twitter.util.Future;
import com.twitter.util.Throw;
import com.twitter.util.Throwables;

/**
 * Some utility functions related to Future handle
 */
public final class Futures {
  private Futures() {}

  /**
   * Check if a future has completed with success
   */
  public static boolean completedWithSuccess(Future<?> future) {
    try {
      return future.isDefined() && Await.result(future.liftToTry()).isReturn();
    } catch (Exception e) {
      return com.twitter.util.Throwables.unchecked(e);
    }
  }

  /**
   * Check if a future has completed with failure
   */
  public static boolean completedWithFailure(Future<?> future) {
    try {
      return future.isDefined() && Await.result(future.liftToTry()).isThrow();
    } catch (Exception e) {
      return com.twitter.util.Throwables.unchecked(e);
    }
  }

  public static Throwable getException(Future future) {
    Preconditions.checkState(completedWithFailure(future));
    return ((Throw) future.poll().get()).e();
  }

  /**
   * Wait for a map of futures and return a future of map, with the keys mapping to the value
   * returned from corresponding futures.
   */
  public static <K, V> Future<Map<K, V>> collect(final Map<K, Future<V>> futures) {
    try {
      List<Future<Tuple2<K, V>>> entries = Lists.newArrayListWithCapacity(futures.size());
      for (final Map.Entry<K, Future<V>> entry : futures.entrySet()) {
        entries.add(map(entry.getValue(), new Function0<Tuple2<K, V>>() {
          public Tuple2<K, V> apply() {
            try {
              return scala.Tuple2$.MODULE$.apply(entry.getKey(), Await.result(entry.getValue()));
            } catch (Exception e) {
              return Throwables.unchecked(e);
            }
          }
        }));
      }

      final Future<List<Tuple2<K, V>>> listFuture = Future.collect(entries);

      return map(listFuture, new Function0<Map<K, V>>() {
        public Map<K, V> apply() {
          Map<K, V> result = Maps.newHashMapWithExpectedSize(futures.size());
          try {
            for (Tuple2<K, V> pair : Await.result(listFuture)) {
              result.put(pair._1(), pair._2());
            }
            return result;
          } catch (Exception e) {
            return Throwables.unchecked(e);
          }
        }
      });
    } catch (Exception e) {
      return Throwables.unchecked(e);
    }
  }

  private static <A, R> Future<R> map(final Future<A> a, final Function0<R> f) {
    return a.map(new Function<A, R>() {
      public R apply(A unused) {
        return f.apply();
      }
    });
  }
}
