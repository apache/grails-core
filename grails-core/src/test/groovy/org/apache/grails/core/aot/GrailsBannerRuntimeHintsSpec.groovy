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
package org.apache.grails.core.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeHint
import org.springframework.aot.hint.TypeReference
import spock.lang.Specification

import grails.boot.GrailsBanner

/**
 * Covers the banner's option enums surviving into an image.
 *
 * <p>A Groovy enum reaches its own constructor through the metaclass to build its constants, so an
 * image that kept no constructor for it fails in the static initialiser -- while printing the
 * banner, before the application has run. The optional enum is the one that bites: it is touched
 * only where {@code grails.banner.versions.include} names something, so a traced image carries no
 * record of it and turning a version on turns the application off.</p>
 */
class GrailsBannerRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new GrailsBannerRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    private TypeHint hintFor(Class<?> type) {
        hints.reflection().getTypeHint(TypeReference.of(type))
    }

    void 'every option enum can be constructed'() {
        expect: 'the constants are built by a static initialiser that goes through the constructor'
            hintFor(type)?.memberCategories?.contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)

        where:
            type << [GrailsBanner.VersionOption, GrailsBanner.DefaultVersionOption,
                     GrailsBanner.OptionalVersionOption]
    }

    void 'the optional enum is registered though nothing reaches it unless it is configured'() {
        expect: 'an image traced with no versions included would otherwise have no record of it'
            hintFor(GrailsBanner.OptionalVersionOption) != null
    }

    void 'the enums can be read and called'() {
        expect: 'values() and the key each constant carries'
            hintFor(type)?.memberCategories?.contains(MemberCategory.INVOKE_DECLARED_METHODS)
            hintFor(type)?.memberCategories?.contains(MemberCategory.ACCESS_DECLARED_FIELDS)

        where:
            type << [GrailsBanner.VersionOption, GrailsBanner.DefaultVersionOption,
                     GrailsBanner.OptionalVersionOption]
    }

    void 'the metaclass name Groovy looks up is registered'() {
        expect: 'looked up before Groovy decides the enum has no metaclass of its own'
            hints.reflection().getTypeHint(TypeReference.of(
                    "groovy.runtime.metaclass.${GrailsBanner.OptionalVersionOption.name}MetaClass")) != null
    }
}
