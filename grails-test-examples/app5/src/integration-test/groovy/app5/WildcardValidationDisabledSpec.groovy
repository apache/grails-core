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
package app5

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import spock.lang.Specification

@Integration
class WildcardValidationDisabledSpec extends Specification implements HttpClientSupport {

    def 'grails.urlmapping.validateWildcards false keeps the fallback mapping selected ahead of a valid wildcard controller match'() {
        when: 'requesting a path that matches both the fallback mapping and a registered controller'
        def response = http('/wildcard-disabled/target')

        then: 'the fallback action still wins'
        response.assertJson(200, [
            controller: 'wildcardValidation',
            action: 'fallback',
            path: 'target'
        ])
    }

    def 'the target controller itself remains available through the standard controller route'() {
        when: 'requesting the target controller directly'
        def response = http('/target')

        then: 'the target controller still responds successfully'
        response.assertJson(200, [
            controller: 'target',
            action: 'index'
        ])
    }
}
