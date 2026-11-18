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
package org.apache.grails.datamapping.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import spock.lang.Specification

/**
 * Covers the persistence runtime surviving into an ahead-of-time image. A read exercises little of
 * it, so what is missing shows up when a record is written or removed rather than at start-up.
 */
class GormRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new GormRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    private Set<String> registeredTypes() {
        hints.reflection().typeHints().collect { it.type.name } as Set
    }

    private boolean hasCategory(String type, MemberCategory category) {
        def hint = hints.reflection().getTypeHint(TypeReference.of(type))
        hint != null && hint.memberCategories.contains(category)
    }

    void 'the mapping context a datastore is driven through is registered'() {
        expect:
            registeredTypes().contains('org.grails.datastore.mapping.model.MappingContext')
    }

    void 'fields are registered, not only methods'() {
        given: 'a persister hands work to inner classes that read what they captured as properties'
            String type = registeredTypes().find {
                it.startsWith('org.grails.datastore.mapping.')
            }

        expect:
            hasCategory(type, MemberCategory.ACCESS_DECLARED_FIELDS)
            hasCategory(type, MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'the scan reaches the persistence packages and nothing outside them'() {
        expect:
            registeredTypes().size() > 0
            registeredTypes().every {
                it.startsWith('org.grails.datastore.mapping.') || it.startsWith('org.grails.datastore.gorm.')
            }
    }

    void 'a class loader that resolves nothing yields no hints rather than failing'() {
        given:
            RuntimeHints empty = new RuntimeHints()

        when:
            new GormRuntimeHints().registerHints(empty, new URLClassLoader(new URL[0], null))

        then:
            noExceptionThrown()
    }
}
