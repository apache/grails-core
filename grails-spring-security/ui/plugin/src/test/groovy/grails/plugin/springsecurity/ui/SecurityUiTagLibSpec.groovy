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
package grails.plugin.springsecurity.ui

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

/**
 * Verifies the button tags HTML-encode everything they interpolate - the element id,
 * the button text and any passed-through attributes - so a caller supplying
 * request-derived values cannot inject markup.
 */
class SecurityUiTagLibSpec extends Specification implements TagLibUnitTest<SecurityUiTagLib> {

    void 'submitButton encodes the element id, text and passed-through attributes'() {
        when: 'markup-sensitive values reach the tag, as a careless caller could pass them'
        String output = applyTemplate(
                '<s2ui:submitButton elementId="${eid}" text="${txt}" data-extra="${extra}"/>',
                [eid: 'x"&y', txt: '<b>bold</b>', extra: 'a"b'])

        then:
        output.contains('id="x&quot;&amp;y"')
        output.contains('&lt;b&gt;bold&lt;/b&gt;')
        output.contains('data-extra="a&quot;b"')
        !output.contains('<b>bold</b>')
    }

    void 'linkButton encodes the element id, text and passed-through attributes'() {
        when:
        String output = applyTemplate(
                '<s2ui:linkButton elementId="${eid}" text="${txt}" controller="user" action="search" data-extra="${extra}"/>',
                [eid: 'x"y', txt: '<i>t</i>', extra: '1"2'])

        then:
        output.contains('<a href="')
        output.contains('id="x&quot;y"')
        output.contains('&lt;i&gt;t&lt;/i&gt;')
        output.contains('data-extra="1&quot;2"')
        !output.contains('<i>t</i>')
    }
}
