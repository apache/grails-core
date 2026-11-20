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
package org.grails.gsp

import org.grails.taglib.GrailsTagException
import spock.lang.Specification
import spock.lang.Unroll

class GspLineNumberSpec extends Specification {

    GroovyPagesTemplateEngine engine

    void setup() {
        engine = new GroovyPagesTemplateEngine()
        engine.afterPropertiesSet()
    }

    @Unroll
    void "an expression that fails #where names its line"() {
        when:
        render(source)

        then:
        GroovyPagesException e = thrown()
        e.lineNumber == line
        e.message.contains("on line [${line}]")

        where:
        where                                        | source                                                         || line
        'on the only line'                           | '${[].nope()}'                                                 || 1
        'on the first of several lines'              | '${[].nope()}\ntwo\nthree'                                     || 1
        'in the middle'                              | 'one\n${[].nope()}\nthree\n'                                   || 2
        'on the last line, which has a line break'   | 'one\ntwo\n${[].nope()}\n'                                     || 3
        'on the last line, which has no line break'  | 'one\ntwo\n${[].nope()}'                                       || 3
        'after a comment of several lines'           | '<%-- a\n comment\n of three lines --%>\nfour\n${[].nope()}\nsix' || 5
        'after a comment written with braces'        | '%{-- a\n comment --}%\nthree\n${[].nope()}\nfive'             || 4
        'after two comments'                         | '<%-- a\n b --%>\n<%-- c\n\n d --%>${[].nope()}\nsix\n'        || 5
        'on the line a comment ends on'              | 'one\n<%-- a\n b\n c --%> ${[].nope()}\nfive\n'                || 4
        'before a comment'                           | 'one\n${[].nope()}\n<%-- a\n b --%>\nfive\n'                   || 2
        'after a comment on one line'                | 'one\n<%-- a --%>\n${[].nope()}\nfour\n'                       || 3
    }

    @Unroll
    void "a tag that cannot be compiled #where names its line"() {
        when:
        render(source)

        then:
        GrailsTagException e = thrown()
        e.lineNumber == line

        where:
        where                               | source                                         || line
        'on the last line'                  | 'one\ntwo\n<g:if>x</g:if>'                     || 3
        'after a comment of several lines'  | '<%-- a\n b\n c --%>\n<g:if>x</g:if>\nfive\n'  || 4
        'because it is not closed, in a page that ends with a line break'    | 'one\n<g:if test="${true}">\nthree\n' || 3
        'because it is not closed, in a page that ends without a line break' | 'one\n<g:if test="${true}">\nthree'   || 3
    }

    @Unroll
    void "a scriptlet that does not compile #where names its line"() {
        when:
        render(source)

        then:
        GroovyPagesException e = thrown()
        e.lineNumber == line

        where:
        where                               | source                                             || line
        'in the middle'                     | 'one\n<% def x = ) %>\nthree\n'                    || 2
        'after a comment of several lines'  | '<%-- a\n b\n c --%>\nfour\n<% def x = ) %>\nsix\n' || 5
        'after an expression of several lines'             | '${[1,\n 2,\n 3].size()}\nfour\n<% new no.such.Thing() %>\nsix\n'             || 5
        'after two expressions of several lines'           | '${[1,\n 2].size()} ${"""a\nb\nc"""}\nfive\n<% new no.such.Thing() %>\n'       || 6
        'after a scriptlet of several lines'               | '<%\n int a = 1\n int b = 2\n%>\nfive\n<% new no.such.Thing() %>\nseven\n'    || 6
        'after a scriptlet with Windows line breaks'       | '<%\r\n int a = 1\r\n int b = 2\r\n%>\r\nfive\r\n<% new no.such.Thing() %>\r\n' || 6
        'after a tag whose attribute has several lines'    | '<g:if test="${[1,\n 2].size() ==\n 2}">x</g:if>\nfour\n<% new no.such.Thing() %>\n' || 5
        'inside a scriptlet of several lines'              | 'one\n<%\n int a = 1\n def x = )\n%>\nsix\n'                        || 2
        'because a block is left open, in a page that ends with a line break'    | 'one\n<% if (true) { %>\nthree\n' || 3
        'because a block is left open, in a page that ends without a line break' | 'one\n<% if (true) { %>\nthree'   || 3
    }

    void "a scriptlet that does not compile at the end of a page of 5000 lines names line 5000"() {
        given: 'a page whose generated class has more lines than the page'
        String source = (1..4999).collect { int line -> "<% int a${line} = ${line} %>\${a${line}}\n" }.join('') + '<% new no.such.Thing() %>\n'

        when:
        render(source)

        then:
        GroovyPagesException e = thrown()
        e.lineNumber == 5000
    }

    @Unroll
    void "a scriptlet that does not compile on line #line of a long page names it"() {
        given: 'a line of the generated class on either side of where its table of lines grows'
        String source = (1..<line).collect { int each -> "\${${each}}\n" }.join('') + '<% new no.such.Thing() %>\n' + 'more\n' * 10

        when:
        render(source)

        then:
        GroovyPagesException e = thrown()
        e.lineNumber == line

        where:
        line << [900, 990, 1000, 1010, 1990, 2010, 3990, 4010]
    }

    void "a comment is still left out of what a page writes"() {
        expect:
        render('one\n<%-- a\n b --%>two\n%{-- c\n d --}%\nthree') == 'one\ntwo\n\nthree'
    }

    private String render(String source) {
        StringWriter out = new StringWriter()
        engine.createTemplate(source, "lines${source.hashCode().abs()}").make([:]).writeTo(out)
        out.toString()
    }
}
