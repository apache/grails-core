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
package org.grails.doc.gradle

import org.grails.doc.gradle.GroovydocLinkAuditor.Category
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class GroovydocLinkAuditorSpec extends Specification {

    @TempDir
    File apiDir

    void 'reports nothing when every relative link resolves'() {
        given:
        writeHtml('grails/Book.html', '<p>Book</p>')
        writeHtml('index.html', """
            <a href='grails/Book.html'>Book</a>
            <a href="grails/Book.html#author">author</a>
            <a href='#top'>top</a>
            <a href='https://docs.groovy-lang.org/4.0.33/html/gapi/groovy/lang/Closure.html'>Closure</a>
        """)

        expect:
        GroovydocLinkAuditor.audit(apiDir).byCategory.isEmpty()
    }

    void 'fails a link to a fully qualified type that was neither generated nor mapped'() {
        given: 'Groovydoc could not load the type, so it linked to a page next to this one'
        writeHtml('grails/Book.html', "<a href='org.springframework.context.ApplicationContext.html'>ApplicationContext</a>")

        when:
        List<String> violations = GroovydocLinkAuditor.findViolations(apiDir)

        then:
        violations.size() == 1
        violations[0].contains('org.springframework.context.ApplicationContext.html')
        violations[0].contains('Book.html')
    }

    void 'ignores a fully qualified type whose package has no javadoc to link to'() {
        given:
        writeHtml('grails/Book.html', "<a href='org.radeox.regex.MatchResult.html'>MatchResult</a>")

        expect:
        GroovydocLinkAuditor.audit(apiDir, ['org.radeox.']).byCategory.isEmpty()
    }

    void 'reports an inner class link using a slash-separated path'() {
        given: 'the generated page uses the dotted Groovydoc naming convention'
        writeHtml('Query.Order.Direction.html', '<p>Direction</p>')
        and: 'another page links to it using a malformed slash-separated path'
        writeHtml('index.html', "<a href='./Query/Order.Direction.html'>Direction</a>")

        when:
        GroovydocLinkAuditor.Result result = GroovydocLinkAuditor.audit(apiDir)

        then:
        result.violations.empty
        result.byCategory[Category.UNQUALIFIED_TYPE].size() == 1
        result.byCategory[Category.UNQUALIFIED_TYPE][0].contains('Query/Order.Direction.html')
    }

    void 'reports a link written relative to the wrong directory as a warning'() {
        given: 'the page exists at the root of the API docs'
        writeHtml('deprecated-list.html', '<p>Deprecated</p>')
        and: 'a package page links to it without the relative root prefix'
        writeHtml('grails/core/package-summary.html', "<a href='deprecated-list.html'>Deprecated</a>")

        when:
        GroovydocLinkAuditor.Result result = GroovydocLinkAuditor.audit(apiDir)

        then:
        result.violations.empty
        result.byCategory[Category.WRONG_RELATIVE_PATH].size() == 1
    }

    void 'reports a link with too many parent segments as a warning'() {
        given:
        writeHtml('org/grails/encoder/StreamingEncoder.html', '<p>StreamingEncoder</p>')
        writeHtml('index-all.html', "<a href='../../../org/grails/encoder/StreamingEncoder.html'>StreamingEncoder</a>")

        when:
        GroovydocLinkAuditor.Result result = GroovydocLinkAuditor.audit(apiDir)

        then:
        result.violations.empty
        result.byCategory[Category.WRONG_RELATIVE_PATH].size() == 1
    }

    @Unroll
    void 'reports #page as a warning because Groovydoc never generates it'() {
        given:
        writeHtml('grails/Book.html', "<a href='../${page}'>${page}</a>")

        when:
        GroovydocLinkAuditor.Result result = GroovydocLinkAuditor.audit(apiDir)

        then:
        result.violations.empty
        result.byCategory[Category.UNGENERATED_PAGE].size() == 1

        where:
        page << GroovydocLinkAuditor.UNGENERATED_PAGES
    }

    void 'reports a type Groovydoc could not qualify as a warning'() {
        given: 'Groovydoc emitted the simple name of a type it could not place in a package'
        writeHtml('org/grails/query/BsonQuery.html', "<a href='../../../Junction.html'>Junction</a>")

        when:
        GroovydocLinkAuditor.Result result = GroovydocLinkAuditor.audit(apiDir)

        then:
        result.violations.empty
        result.byCategory[Category.UNQUALIFIED_TYPE].size() == 1
    }

    void 'skips doc comment markup that is not a link'() {
        given:
        writeHtml('grails/ApplicationTagLib.html', """<a href="\${resource(dir:'css',file:'main.css')}">css</a>""")

        expect:
        GroovydocLinkAuditor.audit(apiDir).byCategory.isEmpty()
    }

    void 'decodes escaped characters before resolving a link'() {
        given:
        writeHtml('grails/Book Store.html', '<p>Book Store</p>')
        writeHtml('index.html', "<a href='grails/Book%20Store.html'>Book Store</a>")

        expect:
        GroovydocLinkAuditor.audit(apiDir).byCategory.isEmpty()
    }

    void 'audits a repeated href once per page'() {
        given: 'Groovydoc emits the same link in the summary and detail sections'
        writeHtml('grails/Book.html', '''
            <a href='org.example.Missing.html'>Missing</a>
            <a href='org.example.Missing.html'>Missing</a>
        ''')

        when:
        GroovydocLinkAuditor.Result result = GroovydocLinkAuditor.audit(apiDir)

        then:
        result.violations.size() == 1
    }

    void 'ignores links inside HTML comments'() {
        given: 'the nav bar template comments out the Tree entry instead of omitting it'
        writeHtml('package-summary.html', "<!--<li><a href='overview-tree.html'>Tree</a></li>-->")

        expect:
        GroovydocLinkAuditor.audit(apiDir).byCategory.isEmpty()
    }

    void 'takes an href with a stray percent sign literally'() {
        given: 'a doc comment produced an href that was never percent-encoded'
        writeHtml('grails/50%.html', '<p>Fifty</p>')
        writeHtml('index.html', "<a href='grails/50%.html'>Fifty</a>")

        expect:
        GroovydocLinkAuditor.audit(apiDir).byCategory.isEmpty()
    }

    void 'keeps a plus sign literal while decoding percent escapes'() {
        given:
        writeHtml('grails/A + B.html', '<p>A + B</p>')
        writeHtml('index.html', "<a href='grails/A%20+%20B.html'>A + B</a>")

        expect:
        GroovydocLinkAuditor.audit(apiDir).byCategory.isEmpty()
    }

    private File writeHtml(String relativePath, String content) {
        File file = new File(apiDir, relativePath)
        file.parentFile.mkdirs()
        file.text = content
        file
    }
}
