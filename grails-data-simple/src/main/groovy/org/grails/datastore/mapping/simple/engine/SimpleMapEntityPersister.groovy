/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS licenses this file
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
package org.grails.datastore.mapping.simple.engine

import org.grails.datastore.mapping.engine.AssociationIndexer
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.NativeEntryEntityPersister
import org.grails.datastore.mapping.engine.PropertyValueIndexer
import org.grails.datastore.mapping.keyvalue.engine.AbstractKeyValueEntityPersister
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Custom
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.simple.SimpleMapSession
import org.grails.datastore.mapping.simple.query.SimpleMapQuery
import grails.gorm.multitenancy.Tenants
import org.springframework.context.ApplicationEventPublisher
import org.grails.datastore.mapping.core.OptimisticLockingException
import org.grails.datastore.mapping.core.Session
import java.util.concurrent.ConcurrentHashMap

/**
 * A {@link org.grails.datastore.mapping.engine.EntityPersister} abstract class that backs onto an in-memory map.
 * Mainly used for mocking and testing scenarios
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleMapEntityPersister extends AbstractKeyValueEntityPersister<Map, Object> {

    SimpleMapEntityPersister(MappingContext mappingContext, PersistentEntity entity, Session session, ApplicationEventPublisher publisher) {
        super(mappingContext, entity, session, publisher)
    }

    protected String getEntityFamily(PersistentEntity entity) {
        return entity.rootEntity.name
    }

    protected String getConnectionName() {
        ((SimpleMapDatastore)session.datastore).getConnectionName()
    }

    @Override
    String getEntityFamily() {
        return getEntityFamily(getPersistentEntity())
    }

    protected Map<Serializable, Map> getDatastoreMap() {
        ((SimpleMapSession)session).getBackingMap()
    }

    protected Map getIndices() {
        ((SimpleMapSession)session).getIndices()
    }

    @Override
    protected void setEntryValue(Map nativeEntry, String property, Object value) {
        nativeEntry.put(property, value)
    }

    @Override
    protected Object getEntryValue(Map nativeEntry, String property) {
        return nativeEntry.get(property)
    }

    @Override
    protected Object generateIdentifier(PersistentEntity persistentEntity, Map entry) {
        def identity = persistentEntity.identity
        if (identity != null && identity.type == UUID) {
            return UUID.randomUUID()
        }
        return ((SimpleMapDatastore)session.datastore).nextId(getEntityFamily(persistentEntity))
    }

    @Override
    boolean isDirty(Object entity, Object entry) {
        if (!(entry instanceof Map)) return true
        
        def persistentEntity = getPersistentEntity()
        def reflector = mappingContext.getEntityReflector(persistentEntity)
        
        for (PersistentProperty prop in persistentEntity.persistentProperties) {
            def currentValue = reflector.getProperty(entity, prop.name)
            def entryValue = ((Map)entry).get(prop.name)
            
            def marshalled = marshalProperty(prop, currentValue)
            if (marshalled != entryValue) return true
        }
        return false
    }

    private static final ThreadLocal<Set<Integer>> PERSISTING = ThreadLocal.withInitial { [] as Set }

    private Object marshalProperty(PersistentProperty prop, Object value) {
        if (value == null) return null
        if (prop instanceof Embedded) {
            def associated = prop.associatedEntity
            def embeddedEntry = [:]
            if (associated != null) {
                def embeddedReflector = mappingContext.getEntityReflector(associated)
                for (PersistentProperty embeddedProp in associated.persistentProperties) {
                    embeddedEntry.put(embeddedProp.name, embeddedReflector.getProperty(value, embeddedProp.name))
                }
            } else {
                // Fallback for non-entity embedded types
                def type = prop.type
                for (java.lang.reflect.Field field in type.declaredFields) {
                    if (!field.synthetic && !java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                        field.setAccessible(true)
                        embeddedEntry.put(field.name, field.get(value))
                    }
                }
            }
            return embeddedEntry
        } else if (prop instanceof Association) {
            if (value instanceof Collection) {
                 return value.collect { 
                     if (it == null) return null
                     def persister = session.getPersister(it)
                     return persister != null ? persister.getObjectIdentifier(it) : it
                 }.findAll { it != null }
            } else if (value != null) {
                def persister = session.getPersister(value)
                def id = persister != null ? persister.getObjectIdentifier(value) : value
                return id
            }
            return null
        } else if (prop instanceof Basic || prop instanceof Custom) {
            def marshaller = ((Object)prop).getCustomTypeMarshaller()
            if (marshaller != null && marshaller.supports(mappingContext)) {
                return marshaller.write(prop, value, [:])
            }
        }
        return value
    }

    @Override
    protected void updateEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, Object key, Map entry) {
        def family = getEntityFamily(persistentEntity)
        def dsMap = getDatastoreMap()
        if (dsMap[family] == null) {
            dsMap[family] = new ConcurrentHashMap<>()
        }
        
        Object k = key instanceof Number ? key.longValue() : key
        Map existing = (Map) dsMap[family].get(k)
        

        if (isVersioned(entityAccess)) {
            if (existing == null || isDirty(entityAccess.getEntity(), existing)) {
                incrementVersion(entityAccess)
            }
        }
        
        populateEntry(persistentEntity, entityAccess, entry)
        
        if (existing == null) {
            dsMap[family].put(k, entry)
        }
        else {
            for (PersistentProperty prop in persistentEntity.persistentProperties) {
                def oldVal = existing.get(prop.name)
                def newVal = entry.get(prop.name)
                if (oldVal != newVal) {
                    def indexer = getPropertyIndexer(prop)
                    if (indexer != null && oldVal != null) {
                        indexer.deindex(oldVal, k)
                    }
                }
            }
            existing.putAll(entry)
        }
        updateInheritanceHierarchy(persistentEntity, k, entry)
    }

    @Override
    protected Object storeEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, Object storeId, Map nativeEntry) {
        if (isVersioned(entityAccess)) {
            setVersion(entityAccess)
        }
        populateEntry(persistentEntity, entityAccess, nativeEntry)
        def f = getEntityFamily(persistentEntity)
        def dsMap = getDatastoreMap()
        Map<Object, Map> familyMap = (Map) dsMap[f]
        if (familyMap == null) {
            familyMap = new ConcurrentHashMap<>()
            dsMap.put(f, familyMap)
        }
        Object k = storeId instanceof Number ? storeId.longValue() : storeId
        familyMap.put(k, nativeEntry)
        updateInheritanceHierarchy(persistentEntity, k, nativeEntry)
        return k
    }

    private void populateEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, Map entry) {
        if (!persistentEntity.root) {
            entry.discriminator = persistentEntity.discriminator
        }
        if (persistentEntity.identity != null) {
            entry.put(persistentEntity.identity.name, entityAccess.getIdentifier())
        }
        for (PersistentProperty prop in persistentEntity.persistentProperties) {
            def value = entityAccess.getProperty(prop.name)
            entry.put(prop.name, marshalProperty(prop, value))
        }
    }

    @Override
    protected Map createNewEntry(String family) {
        return [:]
    }

    @Override
    protected Map retrieveEntry(PersistentEntity persistentEntity, String family, Serializable key) {
        def dsMap = getDatastoreMap()
        Map familyMap = (Map) dsMap[family]
        if (familyMap == null) return null
        Map entry = (Map) familyMap.get(key instanceof Number ? key.longValue() : key)
        if (entry != null && persistentEntity.isMultiTenant()) {
            SimpleMapDatastore datastore = (SimpleMapDatastore) session.datastore
            if (datastore.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
                def currentId = Tenants.currentId(datastore)
                if (currentId != null) {
                    def entryTenantId = entry.get("tenantId")
                    if (entryTenantId != null && entryTenantId.toString() != currentId.toString()) {
                        return null
                    }
                }
            }
        }
        return entry != null ? new HashMap(entry) : null
    }

    @Override
    protected void deleteEntry(String family, key, entry) {
        def dsMap = getDatastoreMap()
        def familyMap = (Map) dsMap[family]
        if (familyMap != null) {
            def k = key instanceof Number ? key.longValue() : key
            def existing = familyMap.get(k)
            if (existing instanceof Map) {
                for (PersistentProperty prop in persistentEntity.persistentProperties) {
                    def indexer = getPropertyIndexer(prop)
                    if (indexer != null) {
                        def val = existing.get(prop.name)
                        if (val != null) {
                            indexer.deindex(val, k)
                        }
                    }
                }
            }
            familyMap.remove(k)
        }
    }

    @Override
    PropertyValueIndexer getPropertyIndexer(PersistentProperty property) {
        return new PropertyValueIndexer() {
            private String getIndexRoot() {
                return "~${property.owner.rootEntity.name}:${property.name}"
            }

            @Override
            String getIndexName(Object value) {
                "${getIndexRoot()}:${value}"
            }

            @Override
            void index(Object value, Object primaryKey) {
                if (value == null || primaryKey == null) return
                def index = getIndexName(value)
                def indicesMap = getIndices()
                def indexed = (List) indicesMap[index]
                if (indexed == null) {
                    indexed = []
                    indicesMap[index] = indexed
                }
                def pk = primaryKey instanceof Number ? primaryKey.longValue() : primaryKey
                if (!indexed.contains(pk)) {
                    indexed << pk
                }
            }

            @Override
            List query(Object value) {
                query(value, 0, -1)
            }

            @Override
            List query(Object value, int offset, int max) {
                def index = getIndexName(value)
                def results = (List) getIndices()[index] ?: []
                if (offset > 0 && max > 0) {
                    int last = offset + max - 1
                    if (offset >= results.size()) return []
                    return results[offset..Math.min(last, results.size() - 1)]
                }
                if (max > 0) {
                    return results[0..Math.min(max - 1, results.size() - 1)]
                }
                if (offset > 0) {
                    if (offset >= results.size()) return []
                    return results[offset..(results.size() - 1)]
                }
                return results
            }

            @Override
            void deindex(Object value, Object primaryKey) {
                def index = getIndexName(value)
                def pk = primaryKey instanceof Number ? primaryKey.longValue() : primaryKey
                ((List) getIndices()[index])?.remove(pk)
            }
        }
    }

    @Override
    AssociationIndexer getAssociationIndexer(Map nativeEntry, Association association) {
        return new AssociationIndexer() {
            @Override
            boolean doesReturnKeys() {
                return true
            }

            @Override
            void preIndex(Object primaryKey, List foreignKeys) {
                // no-op
            }

            @Override
            void index(Object primaryKey, List foreignKeys) {
                if (primaryKey == null || foreignKeys == null) return
                def k = primaryKey instanceof Number ? primaryKey.longValue() : primaryKey
                def index = getIndexName(k)
                getIndices()[index] = foreignKeys.collect { it instanceof Number ? it.longValue() : it }.findAll { it != null }
            }

            @Override
            void index(Object primaryKey, Object foreignKey) {
                if (primaryKey == null || foreignKey == null) return
                Object k = primaryKey instanceof Number ? primaryKey.longValue() : primaryKey
                Object f = foreignKey instanceof Number ? foreignKey.longValue() : foreignKey
                
                if (association.getAssociatedEntity().getJavaClass().isInstance(primaryKey)) {
                    Object temp = k
                    k = f
                    f = temp
                }
                
                def index = getIndexName(k)
                def indexed = (List) getIndices()[index]
                if (indexed == null) {
                    indexed = []
                    getIndices()[index] = indexed
                }
                if (!indexed.contains(f)) {
                    indexed << f
                }
            }

            @Override
            List query(Object primaryKey) {
                def k = primaryKey instanceof Number ? primaryKey.longValue() : primaryKey
                def index = getIndexName(k)
                return (List) getIndices()[index] ?: []
            }

            @Override
            PersistentEntity getIndexedEntity() {
                return association.getAssociatedEntity()
            }

            void deindex(Object primaryKey, Object foreignKey) {
                def k = primaryKey instanceof Number ? primaryKey.longValue() : primaryKey
                def f = foreignKey instanceof Number ? foreignKey.longValue() : foreignKey
                def index = getIndexName(k)
                ((List) getIndices()[index])?.remove(f)
            }

            void deindex(Object primaryKey) {
                def k = primaryKey instanceof Number ? primaryKey.longValue() : primaryKey
                def index = getIndexName(k)
                getIndices().remove(index)
            }

            private String getIndexName(Object primaryKey) {
                def connectionName = getConnectionName()
                def k = primaryKey instanceof Number ? primaryKey.longValue() : primaryKey
                def indexName = "~${association.owner.rootEntity.name}:${association.name}:${k}"
                if (connectionName != null && !org.grails.datastore.mapping.core.connections.ConnectionSource.DEFAULT.equals(connectionName)) {
                    indexName = "${connectionName}:${indexName}"
                }
                return indexName
            }
        }
    }

    @Override
    protected void setManyToMany(PersistentEntity persistentEntity, Object obj,
                                 Map nativeEntry, org.grails.datastore.mapping.model.types.ManyToMany manyToMany, Collection associatedObjects,
                                 Map<org.grails.datastore.mapping.model.types.Association, List<Serializable>> toManyKeys) {
        if (associatedObjects != null) {
            def keys = []
            for (associated in associatedObjects) {
                if (associated == null) continue
                def hash = System.identityHashCode(associated)
                if (!PERSISTING.get().contains(hash)) {
                    PERSISTING.get().add(hash)
                    try {
                        keys << session.persist(associated)
                    } finally {
                        PERSISTING.get().remove(hash)
                    }
                } else {
                    keys << session.getObjectIdentifier(associated)
                }
            }
            keys = keys.findAll { it != null }
            toManyKeys.put(manyToMany, keys)
            nativeEntry.put(manyToMany.name, keys)
        }
    }

    @Override
    protected PersistentEntity discriminatePersistentEntity(PersistentEntity persistentEntity, Map nativeEntry) {
        def disc = nativeEntry?.get("discriminator")
        if (disc) {
            def child = mappingContext.getChildEntityByDiscriminator(persistentEntity.rootEntity, disc.toString())
            if (child) return child
        }
        return persistentEntity
    }

    protected void updateInheritanceHierarchy(PersistentEntity persistentEntity, Object key, Map entry) {
        def parent = persistentEntity.parentEntity
        while (parent != null) {
            def f = getEntityFamily(parent)
            def dsMap = getDatastoreMap()
            Map<Object, Map> parentMap = (Map) dsMap[f]
            if (parentMap == null) {
                parentMap = new ConcurrentHashMap()
                dsMap.put(f, parentMap)
            }
            parentMap.put(key instanceof Number ? key.longValue() : key, entry)
            parent = parent.parentEntity
        }
    }

    @Override
    Object createObjectFromNativeEntry(PersistentEntity persistentEntity, Serializable nativeKey, Map nativeEntry) {
        def obj = super.createObjectFromNativeEntry(persistentEntity, nativeKey, nativeEntry)
        def reflector = mappingContext.getEntityReflector(persistentEntity)
        
        for (PersistentProperty prop in persistentEntity.persistentProperties) {
            if (prop instanceof Embedded) {
                def embeddedEntry = nativeEntry.get(prop.name)
                if (embeddedEntry instanceof Map) {
                    def type = prop.type
                    def embeddedInstance = type.newInstance()
                    def associated = prop.associatedEntity
                    if (associated != null) {
                        def embeddedReflector = mappingContext.getEntityReflector(associated)
                        for (PersistentProperty embeddedProp in associated.persistentProperties) {
                            embeddedReflector.setProperty(embeddedInstance, embeddedProp.name, embeddedEntry.get(embeddedProp.name))
                        }
                    } else {
                        // Fallback for non-entity embedded types
                        for (java.lang.reflect.Field field in type.declaredFields) {
                            if (!field.synthetic && !java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                                field.setAccessible(true)
                                field.set(embeddedInstance, embeddedEntry.get(field.name))
                            }
                        }
                    }
                    reflector.setProperty(obj, prop.name, embeddedInstance)
                }
            }
        }
        return obj
    }

    @Override
    protected Collection getManyToManyKeys(PersistentEntity persistentEntity, Object obj,
            Serializable nativeKey, Map nativeEntry, org.grails.datastore.mapping.model.types.ManyToMany manyToMany) {
        def val = nativeEntry.get(manyToMany.getName())
        if (val instanceof Collection) {
            return (Collection) val.findAll { it != null }
        }
        return Collections.emptyList()
    }

    @Override
    org.grails.datastore.mapping.query.Query createQuery() {
        return new org.grails.datastore.mapping.simple.query.SimpleMapQuery((org.grails.datastore.mapping.simple.SimpleMapSession)session, getPersistentEntity(), this)
    }

    @Override
    protected void deleteEntries(String family, List<Object> keys) {
        keys?.each {
            deleteEntry(family, it, null)
        }
    }
}
