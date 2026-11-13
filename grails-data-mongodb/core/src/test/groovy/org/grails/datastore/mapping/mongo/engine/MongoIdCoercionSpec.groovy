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
package org.grails.datastore.mapping.mongo.engine

import org.bson.types.ObjectId
import org.springframework.core.convert.ConversionService
import spock.lang.Specification

import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

class MongoIdCoercionSpec extends Specification {

    void "resolveStoredAs returns null for a null entity"() {
        expect:
        MongoIdCoercion.resolveStoredAs(null) == null
    }

    void "resolveStoredAs returns null when the entity has no mapping"() {
        given:
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> null
        }

        expect:
        MongoIdCoercion.resolveStoredAs(entity) == null
    }

    void "resolveStoredAs returns null when the mapping has no identifier"() {
        given:
        ClassMapping mapping = Mock(ClassMapping) {
            getIdentifier() >> null
        }
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> mapping
        }

        expect:
        MongoIdCoercion.resolveStoredAs(entity) == null
    }

    void "resolveStoredAs returns the identifier's storedAs class"() {
        given:
        IdentityMapping identifier = Mock(IdentityMapping) {
            getStoredAs() >> ObjectId
        }
        ClassMapping mapping = Mock(ClassMapping) {
            getIdentifier() >> identifier
        }
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> mapping
        }

        expect:
        MongoIdCoercion.resolveStoredAs(entity) == ObjectId
    }

    void "resolveStoredAs returns null when reading the mapping throws"() {
        given:
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> { throw new IllegalStateException('boom') }
        }

        expect:
        MongoIdCoercion.resolveStoredAs(entity) == null
    }

    void "coerceIdToStoredType returns null for a null key"() {
        expect:
        MongoIdCoercion.coerceIdToStoredType(null, Mock(PersistentEntity)) == null
    }

    void "coerceIdToStoredType returns the key unchanged when the entity declares no storedAs"() {
        given:
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> null
        }

        expect:
        MongoIdCoercion.coerceIdToStoredType('abc123', entity) == 'abc123'
    }

    void "coerceIdToStoredType returns the key unchanged when it is already an instance of storedAs"() {
        given:
        ObjectId id = new ObjectId()
        IdentityMapping identifier = Mock(IdentityMapping) {
            getStoredAs() >> ObjectId
        }
        ClassMapping mapping = Mock(ClassMapping) {
            getIdentifier() >> identifier
        }
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> mapping
        }

        expect:
        MongoIdCoercion.coerceIdToStoredType(id, entity).is(id)
    }

    void "coerceIdToStoredType converts the key to storedAs when the converter succeeds"() {
        given:
        ObjectId id = new ObjectId()
        ConversionService conversionService = Mock(ConversionService) {
            convert('507f1f77bcf86cd799439011', ObjectId) >> id
        }
        MappingContext mappingContext = Mock(MappingContext) {
            getConversionService() >> conversionService
        }
        IdentityMapping identifier = Mock(IdentityMapping) {
            getStoredAs() >> ObjectId
        }
        ClassMapping mapping = Mock(ClassMapping) {
            getIdentifier() >> identifier
        }
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> mapping
            getMappingContext() >> mappingContext
        }

        expect:
        MongoIdCoercion.coerceIdToStoredType('507f1f77bcf86cd799439011', entity).is(id)
    }

    void "coerceIdToStoredType returns the original key when the converter rejects the value with null"() {
        given: "a non-hex natural-key String against storedAs: ObjectId"
        ConversionService conversionService = Mock(ConversionService) {
            convert('not-a-valid-hex-id', ObjectId) >> null
        }
        MappingContext mappingContext = Mock(MappingContext) {
            getConversionService() >> conversionService
        }
        IdentityMapping identifier = Mock(IdentityMapping) {
            getStoredAs() >> ObjectId
        }
        ClassMapping mapping = Mock(ClassMapping) {
            getIdentifier() >> identifier
        }
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> mapping
            getMappingContext() >> mappingContext
        }

        expect:
        MongoIdCoercion.coerceIdToStoredType('not-a-valid-hex-id', entity) == 'not-a-valid-hex-id'
    }

    void "coerceIdToStoredType returns the original key when the converter throws"() {
        given:
        ConversionService conversionService = Mock(ConversionService) {
            convert(_, _) >> { throw new IllegalArgumentException('cannot convert') }
        }
        MappingContext mappingContext = Mock(MappingContext) {
            getConversionService() >> conversionService
        }
        IdentityMapping identifier = Mock(IdentityMapping) {
            getStoredAs() >> ObjectId
        }
        ClassMapping mapping = Mock(ClassMapping) {
            getIdentifier() >> identifier
        }
        PersistentEntity entity = Mock(PersistentEntity) {
            getMapping() >> mapping
            getMappingContext() >> mappingContext
        }

        expect:
        MongoIdCoercion.coerceIdToStoredType('whatever', entity) == 'whatever'
    }
}
