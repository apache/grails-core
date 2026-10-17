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
package org.apache.grails.web.common.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Specification

import grails.util.Holders
import grails.web.context.WebRequestServletHolder

/**
 * Covers the holder the servlet context is kept in being creatable in an image, where {@link Holders}
 * creates it by name.
 */
class ServletContextHolderRuntimeHintsSpec extends Specification {

    void 'the servlet context holder can be created'() {
        given:
            def hints = new RuntimeHints()

        when:
            new ServletContextHolderRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'without it the dispatcher servlet failed to start, handing the servlet context to a holder that was not there'
            RuntimeHintsPredicates.reflection().onType(WebRequestServletHolder)
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
    }

    void 'the holder registered is the one Holders creates'() {
        when: 'the servlet context is handed to Holders, which created its holder by name'
            def servletContext = new Object()
            Holders.servletContext = servletContext

        then:
            Holders.servletContext.is(servletContext)

        cleanup:
            Holders.servletContext = null
    }
}
