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

class GroupDefaultsSpec extends AbstractUrlMappingsSpec {

    void 'inherits group defaults in child mappings'() {
        given: 'a group with shared namespace and controller defaults'
        def mappings = getUrlMappingsHolder {
            group('/api', namespace: 'api', controller: 'resource') {
                '/list'(action: 'list')
                '/show'(action: 'show')
            }
        }

        when: 'matching the list child mapping'
        def matches = mappings.matchAll('/api/list')

        then: 'the list mapping inherits the group defaults'
        matches.length == 1
        with(matches.first()) {
            namespace == 'api'
            controllerName == 'resource'
            actionName == 'list'
        }

        when: 'matching the show child mapping'
        matches = mappings.matchAll('/api/show')

        then: 'the show mapping inherits the group defaults'
        matches.length == 1
        with(matches.first()) {
            namespace == 'api'
            controllerName == 'resource'
            actionName == 'show'
        }
    }

    void 'allows child mappings to override group defaults'() {
        given: 'a group with controller defaults and an overriding child mapping'
        def mappings = getUrlMappingsHolder {
            group('/api', namespace: 'api', controller: 'defaultCtrl') {
                '/special'(controller: 'specialCtrl', action: 'handle')
                '/normal'(action: 'index')
            }
        }

        when: 'matching the overriding child mapping'
        def matches = mappings.matchAll('/api/special')

        then: 'the child mapping uses its explicit controller and action'
        matches.length == 1
        with(matches.first()) {
            namespace == 'api'
            controllerName == 'specialCtrl'
            actionName == 'handle'
        }

        when: 'matching the child mapping without an override'
        matches = mappings.matchAll('/api/normal')

        then: 'the child mapping inherits the group defaults'
        matches.length == 1
        with(matches.first()) {
            namespace == 'api'
            controllerName == 'defaultCtrl'
            actionName == 'index'
        }
    }

    void 'matches groups without defaults using explicit child mappings'() {
        given: 'a group without default namespace or controller values'
        def mappings = getUrlMappingsHolder {
            group('/legacy') {
                '/foo'(controller: 'foo', action: 'bar')
            }
        }

        when: 'matching the explicit child mapping'
        def matches = mappings.matchAll('/legacy/foo')

        then: 'the explicit child mapping resolves without inherited defaults'
        matches.length == 1
        with(matches.first()) {
            namespace == null
            controllerName == 'foo'
            actionName == 'bar'
        }
    }

    void 'inherits outer group defaults across nested groups'() {
        given: 'nested groups with defaults at different levels'
        def mappings = getUrlMappingsHolder {
            group('/community', namespace: 'community') {
                group('/topics', controller: 'topic') {
                    '/gallery'(action: 'gallery')
                    '/create'(action: 'create')
                }
            }
        }

        when: 'matching a child mapping inside the nested groups'
        def matches = mappings.matchAll('/community/topics/gallery')

        then: 'the child mapping inherits defaults from the outer and inner groups'
        matches.length == 1
        with(matches.first()) {
            namespace == 'community'
            controllerName == 'topic'
            actionName == 'gallery'
        }
    }
}
