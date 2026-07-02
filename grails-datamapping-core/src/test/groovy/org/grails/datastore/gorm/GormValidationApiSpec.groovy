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
package org.grails.datastore.gorm

import org.springframework.validation.Validator

import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Unit tests for {@link GormValidationApi}.
 *
 * Verifies that the resolved {@link Validator} is cached after first resolution, avoiding
 * repeated (and potentially expensive) validator lookups on every call.
 */
class GormValidationApiSpec extends Specification {

    static class Foo {
    }

    void "getValidator caches the resolved validator instead of re-resolving it on every call"() {
        given:
        PersistentEntity persistentEntity = Mock(PersistentEntity)
        Validator validator = Mock(Validator)
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntity(Foo.name) >> persistentEntity
        }
        Datastore datastore = Mock(Datastore) {
            getMappingContext() >> mappingContext
        }
        GormValidationApi<Foo> api = new GormValidationApi<>(Foo, datastore)

        when:
        Validator first = api.getValidator()
        Validator second = api.getValidator()

        then: "the validator is only resolved once, on the first call"
        1 * mappingContext.getEntityValidator(persistentEntity) >> validator
        first == validator
        second == validator
    }

    void "getValidator returns null and does not cache when no validator can be resolved"() {
        given:
        PersistentEntity persistentEntity = Mock(PersistentEntity)
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntity(Foo.name) >> persistentEntity
        }
        Datastore datastore = Mock(Datastore) {
            getMappingContext() >> mappingContext
        }
        GormValidationApi<Foo> api = new GormValidationApi<>(Foo, datastore)

        when:
        Validator result = api.getValidator()

        then:
        1 * mappingContext.getEntityValidator(persistentEntity) >> null
        result == null
    }
}
