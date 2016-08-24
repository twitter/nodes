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

package com.twitter.nodes_examples.search;

/**
 * Search Request
 */
public class SearchRequest {
  private final String query;
  private final int numResults;
  private final long userId;

  public SearchRequest(String query, int numResults, long userId) {
    this.query = query;
    this.numResults = numResults;
    this.userId = userId;
  }

  public String getQuery() {
    return query;
  }

  public int getNumResults() {
    return numResults;
  }

  public long getUserId() {
    return userId;
  }
}
