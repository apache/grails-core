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
package functionaltests.contentneg

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Without {@code grails.mime.negotiation.spring}, Grails resolves formats with its own rules, and Spring MVC writes the
 * values of actions as the {@code Accept} header asks.
 */
@Integration
@Tag('http-client')
class GrailsFormatNegotiationSpec extends Specification implements HttpClientSupport {

    void 'a value Spring MVC writes for an action is in the type the Accept header asks for, whatever its URL extension'() {
        expect:
        http('/springWritten/entity.html', Accept: '*/*').assertStatus(200).contentType.startsWith('application/json')
        http('/springWritten/text.json', Accept: 'text/plain').assertStatus(200).contentType.startsWith('text/plain')
    }

    void 'the formats Grails resolves follow the Grails rules: #accept'() {
        expect:
        http('/springWritten/formats', Accept: accept).assertStatus(200).assertEquals(formats)

        where:
        accept                  || formats
        '*/*, application/json' || 'all|all'
        'image/png'             || 'all|all'
    }
}
