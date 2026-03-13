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
package functionaltests.interceptors

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Integration tests for advanced interceptor matching patterns.
 *
 * Tests cover:
 * - Namespace-based matching
 * - HTTP method-based matching
 * - Action exclusion patterns
 * - Multiple match rules (OR logic)
 * - Combined matching criteria
 */
@Integration
class InterceptorAdvancedMatchingSpec extends Specification implements HttpClientSupport {

    def setup() {
        // Reset interceptor tracking before each test
        http('/api/advancedMatching/reset')
    }

    // ========== Namespace Matching Tests ==========

    def "test namespace interceptor matches controller in api namespace"() {
        when:
        def response = http('/api/advancedMatching/index')

        then:
        response.expectStatus(200)
        with(response.json()) {
            namespace == 'api'
            interceptors.contains('namespace:api')
        }
    }

    def "test namespace interceptor matches all actions in namespace"() {
        when:
        def response = http('/api/advancedMatching/list')

        then:
        response.expectStatus(200)
        with(response.json()) {
            action == 'list'
            interceptors.contains('namespace:api')
        }
    }

    // ========== HTTP Method Matching Tests ==========

    def "test method interceptor matches POST requests"() {
        when:
        def response = httpPostJson('/api/advancedMatching/save', [:])

        then:
        response.expectStatus(200)
        with(response.json()) {
            method == 'POST'
            interceptors.contains('method:POST')
        }
    }

    def "test method interceptor does not match GET requests"() {
        when:
        def response = http('/api/advancedMatching/list')

        then:
        response.expectStatus(200)
        !response.json().interceptors.contains('method:POST')
    }

    def "test method interceptor matches PUT requests as POST variant"() {
        when:
        def response = httpPutJson('/api/advancedMatching/update', [:])

        then:
        response.expectStatus(200)
        // PUT is not POST, so interceptor should not match
        !response.json().interceptors.contains('method:POST')
    }

    // ========== Action Exclusion Tests ==========

    def "test excludes interceptor does not match excluded index action"() {
        when:
        def response = http('/api/advancedMatching/index')

        then:
        response.expectStatus(200)
        with(response.json()) {
            action == 'index'
            !interceptors.contains('excludes:index,reset')
        }
    }

    def "test excludes interceptor matches non-excluded actions"() {
        when:
        def response = http('/api/advancedMatching/list')

        then:
        response.expectStatus(200)
        with(response.json()) {
            action == 'list'
            interceptors.contains('excludes:index,reset')
        }
    }

    def "test excludes interceptor matches show action"() {
        when:
        def response = http('/api/advancedMatching/show/123')

        then:
        response.expectStatus(200)
        with(response.json()) {
            action == 'show'
            interceptors.contains('excludes:index,reset')
        }
    }

    // ========== Multiple Rules (OR) Tests ==========

    def "test multiple rules interceptor matches show action"() {
        when:
        def response = http('/api/advancedMatching/show/1')

        then:
        response.expectStatus(200)
        with(response.json()) {
            action == 'show'
            interceptors.contains('multiRule:show|list')
        }
    }

    def "test multiple rules interceptor matches list action"() {
        when:
        def response = http('/api/advancedMatching/list')

        then:
        response.expectStatus(200)
        with(response.json()) {
            action == 'list'
            interceptors.contains('multiRule:show|list')
        }
    }

    def "test multiple rules interceptor does not match create action"() {
        when:
        def response = http('/api/advancedMatching/create')

        then:
        response.expectStatus(200)
        with(response.json()) {
            action == 'create'
            !interceptors.contains('multiRule:show|list')
        }
    }

    // ========== Combined Matching Tests ==========

    def "test multiple interceptors can match same request"() {
        when: "accessing list action in api namespace"
        def response = http('/api/advancedMatching/list')

        then: "namespace, excludes, and multiRule interceptors all match"
        response.expectStatus(200)
        with(response.json()) {
            interceptors.contains('namespace:api')
            interceptors.contains('excludes:index,reset')
            interceptors.contains('multiRule:show|list')
        }
    }

    def "test POST to save triggers namespace and method interceptors"() {
        when:
        def response = httpPostJson('/api/advancedMatching/save', [:])

        then:
        response.expectStatus(200)
        with(response.json()) {
            interceptors.contains('namespace:api')
            interceptors.contains('method:POST')
            interceptors.contains('excludes:index,reset')
            // save is not in show|list, so multiRule should not match
            !interceptors.contains('multiRule:show|list')
        }
    }

    def "test index action only matches namespace interceptor"() {
        when:
        def response = http('/api/advancedMatching/index')

        then: "only namespace interceptor matches (others exclude index)"
        response.expectStatus(200)
        with(response.json()) {
            interceptors.contains('namespace:api')
            !interceptors.contains('excludes:index,reset')
            !interceptors.contains('multiRule:show|list')
            !interceptors.contains('method:POST')
        }
    }

    // ========== Interceptor Order Tests ==========

    def "test interceptors execute in defined order"() {
        when:
        def response = http('/api/advancedMatching/list')

        then: "interceptors ordered by their order property"
        response.expectStatus(200)
        def interceptors = response.json().interceptors

        // namespace (100) < excludes (300) < multiRule (400)
        def namespaceIdx = interceptors.indexOf('namespace:api')
        def excludesIdx = interceptors.indexOf('excludes:index,reset')
        def multiRuleIdx = interceptors.indexOf('multiRule:show|list')

        namespaceIdx < excludesIdx
        excludesIdx < multiRuleIdx
    }
}
