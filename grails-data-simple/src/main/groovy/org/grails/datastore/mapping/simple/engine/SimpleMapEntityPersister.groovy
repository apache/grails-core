/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.simple.engine

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.IdentityGenerationException
import org.grails.datastore.mapping.core.OptimisticLockingException
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.engine.AssociationIndexer
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.engine.PropertyValueIndexer
import org.grails.datastore.mapping.keyvalue.engine.AbstractKeyValueEntityPersister
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.simple.query.SimpleMapQuery
import org.springframework.context.ApplicationEventPublisher

/**
 * A simple implementation of the {@link org.grails.datastore.mapping.engine.EntityPersister} abstract class that backs onto an in-memory map.
 * Mainly used for mocking and testing scenarios
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleMapEntityPersister extends AbstractKeyValueEntityPersister<Map, Object> {

    Map<String, Map> datastore
    Map indices
    def lastKey
    String family

    SimpleMapEntityPersister(MappingContext context, PersistentEntity entity, Session session,
                             SimpleMapDatastore datastore, ApplicationEventPublisher publisher) {
        super(context, entity, session, publisher)
        this.datastore = datastore.backingMap
        this.indices = datastore.indices
        family = getFamily(entity, entity.getMapping())
        final identity = entity.getIdentity()
        def idType = identity?.type
        if (this.datastore[family] == null) this.datastore[family] = [:]

        if (idType == Integer) {
            lastKey = this.datastore[family].size()
        }
        else {
            lastKey = this.datastore[family].size().longValue()
        }
    }

    protected PersistentEntity discriminatePersistentEntity(PersistentEntity persistentEntity, Map nativeEntry) {
        def disc = nativeEntry?.discriminator
        if (disc) {
            def childEntity = getMappingContext().getChildEntityByDiscriminator(persistentEntity.rootEntity, disc)
            if (childEntity) return childEntity
        }
        return persistentEntity
    }

    Query createQuery() {
        return new SimpleMapQuery(session, getPersistentEntity(), this)
    }

    protected void deleteEntry(String family, key, entry) {
        datastore[family].remove(key)
        def parent = persistentEntity.parentEntity
        while (parent != null) {
            def f = getFamily(parent, parent.mapping)
            datastore[f].remove(key)
            parent = parent.parentEntity
        }
    }

    @Override
    protected boolean isPropertyIndexed(Property mappedProperty) {
        return true // index all
    }

    PropertyValueIndexer getPropertyIndexer(PersistentProperty property) {
        return new PropertyValueIndexer() {

            String getIndexRoot() {
                return "~${property.owner.rootEntity.name}:${property.name}"
            }

            void deindex(value, primaryKey) {
                def index = getIndexName(value)
                List indexed = indices[index]
                if (indexed) {
                    indexed.removeElement(primaryKey)
                }
            }

            void index(value, primaryKey) {

                def index = getIndexName(value)
                def indexed = indices[index]
                if (indexed == null) {
                    indexed = []
                    indices[index] = indexed
                }
                if (!indexed.contains(primaryKey)) {
                    indexed << primaryKey
                }
            }

            List query(value) {
                query(value, 0, -1)
            }

            List query(value, int offset, int max) {
                def index = getIndexName(value)

                def indexed = indices[index]
                if (!indexed) {
                    return Collections.emptyList()
                }
                return indexed[offset..max]
            }

            String getIndexName(value) {
                return "${indexRoot}:$value"
            }
        }
    }

    AssociationIndexer getAssociationIndexer(Map nativeEntry, Association association) {
        if(association?.associatedEntity == null) {
            return null
        }

        return new AssociationIndexer() {

            private getIndexName(primaryKey) {
                "~${association.owner.name}:${association.name}:$primaryKey"
            }

            @Override
            boolean doesReturnKeys() {
                return true
            }

            @Override
            void preIndex(Object primaryKey, List foreignKeys) {
                // handled by index below.
            }

            void index(primaryKey, List foreignKeys) {
                def indexed = getIndex(primaryKey)

                indexed.addAll(foreignKeys)
                def index = getIndexName(primaryKey)
                indexed = indexed.unique()
                indices[index] = indexed
            }

            private List getIndex(primaryKey) {
                def index = getIndexName(primaryKey)
                def indexed = indices[index]
                if (indexed == null) {
                    indexed = []
                    indices[index] = indexed
                }
                return indexed
            }

            void index(primaryKey, foreignKey) {
                def indexed = getIndex(primaryKey)
                if (!indexed.contains(foreignKey))
                    indexed.add(foreignKey)
            }

            List query(primaryKey) {
                def index = getIndexName(primaryKey)
                def indexed = indices[index]
                if (indexed == null) {
                    return Collections.emptyList()
                }
                return indexed
            }

            PersistentEntity getIndexedEntity() {
                return association.associatedEntity
            }
        }
    }

    @Override
    protected void setManyToMany(PersistentEntity persistentEntity, Object obj, Map nativeEntry, ManyToMany manyToMany, Collection associatedObjects, Map<Association, List<Serializable>> toManyKeys) {

        def identifiers
        if (manyToMany.isOwningSide()) {
            identifiers = session.persist(associatedObjects)
        }
        else {
            identifiers = associatedObjects.collect {
                EntityPersister persister = session.getPersister(it)
                persister.getObjectIdentifier(it)
            }
        }
        toManyKeys.put(manyToMany, identifiers)
    }

    @Override
    protected Collection getManyToManyKeys(PersistentEntity persistentEntity, Object obj, Serializable nativeKey, Map nativeEntry, ManyToMany manyToMany) {
        final indexer = getAssociationIndexer(nativeEntry, manyToMany)
        final primaryKey = getObjectIdentifier(obj)
        indexer.query(primaryKey)
    }

    protected Map createNewEntry(String family) {
        return [:]
    }

    protected getEntryValue(Map nativeEntry, String property) {
        return nativeEntry[property]
    }

    protected void setEntryValue(Map nativeEntry, String key, value) {
        if (mappingContext.isPersistentEntity(value)) {
            EntityPersister persister = session.getPersister(value)
            value = persister.getObjectIdentifier(value)
        }
        nativeEntry[key] = value
    }

    protected void setEmbedded(Map nativeEntry, String key, Map values) {
        nativeEntry[key] = values
    }

    protected Map getEmbedded( Map nativeEntry, String key) {
        nativeEntry[key]
    }

    protected Map retrieveEntry(PersistentEntity persistentEntity, String family, Serializable key) {
        Map entry = datastore[family].get(key)
        if (entry != null) {
            // returning a copy is important here so that updates are applied to the copy and not the original
            return new LinkedHashMap<>(entry)
        }
        return null
    }

    protected generateIdentifier(PersistentEntity persistentEntity, Map id) {
        final isRoot = persistentEntity.root
        final type = isRoot ? persistentEntity.identity.type : persistentEntity.rootEntity.identity.type
        if ((String.isAssignableFrom(type)) || (Number.isAssignableFrom(type))) {
            def key
            if (isRoot) {
                key = ++lastKey
            }
            else {
                def root = persistentEntity.rootEntity
                session.getPersister(root).lastKey++
                key = session.getPersister(root).lastKey
            }
            return type == String ? key.toString() : key
        }
        else if (UUID.isAssignableFrom(type)) {
          return UUID.randomUUID()
        }
        else {
            try {
                return type.newInstance()
            } catch (e) {
                throw new IdentityGenerationException("Cannot generator identity for entity $persistentEntity with type $type")
            }
        }
    }

    protected storeEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, storeId, Map nativeEntry) {
        if (!persistentEntity.root) {
            nativeEntry.discriminator = persistentEntity.discriminator
        }
        datastore[family].put(storeId, nativeEntry)
        indexIdentifier(persistentEntity, storeId)
        updateInheritanceHierarchy(persistentEntity, storeId, nativeEntry)
        return storeId
    }

    protected def indexIdentifier(PersistentEntity persistentEntity, storeId) {
        final indexer = getPropertyIndexer(persistentEntity.identity)
        indexer.index(storeId, storeId)
    }

    private updateInheritanceHierarchy(PersistentEntity persistentEntity, storeId, Map nativeEntry) {
        def parent = persistentEntity.parentEntity
        while (parent != null) {

            def f = getFamily(parent, parent.mapping)
            def parentEntry = datastore[f]
            if (parentEntry == null) {
                parentEntry = [:]
                datastore[f] = parentEntry
            }
            parentEntry.put(storeId, nativeEntry)
            parent = parent.parentEntity
        }
    }

    protected void updateEntry(PersistentEntity persistentEntity, EntityAccess entityAccess, key, Map entry) {
        def family = getFamily(persistentEntity, persistentEntity.getMapping())
        def existing = datastore[family].get(key)

        if (isVersioned(entityAccess)) {
            if (existing == null) {
                setVersion entityAccess
            }
            else {
                def oldVersion = existing.version
                def currentVersion = entityAccess.getProperty('version')
                if (Number.isAssignableFrom(entityAccess.getPropertyType('version'))) {
                    oldVersion = existing.version?.toLong()
                    currentVersion = entityAccess.getProperty('version')?.toLong()
                    if (currentVersion == null && oldVersion == null) {
                        currentVersion = 0L
                        entityAccess.setProperty("version", currentVersion)
                        entry["version"] = currentVersion
                    }
                }
                if (oldVersion != null && currentVersion != null && !oldVersion.equals(currentVersion)) {
                    throw new OptimisticLockingException(persistentEntity, key)
                }
                incrementVersion(entityAccess)
            }
        }

        indexIdentifier(persistentEntity, key)
        if (existing == null) {
            datastore[family].put(key, entry)
        }
        else {
            existing.putAll(entry)
        }
        updateInheritanceHierarchy(persistentEntity, key, entry)
    }

    protected void deleteEntries(String family, List<Object> keys) {
        keys?.each {
            deleteEntry(family, it, null)
        }
    }
}
