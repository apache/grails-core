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
package grails.interceptors

import grails.artefact.Interceptor
import grails.web.mapping.UrlMappingInfo

/**
 * A Matcher is used to configure matching for {@link grails.artefact.Interceptor} instances
 *
 * @see grails.artefact.Interceptor#match(java.util.Map)
 *
 * @author Graeme Rocher
 * @since 3.0
 */
interface Matcher {

    String THROWABLE = 'org.grails.interceptors.THROWABLE'

    /**
     * Checks whether the given {@link UrlMappingInfo} matches
     *
     * @param info The {@link UrlMappingInfo} to check
     * @return True if it does match
     */
    boolean doesMatch(String uri, UrlMappingInfo info)

    /**
     * Perform the matches using the http method of the request instead of the UrlMappingInfo
     * @param uri
     * @param info
     * @param method
     * @return
     */
    boolean doesMatch(String uri, UrlMappingInfo info, String method)

    /**
     * Performs the match for a request deployed under a context path.
     *
     * <p>{@link grails.artefact.Interceptor#doesMatch(jakarta.servlet.http.HttpServletRequest)} canonicalizes the
     * request path once and calls this method for every matcher. The default implementation delegates to
     * {@link #doesMatch(String, UrlMappingInfo, String)} with the same path, so a custom matcher only needs to
     * implement this method if it wants to take the context path into account.
     *
     * @param uri The path of the request within the application: decoded, with matrix parameters removed and the
     * context path stripped, exactly as URL mappings route it
     * @param info The {@link UrlMappingInfo} matched for the request, or {@code null} if none was matched
     * @param method The HTTP method of the request
     * @param contextPath The context path of the request, empty when the application is deployed at the root
     * @return True if it does match
     * @since 8.0
     */
    default boolean doesMatch(String uri, UrlMappingInfo info, String method, String contextPath) {
        doesMatch(uri, info, method)
    }

    /**
     * Defines the match for the given arguments
     *
     * @param arguments A named argument map including one or more of the controller name, action name, namespace and method
     * @return This matcher
     */
    Matcher matches(Map arguments)

    /**
     * Indicate that this matcher should match all requests
     *
     * @return This matcher
     */
    Matcher matchAll()

    /**
     * Adds an exclusion for the given arguments
     *
     * @param arguments A named argument map including one or more of the controller name, action name, namespace and method
     *
     * @return This matcher
     */
    Matcher excludes(Map arguments)

    /**
     * Synonym for {@link Matcher#excludes(groovy.lang.Closure)}
     */
    Matcher except(Map arguments)

    /**
     * Adds an exclusion that is calculated by the given closure
     *
     * @param condition The condition, a closure which has full access to the properties of the {@link Interceptor}
     * @return This matcher
     */
    Matcher excludes(@DelegatesTo(Interceptor) Closure<Boolean> condition)

    /**
     * Checks whether the current matcher is a exclude matcher or not
     */
    boolean isExclude()
}
