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
package functionaltests.encoding

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * A filter registered at {@code Ordered.HIGHEST_PRECEDENCE + 1} reads a request parameter before Grails sees the
 * request. Once a filter reads a parameter, the container has decoded the body, so the encoding filter has to run
 * before it. A filter registered at {@code Ordered.HIGHEST_PRECEDENCE} through a {@code FilterRegistrationBean}, the
 * encoding filter's own order, still runs before it.
 */
@Integration
@Tag('http-client')
@Issue('https://github.com/apache/grails-core/issues/15921')
class CharacterEncodingSpec extends Specification implements HttpClientSupport {

    void 'decodes a form post as UTF-8 when a filter ahead of Grails reads its parameters'() {
        when: 'a form is posted without a charset, through a filter that reads one of its parameters'
        def response = httpPostForm('/encoding/echo', [text: 'Café – 🍂'])

        then: 'the filter and the controller both see the text as it was typed'
        response.assertJsonContains(200, [text: 'Café – 🍂', readByFilter: 'Café – 🍂'])
    }

    void 'runs a filter registered at HIGHEST_PRECEDENCE through a FilterRegistrationBean ahead of the encoding filter'() {
        when: 'a form is posted without a charset'
        def response = httpPostForm('/encoding/echo', [text: 'Café'])

        then: 'the filter registered at the encoding filter\'s own order found no request encoding set yet'
        response.assertJsonContains(200, [text: 'Café', encodingAtHighestPrecedence: 'none'])
    }
}
