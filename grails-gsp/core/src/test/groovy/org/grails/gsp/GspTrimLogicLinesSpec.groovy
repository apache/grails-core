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

import spock.lang.Specification
import spock.lang.Unroll

class GspTrimLogicLinesSpec extends Specification {

    GroovyPagesTemplateEngine engine

    void setup() {
        engine = new GroovyPagesTemplateEngine()
        engine.afterPropertiesSet()
    }

    void "a line holding only a scriptlet writes nothing"() {
        expect:
        render('''<%@ page trimLogicLines="true" %>
plugins {
<% for (String id : ids) { %>
    id '${id}'
<% } %>
}
''', [ids: ['a', 'b']]) == '''plugins {
    id 'a'
    id 'b'
}
'''
    }

    void "the indentation of a line holding only logic is removed with it"() {
        expect:
        render('''<%@ page trimLogicLines="true" %>
repositories {
    <% if (central) { %>
    mavenCentral()
    <% } else { %>
    mavenLocal()
    <% } %>
}
''', [central: central]) == """repositories {
    ${expected}
}
"""

        where:
        central | expected
        true    | 'mavenCentral()'
        false   | 'mavenLocal()'
    }

    void "a line holding text, an expression or an output scriptlet beside the logic is written as it is"() {
        expect:
        render('''<%@ page trimLogicLines="true" %>
<% if (true) { %>text <% } %>
<% if (true) { %>${'expression'}<% } %>
<% if (true) { %><%= 'output' %><% } %>
''', [:]) == '''text 
expression
output
'''
    }

    void "directives, comments and several constructs on one line write nothing"() {
        expect:
        render('''%{--
  A comment over several lines
--}%
<%@ page trimLogicLines="true" %>
<%@ page import="java.time.DayOfWeek" %>
<%-- a JSP comment --%>
  <% String day = DayOfWeek.MONDAY.name() %> %{ day = day.toLowerCase() }%  <%-- trailing --%>  
${day}
''', [:]) == 'monday\n'
    }

    @Unroll
    void "a page ending with a logic line #description"() {
        expect:
        render('<%@ page trimLogicLines="true" %>\nline\n<% if (true) { %>' + ending, [:]) == 'line\n'

        where:
        description               | ending
        'without a line break'    | '<% } %>'
        'with a line break'       | '<% } %>\n'
        'with Windows line breaks' | '<% } %>\r\n'
    }

    void "Windows line breaks are removed with the logic line"() {
        expect:
        render('<%@ page trimLogicLines="true" %>\r\n<% if (true) { %>\r\none\r\n<% } %>\r\ntwo\r\n', [:]) == 'one\r\ntwo\r\n'
    }

    void "the directive may be declared by a later page directive"() {
        expect:
        render('''<%@ page import="java.time.DayOfWeek" %>
<%@ page trimLogicLines="true" %>
<% if (true) { %>
${DayOfWeek.FRIDAY}
<% } %>
''', [:]) == 'FRIDAY\n'
    }

    void "without the directive every line is written as it always was"() {
        expect:
        render('''<% if (true) { %>
one
<% } %>
''', [:]) == '\none\n\n'
    }

    void "the lines of a page keep their numbers in the errors reported against them"() {
        given:
        String source = '''<%@ page trimLogicLines="true" %>
<% if (true) { %>
    <% if (true) { %>
${'fine'}
<% } %>
<% } %>
<% throw new IllegalStateException('line 7') %>
'''

        GroovyPageTemplate template = (GroovyPageTemplate) engine.createTemplate(source, 'lines_page')

        when:
        template.make([:]).writeTo(new StringWriter())

        then:
        IllegalStateException e = thrown()
        StackTraceElement frame = e.stackTrace.find { StackTraceElement element -> element.className == 'lines_page' }
        template.metaInfo.lineNumbers[frame.lineNumber - 1] == 7
    }

    void "a statically compiled page declaring its model trims its logic lines too"() {
        expect:
        render('''<%@ page trimLogicLines="true" %>
<%@ model="List<String> names" %>
<% for (String name : names) { %>
- ${name.toUpperCase()}
<% } %>
''', [names: ['x', 'y']]) == '- X\n- Y\n'
    }

    private String render(String source, Map model) {
        StringWriter out = new StringWriter()
        engine.createTemplate(source, "trim${source.hashCode().abs()}").make(model).writeTo(out)
        out.toString()
    }
}
