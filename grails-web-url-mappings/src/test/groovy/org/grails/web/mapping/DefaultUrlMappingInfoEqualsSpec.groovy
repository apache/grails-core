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

import spock.lang.Specification

import grails.web.mapping.UrlMappingData

/**
 * Two infos built from the same mapping values are equal, even when the values are
 * closures or names that the info's accessors resolve to something else.
 */
class DefaultUrlMappingInfoEqualsSpec extends Specification {

    private DefaultUrlMappingInfo info(Object controller, Object action, Object namespace = null, Object plugin = null, Object view = null) {
        new DefaultUrlMappingInfo(null, controller, action, namespace, plugin, view, [:], Stub(UrlMappingData), null)
    }

    void 'infos built from the same closure values are equal'() {
        given:
        Closure controller = { -> 'books' }
        Closure action = { -> 'list' }
        Closure namespace = { -> 'admin' }

        expect:
        info(controller, action, namespace) == info(controller, action, namespace)
        info(controller, action, namespace).hashCode() == info(controller, action, namespace).hashCode()
    }

    void 'infos built from different closures are not equal'() {
        expect:
        info({ -> 'books' }, 'list') != info({ -> 'books' }, 'list')
    }

    void 'infos built from the same names are equal'() {
        expect:
        info('book', 'list', 'admin', 'myPlugin') == info('book', 'list', 'admin', 'myPlugin')
    }

    void 'infos differing in any name are not equal'() {
        expect:
        info('book', 'list') != info('author', 'list')
        info('book', 'list') != info('book', 'show')
        info('book', 'list', 'one') != info('book', 'list', 'two')
        info('book', 'list', null, 'one') != info('book', 'list', null, 'two')
    }

    void 'view mappings compare by view'() {
        given:
        Closure view = { -> '/index' }

        expect:
        new DefaultUrlMappingInfo(view, [:], Stub(UrlMappingData), null) == new DefaultUrlMappingInfo(view, [:], Stub(UrlMappingData), null)
        new DefaultUrlMappingInfo('/a', [:], Stub(UrlMappingData), null) != new DefaultUrlMappingInfo('/b', [:], Stub(UrlMappingData), null)
    }

    void 'uri mappings compare by uri'() {
        given:
        Closure uri = { -> '/somewhere' }

        expect:
        new DefaultUrlMappingInfo(uri, Stub(UrlMappingData), null) == new DefaultUrlMappingInfo(uri, Stub(UrlMappingData), null)
        new DefaultUrlMappingInfo('/a', Stub(UrlMappingData), null) != new DefaultUrlMappingInfo('/b', Stub(UrlMappingData), null)
    }

    void 'an info is equal to itself and not to other types'() {
        given:
        DefaultUrlMappingInfo info = info('book', 'list')

        expect:
        info == info
        info != null
        info != 'book'
    }

}
