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
 * Verifies that an auto-discovered {@link Validator} is re-resolved from the {@link MappingContext}
 * on every call rather than being frozen on this API instance. {@code GormValidationApi} instances
 * are registered once per entity class and reused for the lifetime of the owning
 * {@code GormRegistry}/datastore, so permanently caching whichever validator happened to be
 * resolved first would make later calls to {@link MappingContext#addEntityValidator} invisible
 * (this bit Neo4j's TCK manager, which reuses one long-lived datastore/GormEnhancer per spec
 * class and registers a fresh mock validator per test). The {@link MappingContext} itself already
 * caches the resolved validator per entity, so re-resolving here is still cheap.
 *
 * An explicitly assigned validator (via {@link GormValidationApi#setValidator}) is the one
 * exception that remains sticky, since that is a deliberate, permanent override rather than an
 * auto-discovered lookup.
 */
class GormValidationApiSpec extends Specification {

    static class Foo {
    }

    void "getValidator re-resolves an auto-discovered validator from the MappingContext on every call"() {
        given:
        PersistentEntity persistentEntity = Mock(PersistentEntity)
        Validator firstValidator = Mock(Validator)
        Validator secondValidator = Mock(Validator)
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntity(Foo.name) >> persistentEntity
        }
        Datastore datastore = Mock(Datastore) {
            getMappingContext() >> mappingContext
        }
        GormValidationApi<Foo> api = new GormValidationApi<>(Foo, datastore)

        when:
        Validator first = api.getValidator()

        then: "the first call resolves whatever validator is currently registered"
        1 * mappingContext.getEntityValidator(persistentEntity) >> firstValidator
        first == firstValidator

        when: "the registered validator is replaced (e.g. via MappingContext#addEntityValidator)"
        Validator second = api.getValidator()

        then: "the replacement is picked up rather than the stale first result"
        1 * mappingContext.getEntityValidator(persistentEntity) >> secondValidator
        second == secondValidator
    }

    void "getValidator returns the validator explicitly set via setValidator without consulting the MappingContext"() {
        given:
        PersistentEntity persistentEntity = Mock(PersistentEntity)
        Validator explicitValidator = Mock(Validator)
        MappingContext mappingContext = Mock(MappingContext) {
            getPersistentEntity(Foo.name) >> persistentEntity
        }
        Datastore datastore = Mock(Datastore) {
            getMappingContext() >> mappingContext
        }
        GormValidationApi<Foo> api = new GormValidationApi<>(Foo, datastore)
        api.setValidator(explicitValidator)

        when:
        Validator first = api.getValidator()
        Validator second = api.getValidator()

        then: "the explicit override sticks and the MappingContext is never consulted"
        0 * mappingContext.getEntityValidator(_)
        first == explicitValidator
        second == explicitValidator
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
