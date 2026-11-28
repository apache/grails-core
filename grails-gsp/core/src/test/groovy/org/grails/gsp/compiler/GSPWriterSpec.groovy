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
package org.grails.gsp.compiler

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.exceptions.ExceptionUtils

/**
 * The lines the writer of the generated class counts are those the Groovy compiler counts. Only one
 * compiler is on the class path of a build, and the versions differ in what a carriage return by
 * itself is, so the writer is given either answer here; {@code GspLineNumberSpec} has the pages,
 * with the compiler that is there.
 */
class GSPWriterSpec extends Specification {

    @Unroll
    void "#what is #lines lines for a compiler that counts line feeds only, and #withCarriageReturns for one that counts a carriage return by itself"() {
        expect:
        linesOf(written, false) == lines
        linesOf(written, true) == withCarriageReturns

        where:
        what                                             | written                  || lines | withCarriageReturns
        'text without a line break'                      | ['a b']                  || 0     | 0
        'a line feed'                                    | ['a\nb']                 || 1     | 1
        'a carriage return and a line feed'              | ['a\r\nb']               || 1     | 1
        'a carriage return by itself'                    | ['a\rb']                 || 0     | 1
        'two carriage returns'                           | ['a\r\rb']               || 0     | 2
        'a line feed and a carriage return'              | ['a\n\rb']               || 1     | 2
        'two carriage returns and a line feed'           | ['a\r\r\nb']             || 1     | 2
        'a carriage return and a line feed, in pieces'   | ['a\r', '\nb']           || 1     | 1
        'a carriage return, text and a line feed'        | ['a\r', 'b', '\nc']      || 1     | 2
        'the line breaks of a scriptlet of Windows'      | ['a\r\n', 'b\r\n', 'c']  || 2     | 2
        'a form feed and a line separator'               | ['a\fb c']          || 0     | 0
    }

    @Unroll
    void "a line that is ended after #what is #lines lines, and #withCarriageReturns for a compiler that counts a carriage return by itself"() {
        given:
        GSPWriter plain = writer(false)
        GSPWriter counting = writer(true)

        when:
        [plain, counting].each { GSPWriter each ->
            each.print(written)
            each.println()
        }

        then:
        plain.currentLineNumber - 1 == lines
        counting.currentLineNumber - 1 == withCarriageReturns

        where: 'the end of the line is the line separator of the system, whose line feed is one line break with a carriage return in front of it'
        what                | written || lines | withCarriageReturns
        'text'              | 'a'     || 1     | 1
        'a line feed'       | 'a\n'   || 2     | 2
        'a carriage return' | 'a\r'   || 1     | (System.lineSeparator() == '\n' ? 1 : 2)
    }

    @Unroll
    void "the lines of code of a scriptlet #what are recorded as #lines, and #withCarriageReturns for a compiler that counts a carriage return by itself"() {
        expect: 'each line of the generated source has the line of the page that its code is on'
        linesOfPage(false) { GSPWriter writer -> writer.printlnPageText(code, 5) } == lines
        linesOfPage(true) { GSPWriter writer -> writer.printlnPageText(code, 5) } == withCarriageReturns

        where: 'a line of the page ends with a line feed only'
        what                                  | code             || lines     | withCarriageReturns
        'of one line'                         | 'a'              || [5]       | [5]
        'of three lines'                      | 'a\nb\nc'        || [5, 6, 7] | [5, 6, 7]
        'with a blank line'                   | 'a\n\nc'         || [5, 6, 7] | [5, 6, 7]
        'with Windows line breaks'            | 'a\r\nb\r\nc'    || [5, 6, 7] | [5, 6, 7]
        'with a carriage return by itself'    | 'a\rb\nc'        || [5, 6]    | [5, 5, 6]
    }

    void "the lines of an expression of several lines are those of the page it is on, and what is printed after it is the line of the token again"() {
        when:
        List<Integer> lines = linesOfPage(false) { GSPWriter writer ->
            writer.printlnToResponse('out', '[1,\n 2,\n 3]', 7)
            writer.printlnToResponse('out', 'a')
        }

        then: 'the lines of what is printed for no line of the page are those of the token the parser is at'
        lines == [7, 8, 9, 1]
    }

    void "the writer of a page counts what the compiler on the class path counts"() {
        when:
        new GroovyShell().parse('/* a\rb */ new no.such.Thing()')

        then:
        MultipleCompilationErrorsException e = thrown()
        GroovyCompilerLines.carriageReturnEndsLine() == (ExceptionUtils.extractLineNumber(e) == 2)
        linesOf(['a\rb'], GroovyCompilerLines.carriageReturnEndsLine()) == ExceptionUtils.extractLineNumber(e) - 1
    }

    private static int linesOf(List<String> written, boolean carriageReturnEndsLine) {
        GSPWriter writer = writer(carriageReturnEndsLine)
        written.each { String each -> writer.print(each) }
        writer.currentLineNumber - 1
    }

    private static List<Integer> linesOfPage(boolean carriageReturnEndsLine, Closure<?> print) {
        GSPWriter writer = writer(carriageReturnEndsLine)
        print(writer)
        writer.lineNumbers.toList().subList(0, writer.currentLineNumber - 1)
    }

    private static GSPWriter writer(boolean carriageReturnEndsLine) {
        new GSPWriter(new StringWriter(), new GroovyPageParser('lines', '/lines', 'lines.gsp', 'one', null), carriageReturnEndsLine)
    }
}
