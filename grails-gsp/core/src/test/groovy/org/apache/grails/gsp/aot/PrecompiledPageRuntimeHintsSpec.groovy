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
package org.apache.grails.gsp.aot

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Specification

/**
 * Covers what a page compiled at build time is read from being carried into an image.
 *
 * <p>Compiling a page splits it: the code becomes a class, and the static text between the code --
 * most of the page -- is written beside it as a resource that the class reads as it renders. An
 * image carries a resource only when asked, and these are named by convention rather than by any
 * code, so nothing asked. Every page then rendered with nothing where its text should be.</p>
 */
class PrecompiledPageRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new PrecompiledPageRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    void 'the static text of a compiled page is carried'() {
        expect: 'without it the page renders empty and reports a null it cannot explain'
            RuntimeHintsPredicates.resource()
                    .forResource('gsp_demo_indexgsp_html.data')
                    .test(hints)
    }

    void 'the line numbers that map generated code back to the page are carried'() {
        expect:
            RuntimeHintsPredicates.resource()
                    .forResource('gsp_demo_indexgsp_linenumbers.data')
                    .test(hints)
    }

    void 'the list of the pages compiled at build time is carried'() {
        expect: 'read to find the compiled pages at all'
            RuntimeHintsPredicates.resource().forResource('gsp/views.properties').test(hints)
    }

    void 'an unrelated resource is not carried'() {
        expect: 'the patterns name the compiled pages rather than everything'
            !RuntimeHintsPredicates.resource().forResource('application.yml').test(hints)
    }
}
