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
package grails.plugin.springsecurity.web.filter

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.web.filter.GenericFilterBean

/**
 * Responds to every request with 503 Service Unavailable until the application has started,
 * that is, until the plugin has initialized and Spring Boot has published
 * <code>ApplicationStartedEvent</code>.
 */
@Slf4j
@CompileStatic
class StartupGateFilter extends GenericFilterBean implements ApplicationListener<ApplicationStartedEvent> {

    private boolean initialized
    private boolean started
    private volatile boolean opened

    /**
     * Called by the plugin at the end of <code>doWithApplicationContext()</code>.
     */
    synchronized void markInitialized() {
        initialized = true
        openIfReady()
    }

    synchronized void onApplicationEvent(ApplicationStartedEvent event) {
        started = true
        openIfReady()
    }

    boolean isOpen() {
        opened
    }

    void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (!opened) {
            log.debug('Rejecting request to {}, the application has not started yet', ((HttpServletRequest) req).requestURI)
            // set the status rather than calling sendError() to avoid an error dispatch
            ((HttpServletResponse) res).status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
            return
        }

        chain.doFilter(req, res)
    }

    private void openIfReady() {
        if (initialized && started) {
            opened = true
            log.debug('Application has started, accepting requests')
        }
    }
}
