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
package org.apache.grails.data.mongo.core.querytail

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.model.EmbeddedPersistentEntity
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.mongo.MongoConstants
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * The field names of the collections of a MongoDB datastore, as its mapping declares them, which is
 * what {@link MongoCommandRedactor} keeps the keys of a command by. A key is a field name when its
 * path from the top of a document resolves through the mapping of an entity stored in the
 * collection, stepping into embedded entities and over the indexes of a list. A path into a map
 * property, into a property the mapping does not know, such as a dynamic attribute, or into a
 * collection no entity is stored in resolves to nothing, as the keys found there may be data.
 *
 * @since 8.1
 */
@CompileStatic
class MongoMappedFields {

    private final MongoDatastore datastore
    private volatile Map<String, List<PersistentEntity>> entitiesByCollection = Collections.emptyMap()
    private volatile int entityCount = -1

    /**
     * @param datastore the datastore whose mapping names the fields
     */
    MongoMappedFields(MongoDatastore datastore) {
        this.datastore = datastore
    }

    /**
     * @param collection the collection a command runs against
     * @param path the path of a key from the top of a document, one segment per level, a numeric
     * segment being an index into a list
     * @return whether the mapping knows the path as a field
     */
    boolean isField(String collection, List<String> path) {
        if (collection == null || path.isEmpty()) {
            return false
        }
        for (PersistentEntity entity in entitiesOf(collection)) {
            if (resolves(entity, path)) {
                return true
            }
        }
        false
    }

    private static boolean resolves(PersistentEntity entity, List<String> path) {
        PersistentEntity current = entity
        int last = path.size() - 1
        for (int i = 0; i <= last; i++) {
            String segment = path[i]
            if (isIndex(segment)) {
                continue
            }
            if (segment == MongoConstants.MONGO_ID_FIELD || segment == MongoConstants.MONGO_CLASS_FIELD) {
                return i == last
            }
            PersistentProperty property = propertyOf(current, segment)
            if (property == null) {
                return false
            }
            if (i == last) {
                return true
            }
            if (property instanceof Embedded) {
                current = ((Embedded) property).associatedEntity
            }
            else if (property instanceof EmbeddedCollection) {
                current = ((EmbeddedCollection) property).associatedEntity
            }
            else {
                return false
            }
            if (current == null) {
                return false
            }
        }
        true
    }

    /**
     * @param collection the collection a command runs against
     * @param path the field path of a document, as {@link #isField} takes it
     * @return whether the mapping knows the path as a field that holds an array, whose elements a
     * numeric key after it indexes; a map property is not one, so a numeric key in it is data
     */
    boolean isIndexed(String collection, List<String> path) {
        if (collection == null || path.isEmpty()) {
            return false
        }
        for (PersistentEntity entity in entitiesOf(collection)) {
            PersistentProperty property = propertyAt(entity, path)
            if (property != null && (property.type.isArray() || Collection.isAssignableFrom(property.type))) {
                return true
            }
        }
        false
    }

    /** @return the property the path ends at, or null when the mapping does not know the path as a field */
    private static PersistentProperty propertyAt(PersistentEntity entity, List<String> path) {
        PersistentEntity current = entity
        PersistentProperty property = null
        int last = path.size() - 1
        for (int i = 0; i <= last; i++) {
            String segment = path[i]
            if (isIndex(segment)) {
                continue
            }
            if (segment == MongoConstants.MONGO_ID_FIELD || segment == MongoConstants.MONGO_CLASS_FIELD) {
                return null
            }
            property = propertyOf(current, segment)
            if (property == null || i == last) {
                return property
            }
            if (property instanceof Embedded) {
                current = ((Embedded) property).associatedEntity
            }
            else if (property instanceof EmbeddedCollection) {
                current = ((EmbeddedCollection) property).associatedEntity
            }
            else {
                return null
            }
            if (current == null) {
                return null
            }
        }
        property
    }

    private static PersistentProperty propertyOf(PersistentEntity entity, String field) {
        for (PersistentProperty property in entity.persistentProperties) {
            if (MappingUtils.getTargetKey(property) == field) {
                return property
            }
        }
        for (PersistentProperty property in [entity.identity, entity.version]) {
            if (property != null && MappingUtils.getTargetKey(property) == field) {
                return property
            }
        }
        null
    }

    /** @return whether a segment of a path is an index into a list: digits only */
    @PackageScope
    static boolean isIndex(String segment) {
        if (segment.isEmpty()) {
            return false
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return false
            }
        }
        true
    }

    /**
     * The entities stored in a collection, every subclass of a root entity being stored with it,
     * gathered again when the mapping has gained entities.
     */
    private List<PersistentEntity> entitiesOf(String collection) {
        Collection<PersistentEntity> entities = datastore.mappingContext.persistentEntities
        if (entities.size() != entityCount) {
            Map<String, List<PersistentEntity>> byCollection = [:]
            for (PersistentEntity entity in entities) {
                if (entity instanceof EmbeddedPersistentEntity) {
                    continue
                }
                String name = datastore.getCollectionName(entity)
                List<PersistentEntity> stored = byCollection.get(name)
                if (stored == null) {
                    stored = []
                    byCollection.put(name, stored)
                }
                stored.add(entity)
            }
            entitiesByCollection = byCollection
            entityCount = entities.size()
        }
        entitiesByCollection.get(collection) ?: Collections.<PersistentEntity>emptyList()
    }
}
