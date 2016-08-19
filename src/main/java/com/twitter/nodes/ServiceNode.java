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

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import com.twitter.finagle.Service;
import com.twitter.finagle.ServiceNotAvailableException;
import com.twitter.logging.Logger;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;

/**
 * Wraps a service as a node.
 */
public abstract class ServiceNode<Req, Resp> extends NullableNode<Resp> {

  private static final Logger LOG = Logger.get(ServiceNode.class);

  protected final com.twitter.finagle.Service<Req, Resp> service;
  protected final String serviceName;

  protected ServiceNode(String serviceName,
                        @Nullable com.twitter.finagle.Service<Req, Resp> service,
                        List<Node> dependentNodes) {
    super("", dependentNodes);
    this.serviceName = serviceName;
    this.service = service;
    Preconditions.checkArgument(service != null,
        String.format("NODE[%s]: You must have a service", getName()));
    this.name = createName();  // fix the name
  }

  protected ServiceNode(com.twitter.finagle.Service<Req, Resp> service,
                        List<Node> dependentNodes) {
    this(null, service, dependentNodes);
  }

  public ServiceNode(com.twitter.finagle.Service<Req, Resp> service,
                     Node... nodes) {
    this(service, ImmutableList.copyOf(nodes));
  }

  /**
   * A special constructor where service type information is not provided.
   */
  protected ServiceNode(List<Node> dependentNodes) {
    super("", dependentNodes);
    this.serviceName = null;
    this.service = null;
    this.name = createName();  // fix the name
  }

  private String createName() {
    return String.format("%s[%s]",
        this.getClass().getSimpleName(),
        serviceName != null
            ? serviceName
            : service != null ? service.getClass().getSimpleName() : "?");
  }

  /**
   * Get service type of current node.
   * NOTE: Called at the evaluation time.
   * If your service type is computed from other dependent nodes, you can safely get the value of
   * your dependencies.
   */
  public String getServiceName() {
    return serviceName;
  }

  /**
   * Get service object using either the previously provided object, or look up in the service
   * directory.
   * NOTE: Called at the evaluation time.
   */
  @VisibleForTesting
  Service<Req, Resp> getService() {
    return service;
  }

  private static final Future NULL_FUTURE = Future.value(null);

  @Override
  protected Future<Resp> evaluate() {
    try {
      Req request = buildRequest();
      debugDetailed("built service request: %s", request);
      if (request != null) {
        com.twitter.finagle.Service<Req, Resp> serviceToUse = getService();
        if (serviceToUse == null) {
          return Future.exception(new ServiceNotAvailableException());
        }

        Future<Resp> responseFuture = serviceToUse.apply(request);
        // Add callbacks to track statistics or bookkeeping tasks on the subclasses.
        return responseFuture.addEventListener(new FutureEventListener<Resp>() {
          @Override
          public void onFailure(Throwable cause) { }

          @Override
          public void onSuccess(Resp value) {
            postResponse(value);
          }
        });
      } else {
        return buildResponseForNullRequest();
      }
    } catch (Exception e) {
      LOG.error(e, String.format(
          "Node [%s] threw exception while building service request:", getName()));
      return Future.exception(e);
    }
  }

  /**
   * Provides a callback to subclasses to inspect the successful responses.
   *
   * Service nodes can override this method to track statistics about the responses, but they
   * shouldn't have any code that depends on the actions called from this method (e.g.
   * transformations).
   *
   * Subclasses should override this method ONLY for bookkeeping.
   *
   * @param resp The successful service response.
   */
  protected void postResponse(Resp resp) { }

  /**
   * Builds a request for the underlying node service using the dependent Node values.
   *
   * @return service request
   */
  protected abstract Req buildRequest();

  /**
   * Builds a response if the request is null. For some non-optional service you may not want it to
   * fail upon seeing a null request, instead you want to return a default or dummy response.
   */
  protected Future<Resp> buildResponseForNullRequest() {
    return NULL_FUTURE;
  }

  /**
   * Creates a new node from the response of calling a service building the request from a node.
   */
  public static <Req, Resp> Node<Resp> wrapService(
      final Service<Req, Resp> service, final Node<Req> inputNode) {
    return new ServiceNode<Req, Resp>(service, inputNode) {
      @Override
      protected Req buildRequest() {
        return inputNode.emit();
      }
    };
  }

}
