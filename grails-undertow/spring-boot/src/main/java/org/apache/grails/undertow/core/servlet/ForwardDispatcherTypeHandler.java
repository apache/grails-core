/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.undertow.core.servlet;

import jakarta.servlet.DispatcherType;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.AttachmentKey;

/**
 * Restores the dispatcher type of a dispatch once a forward it made has returned, as Tomcat and Jetty do. Undertow
 * restores it once an include or a forward to a named servlet returns, but not once a forward to a path does, so the
 * request goes on reading as a forward. A filter that ran before the forward then reads the request, once its chain
 * has returned, as a forward too: {@code ServerHttpObservationFilter} takes an asynchronous dispatch that forwarded,
 * as one to an error handler does, for a plain one, and stops its observation there as well as when the asynchronous
 * processing completes, reporting the failure the request left twice.
 */
final class ForwardDispatcherTypeHandler implements HttpHandler {

    /** The dispatcher type of the dispatch in progress, which a dispatch made inside it restores when it returns. */
    private static final AttachmentKey<DispatcherType> DISPATCH = AttachmentKey.create(DispatcherType.class);

    private final HttpHandler next;

    ForwardDispatcherTypeHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (context == null) {
            this.next.handleRequest(exchange);
            return;
        }
        DispatcherType enclosing = exchange.putAttachment(DISPATCH, context.getDispatcherType());
        try {
            this.next.handleRequest(exchange);
        }
        finally {
            if (enclosing == null) {
                exchange.removeAttachment(DISPATCH);
            }
            else {
                exchange.putAttachment(DISPATCH, enclosing);
                context.setDispatcherType(enclosing);
            }
        }
    }

}
