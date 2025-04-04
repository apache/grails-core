/*
 * Copyright 2015 original authors
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
package org.grails.datastore.mapping.mongo.engine

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import grails.gorm.DetachedCriteria
import groovy.transform.CompileStatic
import org.bson.Document
import org.bson.types.ObjectId
import org.grails.datastore.mapping.cache.TPCacheAdapterRepository
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.IdentityGenerationException
import org.grails.datastore.mapping.core.SessionImplementor
import org.grails.datastore.mapping.core.impl.PendingDeleteAdapter
import org.grails.datastore.mapping.core.impl.PendingInsertAdapter
import org.grails.datastore.mapping.core.impl.PendingOperationAdapter
import org.grails.datastore.mapping.core.impl.PendingUpdateAdapter
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckableCollection
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingCollection
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.ThirdPartyCacheEntityPersister
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.model.*
import org.grails.datastore.mapping.model.types.*
import org.grails.datastore.mapping.mongo.MongoCodecSession
import org.grails.datastore.mapping.mongo.MongoConstants
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.query.MongoQuery
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.reflect.FieldEntityAccess
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DataIntegrityViolationException

import jakarta.persistence.CascadeType

/**
 * An {@org.grails.datastore.mapping.engine.EntityPersister} that uses the MongoDB 3.0 {@link org.bson.codecs.configuration.CodecRegistry} infrastructure
 *
 * @author Graeme Rocher
 * @since 5.0.0
 */
@CompileStatic
class MongoCodecEntityPersister extends ThirdPartyCacheEntityPersister<Object> {

    public static final String INSTANCE_PREFIX = "instance:"
    public static final String MONGO_ID_FIELD = MongoConstants.MONGO_ID_FIELD
    public static final String MONGO_CLASS_FIELD = MongoConstants.MONGO_CLASS_FIELD
    protected static final String NEXT_ID = "next_id"
    protected static final String NEXT_ID_SUFFIX = ".$NEXT_ID"
    public static final String INC_OPERATOR = MongoConstants.INC_OPERATOR
    public static final String ASSIGNED_IDENTIFIER_MAPPING = MongoConstants.ASSIGNED_IDENTIFIER_MAPPING


    protected final MongoCodecSession mongoSession
    protected final MongoDatastore mongoDatastore
    protected boolean hasNumericalIdentifier = false
    protected boolean hasStringIdentifier = false
    protected final EntityReflector fastClassData

    MongoCodecEntityPersister(MappingContext mappingContext, PersistentEntity entity, MongoCodecSession session, ApplicationEventPublisher publisher, TPCacheAdapterRepository<Object> cacheAdapterRepository) {
        super(mappingContext, entity, session, publisher, cacheAdapterRepository)
        this.mongoSession = session
        this.mongoDatastore = session.datastore
        this.fastClassData = FieldEntityAccess.getOrIntializeReflector(entity)
        PersistentProperty identity = entity.identity
        if (identity != null) {
            hasNumericalIdentifier = Long.class.isAssignableFrom(identity.type)
            hasStringIdentifier = String.class.isAssignableFrom(identity.type)
        }
    }

    @Override
    MongoCodecSession getSession() {
        return (MongoCodecSession) super.getSession()
    }

    protected String getIdentifierName(ClassMapping cm) {
        final IdentityMapping identifier = cm.getIdentifier()
        if (identifier != null && identifier.getIdentifierName() != null) {
            return identifier.getIdentifierName()[0]
        }
        return null
    }

    @Override
    protected List<Object> retrieveAllEntities(PersistentEntity pe, Serializable[] keys) {
        retrieveAllEntities pe, Arrays.asList(keys)
    }

    @Override
    protected List<Object> retrieveAllEntities(PersistentEntity pe, Iterable<Serializable> keys) {
        def idList = keys.toList()
        if (idList.isEmpty()) {
            // don't bother with query if list of keys is empty
            return []
        } else {
            createQuery()
                    .in(pe.identity.name, idList)
                    .list()

        }
    }

    @Override
    protected List<Serializable> persistEntities(PersistentEntity pe, @SuppressWarnings("rawtypes") Iterable objs) {
        objs.collect() {
            persistEntity(pe, it)
        }
    }

