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
package org.grails.datastore.gorm.mongo.bugs

import grails.persistence.Entity
import org.bson.types.ObjectId
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettingsBuilder
import org.grails.datastore.mapping.mongo.engine.MongoIdCoercion
import spock.lang.Specification

/**
 * Unit coverage for {@link MongoIdCoercion}, the helper both directions of the codec and
 * {@code MongoQuery} rely on to move identifiers between the type a domain declares and the
 * type actually stored in {@code _id}.
 *
 * Uses {@link MongoMappingContext} directly -- no live MongoDB -- because this is purely
 * about type resolution and conversion. The end-to-end behaviour is covered by
 * {@link StringIdWithObjectIdStorageSpec} and {@link StringIdAssociationStorageSpec}.
 */
class MongoIdCoercionSpec extends Specification {

    private MongoMappingContext contextFor(Map<String, ?> props, Class... domainClasses) {
        def settings = new MongoConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(props)).build()
        new MongoMappingContext(settings, domainClasses)
    }

    private PersistentEntity entityFor(Class domainClass, Map<String, ?> props = [:]) {
        contextFor(props, domainClass).getPersistentEntity(domainClass.name)
    }

    // ---------- resolveStoredAs ----------

    void "resolveStoredAs reports the storage type a String-id domain resolved to"() {
        expect:
        MongoIdCoercion.resolveStoredAs(entityFor(CoercionVideo)) == ObjectId
    }

    void "resolveStoredAs reports String when the application pins the old default"() {
        expect:
        MongoIdCoercion.resolveStoredAs(
                entityFor(CoercionVideo, [(MongoSettings.SETTING_STRING_IDS_DEFAULT_STORED_AS): 'string'])) == String
    }

    void "resolveStoredAs returns null for a null entity rather than throwing"() {
        expect:
        MongoIdCoercion.resolveStoredAs(null) == null
    }

    // ---------- coerceIdToStoredType ----------

    void "coerceIdToStoredType converts a hex String to the stored ObjectId"() {
        given:
        String hex = new ObjectId().toHexString()

        when:
        Object coerced = MongoIdCoercion.coerceIdToStoredType(hex, entityFor(CoercionVideo))

        then:
        coerced instanceof ObjectId
        ((ObjectId) coerced).toHexString() == hex
    }

    void "coerceIdToStoredType leaves a value that is already the stored type untouched"() {
        given:
        ObjectId id = new ObjectId()

        expect:
        MongoIdCoercion.coerceIdToStoredType(id, entityFor(CoercionVideo)).is(id)
    }

    void "coerceIdToStoredType returns null for a null key"() {
        expect:
        MongoIdCoercion.coerceIdToStoredType(null, entityFor(CoercionVideo)) == null
    }

    void "coerceIdToStoredType keeps a non-hex natural key as-is instead of producing a null filter"() {
        given: 'a natural key that cannot become an ObjectId -- the encoder writes it as BSON String'
        String naturalKey = 'jsmith@example.com'

        expect: 'the original value, so queries target what the encoder actually wrote'
        MongoIdCoercion.coerceIdToStoredType(naturalKey, entityFor(CoercionVideo)) == naturalKey
    }

    // ---------- coerceIdToDeclaredType ----------

    void "coerceIdToDeclaredType converts a stored ObjectId back to the declared hex String"() {
        given:
        ObjectId id = new ObjectId()

        when:
        Object coerced = MongoIdCoercion.coerceIdToDeclaredType(id, entityFor(CoercionVideo))

        then: 'domain code sees the String it declared, whatever the storage type is'
        coerced instanceof String
        coerced == id.toHexString()
    }

    void "coerceIdToDeclaredType leaves a value that is already the declared type untouched"() {
        given:
        String hex = new ObjectId().toHexString()

        expect:
        MongoIdCoercion.coerceIdToDeclaredType(hex, entityFor(CoercionVideo)).is(hex)
    }

    void "coerceIdToDeclaredType returns the input for null value or null entity"() {
        expect:
        MongoIdCoercion.coerceIdToDeclaredType(null, entityFor(CoercionVideo)) == null
        MongoIdCoercion.coerceIdToDeclaredType('x', null) == 'x'
    }

    void "coerceIdToDeclaredType leaves an ObjectId-id domain alone"() {
        given:
        ObjectId id = new ObjectId()

        expect: 'declared type is already ObjectId, so there is nothing to convert'
        MongoIdCoercion.coerceIdToDeclaredType(id, entityFor(CoercionObjectIdVideo)).is(id)
    }

    void "the two directions round-trip"() {
        given:
        PersistentEntity entity = entityFor(CoercionVideo)
        String hex = new ObjectId().toHexString()

        expect:
        MongoIdCoercion.coerceIdToDeclaredType(
                MongoIdCoercion.coerceIdToStoredType(hex, entity), entity) == hex
    }
}

@Entity
class CoercionVideo {
    String id
    String title
}

@Entity
class CoercionObjectIdVideo {
    ObjectId id
    String title
}
