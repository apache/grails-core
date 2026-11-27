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
package org.apache.grails.datamapping.core.querytail

import groovy.transform.CompileStatic

import jakarta.servlet.ServletRequest

import org.apache.grails.datastore.core.querytail.QueryTailRequestReader
import org.apache.grails.datastore.core.querytail.QueryTailRequestTarget

/**
 * Resolves a request as a web framework would, to the page a test keeps on the request in {@link #PAGE}, and reads
 * its path by the paths the test keeps in {@link #PATHS}: the tests of the capture, which has no web framework of its
 * own, find it with {@link ServiceLoader} as an application finds the reader of its framework. A request that names
 * {@link #FAILING} as its page makes the reader throw.
 */
@CompileStatic
class QueryTailStubRequestReader implements QueryTailRequestReader {

    /** The request attribute that names the page the request is resolved to. */
    static final String PAGE = QueryTailStubRequestReader.name + '.PAGE'

    /** The request attribute that maps each path within the application the target reads to what it reads it as. */
    static final String PATHS = QueryTailStubRequestReader.name + '.PATHS'

    /** The page that makes the reader fail, as a reader of a broken framework may. */
    static final String FAILING = 'failing'

    /** The request attribute the reader keeps its target in, so that it returns the same one until the page changes. */
    private static final String TARGET = QueryTailStubRequestReader.name + '.TARGET'

    /**
     * Binds what the reader resolves a request to on it.
     *
     * @param request the request
     * @param page the page it is resolved to
     * @param paths the paths within the application the target reads, and what it reads each as
     */
    static void resolve(ServletRequest request, String page, Map<String, String> paths = [:]) {
        request.setAttribute(PAGE, page)
        request.setAttribute(PATHS, paths)
    }

    @Override
    QueryTailRequestTarget read(QueryTailRequestReader.Attributes request) {
        Object page = request.getAttribute(PAGE)
        if (page == null) {
            return null
        }
        if (page == FAILING) {
            throw new UnsupportedOperationException('a reader that fails')
        }
        Object paths = request.getAttribute(PATHS)
        Object earlier = request.getAttribute(TARGET)
        if (earlier instanceof StubTarget && ((StubTarget) earlier).page == page && ((StubTarget) earlier).paths.is(paths)) {
            return (StubTarget) earlier
        }
        StubTarget target = new StubTarget(page.toString(), paths instanceof Map ? (Map<String, String>) paths : [:])
        request.setAttribute(TARGET, target)
        target
    }
}

/**
 * What {@link QueryTailStubRequestReader} resolves a request to, which counts how often it is asked to read a path.
 */
@CompileStatic
class StubTarget implements QueryTailRequestTarget {

    final String page
    final Map<String, String> paths
    int reads

    StubTarget(String page, Map<String, String> paths) {
        this.page = page
        this.paths = paths
    }

    @Override
    String describePath(String path) {
        reads++
        paths.get(path)
    }
}