    @Override
    protected Object retrieveEntity(PersistentEntity pe, Serializable key) {
        Object o = getFromTPCache(pe, key)
        if (o != null) {
            return o
        }

        if (cancelLoad(pe, null)) {
            return null
        } else {
            MongoCollection mongoCollection = getMongoCollection(pe)
            Document idQuery = createIdQuery(key)
            o = mongoCollection
                    .withDocumentClass(persistentEntity.javaClass)
                    .withCodecRegistry(mongoDatastore.codecRegistry)
                    .find(idQuery, pe.javaClass)
                    .limit(1)
                    .first()

            if (o != null) {
                if (!cancelLoad(pe, createEntityAccess(pe, o))) {
                    firePostLoadEvent(pe, createEntityAccess(pe, o))
                    return o
                }
            }
        }
        return null
    }

    protected Document createIdQuery(Object key) {
        new Document(AbstractMongoObectEntityPersister.MONGO_ID_FIELD, key)
    }

    @Override
    protected Serializable persistEntity(PersistentEntity entity, Object obj, boolean isInsert) {


        ProxyFactory proxyFactory = getProxyFactory()
        // if called internally, obj can potentially be a proxy, which won't work.
        obj = proxyFactory.unwrap(obj)

        Serializable id = getObjectIdentifier(obj)

        SessionImplementor<Object> si = (SessionImplementor<Object>) session

        if (si.isPendingAlready(obj)) {
            return (Serializable) id
        }


        final boolean idIsNull = id == null
        boolean isUpdate = !idIsNull && !isInsert
        def mongoCodecSession = mongoSession
        boolean assignedId = isAssignedId(persistentEntity)
        if (isNotUpdateForAssignedId(persistentEntity, obj, isUpdate, assignedId, si)) {
            isUpdate = false
        }
        if (isUpdate && !getSession().isDirty(obj)) {
            return (Serializable) id
        } else {
            final EntityAccess entityAccess = createEntityAccess(entity, obj)
            boolean isAssigned = isAssignedId(entity)
            if (!isAssigned && idIsNull) {
                id = generateIdentifier(entity)
                if (id != null) {
                    entityAccess.setIdentifier(id)
                } else {
                    throw new DataIntegrityViolationException("Failed to generate a valid identifier for entity [$obj]")
                }
            } else if (idIsNull) {
                throw new DataIntegrityViolationException("Entity [$obj] has null identifier when identifier strategy is manual assignment. Assign an appropriate identifier before persisting.")
            } else if (isAssigned && !si.isStateless(entity)) {
                isUpdate = mongoCodecSession.contains(obj)
            }

            si.registerPending(obj)
            processAssociations(mongoCodecSession, entity, entityAccess, obj, proxyFactory, isUpdate)

            if (!isUpdate) {
                MongoCodecEntityPersister self = this
                mongoCodecSession.addPendingInsert(new PendingInsertAdapter(entity, id, obj, entityAccess) {
                    @Override
                    void run() {
                        if (!cancelInsert(entity, entityAccess)) {
                            updateCaches(entity, obj, id)
                            addCascadeOperation(new PendingOperationAdapter(entity, id, obj) {
                                @Override
                                void run() {
                                    self.firePostInsertEvent(entity, entityAccess)
                                }
                            })
                        } else {
                            setVetoed(true)
                        }
                    }
                })
            } else {
                mongoCodecSession.addPendingUpdate(new PendingUpdateAdapter(entity, id, obj, entityAccess) {
                    @Override
                    void run() {
                        if (!cancelUpdate(entity, entityAccess)) {
                            updateCaches(entity, obj, id)
                            addCascadeOperation(new PendingOperationAdapter(entity, id, obj) {
                                @Override
                                void run() {
                                    firePostUpdateEvent(entity, entityAccess)
                                }
                            })
                        } else {
                            setVetoed(true)
                        }
                    }
                })
            }
        }
        return id
    }

    protected boolean isAssignedId(PersistentEntity persistentEntity) {
        Property mapping = persistentEntity.identity.mapping.mappedForm
        return ASSIGNED_IDENTIFIER_MAPPING.equals(mapping?.generator)
    }

    private boolean isNotUpdateForAssignedId(PersistentEntity persistentEntity, Object obj, boolean update, boolean assignedId, SessionImplementor<Object> si) {
        return assignedId && update && !si.isStateless(persistentEntity) && !session.contains(obj)
    }

