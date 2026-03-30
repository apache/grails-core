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
package app4

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import spock.lang.Specification

@Integration
class WildcardActionValidationFunctionalSpec extends Specification implements HttpClientSupport {

    def 'resolves wildcard action mappings to declared controller actions'() {
        when: 'requesting a path segment that matches a real topic action'
        def response = http('/gallery')

        then: 'the wildcard action mapping dispatches to that action'
        response.assertJson(200, [
            controller: 'topic',
            action: 'gallery'
        ])
    }

    def 'falls through invalid wildcard action mappings to the next match'() {
        when: 'requesting a numeric path segment that is not a real topic action'
        def response = http('/42')

        then: 'the fallback id mapping handles the request'
        response.assertJson(200, [
            controller: 'topic',
            action: 'show',
            id: '42'
        ])
    }

    def 'validates wildcard controller tokens against registered controllers'() {
        when: 'requesting a registered controller and action path'
        def response = http('/topic/gallery')

        then: 'the wildcard controller mapping resolves the request'
        response.assertJson(200, [
            controller: 'topic',
            action: 'gallery'
        ])

        when: 'requesting an unregistered controller path'
        response = http('/nonexistent/foo')

        then: 'the request is rejected'
        response.assertStatus(404)
    }

    def 'prefers literal path mappings over wildcard controller matches'() {
        when: 'a literal path mapping and a wildcard controller mapping can both match the request'
        def response = http('/community')

        then: 'the literal mapping wins'
        response.assertJson(200, [
            controller: 'topic',
            action: 'home'
        ])
    }

    def 'prefers explicit method mappings over wildcard optional action matches'() {
        when: 'posting to the invites collection'
        def response = httpPostJson('/invites', '{}')

        then: 'the explicit POST mapping wins over the optional action mapping'
        response.assertJson(200, [
            controller: 'invite',
            action: 'create',
            method: 'POST'
        ])

        when: 'requesting a named invite action over GET'
        response = http('/invites/manage')

        then: 'the wildcard optional action mapping still resolves real actions'
        response.assertJson(200, [
            controller: 'invite',
            action: 'manage'
        ])
    }

}
