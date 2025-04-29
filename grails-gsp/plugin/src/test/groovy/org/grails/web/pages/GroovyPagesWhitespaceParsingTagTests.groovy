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
package org.grails.web.pages

import org.grails.web.taglib.AbstractGrailsTagTests
import org.junit.jupiter.api.Test


/**
 * @author Marc Palmer (marc@anyware.co.uk)
 */
class GroovyPagesWhitespaceParsingTagTests extends AbstractGrailsTagTests {

    @Test
    void testTagWithTab() {
        // there is a tab (\t) between "if" and test
        def template = '<g:if\ttest="${2 > 1}">rejoice</g:if>'

        assertOutputEquals "rejoice", template
    }

    @Test
    void testTagWithSpace() {
        // there is a tab (\t) between "if" and test
        def template = '<g:if test="${2 > 1}">rejoice</g:if>'

        assertOutputEquals "rejoice", template
    }

    @Test
    void testTagWithNewline() {
        // there is a tab (\t) between "if" and test
        def template = """<g:if
test="${2 > 1}">rejoice</g:if>"""

        assertOutputEquals "rejoice", template
    }

    @Test
    void testTagWithSurroundingNewlines() {
        def template = """
<g:if test="${2 > 1}">rejoice</g:if>
"""

        assertOutputEquals "\nrejoice\n", template
}

    @Test
    void testTagWithSurroundingContent() {
        def template = """Hello
this is

<g:if test="${2 > 1}">testing</g:if>
whitespace handling


of tags in GSP"""

        assertOutputEquals "Hello\nthis is\n\ntesting\nwhitespace handling\n\n\nof tags in GSP", template
    }

    @Test
    void testTagWithSurroundingContentMultipleNewlines() {
        def template = """Hello
this is

<g:if test="${2 > 1}">testing</g:if>


whitespace handling


of tags in GSP"""

        assertOutputEquals "Hello\nthis is\n\ntesting\n\n\nwhitespace handling\n\n\nof tags in GSP", template
    }

    @Test
    void testConsecutiveTagInvocations() {
        def template = """Hello <g:if test="${2 > 1}">one</g:if> <g:if test="${2 > 1}">two</g:if><g:if test="${2 > 1}">three</g:if>"""

        assertOutputEquals "Hello one twothree", template
    }

    @Test
    void testConsecutiveTagInvocationsWithLineBreaks() {
        def template = """Hello <g:if test="${2 > 1}">one</g:if>
  <g:if test="${2 > 1}">two</g:if>
<g:if test="${2 > 1}">three</g:if>"""

        assertOutputEquals "Hello one\n  two\nthree", template
    }
}