    protected void processAssociations(MongoCodecSession mongoCodecSession, PersistentEntity entity, EntityAccess entityAccess, obj, ProxyFactory proxyFactory, boolean isUpdate) {
        // now we must ensure that all cascades are handled and inserts / updates scheduled
        for (association in entity.associations) {
            def associatedEntity = association.associatedEntity
            if (association instanceof ToOne) {
                if (association instanceof Embedded) {
                    def propertyName = association.name
                    def value = entityAccess.getProperty(propertyName)
                    if (!proxyFactory.isInitialized(value)) {
                        continue
                    }
                    if (value != null) {
                        processAssociations(mongoCodecSession,
                                associatedEntity,
                                createEntityAccess(associatedEntity, value),
                                value,
                                proxyFactory,
                                isUpdate)
                    }
                } else {
                    def propertyName = association.name
                    def value = entityAccess.getProperty(propertyName)
                    if (value != null) {
                        if (association.isBidirectional() && !isUpdate) {
                            def inverseAccess = createEntityAccess(associatedEntity, value)
                            def inverseSide = association.inverseSide

                            def inverseName = inverseSide.name
                            if (inverseSide instanceof ToOne) {
                                inverseAccess.setPropertyNoConversion(
                                        inverseName,
                                        obj
                                )
                            } else if (inverseSide instanceof OneToMany) {
                                if (isUpdate) continue

                                def inverseCollection = inverseAccess.getProperty(inverseName)

                                if (inverseCollection == null) {
                                    inverseCollection = MappingUtils.createConcreteCollection(inverseSide.type)
                                    inverseAccess.setPropertyNoConversion(
                                            inverseName,
                                            inverseCollection
                                    )
                                }
                                if (inverseCollection instanceof Collection) {
                                    def coll = (Collection) inverseCollection
                                    if (!coll.contains(obj)) {
                                        coll << obj
                                    }
                                }

                            }
                        }
                        if (proxyFactory.isInitialized(value)) {
                            def dirtyCheckable = (DirtyCheckable) value
                            if (dirtyCheckable.hasChanged()) {
                                if (association.isOwningSide() || association.doesCascade(CascadeType.PERSIST)) {
                                    mongoCodecSession.persist(value)
                                }
                            }
                        }
                    }

                }
            } else if ((association instanceof OneToMany) || (association instanceof ManyToMany)) {
                def propertyName = association.name
                def value = entityAccess.getProperty(propertyName)
                boolean shouldPersist = false
                if (value != null && association.doesCascade(CascadeType.PERSIST)) {
                    if (!isUpdate) {
                        shouldPersist = true
                    } else {
                        if (value instanceof DirtyCheckableCollection) {
                            def dirtyCheckingCollection = DirtyCheckingSupport.wrap((Collection) value, (DirtyCheckable) obj, propertyName)
                            if (((DirtyCheckingCollection) dirtyCheckingCollection).hasChanged()) {
                                shouldPersist = true
                            }
                        } else {
                            shouldPersist = true
                        }
                    }

                    if (shouldPersist) {

                        def associatedEntities = (Iterable) value
                        if (association.isBidirectional()) {
                            def inverseSide = association.inverseSide
                            def inverseName = inverseSide.name
                            if (inverseSide instanceof ToOne) {

                                for (ae in associatedEntities) {
                                    createEntityAccess(associatedEntity, ae)
                                            .setPropertyNoConversion(inverseName, obj)
                                }
                            }
                        }

                        def identifiers = mongoCodecSession.persist(associatedEntities)
                        mongoCodecSession.setAttribute(
                                obj,
                                "${association}.ids",
                                identifiers
                        )

                        def dirtyCheckingCollection = DirtyCheckingSupport.wrap((Collection) value, (DirtyCheckable) obj, propertyName)
                        entityAccess.setPropertyNoConversion(propertyName, dirtyCheckingCollection)
                    }
                }
            }
        }
    }

    protected void updateCaches(PersistentEntity persistentEntity, Object e, Serializable id) {
        updateTPCache(persistentEntity, e, id)
    }

    Serializable generateIdentifier(final PersistentEntity persistentEntity) {
        // If there is a numeric identifier then we need to rely on optimistic concurrency controls to obtain a unique identifer
        // sequence. If the identifier is not numeric then we assume BSON ObjectIds.
        if (hasNumericalIdentifier) {
            final String collectionName = getCollectionName(persistentEntity)
            final MongoClient client = (MongoClient) mongoSession.nativeInterface

            final MongoCollection<Document> dbCollection = client
                    .getDatabase(mongoSession.getDatabase(persistentEntity))
                    .getCollection("${collectionName}${NEXT_ID_SUFFIX}")

            int attempts = 0

            while (true) {

                final options = new FindOneAndUpdateOptions()
                options.upsert(true).returnDocument(ReturnDocument.AFTER)
                Document result = dbCollection.findOneAndUpdate(new Document(MONGO_ID_FIELD, collectionName), new Document(INC_OPERATOR, new Document(NEXT_ID, 1L)), options)
                // result should never be null and we shouldn't come back with an error ,but you never know. We should just retry if this happens...
                if (result != null) {
                    return result.getLong(NEXT_ID)
                } else {
                    attempts++
                    if (attempts > 3) {
                        throw new IdentityGenerationException("Unable to generate identity for [$persistentEntity.name] using findAndModify after 3 attempts")
                    }
                }
            }
        }

        ObjectId objectId = ObjectId.get()
        def identityType = persistentEntity.identity.type
        if (ObjectId.class.isAssignableFrom(identityType)) {
            return objectId
        }

        return objectId.toString()
    }


