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
package org.grails.plugins.web.controllers;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;

import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.MultipartFilter;

/**
 * Resolves multipart requests and defers temporary-file cleanup while async processing is active.
 *
 * @since 8.0
 */
public class GrailsMultipartFilter extends MultipartFilter {

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        var multipartResolver = lookupMultipartResolver(request);
        var processedRequest = request;
        if (multipartResolver.isMultipart(processedRequest)) {
            processedRequest = multipartResolver.resolveMultipart(processedRequest);
        }

        try {
            filterChain.doFilter(processedRequest, response);
        }
        finally {
            if (processedRequest instanceof MultipartHttpServletRequest multipartRequest) {
                if (processedRequest.isAsyncStarted()) {
                    processedRequest.getAsyncContext().addListener(
                            new MultipartCleanupListener(multipartResolver, multipartRequest));
                }
                else {
                    multipartResolver.cleanupMultipart(multipartRequest);
                }
            }
        }
    }

    private static final class MultipartCleanupListener implements AsyncListener {

        private final MultipartResolver multipartResolver;
        private final MultipartHttpServletRequest multipartRequest;
        private final AtomicBoolean cleanedUp = new AtomicBoolean();

        private MultipartCleanupListener(
                MultipartResolver multipartResolver, MultipartHttpServletRequest multipartRequest) {
            this.multipartResolver = multipartResolver;
            this.multipartRequest = multipartRequest;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            cleanup();
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            cleanup();
        }

        @Override
        public void onError(AsyncEvent event) {
            cleanup();
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }

        private void cleanup() {
            if (cleanedUp.compareAndSet(false, true)) {
                multipartResolver.cleanupMultipart(multipartRequest);
            }
        }
    }
}
