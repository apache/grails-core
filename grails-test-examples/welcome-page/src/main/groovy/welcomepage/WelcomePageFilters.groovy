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
package welcomepage

import groovy.transform.CompileStatic

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.core.Ordered

@CompileStatic
class PassThroughFilter implements Filter {
    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        chain.doFilter(request, response)
    }
}

@CompileStatic
class ServletAndUrlFilter extends PassThroughFilter {}

@CompileStatic
class UrlFilter extends PassThroughFilter {}

@CompileStatic
class MatchAllFilter extends PassThroughFilter {}

@CompileStatic
class DisabledFilter extends PassThroughFilter {}

@CompileStatic
class ErrorOnlyFilter extends PassThroughFilter {}

@CompileStatic
class ContainerFilter extends PassThroughFilter {}

/**
 * Adds a filter straight to the container, outside any Spring registration bean.
 */
@CompileStatic
class ContainerFilterInitializer implements ServletContextInitializer {
    @Override
    void onStartup(ServletContext servletContext) {
        servletContext.addFilter('containerFilter', new ContainerFilter())
                .addMappingForUrlPatterns(null, false, '/container/*')
    }
}

/**
 * Maps servletAndUrlFilter by URL only after urlFilter is registered, so the container holds
 * servletAndUrlFilter by servlet name, then urlFilter by URL, then servletAndUrlFilter by URL.
 */
@CompileStatic
class LateUrlMappingInitializer implements ServletContextInitializer, Ordered {
    @Override
    int getOrder() {
        Ordered.LOWEST_PRECEDENCE
    }

    @Override
    void onStartup(ServletContext servletContext) {
        servletContext.getFilterRegistration('servletAndUrlFilter').addMappingForUrlPatterns(null, true, '/*')
    }
}
