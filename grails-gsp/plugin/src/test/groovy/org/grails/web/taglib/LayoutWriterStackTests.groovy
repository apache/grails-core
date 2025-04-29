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
package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Shared
import spock.lang.Specification

class LayoutWriterStackTests extends Specification implements TagLibUnitTest<TwoColumnTagLib> {

    @Shared def template = """
    <g:twoColumn>
        <g:left>leftContent</g:left>
        <g:right>rightContent</g:right>
        bodyContent
    </g:twoColumn>"""

    void testLayoutTag() {
        when:
        String result = applyTemplate(template)

        then:
        assertEqualsIgnoreWhiteSpace("""
        <div class='twoColumn'>
            left: <div class='left'>leftContent</div>,
            right: <div class='right'>rightContent</div>,
            body: bodyContent
        </div>""",
                result)
    }

    void testNestedLayoutTags() {
        given:
        def nested = template.replaceAll("leftContent", template)
        String result = applyTemplate(nested)

        expect:
        assertEqualsIgnoreWhiteSpace("""
        <div class='twoColumn'>
            left: <div class='left'>
                <div class='twoColumn'>
                    left: <div class='left'>leftContent</div>,
                    right: <div class='right'>rightContent</div>,
                    body: bodyContent
                </div>
            </div>,
            right: <div class='right'>rightContent</div>,
            body: bodyContent</div>""",
                result)
    }

    boolean assertEqualsIgnoreWhiteSpace(String s1, String s2) {
        s1.replaceAll(/\s/, '') == s2.replaceAll(/\s/, '')
    }
}

@Artefact("TagLib")
class TwoColumnTagLib {

    Closure twoColumn = {attrs, body ->
        def parts = LayoutWriterStack.writeParts(body)
        out << "<div class='twoColumn'>left: " << parts.left << ", right: " << parts.right << ", body: " << parts.body << "</div>"
    }
    Closure left = {attrs, body ->
        def w = LayoutWriterStack.currentWriter('left')
        w << "<div class='left'>" << body() << "</div>"
    }

    Closure right = {attrs, body ->
        def w = LayoutWriterStack.currentWriter('right')
        w << "<div class='right'>" << body() << "</div>"
    }
}
