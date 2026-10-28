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
package org.apache.grails.data.mongodb.core.querytail

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.model.EmbeddedPersistentEntity
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Basic
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
 * <p>The entities of each collection are gathered once, and again only after the mapping has
 * gained an entity, which it says as it adds one, and so are the fields of each entity, by name, so
 * a key costs no walk over every entity, nor over every property of one. The mapping tells the
 * fields of every datastore built on it, and every connection source of those datastores, which
 * share {@linkplain #of one}: the mapping keeps what it tells of its entities for as long as it
 * lives, and names the collection of each entity as every one of those datastores does, so that
 * none of them is kept once it closes.</p>
 *
 * @since 9.0
 */
@CompileStatic
class MongoMappedFields {

    /**
     * The fields of each mapping. The mapping holds its fields, through what it tells of its entities, and
     * nothing else does, so that neither keeps the other once the application lets go of the mapping.
     */
    private static final Map<MappingContext, WeakReference<MongoMappedFields>> BY_MAPPING = new WeakHashMap<>()

    private final MappingContext mapping
    private volatile Map<String, List<PersistentEntity>> entitiesByCollection = Collections.emptyMap()

    /**
     * The properties of each entity of the mapping, and of each entity embedded in one, by the field each is stored
     * in. Keyed by the entity itself, since an entity embedded in another is not the entity of the same class stored
     * in its own collection; never changed once gathered, so that it is read without a lock.
     */
    private volatile Map<PersistentEntity, Map<String, PersistentProperty>> fieldsByEntity = Collections.emptyMap()

    /** How many entities the mapping has said it gained, which the entities of each collection are gathered again for. */
    private final AtomicInteger added = new AtomicInteger()

    /**
     * How many of the entities the mapping said it gained the entities of each collection were gathered after, or -1
     * before they were gathered first; changed only under {@link #gathering}, once what was gathered is in place.
     */
    private volatile int gathered = -1

    /** Taken to gather the entities of each collection, which a key waits for rather than read what was there before. */
    private final Object gathering = new Object()

    /**
     * @param datastore a datastore
     * @return the fields the mapping of the datastore names, the same for every datastore built on that mapping
     */
    static MongoMappedFields of(MongoDatastore datastore) {
        MappingContext mapping = datastore.mappingContext
        synchronized (BY_MAPPING) {
            MongoMappedFields fields = BY_MAPPING.get(mapping)?.get()
            if (fields == null) {
                fields = new MongoMappedFields(mapping)
                BY_MAPPING.put(mapping, new WeakReference<MongoMappedFields>(fields))
            }
            fields
        }
    }

    /**
     * @param mapping the mapping that names the fields
     */
    private MongoMappedFields(MappingContext mapping) {
        this.mapping = mapping
        mapping.addMappingContextListener({ PersistentEntity entity ->
            added.incrementAndGet()
        } as MappingContext.Listener)
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

    /**
     * @return whether the mapping of the entity knows the path as a field: a property it declares, or the identity or
     * the class of the document, or of a document embedded in it, which every document has whatever its mapping says
     */
    private boolean resolves(PersistentEntity entity, List<String> path) {
        int last = path.size() - 1
        if (isDocumentField(path[last])) {
            List<String> document = path.subList(0, last)
            return document.every { String segment -> isIndex(segment) } || embeddedIn(propertyAt(entity, document)) != null
        }
        propertyAt(entity, path) != null
    }

    /**
     * @param collection the collection a command runs against
     * @param path the field path of a document, as {@link #isField} takes it
     * @param depth how many indices into the field have been taken: 0 for the field itself, 1 for
     * one of its elements, which is an array only when the field is a list of lists
     * @return whether the mapping knows the path, at that depth, as an array whose elements a numeric
     * key indexes; a map property, or an element that is a map or a document, is not one, so a
     * numeric key in it is data
     */
    boolean isIndexed(String collection, List<String> path, int depth) {
        if (collection == null || path.isEmpty()) {
            return false
        }
        for (PersistentEntity entity in entitiesOf(collection)) {
            PersistentProperty property = propertyAt(entity, path)
            if (property != null && indexedAt(property, depth)) {
                return true
            }
        }
        false
    }

    private static boolean indexedAt(PersistentProperty property, int depth) {
        Class<?> type = property.type
        if (depth == 0) {
            return type.isArray() || Collection.isAssignableFrom(type)
        }
        if (depth > 1) {
            return false
        }
        Class<?> element = type.isArray() ? type.componentType : (property instanceof Basic ? ((Basic) property).componentType : null)
        element != null && (element.isArray() || Collection.isAssignableFrom(element))
    }

    /**
     * Walks a path through the mapping of an entity, stepping into the entities embedded in it and over the indexes of a
     * list.
     *
     * @return the property the path ends at, or null when the mapping does not know the path as a property
     */
    private PersistentProperty propertyAt(PersistentEntity entity, List<String> path) {
        PersistentEntity current = entity
        PersistentProperty property = null
        int last = path.size() - 1
        for (int i = 0; i <= last; i++) {
            String segment = path[i]
            if (isIndex(segment)) {
                continue
            }
            if (isDocumentField(segment)) {
                return null
            }
            property = propertyOf(current, segment)
            if (property == null || i == last) {
                return property
            }
            current = embeddedIn(property)
            if (current == null) {
                return null
            }
        }
        property
    }

    /** @return the entity a property embeds, one document or a collection of them, or null when it embeds none */
    private static PersistentEntity embeddedIn(PersistentProperty property) {
        if (property instanceof Embedded) {
            return ((Embedded) property).associatedEntity
        }
        if (property instanceof EmbeddedCollection) {
            return ((EmbeddedCollection) property).associatedEntity
        }
        null
    }

    /** @return whether a field is one every document has, its identity or its class */
    private static boolean isDocumentField(String field) {
        field == MongoConstants.MONGO_ID_FIELD || field == MongoConstants.MONGO_CLASS_FIELD
    }

    private PersistentProperty propertyOf(PersistentEntity entity, String field) {
        Map<String, PersistentProperty> fields = fieldsByEntity.get(entity)
        // an entity gathered after a path reached it, as one the mapping gained while it was being walked
        (fields != null ? fields : fieldsOf(entity)).get(field)
    }

    /** Gathers the fields of an entity, and of every entity embedded in it, that are not gathered yet. */
    private static void gatherFields(PersistentEntity entity, Map<PersistentEntity, Map<String, PersistentProperty>> gathered) {
        if (entity == null || gathered.containsKey(entity)) {
            return
        }
        gathered.put(entity, fieldsOf(entity))
        for (PersistentProperty property in entity.persistentProperties) {
            gatherFields(embeddedIn(property), gathered)
        }
    }

    /**
     * @return the properties of an entity by the field each is stored in, the first one stored in a field
     * winning: its persistent properties in order, then its identity and its version
     */
    private static Map<String, PersistentProperty> fieldsOf(PersistentEntity entity) {
        Map<String, PersistentProperty> fields = new HashMap<>()
        for (PersistentProperty property in entity.persistentProperties) {
            fields.putIfAbsent(MappingUtils.getTargetKey(property), property)
        }
        for (PersistentProperty property in [entity.identity, entity.version]) {
            if (property != null) {
                fields.putIfAbsent(MappingUtils.getTargetKey(property), property)
            }
        }
        fields
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
     * gathered again when the mapping has gained entities, as the fields of each entity are. A key
     * asked for while they are gathered waits for them, rather than read the entities gathered
     * before, which the first time are none. They are gathered for the entities the mapping had said
     * it gained when gathering began, so that an entity added while they are has them gathered again
     * for the next key.
     */
    private List<PersistentEntity> entitiesOf(String collection) {
        if (gathered != added.get()) {
            gather()
        }
        entitiesByCollection.get(collection) ?: Collections.<PersistentEntity>emptyList()
    }

    private void gather() {
        synchronized (gathering) {
            int generation = added.get()
            if (gathered == generation) {
                // gathered by another key while this one waited
                return
            }
            Map<PersistentEntity, Map<String, PersistentProperty>> fields = new IdentityHashMap<>()
            Map<String, List<PersistentEntity>> byCollection = [:]
            for (PersistentEntity entity in mapping.persistentEntities) {
                gatherFields(entity, fields)
                if (entity instanceof EmbeddedPersistentEntity) {
                    continue
                }
                String name = MongoDatastore.getMappedCollectionName(entity)
                List<PersistentEntity> stored = byCollection.get(name)
                if (stored == null) {
                    stored = []
                    byCollection.put(name, stored)
                }
                stored.add(entity)
            }
            fieldsByEntity = fields
            entitiesByCollection = byCollection
            gathered = generation
        }
    }
}
