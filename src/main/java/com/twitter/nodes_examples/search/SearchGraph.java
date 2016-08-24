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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.twitter.finagle.Service;
import com.twitter.nodes.Node;
import com.twitter.nodes.OptionalDep;
import com.twitter.nodes.ServiceNode;
import com.twitter.nodes.Subgraph;
import com.twitter.util.Future;

/**
 * The main graph for search workflow.
 */
public class SearchGraph extends Subgraph {
  // The exposed output node
  public final Node<SearchResponse> responseNode;

  /**
   * Construct a search graph using the request node as input.
   */
  public SearchGraph(Node<SearchRequest> requestNode) {
    // look up user score using UserScoreServiceNode, this step is independent from search
    Node<Double> userScoreNode =
        Node.ifThenElse(
            requestNode.map("hasUserId", r -> r.getUserId() > 0),
            new UserScoreServiceNode(
                requestNode.map("getUserId", SearchRequest::getUserId)),
            Node.value(0.0, "defaultUserScore"));

    // Search the index and find result ids for the given query
    Node<List<Long>> resultIdsNode = Node.build(
        SearchIndexNode.class,
        SearchIndexNode.D.QUERY, requestNode.map("getQuery", SearchRequest::getQuery),
        SearchIndexNode.D.NUM_RESULTS, requestNode.map("getNumResults",
            r -> r.getNumResults() > 0 ? r.getNumResults() : 10));

    // Hydrate the ids returned from the index
    Node<Map<Long, String>> hydrationMapNode = Node.build(
        HydrationNode.class,
        HydrationNode.D.ID_LIST, resultIdsNode,
        HydrationNode.D.PREFIX, Node.value("cool_prefix", "newPrefix"));

    // Combined the hydration information and the user score to create the final response
    this.responseNode = Node.build(
        BuildResponseNode.class,
        BuildResponseNode.D.USER_SCORE, userScoreNode,
        BuildResponseNode.D.RESULT_ID_LIST, resultIdsNode,
        BuildResponseNode.D.HYDRATION_MAP, hydrationMapNode)
        .when(requestNode.map("hasQuery", r -> !r.getQuery().isEmpty()));

    markExposedNodes();
  }

  // ------------- A bunch of mock service-like nodes ----------------

  /**
   * A mock search index node, only return ids for results
   */
  public static class SearchIndexNode extends Node<List<Long>> {
    public enum D {
      QUERY,
      NUM_RESULTS
    }

    @Override
    protected Future<List<Long>> evaluate() throws Exception {
      String query = getDep(D.QUERY);
      int numResult = getDep(D.NUM_RESULTS);
      List<Long> results = new ArrayList<>(numResult);
      for (int i = 0; i < numResult; ++i) {
        results.add((long) query.hashCode() + i);
      }
      return DelayedResponse.get().delay(results);
    }
  }

  /**
   * A mock hydration node, just create a map of strings keyed by long ids.
   */
  public static class HydrationNode extends Node<Map<Long, String>> {
    public enum D {
      ID_LIST,
      @OptionalDep PREFIX
    }

    @Override
    protected Future<Map<Long, String>> evaluate() throws Exception {
      List<Long> idList = getDep(D.ID_LIST);
      String prefix = getDep(D.PREFIX, "default_text");
      Map<Long, String> map = Maps.newHashMap();
      for (long id : idList) {
        if (id % 3 == 0) {
          continue;  // skip some entries
        }
        map.put(id, prefix + ":" + id);
      }
      return DelayedResponse.get().delay(map);
    }
  }

  /**
   * A node to build responses
   */
  public static class BuildResponseNode extends Node<SearchResponse> {
    public enum D {
      USER_SCORE,
      RESULT_ID_LIST,
      HYDRATION_MAP
    }

    @Override
    protected Future<SearchResponse> evaluate() throws Exception {
      Double userScore = getDep(D.USER_SCORE);
      List<Long> idList = getDep(D.RESULT_ID_LIST);
      Map<Long, String> hydrationMap = getDep(D.HYDRATION_MAP);

      // Create a list of results following the original order, but only keeping those
      // successfully hydrated.
      List<SearchResult> results = Lists.newArrayList();
      for (long id : idList) {
        String text = hydrationMap.get(id);
        if (text != null) {
          results.add(new SearchResult(id, text));
        } else {
          debugDetailed("Unable to create result with id=%d", id);
        }
      }

      SearchResponse response = new SearchResponse(results, userScore);
      // no delay here as this is just local non-async computation.
      return Future.value(response);
    }
  }

  /**
   * Another mocked service to get user score, a double value based on the user id. This shows
   * one possible usage of the ServiceNode.
   */
  public static class UserScoreServiceNode extends ServiceNode<Long, Double> {
    public UserScoreServiceNode(Node<Long> user) {
      super(ImmutableList.of(user));
    }

    @Override
    protected Long buildRequest() {
      return getDep(DefaultDependencyEnum.DEP0);  // get the first dependency
    }

    @Override
    protected Service<Long, Double> getService() {
      return new Service<Long, Double>() {
        @Override
        public Future<Double> apply(Long request) {
          return DelayedResponse.get().delay(request.longValue() / (request.longValue() + 100.0));
        }
      };
    }
  }
}
