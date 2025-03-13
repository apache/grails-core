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
package org.grails.datastore.mapping.mongo.engine.codecs

import com.mongodb.DBRef
import groovy.transform.CompileStatic
import org.bson.*
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.grails.datastore.bson.codecs.BsonPersistentEntityCodec
import org.grails.datastore.bson.codecs.PropertyDecoder
import org.grails.datastore.bson.codecs.PropertyEncoder
import org.grails.datastore.bson.codecs.decoders.EmbeddedCollectionDecoder
import org.grails.datastore.bson.codecs.decoders.EmbeddedDecoder
import org.grails.datastore.bson.codecs.encoders.EmbeddedCollectionEncoder
import org.grails.datastore.bson.codecs.encoders.EmbeddedEncoder
import org.grails.datastore.bson.codecs.encoders.IdentityEncoder
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.schemaless.DynamicAttributes
import org.grails.datastore.mapping.collection.PersistentList
import org.grails.datastore.mapping.collection.PersistentSet
import org.grails.datastore.mapping.collection.PersistentSortedSet
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.AbstractDatastore
import org.grails.datastore.mapping.core.DatastoreException
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckableCollection
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.engine.internal.MappingUtils
import org.grails.datastore.mapping.model.EmbeddedPersistentEntity
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.*
import org.grails.datastore.mapping.mongo.MongoCodecSession
import org.grails.datastore.mapping.mongo.MongoConstants
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoAttribute
import org.grails.datastore.mapping.mongo.engine.MongoCodecEntityPersister
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.reflect.FieldEntityAccess

import jakarta.persistence.FetchType
/**
 * A MongoDB codec for persisting {@link PersistentEntity} instances
 *
 * @author Graeme Rocher
 * @since 4.1
 */
@CompileStatic
class PersistentEntityCodec extends BsonPersistentEntityCodec {
    private static final String BLANK_STRING = ""
    public static final String MONGO_SET_OPERATOR = '$set'
    public static final String MONGO_UNSET_OPERATOR = '$unset'

    public static final String DB_REF_ID_FIELD = '$id'
    public static final String SCHEMALESS_ATTRIBUTES = "schemaless.attributes"

    static {
        registerEncoder(Identity, (PropertyEncoder) new IdentityEncoder() {
            @Override
            protected String getIdentifierName(Identity property) {
                MongoConstants.MONGO_ID_FIELD
            }
        })

        registerEncoder(Embedded, (PropertyEncoder) new EmbeddedEncoder() {
            @Override
            protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry codecRegistry, PersistentEntity associatedEntity) {
                return new PersistentEntityCodec(codecRegistry, associatedEntity)
            }
        })

