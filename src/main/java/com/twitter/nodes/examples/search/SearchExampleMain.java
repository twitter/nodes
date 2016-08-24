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

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import com.google.common.io.Files;

import com.twitter.nodes.Node;
import com.twitter.nodes.utils.DebugLevel;
import com.twitter.nodes.utils.DebugManager;
import com.twitter.nodes.utils.DebugMessageBuilder;
import com.twitter.util.Await;
import com.twitter.util.Future;

/**
 * An example search engine server implemented with Nodes.
 */
public class SearchExampleMain {
  private static final Logger LOG = Logger.getLogger(SearchExampleMain.class.getSimpleName());

  public static void main(String[] args) {
    // Enable debugging, this is both for the collection of debug info and also for enabling the
    // DOT visualization.
    DebugManager.update(new DebugMessageBuilder(DebugLevel.DEBUG_DETAILED));

    // Create the input and the graph
    SearchRequest request = new SearchRequest("query", 10, 777L);
    SearchGraph searchGraph = new SearchGraph(Node.value(request));

    // Actually start execution
    Future<SearchResponse> responseFuture = searchGraph.responseNode.apply();

    // Wait for response and print results
    try {
      SearchResponse response = Await.result(responseFuture);
      System.out.println("Search response: " + response);
    } catch (Exception e) {
      LOG.warning("Exception thrown while waiting for results: " + e);
    }

    // Print the debug information
    System.out.println("Debug output:\n" + DebugManager.getDebugMessage());

    // Produce dependency graph visualization
    try {
      Files.write(searchGraph.toDotGraph().getBytes(), new File("graph.dot"));
    } catch (IOException e) {
      LOG.warning("Cannot write to local file");
    }
  }
}
