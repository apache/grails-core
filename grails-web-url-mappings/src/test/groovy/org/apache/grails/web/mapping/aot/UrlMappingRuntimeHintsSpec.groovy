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
package org.apache.grails.web.mapping.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Specification

import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo

/**
 * Covers the URL mappings being evaluated and used in an image: the types Groovy dispatches on, and the
 * builder a URL mappings closure is delegated to.
 */
class UrlMappingRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new UrlMappingRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    void 'the types Groovy dispatches on can be called'() {
        expect:
            [UrlMappingInfo, UrlMapping, LinkGenerator].every {
                RuntimeHintsPredicates.reflection().onType(it)
                        .withMemberCategories(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS)
                        .test(hints)
            }
    }

    void 'the builder a URL mappings closure is delegated to can be called and read'() {
        expect: 'each mapping is a call the closure resolves against the builder, which reads its own fields too'
            RuntimeHintsPredicates.reflection()
                    .onType(TypeReference.of('org.grails.web.mapping.DefaultUrlMappingEvaluator$UrlMappingBuilder'))
                    .withMemberCategories(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS)
                    .test(hints)
    }
}
