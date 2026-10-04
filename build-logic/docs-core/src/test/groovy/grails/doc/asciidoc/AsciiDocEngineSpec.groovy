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
package grails.doc.asciidoc

import org.asciidoctor.log.LogHandler
import org.asciidoctor.log.LogRecord
import org.radeox.engine.context.BaseInitialRenderContext
import spock.lang.Shared
import spock.lang.Specification

class AsciiDocEngineSpec extends Specification {

    @Shared
    AsciiDocEngine engine = new AsciiDocEngine(new BaseInitialRenderContext())

    @Shared
    List<String> messages = []

    void setupSpec() {
        engine.asciidoctor.registerLogHandler({ LogRecord record -> messages << record.message } as LogHandler)
    }

    void setup() {
        messages.clear()
    }

    void 'a section whose headings start below level 1 renders without a sequence warning'() {
        when:
        String html = engine.render('''
            |////
            |License header, as at the top of every guide section
            |////
            |
            |Introduction to the section.
            |
            |==== Deeply nested heading
            |
            |Content
            |
            |==== Sibling heading
            |
            |More content
            |'''.stripMargin(), null)

        then:
        messages.empty

        and: 'the headings keep the level they were written at'
        html.contains('<div class="sect3">')
        html.contains('<h4 id="_deeply_nested_heading">Deeply nested heading</h4>')
        html.contains('<h4 id="_sibling_heading">Sibling heading</h4>')
    }

    void 'a heading that skips a level inside the section is still reported'() {
        when:
        engine.render('''
            |Introduction to the section.
            |
            |=== Section heading
            |
            |===== Skipped a level
            |
            |Content
            |'''.stripMargin(), null)

        then:
        messages == ['section title out of sequence: expected level 3, got level 4']
    }
}
