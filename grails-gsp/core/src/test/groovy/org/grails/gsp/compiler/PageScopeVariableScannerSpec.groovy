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

import spock.lang.Specification

class PageScopeVariableScannerSpec extends Specification {

    private static Set<String> scan(String source) {
        Set<String> names = new LinkedHashSet<>()
        PageScopeVariableScanner.collect(source, names)
        names
    }

    void 'the var and status of a namespaced tag are collected'() {
        expect:
        scan(source) == expected as Set

        where:
        source                                                                  || expected
        '<g:set var="total" value="${1}"/>'                                     || ['total']
        '<g:each in="${[]}" var="book" status="i">x</g:each>'                   || ['book', 'i']
        '<g:eachError bean="${b}" var="error">x</g:eachError>'                  || ['error']
        "<g:set var='single' value='\${1}'/>"                                   || ['single']
        '<g:set\n    var = "spaced"\n    value="${1}"/>'                        || ['spaced']
        '<my:tag var="$dollar_1"/>'                                             || ['$dollar_1']
    }

    void 'only a namespaced tag introduces a name'() {
        expect:
        scan('<div var="notATagVar"></div><input status="x">').isEmpty()
    }

    void 'a value that is not a plain identifier introduces nothing'() {
        expect:
        scan(source).isEmpty()

        where:
        source << [
                '<g:set var="${dynamic}" value="1"/>',
                '<g:set var="1leadingDigit"/>',
                '<g:set var=""/>',
                '<g:set var=unquoted/>',
        ]
    }

    void 'var or status inside another attribute value is not an attribute'() {
        expect:
        scan('<g:message code="x" default="var=\'nope\' status=\'nor\'"/>').isEmpty()
    }

    void 'a quote inside an expression does not end the attribute holding it'() {
        given: 'the og:title line that stopped a real application compiling, as the scan receives it: the ' +
                'layout preprocessor has already turned the meta into a namespaced tag'
        String source = '''<grailsLayout:captureMeta gsp_sm_xmlClosingForEmptyTag="/" property="og:title" content="${image.title?:'Untitled'.replaceAll('"','\\'')}" />''' +
                '<g:set var="after" value="${1}"/>'

        expect: 'the name after it is still found, so the scan stayed in step'
        scan(source) == ['after'] as Set
    }

    void 'an expression may hold braces, closures and strings with braces in them'() {
        expect:
        scan('<g:each in="${items.findAll { it.x > 1 && it.y != "}" }}" var="item">x</g:each>') == ['item'] as Set
    }

    void 'a triple quoted string holding a lone quote does not end the expression holding it'() {
        expect:
        scan(source) == ['total'] as Set

        where:
        source << [
                """<g:set value="\${x ?: '''it's fine'''}" var="total"/>""",
                """<g:set value="\${x ?: \"\"\"say "hi" now\"\"\"}" var="total"/>""",
        ]
    }

    void 'a page far longer than the stack is deep is scanned without recursing through it'() {
        given: 'a namespaced tag holding a quote-bearing expression, a long page, then a name at its very end'
        String unbalancing = '''<grailsLayout:captureMeta content="${t ?: 'Untitled'.replaceAll('"', '\\'')}" />\n'''
        String filler = '<p class="c" data-x=\'y\'>text with "quotes" and \'more\'</p>\n' * 5000
        String source = unbalancing + filler + '<g:set var="last" value="${1}"/>'

        and: 'a thread with a small stack, which the previous regular expression overflowed on this page'
        Set<String> names = null
        Throwable failure = null
        Thread thread = new Thread(null, {
            try {
                names = scan(source)
            } catch (Throwable t) {
                failure = t
            }
        }, 'small-stack', 256 * 1024)

        when:
        thread.start()
        thread.join()

        then:
        failure == null
        names == ['last'] as Set
    }
}
