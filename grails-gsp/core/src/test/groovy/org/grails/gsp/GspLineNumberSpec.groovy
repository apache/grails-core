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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.exceptions.ExceptionUtils
import org.grails.taglib.GrailsTagException
import spock.lang.Specification
import spock.lang.Unroll

class GspLineNumberSpec extends Specification {

    // lines of other things after the scriptlet that fails, so that an entry of the table of lines that
    // is a few too early or too late is one of another line
    static final String AFTER = '\nnext\n${1}\nnext\n${2}\nnext\n${3}\nnext\n${4}\nnext\n${5}\nnext\n${6}\nlast\n'

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
    void "an expression that is not closed #where names the line it starts on"() {
        when:
        render(source)

        then:
        GrailsTagException e = thrown()
        e.message.contains('Unclosed GSP expression')
        e.lineNumber == line

        where:
        where                                  | source                                                    || line
        'on the only line'                     | 'a ${open'                                                || 1
        'after text of several lines'          | 'a\nb\nc\n${open\nh\n'                                || 4
        'after a scriptlet and more text'      | 'a\nb\nc\n<% int x = 1 %>\ne\nf\n${open\nh\n'        || 7
        'in the middle of a line'              | 'one\ntwo ${a} three ${open\nfour'                       || 2
        'in a tag'                             | 'one\ntwo\n<g:if test="${open">\nfour\n'                || 3
        'on a later line of a tag'             | 'one\n<g:if a="b"\n   test="${open">\nfour\n'           || 3
        'after a comment of several lines'     | '<%-- c\n c --%>\nthree\nfour ${open\n'                 || 4
    }

    @Unroll
    void "the Groovy compiler, whose lines the table of a page is of, takes #what for #lines, or for #withCarriageReturns where a carriage return by itself ends a line"() {
        expect: 'a class there is not is named after a comment that has the characters in it, on the line the compiler counts'
        linesTheCompilerCounts(separator) == (linesTheCompilerCounts('\r') == 1 ? withCarriageReturns : lines)

        where: 'a line feed is a line and a carriage return in front of it is none of its own for every compiler; Groovy up to 6.0.0 counts nothing else, and later versions count a carriage return by itself'
        what                                   | separator || lines | withCarriageReturns
        'a line feed'                          | '\n'      || 1     | 1
        'a carriage return and a line feed'    | '\r\n'    || 1     | 1
        'a carriage return by itself'          | '\r'      || 0     | 1
        'two carriage returns'                 | '\r\r'    || 0     | 2
        'a line feed and a carriage return'    | '\n\r'    || 1     | 2
        'two carriage returns and a line feed' | '\r\r\n'  || 1     | 2
        'a form feed'                          | '\f'      || 0     | 0
        'a vertical tab'                       | '\u000B'  || 0     | 0
        'a next line character'                | '\u0085'  || 0     | 0
        'a line separator'                     | '\u2028'  || 0     | 0
        'a paragraph separator'                | '\u2029'  || 0     | 0
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
        'after an expression of many lines'                | '${[1,\n 2,\n 3,\n 4,\n 5,\n 6,\n 7].size()}\neight\n<% new no.such.Thing() %>' + AFTER || 9
        'after two expressions of several lines'           | '${[1,\n 2].size()} ${"""a\nb\nc\nd\ne"""}\nseven\n<% new no.such.Thing() %>' + AFTER || 8
        'after a scriptlet of many lines'                  | '<%\n int a = 1\n int b = 2\n int c = 3\n int d = 4\n int e = 5\n%>\neight\n<% new no.such.Thing() %>' + AFTER || 9
        'after a scriptlet with Windows line breaks'       | '<%\r\n int a = 1\r\n int b = 2\r\n int c = 3\r\n int d = 4\r\n int e = 5\r\n%>\r\neight\r\n<% new no.such.Thing() %>' + AFTER || 9
        'after a tag whose attribute has many lines'       | '<g:if test="${[1,\n 2,\n 3,\n 4,\n 5].size() ==\n 5}">x</g:if>\nseven\n<% new no.such.Thing() %>' + AFTER || 8
        'after an expression with carriage returns in it'        | 'one\n${[1,\r 2,\r 3,\r 4,\r 5].size()}\nthree\n<% new no.such.Thing() %>' + AFTER     || 4
        'after a tag whose attribute has carriage returns'       | 'one\n<g:if test="${[1,\r 2,\r 3,\r 4]}">x</g:if>\nthree\n<% new no.such.Thing() %>' + AFTER || 4
        'after a scriptlet with many carriage returns'           | 'one\n<% int a = 1;\r int b = 2;\r int c = 3;\r int d = 4;\r int e = 5 %>\nthree\n<% new no.such.Thing() %>' + AFTER || 4
        'after line feeds and carriage returns in an expression' | 'one\n${[1,\n\r 2,\r 3,\r 4,\n\r 5].size()}\nfive\n<% new no.such.Thing() %>' + AFTER || 6
        'after carriage returns in a page with Windows line breaks' | 'one\r\n${[1,\r 2,\r 3,\r 4,\r\n 5].size()}\r\nfour\r\n<% new no.such.Thing() %>' + AFTER || 5
        'in a page that has carriage returns only, which is one line' | 'one\r<% int a = 1 %>\rthree\r<% new no.such.Thing() %>\rfive\r${1}\r'            || 1
        'because an expression has a bracket that closes nothing' | 'one\n${a]}b\nthree\n'                                                                    || 2
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
    void "a scriptlet that does not compile on line #line of a long page of #kind names it"() {
        given: 'a page whose class has its lines on either side of where the table of lines grows, at 1000, 2000 and 4000 of them'
        String source = (1..<line).collect { int each -> make(each) }.join('') + '<% new no.such.Thing() %>\n' + 'more\n' * 10

        when:
        render(source)

        then:
        GroovyPagesException e = thrown()
        e.lineNumber == line

        where:
        kind          | make                                                    | line
        'expressions' | { int each -> "\${${each}}\n".toString() }               | 990
        'expressions' | { int each -> "\${${each}}\n".toString() }               | 2010
        'expressions' | { int each -> "\${${each}}\n".toString() }               | 3990
        'expressions' | { int each -> "\${${each}}\n".toString() }               | 4010
        'scriptlets'  | { int each -> "<% int a${each} = ${each} %>\n".toString() } | 200
        'scriptlets'  | { int each -> "<% int a${each} = ${each} %>\n".toString() } | 300
        'scriptlets'  | { int each -> "<% int a${each} = ${each} %>\n".toString() } | 600
        'scriptlets'  | { int each -> "<% int a${each} = ${each} %>\n".toString() } | 1100
        'scriptlets'  | { int each -> "<% int a${each} = ${each} %>\n".toString() } | 2100
    }

    void "a comment is still left out of what a page writes"() {
        expect:
        render('one\n<%-- a\n b --%>two\n%{-- c\n d --}%\nthree') == 'one\ntwo\n\nthree'
    }

    private static int linesTheCompilerCounts(String separator) {
        try {
            new GroovyShell().parse("/* a${separator}b */ new no.such.Thing()")
        } catch (MultipleCompilationErrorsException e) {
            return ExceptionUtils.extractLineNumber(e) - 1
        }
        throw new IllegalStateException('a class there is not was compiled')
    }

    private String render(String source) {
        StringWriter out = new StringWriter()
        engine.createTemplate(source, "lines${source.hashCode().abs()}").make([:]).writeTo(out)
        out.toString()
    }
}
