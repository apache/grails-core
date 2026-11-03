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
package org.apache.grails.startup

import jakarta.servlet.DispatcherType
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.web.context.ConfigurableWebApplicationContext

/**
 * Answers for the startup progress from inside the application, once the embedded web server has the port,
 * with the {@link StartupProgressResponder}.
 *
 * <p>Grails runs plugin startup hooks and {@code BootStrap} classes after the web server starts, so without
 * this a progress page already open would see the application answer, and reload, before the application had
 * finished starting, and a browser opening the application would get pages from an application still running
 * {@code BootStrap}. It also serves the startup report for as long as the application runs.</p>
 *
 * <p>Like any filter it only sees requests that reach a servlet, which in a Grails application the dispatcher
 * servlet mapped to {@code /} guarantees. Everything that touches the Servlet API lives here, so the run
 * listener can decide the application is not a servlet application without loading it.</p>
 */
@CompileStatic
final class StartupProgressFilter implements Filter {

    private final StartupProgressResponder responder

    private final StartupProgressResponder.Takes takes

    private StartupProgressFilter(StartupProgressResponder responder, StartupProgressResponder.Takes takes) {
        this.responder = responder
        this.takes = takes
    }

    /**
     * Whether refreshing this context starts an embedded web server, rather than joining a servlet
     * container that is already running, as a deployed war does.
     */
    @PackageScope
    static boolean startsEmbeddedServer(ConfigurableApplicationContext context) {
        return context instanceof ConfigurableWebApplicationContext && ((ConfigurableWebApplicationContext) context).getServletContext() == null
    }

    /**
     * @param servesProgressPage whether browsers loading a page while the application starts get the progress page
     */
    @PackageScope
    static FilterRegistrationBean<Filter> registration(StartupProgressResponder responder, boolean servesProgressPage) {
        StartupProgressResponder.Takes takes = servesProgressPage ? StartupProgressResponder.Takes.PAGE_LOADS : StartupProgressResponder.Takes.OWN_PATHS_ONLY
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<Filter>(new StartupProgressFilter(responder, takes))
        registration.setName('grailsStartupProgressFilter')
        registration.addUrlPatterns('/*')
        registration.setDispatcherTypes(DispatcherType.REQUEST)
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE)
        return registration
    }

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse &&
                responder.respond(new ServletExchange((HttpServletRequest) request, (HttpServletResponse) response), takes)) {
            return
        }
        chain.doFilter(request, response)
    }

    /** A request to the web server, as the responder answers it. */
    private record ServletExchange(HttpServletRequest request, HttpServletResponse response) implements StartupProgressResponder.Exchange {

        @Override
        String method() {
            return request.getMethod()
        }

        @Override
        String path() {
            return request.getRequestURI()
        }

        @Override
        String query() {
            return request.getQueryString()
        }

        @Override
        String header(String name) {
            return request.getHeader(name)
        }

        @Override
        List<String> headers(String name) {
            return Collections.list(request.getHeaders(name))
        }

        @Override
        void setHeader(String name, String value) {
            response.setHeader(name, value)
        }

        @Override
        void send(int status, String contentType, byte[] body) throws IOException {
            response.setStatus(status)
            if (contentType != null) {
                response.setContentType(contentType)
            }
            response.setContentLength(body.length)
            if (body.length > 0) {
                response.getOutputStream().write(body)
            }
            response.flushBuffer()
        }
    }
}
