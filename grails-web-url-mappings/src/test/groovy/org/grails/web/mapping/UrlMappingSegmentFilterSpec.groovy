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
package org.grails.web.mapping

import grails.web.mapping.AbstractUrlMappingsSpec

/**
 * Covers the segment-count pre-filter that lets a mapping skip candidate patterns before any regular
 * expression work. The filter must only ever remove patterns that provably cannot match, so which
 * mapping wins has to be identical with and without it.
 */
class UrlMappingSegmentFilterSpec extends AbstractUrlMappingsSpec {

    void 'a double wildcard still matches across any number of segments'() {
        given: 'a mapping whose ** token can span segments, so the segment count cannot rule it out'
        def holder = getUrlMappingsHolder {
            "/files/$path**"(controller: 'file', action: 'serve')
        }

        expect:
        holder.match(uri)?.actionName == 'serve'

        where:
        uri << ['/files/a', '/files/a/b', '/files/a/b/c/d/e']
    }

    void 'a trailing slash still matches, being one segment longer than the pattern'() {
        given:
        def holder = getUrlMappingsHolder {
            "/book/list"(controller: 'book', action: 'list')
        }

        expect:
        holder.match('/book/list')?.actionName == 'list'
        holder.match('/book/list/')?.actionName == 'list'
    }

    void 'a URI with the wrong segment count does not match'() {
        given:
        def holder = getUrlMappingsHolder {
            "/book/list"(controller: 'book', action: 'list')
        }

        expect:
        holder.match('/book') == null
        holder.match('/book/list/extra') == null
    }

    void 'the first declared mapping still wins when several could match'() {
        given: 'two mappings that both match the same URI, the specific one declared first'
        def holder = getUrlMappingsHolder {
            "/book/$id"(controller: 'book', action: 'specific')
            "/$controller/$action?/$id?"()
        }

        expect: 'declaration order decides, exactly as it did before the filter'
        holder.match('/book/42').actionName == 'specific'
    }

    void 'optional trailing tokens still match at every arity'() {
        given: 'one mapping that expands into several logical URLs of different segment counts'
        def holder = getUrlMappingsHolder {
            "/book/$action?/$id?"(controller: 'book')
        }

        expect: 'each arity is still reachable, so no logical URL was filtered out'
        holder.match('/book') != null
        holder.match('/book/show') != null
        holder.match('/book/show/42') != null

        and: 'and one segment too many still does not match'
        holder.match('/book/show/42/extra') == null
    }

    void 'matchAll returns the same mappings the scan would have produced'() {
        given: 'a URI matched by several mappings of differing segment counts'
        def holder = getUrlMappingsHolder {
            "/api/books/$id"(controller: 'book', action: 'show', method: 'GET')
            "/api/books/$id"(controller: 'book', action: 'update', method: 'PUT')
            "/api/$other"(controller: 'other', action: 'index')
        }

        when:
        def all = holder.matchAll('/api/books/42', 'GET')

        then: 'only the GET mapping of the right arity survives, in declaration order'
        all*.actionName == ['show']
    }
}
