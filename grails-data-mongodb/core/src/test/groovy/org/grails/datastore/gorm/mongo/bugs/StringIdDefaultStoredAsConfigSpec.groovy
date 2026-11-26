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
import spock.lang.Specification

/**
 * Exercises the global config {@code grails.mongodb.stringIds.defaultStoredAs}.
 *
 * Uses {@link MongoMappingContext} directly (no live MongoDB) — this spec is purely about
 * how the mapping layer resolves {@code storedAs} from config. The plumbing into the
 * encoder/decoder/query paths is exercised by {@link StringIdWithObjectIdStorageSpec}.
 */
class StringIdDefaultStoredAsConfigSpec extends Specification {

    private MongoMappingContext contextFor(Map<String, ?> props, Class... domainClasses) {
        def settings = new MongoConnectionSourceSettingsBuilder(DatastoreUtils.createPropertyResolver(props)).build()
        new MongoMappingContext(settings, domainClasses)
    }

    void "without the global config, String id has no storedAs (default behavior unchanged)"() {
        given:
        MongoMappingContext ctx = contextFor([:], PlainStringIdDomain)

        when:
        PersistentEntity entity = ctx.getPersistentEntity(PlainStringIdDomain.name)

        then:
        entity != null
        entity.mapping.identifier.storedAs == null
    }

    void "with the global default set to objectid, String id picks up ObjectId storedAs"() {
        given:
        MongoMappingContext ctx = contextFor(
                [(MongoSettings.SETTING_STRING_IDS_DEFAULT_STORED_AS): 'objectid'],
                PlainStringIdDomain
        )

        when:
        PersistentEntity entity = ctx.getPersistentEntity(PlainStringIdDomain.name)

        then:
        entity.mapping.identifier.storedAs == ObjectId
    }

    void "explicit per-domain storedAs wins over the global default"() {
        given: 'global says objectid, but the domain explicitly declares storedAs: String'
        MongoMappingContext ctx = contextFor(
                [(MongoSettings.SETTING_STRING_IDS_DEFAULT_STORED_AS): 'objectid'],
                ExplicitStringStoredAsDomain
        )

        when:
        PersistentEntity entity = ctx.getPersistentEntity(ExplicitStringStoredAsDomain.name)

        then:
        entity.mapping.identifier.storedAs == String
    }

    void "global default only applies to String-id domains, not ObjectId-id ones"() {
        given:
        MongoMappingContext ctx = contextFor(
                [(MongoSettings.SETTING_STRING_IDS_DEFAULT_STORED_AS): 'objectid'],
                ObjectIdDeclaredDomain
        )

        when:
        PersistentEntity entity = ctx.getPersistentEntity(ObjectIdDeclaredDomain.name)

        then: 'domain declared ObjectId id; the global default for String-ids does not apply'
        entity.mapping.identifier.storedAs == null
    }

    void "unrecognized storedAs value parses as null (safe fallback, no boot failure)"() {
        given:
        MongoMappingContext ctx = contextFor(
                [(MongoSettings.SETTING_STRING_IDS_DEFAULT_STORED_AS): 'banana'],
                PlainStringIdDomain
        )

        when:
        PersistentEntity entity = ctx.getPersistentEntity(PlainStringIdDomain.name)

        then:
        entity.mapping.identifier.storedAs == null
    }
}

@Entity
class PlainStringIdDomain {
    String id
    String name
}

@Entity
class ExplicitStringStoredAsDomain {
    String id
    String name
    static mapping = {
        id storedAs: String
    }
}

@Entity
class ObjectIdDeclaredDomain {
    ObjectId id
    String name
}