        registerDecoder(Embedded, (PropertyDecoder) new EmbeddedDecoder() {
            @Override
            protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry codecRegistry, PersistentEntity associatedEntity) {
                return new PersistentEntityCodec(codecRegistry, associatedEntity)
            }
        })

        registerEncoder(EmbeddedCollection, (PropertyEncoder) new EmbeddedCollectionEncoder() {
            @Override
            protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry codecRegistry, PersistentEntity associatedEntity) {
                return new PersistentEntityCodec(codecRegistry, associatedEntity)
            }
        })

        registerDecoder(EmbeddedCollection, (PropertyDecoder) new EmbeddedCollectionDecoder() {
            @Override
            protected BsonPersistentEntityCodec createEmbeddedEntityCodec(CodecRegistry codecRegistry, PersistentEntity associatedEntity) {
                return new PersistentEntityCodec(codecRegistry, associatedEntity)
            }
        })

        registerEncoder(OneToOne, new ToOneEncoder())
        registerDecoder(OneToOne, new ToOneDecoder())
        registerEncoder(ManyToOne,new ToOneEncoder())
        registerDecoder(ManyToOne,new ToOneDecoder())
        registerEncoder(OneToMany, new OneToManyEncoder())
        registerDecoder(OneToMany,new OneToManyDecoder())
        registerEncoder(ManyToMany, new OneToManyEncoder())
        registerDecoder(ManyToMany, new OneToManyDecoder())
    }

    PersistentEntityCodec(CodecRegistry codecRegistry, PersistentEntity entity, boolean stateful = true) {
        super(codecRegistry, entity, stateful)
    }

    @Override
    protected boolean isIdentifierProperty(String name) {
        return MongoConstants.MONGO_ID_FIELD == name
    }

    @Override
    protected void readingComplete(EntityAccess access) {
        Closure callback = { Session session ->
            decodeAssociations(session, access)
        }
        if(entity instanceof EmbeddedPersistentEntity) {
            callback( AbstractDatastore.retrieveSession(MongoDatastore) )
        }
        else {
            GormEnhancer.findStaticApi(entity.javaClass).withSession callback
        }
    }

    @Override
    protected Object retrieveCachedInstance(EntityAccess access) {
        Closure callback = { Session session ->
            Object cachedInstance = null
            if (session?.contains(access.entity)) {
                cachedInstance = session.retrieve(access.persistentEntity.javaClass, (Serializable) access.identifier)
            }
            return cachedInstance
        }
        if(entity instanceof EmbeddedPersistentEntity) {
            callback( AbstractDatastore.retrieveSession(MongoDatastore) )
        }
        else {
            GormEnhancer.findStaticApi(entity.javaClass).withSession callback
        }
    }

    protected void decodeAssociations(Session mongoSession, EntityAccess access) {
        if (mongoSession != null) {

            for (association in entity.associations) {
                if (association instanceof OneToMany) {
                    if (association.isBidirectional()) {
                        OneToManyDecoder.initializePersistentCollection(mongoSession, access, association)
                    }
                } else if (association instanceof OneToOne) {
                    if (((ToOne) association).isForeignKeyInChild()) {
                        def associatedClass = association.associatedEntity.javaClass
                        Query query = mongoSession.createQuery(associatedClass)
                        query.eq(association.inverseSide.name, access.identifier)
                                .projections().id()


                        def id = query.singleResult()
                        boolean lazy = ((Property) association.mapping.mappedForm).fetchStrategy == FetchType.LAZY
                        access.setPropertyNoConversion(
                                association.name,
                                lazy ? mongoSession.proxy(associatedClass, (Serializable) id) : mongoSession.retrieve(associatedClass, (Serializable) id)
                        )

                    }
                }
            }
        }
    }

    /**
     * This method will encode an update for the given object based
     * @param value A {@link Bson} that is the update object
     * @return A Bson
     */
    @Override
    Document encodeUpdate(Object value, EntityAccess access = createEntityAccess(value), EncoderContext encoderContext = DEFAULT_ENCODER_CONTEXT, boolean embedded = false) {
        Document update = new Document()
        def entity = access.persistentEntity

        def proxyFactory = mappingContext.proxyFactory
        if( proxyFactory.isProxy(value) ) {
            value = proxyFactory.unwrap(value)
        }
        if(value instanceof DirtyCheckable) {
            def sets = new BsonDocument()
            def unsets = new Document()
            BsonWriter writer = new BsonDocumentWriter(sets)
            writer.writeStartDocument()
            DirtyCheckable dirty = (DirtyCheckable)value
            Set<String> processed = []

            def dirtyProperties = new ArrayList<String>(dirty.listDirtyPropertyNames())
            boolean isNew = dirtyProperties.isEmpty() && dirty.hasChanged()
            def isVersioned = entity.isVersioned()
            if(isNew) {
                // if it is new it can only be an embedded entity that has now been updated
                // so we get all properties
                dirtyProperties = entity.persistentPropertyNames
                if(!entity.isRoot()) {
                    sets.put(MongoConstants.MONGO_CLASS_FIELD, new BsonString(entity.discriminator))
                }

                if(isVersioned) {
                    EntityPersister.incrementEntityVersion(access)
                }

            }
            else {
                // schedule lastUpdated if necessary
                if( entity.getPropertyByName(GormProperties.LAST_UPDATED) != null) {
                    dirtyProperties.add(GormProperties.LAST_UPDATED)
                }
            }


            for(propertyName in dirtyProperties) {
                def prop = entity.getPropertyByName(propertyName)
                if(prop != null) {

                    processed << propertyName
                    Object v = access.getProperty(prop.name)
                    if (v != null) {
                        if(prop instanceof Embedded) {
                            encodeEmbeddedUpdate(sets,unsets, (Association)prop, v)
                        }
                        else if(prop instanceof EmbeddedCollection) {
                            encodeEmbeddedCollectionUpdate(access, sets, unsets, (Association)prop, v)
                        }
                        else {
                            def propKind = prop.getClass().superclass
                            PropertyEncoder<? extends PersistentProperty> propertyEncoder = getPropertyEncoder((Class<? extends PersistentProperty>)propKind)
                            propertyEncoder?.encode(writer, prop, v, access, encoderContext, codecRegistry)
                        }

                    }
                    else if(embedded || !isNew) {
                        unsets[prop.name] = BLANK_STRING
                    }
                }
            }

            if(value instanceof DynamicAttributes) {
                Map<String, Object> attributes = ((DynamicAttributes) value).attributes()
                for(attr in attributes.keySet()) {
                    Object v = attributes.get(attr)
                    if(v == null) {
                        unsets.put(attr,BLANK_STRING)
                    }
                    else {
                        writer.writeName(attr)
                        Codec<Object> codec = (Codec<Object>)codecRegistry.get(v.getClass())
                        codec.encode(writer, v, encoderContext)
                    }
                }
            }
            else {

                GormEnhancer.findStaticApi(entity.javaClass).withSession { Session mongoSession ->
                    if(mongoSession != null) {
                        Document schemaless = (Document)mongoSession.getAttribute(value, SCHEMALESS_ATTRIBUTES)
                        if(schemaless != null) {
                            for(name in schemaless.keySet()) {
                                def v = schemaless.get(name)
                                if(v == null) {
                                    unsets.put(name,BLANK_STRING)
                                }
                                else {
                                    writer.writeName(name)
                                    Codec<Object> codec = (Codec<Object>)codecRegistry.get(v.getClass())
                                    codec.encode(writer, v, encoderContext)
                                }
                            }
                        }
                    }
                }

            }

            for(association in entity.associations) {
                if(processed.contains( association.name )) continue
                if(association instanceof OneToMany) {
                    def v = access.getProperty(association.name)
                    if (v != null) {
                        // TODO: handle unprocessed association
                    }
                }
                else if(association instanceof ToOne) {
                    def v = access.getProperty(association.name)
                    if( v instanceof DirtyCheckable ) {
                        if(((DirtyCheckable)v).hasChanged()) {
                            if(association instanceof Embedded) {
                                encodeEmbeddedUpdate(sets, unsets, association, v)
                            }
                        }
                    }
                }
                else if(association instanceof EmbeddedCollection) {
                    def v = access.getProperty(association.name)
                    if( v instanceof DirtyCheckableCollection ) {
                        if(((DirtyCheckableCollection)v).hasChanged()) {
                            encodeEmbeddedCollectionUpdate(access, sets, unsets, association, v)
                        }
                    }
                }
            }


            boolean hasSets = !sets.isEmpty()
            boolean hasUnsets = !unsets.isEmpty()

            if(hasSets && isVersioned) {
                def version = entity.version
                def propKind = version.getClass().superclass
                MongoCodecEntityPersister.incrementEntityVersion(access)
                def v = access.getProperty(version.name)
                getPropertyEncoder((Class<? extends PersistentProperty>) propKind)?.encode(writer, version, v, access, encoderContext, codecRegistry)
            }

            writer.writeEndDocument()

            if(hasSets) {
                update.put(MONGO_SET_OPERATOR, sets)
            }
            if(hasUnsets) {
                update.put(MONGO_UNSET_OPERATOR,unsets)
            }
        }
        else {
            // TODO: Support non-dirty checkable objects?
        }

        return update
    }

    @Override
    protected String getDiscriminatorAttributeName() {
        return MongoConstants.MONGO_CLASS_FIELD
    }

    protected void encodeEmbeddedCollectionUpdate(EntityAccess parentAccess, BsonDocument sets, Document unsets, Association association, Object v) {
        if(v instanceof Collection) {
            if((v instanceof DirtyCheckableCollection) && !((DirtyCheckableCollection)v).hasChangedSize()) {
                int i = 0
                for(o in (v as Collection)) {
                    def embeddedUpdate = encodeUpdate(o, createEntityAccess(o), EncoderContext.builder().build(), true)
                    def embeddedSets = embeddedUpdate.get(MONGO_SET_OPERATOR)
                    if(embeddedSets != null) {

                        def map = (Map) embeddedSets
                        for (key in map.keySet()) {
                            sets.put("${association.name}.${i}.$key".toString(), (BsonValue) map.get(key))
                        }
                    }
                    def embeddedUnsets = embeddedUpdate.get(MONGO_UNSET_OPERATOR)
                    if(embeddedUnsets) {
                        def map = (Map) embeddedUnsets
                        for (key in map.keySet()) {
                            unsets.put("${association.name}.${i}.$key".toString(), BLANK_STRING)
                        }
                    }
                    i++
                }
            }
            else {
                // if this is not a dirty checkable collection or the collection has changed size then a whole new collection has been
                // set so we overwrite existing
                def associatedEntity = association.associatedEntity
                def rootClass = associatedEntity.javaClass
                PersistentEntityCodec entityCodec =  (PersistentEntityCodec)codecRegistry.get(rootClass)
                def inverseProperty = association.inverseSide
                List<BsonValue> documents =[]
                for(o in v) {
                    if(o == null) {
                        documents << null
                        continue
                    }
                    PersistentEntity entity = associatedEntity
                    PersistentEntityCodec codec = entityCodec

                    def cls = o.getClass()
                    if(rootClass != cls) {
                        // a subclass, so lookup correct codec
                        entity = mappingContext.getPersistentEntity(cls.name)
                        if(entity == null) {
                            throw new DatastoreException("Value [$o] is not a valid type for association [$association]" )
                        }
                        codec = (PersistentEntityCodec)codecRegistry.get(cls)
                    }
                    def ea = createEntityAccess(entity, o)
                    if(inverseProperty != null) {
                        if(inverseProperty instanceof ToOne) {
                            ea.setPropertyNoConversion( inverseProperty.name, parentAccess.entity)
                        }

                    }
                    def doc = new BsonDocument()
                    def id = ea.identifier
                    codec.encode( new BsonDocumentWriter(doc), o, DEFAULT_ENCODER_CONTEXT, id != null )
                    documents.add( doc )
                }
                def bsonArray = new BsonArray(documents)
                sets.put( association.name, bsonArray)
            }
        }
        else {
            // TODO: Map handling
        }

    }
    protected void encodeEmbeddedUpdate(BsonDocument sets, Document unsets, Association association, v) {

        if (v instanceof DirtyCheckable ) {
            v.markDirty()
        }

        def embeddedUpdate = encodeUpdate(v, createEntityAccess(v), DEFAULT_ENCODER_CONTEXT, true)
        def embeddedSets = embeddedUpdate.get(MONGO_SET_OPERATOR)
        if(embeddedSets != null) {

            def map = (Map) embeddedSets
            for (key in map.keySet()) {
                sets.put("${association.name}.$key".toString(), (BsonValue) map.get(key))
            }
        }

        def embeddedUnsets = embeddedUpdate.get(MONGO_UNSET_OPERATOR)
        if(embeddedUnsets) {
            def map = (Map) embeddedUnsets
            for (key in map.keySet()) {
                unsets.put("${association.name}.$key".toString(), BLANK_STRING)
            }
        }
    }

    static class OneToManyDecoder implements PropertyDecoder<Association> {
        @Override
        void decode(BsonReader reader, Association property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry) {
            def session = AbstractDatastore.retrieveSession(MongoDatastore)
            if(property.isBidirectional() && !(property instanceof ManyToMany)) {

                initializePersistentCollection(session, entityAccess, property)
            }
            else {
                def type = property.type
                def propertyName = property.name

                def listCodec = codecRegistry.get(List)
                def identifiers = listCodec.decode(reader, decoderContext)
                MongoAttribute attr = (MongoAttribute)property.mapping.mappedForm
                if(attr?.isReference()) {
                    identifiers = identifiers.collect {
                        if(it instanceof DBRef) {
                            return ((DBRef)it).id
                        }
                        else if(it instanceof Map) {
                            return ((Map)it).get(DB_REF_ID_FIELD)
                        }
                        return it
                    }
                }
                def associatedType = property.associatedEntity.javaClass
                if(SortedSet.isAssignableFrom(type)) {
                    entityAccess.setPropertyNoConversion(
                            propertyName,
                            new PersistentSortedSet( identifiers, associatedType, session)
                    )
                }
                else if(Set.isAssignableFrom(type)) {
                    entityAccess.setPropertyNoConversion(
                            propertyName,
                            new PersistentSet( identifiers, associatedType, session)
                    )
                }
                else {
                    entityAccess.setPropertyNoConversion(
                            propertyName,
                            new PersistentList( identifiers, associatedType, session)
                    )
                }
            }
        }

        static initializePersistentCollection(Session session, EntityAccess entityAccess, Association property) {
            def type = property.type
            def propertyName = property.name
            def identifier = (Serializable) entityAccess.identifier

            if(SortedSet.isAssignableFrom(type)) {
                entityAccess.setPropertyNoConversion(
                        propertyName,
                        new PersistentSortedSet( property, identifier, session)
                )
            }
            else if(Set.isAssignableFrom(type)) {
                entityAccess.setPropertyNoConversion(
                        propertyName,
                        new PersistentSet( property, identifier, session)
                )
            }
            else {
                entityAccess.setPropertyNoConversion(
                        propertyName,
                        new PersistentList( property, identifier, session)
                )
            }
        }
    }
    static class OneToManyEncoder implements PropertyEncoder<Association> {

        @Override
        void encode(BsonWriter writer, Association property, Object value, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {
            boolean shouldEncodeIds = !property.isBidirectional() || (property instanceof ManyToMany)
            MongoCodecSession mongoSession = (MongoCodecSession)AbstractDatastore.retrieveSession(MongoDatastore)
            if(shouldEncodeIds) {
                // if it is unidirectional we encode the values inside the current
                // document, otherwise nothing to do, encoding foreign key stored in inverse side

                def associatedEntity = property.associatedEntity
                if(value instanceof Collection) {
                    boolean updateCollection = false
                    if((value instanceof DirtyCheckableCollection)) {
                        def persistentCollection = (DirtyCheckableCollection) value
                        updateCollection = persistentCollection.hasChanged()
                    }
                    else {
                        // write new collection
                        updateCollection = true
                    }

                    if(updateCollection) {
                        // update existing collection
                        Collection identifiers = (Collection)mongoSession.getAttribute(parentAccess.entity, "${property}.ids")
                        if(identifiers == null) {
                            def entityReflector = FieldEntityAccess.getOrIntializeReflector(associatedEntity)
                            identifiers = ((Collection)value).collect() {
                                entityReflector.getIdentifier(it)
                            }
                        }
                        writer.writeName MappingUtils.getTargetKey((PersistentProperty)property)
                        def listCodec = codecRegistry.get(List)

                        def identifierList = identifiers.toList()
                        MongoAttribute attr = (MongoAttribute)property.mapping.mappedForm
                        if(attr?.isReference()) {
                            def collectionName = mongoSession.getCollectionName(property.associatedEntity)
                            identifierList = identifierList.findAll(){ it != null }.collect {
                                new DBRef(collectionName, it)
                            }
                        }
                        listCodec.encode writer, identifierList, encoderContext
                    }
                }
            }
        }
    }

    /**
     * A {@PropertyEncoder} capable of encoding {@ToOne} association types
     */
    static class ToOneEncoder implements PropertyEncoder<ToOne> {

        @Override
        void encode(BsonWriter writer, ToOne property, Object value, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {

            if(value) {
                def associatedEntity = property.associatedEntity

                Object associationId
                if(!property.isForeignKeyInChild()) {
                    def mappingContext = parentAccess.persistentEntity.mappingContext
                    def proxyFactory = mappingContext.proxyFactory
                    if(proxyFactory.isProxy(value)) {
                        associationId = proxyFactory.getIdentifier(value)
                    }
                    else {
                        def associationAccess = mappingContext.getEntityReflector(associatedEntity)
                        associationId = associationAccess.getIdentifier(value)
                    }
                    if(associationId != null) {
                        writer.writeName MappingUtils.getTargetKey(property)
                        MongoAttribute attr = (MongoAttribute)property.mapping.mappedForm
                        if(attr?.isReference()) {
                            def identityEncoder = codecRegistry.get(DBRef)

                            MongoCodecSession mongoSession = (MongoCodecSession)AbstractDatastore.retrieveSession(MongoDatastore)
                            def ref = new DBRef(mongoSession.getCollectionName( associatedEntity),associationId)
                            identityEncoder.encode writer, ref, encoderContext
                        }
                        else {
                            Codec<Object> identityEncoder = (Codec<Object>) codecRegistry.get((Class<? extends Object>) associationId.getClass())
                            identityEncoder.encode writer, associationId, encoderContext
                        }
                    }
                }
            }
        }
    }

    /**
     * A {@PropertyEncoder} capable of encoding {@ToOne} association types
     */
    static class ToOneDecoder implements PropertyDecoder<ToOne> {

        @Override
        void decode(BsonReader bsonReader, ToOne property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry) {
            MongoCodecSession mongoSession = (MongoCodecSession)AbstractDatastore.retrieveSession(MongoDatastore)
            MongoAttribute attr = (MongoAttribute)property.mapping.mappedForm
            boolean isLazy = isLazyAssociation(attr)
            def associatedEntity = property.associatedEntity
            if(associatedEntity == null) {
                bsonReader.skipValue()
                return
            }

            Serializable associationId

            if(attr.reference && bsonReader.currentBsonType == BsonType.DOCUMENT) {
                def dbRefCodec = codecRegistry.get(Document)
                def dBRef = dbRefCodec.decode(bsonReader, decoderContext)
                associationId = (Serializable)dBRef.get(DB_REF_ID_FIELD)
            }
            else {
                switch(associatedEntity.identity.type) {
                    case ObjectId:
                        associationId = bsonReader.readObjectId()
                        break
                    case Long:
                        associationId = (Long)bsonReader.readInt64()
                        break
                    case Integer:
                        associationId =  (Integer)bsonReader.readInt32()
                        break
                    default:
                        associationId = bsonReader.readString()
                }
            }


            if(isLazy) {
                entityAccess.setPropertyNoConversion(
                        property.name,
                        mongoSession.proxy(associatedEntity.javaClass, associationId )
                )
            }
            else {
                entityAccess.setPropertyNoConversion(
                        property.name,
                        mongoSession.retrieve(associatedEntity.javaClass, associationId )
                )
            }

        }

        private boolean isLazyAssociation(MongoAttribute attribute) {
            if (attribute == null) {
                return true
            }

            return attribute.getFetchStrategy() == FetchType.LAZY
        }

    }




}