    @Override
    protected void deleteEntity(PersistentEntity pe, Object obj) {

        ProxyFactory proxyFactory = getProxyFactory()
        // if called internally, obj can potentially be a proxy, which won't work.
        Serializable id
        if (proxyFactory.isProxy(obj)) {
            id = proxyFactory.getIdentifier(obj)
        } else {
            id = getObjectIdentifier(obj)
        }

        if (id != null) {
            MongoCodecEntityPersister self = this
            mongoSession.addPendingDelete(new PendingDeleteAdapter(pe, id, obj) {
                @Override
                void run() {
                    def entityAccess = self.createEntityAccess(pe, obj)
                    if (!self.cancelDelete(pe, entityAccess)) {
                        mongoSession.clear(obj)
                        addCascadeOperation(new PendingOperationAdapter(pe, id, obj) {
                            @Override
                            void run() {
                                self.firePostDeleteEvent pe, entityAccess
                            }
                        })
                    } else {
                        setVetoed(true)
                    }
                }
            })
            def access = createEntityAccess(pe, obj)
            for (association in pe.associations) {
                if (association.isOwningSide() && association.doesCascade(CascadeType.REMOVE)) {
                    if (!association.isEmbedded() && !(association instanceof Basic)) {
                        def v = access.getProperty(association.name)
                        if (association instanceof ToOne) {
                            if (association.isBidirectional() && association.isCircular()) {
                                // don't cascade to the parent of a circular association with a belongsTo
                                // should probably move this fix for core g-d-m however may impact other impls
                                continue
                            }
                            if (v != null) {
                                mongoSession.delete(v)
                            }
                        } else {
                            if (v != null) {
                                mongoSession.delete((Iterable) v)
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void deleteEntities(PersistentEntity pe, @SuppressWarnings("rawtypes") Iterable objects) {
        def criteria = new DetachedCriteria(pe.javaClass)
        criteria.in(pe.identity.name, objects.collect() { getObjectIdentifier(it) }.findAll() { it != null })
        mongoSession.deleteAll(
                criteria
        )
    }

    @Override
    Query createQuery() {
        return new MongoQuery(mongoSession, persistentEntity)
    }

    @Override
    Serializable refresh(Object o) {
        throw new UnsupportedOperationException("Refresh not supported by codec entity persistence engine")
    }

    @Override
    Object lock(Serializable id) throws CannotAcquireLockException {
        throw new UnsupportedOperationException("Pessimistic locks not supported by MongoDB")
    }

    @Override
    Object lock(Serializable id, int timeout) throws CannotAcquireLockException {
        throw new UnsupportedOperationException("Pessimistic locks not supported by MongoDB")
    }

    @Override
    boolean isLocked(Object o) {
        throw new UnsupportedOperationException("Pessimistic locks not supported by MongoDB")
    }

    @Override
    void unlock(Object o) {
        throw new UnsupportedOperationException("Pessimistic locks not supported by MongoDB")
    }

    @Override
    protected EntityAccess createEntityAccess(PersistentEntity pe, Object obj) {
        return mongoSession.createEntityAccess(pe, obj)
    }

    @Override
    protected Serializable persistEntity(PersistentEntity pe, Object obj) {
        return persistEntity(pe, obj, false)
    }

    protected MongoCollection getMongoCollection(PersistentEntity pe) {
        def database = mongoSession.getDatabase(pe)
        String collection = getCollectionName(pe)

        MongoClient client = (MongoClient) mongoSession.nativeInterface

        MongoCollection mongoCollection = client
                .getDatabase(database)
                .getCollection(collection)
                .withDocumentClass(pe.javaClass)
        return mongoCollection
    }


    protected String getCollectionName(PersistentEntity pe) {
        mongoSession.getCollectionName(pe)
    }
}
