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
