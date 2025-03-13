package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.*
import org.bson.codecs.BsonValueCodecProvider
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import org.grails.datastore.bson.codecs.decoders.*
import org.grails.datastore.bson.codecs.encoders.*
import org.grails.datastore.gorm.schemaless.DynamicAttributes
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.*
/**
 * Encodes and decodes {@link org.grails.datastore.mapping.model.PersistentEntity} objects from a BSON stream
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class BsonPersistentEntityCodec implements Codec {
    public static final EncoderContext DEFAULT_ENCODER_CONTEXT = EncoderContext.builder().build()
    public static final DecoderContext DEFAULT_DECODER_CONTEXT = DecoderContext.builder().build()
    private static final Map<Class<? extends PersistentProperty>, PropertyEncoder> ENCODERS = [:]
    private static final Map<Class<? extends PersistentProperty>, PropertyDecoder> DECODERS = [:]

    static {
        ENCODERS[Identity] = new IdentityEncoder()
        DECODERS[Identity] = new IdentityDecoder()
        ENCODERS[TenantId] = new TenantIdEncoder()
        DECODERS[TenantId] = new TenantIdDecoder()
        ENCODERS[Simple] = new SimpleEncoder()
        DECODERS[Simple] = new SimpleDecoder()
        ENCODERS[Embedded] = new EmbeddedEncoder()
        DECODERS[Embedded] = new EmbeddedDecoder()
        ENCODERS[EmbeddedCollection] = new EmbeddedCollectionEncoder()
        DECODERS[EmbeddedCollection] = new EmbeddedCollectionDecoder()
        ENCODERS[Custom] = new CustomTypeEncoder()
        DECODERS[Custom] = new CustomTypeDecoder()
        ENCODERS[Basic] = new BasicCollectionTypeEncoder()
        DECODERS[Basic] = new BasicCollectionTypeDecoder()
    }

    final MappingContext mappingContext
    final PersistentEntity entity
    final CodecRegistry codecRegistry
    final boolean stateful

    BsonPersistentEntityCodec(CodecRegistry codecRegistry, PersistentEntity entity, boolean stateful = false) {
        this.mappingContext = entity.mappingContext
        this.codecRegistry = codecRegistry
        this.entity = entity
        this.stateful = stateful
    }

    static void registerEncoder(Class<? extends PersistentProperty> type, PropertyEncoder propertyEncoder) {
        ENCODERS.put(type, propertyEncoder)
    }

    static void registerDecoder(Class<? extends PersistentProperty> type, PropertyDecoder propertyDecoder) {
        DECODERS.put(type, propertyDecoder)
    }

    Object decode(BsonReader bsonReader, DecoderContext decoderContext = DEFAULT_DECODER_CONTEXT) {
        bsonReader.readStartDocument()
        def persistentEntity = entity
        def instance = persistentEntity.javaClass.newInstance()

        EntityAccess access = mappingContext.createEntityAccess(persistentEntity, instance)
        BsonType bsonType = bsonReader.readBsonType()
        boolean abortReading = false

        final boolean hasDynamicAttributes = instance instanceof DynamicAttributes
        while(bsonType != BsonType.END_OF_DOCUMENT) {

            def name = bsonReader.readName()
            if(!abortReading) {

                if(isDiscriminatorProperty(name)) {
                    def childEntity = mappingContext
                            .getChildEntityByDiscriminator(persistentEntity.rootEntity, bsonReader.readString())
                    if(childEntity != null) {
                        persistentEntity = childEntity
                        instance = childEntity
                                .newInstance()
                        def newAccess = createEntityAccess(childEntity, instance)
                        newAccess.setIdentifierNoConversion( access.identifier )
                        access = newAccess
                    }
                    bsonType = bsonReader.readBsonType()
                    continue
                }

                if(isIdentifierProperty(name)) {
                    getPropertyDecoder(Identity).decode( bsonReader, (Identity)persistentEntity.identity, access, decoderContext, codecRegistry)
                    Object cachedInstance = retrieveCachedInstance(access)

                    if(cachedInstance != null) {
                        instance = cachedInstance
                        abortReading = true
                    }
                }
                else {
                    PersistentProperty property = persistentEntity.getPropertyByName(name)
                    if(property && bsonType != BsonType.NULL) {
                        def propKind = property.getClass().superclass

                        if(CharSequence.isAssignableFrom(property.type) && bsonType == BsonType.STRING) {
                            access.setPropertyNoConversion(property.name, bsonReader.readString())
                        }
                        else {
                            getPropertyDecoder((Class<? extends PersistentProperty>)propKind)?.decode(bsonReader, property, access, decoderContext, codecRegistry)
                        }

                    }
                    else if(!abortReading && hasDynamicAttributes) {
                        readSchemaless(bsonReader, ((DynamicAttributes)instance), name, decoderContext)
                    }
                    else {
                        bsonReader.skipValue()
                    }

                }
            }
            else if(!abortReading){
                readSchemaless(bsonReader, ((DynamicAttributes)instance), name, decoderContext)
            }
            else {
                bsonReader.skipValue()
            }
            bsonType = bsonReader.readBsonType()
        }
        bsonReader.readEndDocument()

        readingComplete(access)


        return instance
    }


    void encode(BsonWriter writer, Object value, EncoderContext encoderContext = DEFAULT_ENCODER_CONTEXT) {
        encode(writer, value, encoderContext, true)
    }

    void encode(BsonWriter writer, Object value, EncoderContext encoderContext, boolean includeIdentifier) {
        writer.writeStartDocument()
        def access = createEntityAccess(value)
        def entity = access.persistentEntity

        if(!entity.isRoot()) {
            def discriminatorName = getDiscriminatorAttributeName()
            def discriminator = entity.discriminator
            writer.writeName(discriminatorName)
            writer.writeString(discriminator)
        }

        if (includeIdentifier) {
            def id = access.getIdentifier()
            if(id != null) {
                getPropertyEncoder(Identity).encode writer, (Identity)entity.identity, id, access, encoderContext, codecRegistry
            }
        }

        for (PersistentProperty prop in entity.persistentProperties) {
            def propKind = prop.getClass().superclass
            Object v = access.getProperty(prop.name)
            if (v != null) {
                PropertyEncoder<? extends PersistentProperty> encoder = getPropertyEncoder((Class<? extends PersistentProperty>)propKind)
                encoder?.encode(writer, (PersistentProperty) prop, v, access, encoderContext, codecRegistry)
            }
        }

        if(value instanceof DynamicAttributes) {
            def attributes = ((DynamicAttributes) value).attributes()
            writeAttributes(attributes, writer, encoderContext)
        }

        beforeFinishDocument(writer,access)
        writer.writeEndDocument()
        writer.flush()
        writingComplete(access)
    }

    /**
     * This method will encode an update for the given object based
     * @param value A {@link org.bson.conversions.Bson} that is the update object
     * @return A Bson
     */
    Bson encodeUpdate(Object value, EntityAccess access = createEntityAccess(value), EncoderContext encoderContext = DEFAULT_ENCODER_CONTEXT, boolean embedded = false) {
        BsonDocument update = new BsonDocument()
        def entity = access.persistentEntity

        def proxyFactory = mappingContext.proxyFactory
        if( proxyFactory.isProxy(value) ) {
            value = proxyFactory.unwrap(value)
        }

        if(value instanceof DirtyCheckable) {
            BsonWriter writer = new BsonDocumentWriter(update)
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
                            writer.writeName(prop.name)
                            encodeUpdate(v, createEntityAccess(((Embedded)prop).associatedEntity, v), encoderContext, true)
                        }
                        else if(prop instanceof EmbeddedCollection) {
                            // TODO: embedded collections
                        }
                        else {
                            def propKind = prop.getClass().superclass
                            if (prop instanceof PersistentProperty) {
                                PropertyEncoder<? extends PersistentProperty> propertyEncoder = getPropertyEncoder((Class<? extends PersistentProperty>)propKind)
                                propertyEncoder?.encode(writer, prop, v, access, encoderContext, codecRegistry)
                            }
                        }

                    }
                    else if(embedded || !isNew) {
                        writer.writeName(propertyName)
                        writer.writeNull()
                    }
                }
            }

            if(value instanceof DynamicAttributes) {
                Map<String, Object> attributes = ((DynamicAttributes) value).attributes()
                for(attr in attributes.keySet()) {
                    Object v = attributes.get(attr)
                    if(v == null) {
                        writer.writeName(attr)
                        writer.writeNull()
                    }
                    else {
                        writer.writeName(attr)
                        Codec<Object> codec = (Codec<Object>)codecRegistry.get(v.getClass())
                        codec.encode(writer, v, encoderContext)
                    }
                }
            }
            writer.writeEndDocument()
        }

        return update
    }

    @Override
    Class getEncoderClass() {
        entity.javaClass
    }

    /**
     * Writes the dynamic attributes to the writer
     *
     * @param attributes The dynamic attributes
     * @param writer The writer
     * @param encoderContext
     */
    protected void writeAttributes(Map<String, Object> attributes, BsonWriter writer, EncoderContext encoderContext) {
        for (name in attributes.keySet()) {
            writer.writeName name
            Object v = attributes.get(name)
            Codec<Object> codec = (Codec<Object>)codecRegistry.get(v.getClass())
            codec.encode(writer, v, encoderContext)
        }
    }

    /**
     *
     * Retrieve a cached instance if any
     *
     * @param access The entity access
     * @return A cached instance
     */
    protected Object retrieveCachedInstance(EntityAccess access) {
        return null
    }

    /**
     * Whether the given document attribute is the identifier
     *
     * @param name The name of the document attribute
     * @return True if it is an identifier
     */
    protected boolean isIdentifierProperty(String name) {
        return name == GormProperties.IDENTITY
    }

    /**
     * Called directly before the last call to finish the document writing process
     *
     * @param bsonWriter The {@link BsonWriter}
     * @param access The entity access
     */
    protected void beforeFinishDocument(BsonWriter bsonWriter, EntityAccess access) {
        // no-op
    }

    /**
     * Called when the document is fully written from the source entity
     *
     * @param entityAccess Access to the entity
     */
    protected void writingComplete(EntityAccess entityAccess) {
        // no-op
    }

    /**
     * Called when reading from a {@link BsonReader} is completed
     *
     * @param access the access
     */
    protected void readingComplete(EntityAccess access) {
        // no-op
    }

    /**
     * Reads an undeclared property
     *
     * @param bsonReader The bson reader
     * @param dynamicAttributes a document of undeclared properties
     * @param name
     * @param decoderContext
     */
    protected void readSchemaless(BsonReader bsonReader, DynamicAttributes dynamicAttributes, String name, DecoderContext decoderContext) {
        def currentBsonType = bsonReader.getCurrentBsonType()
        def targetClass = BsonValueCodecProvider.getClassForBsonType(currentBsonType)

        def codec = codecRegistry.get(targetClass)

        BsonValue bsonValue = (BsonValue)codec.decode(bsonReader, decoderContext)
        if(bsonValue != null) {

            def converter = CodecExtensions.getBsonConverter(bsonValue.getClass())
            dynamicAttributes.putAt(
                    name,
                    converter != null ? converter.convert( bsonValue ) : bsonValue
            )
        }
    }

    protected EntityAccess createEntityAccess(Object instance) {
        def entity = mappingContext.getPersistentEntity(instance.getClass().name)
        return createEntityAccess(entity, instance)
    }

    protected EntityAccess createEntityAccess(PersistentEntity entity, instance) {
        return mappingContext.createEntityAccess(entity, instance)
    }

    /**
     * @return The name of the discriminator
     */
    protected String getDiscriminatorAttributeName() {
        return GormProperties.CLASS
    }

    protected boolean isDiscriminatorProperty(String name) {
        def discriminatorName = getDiscriminatorAttributeName()
        return discriminatorName != null && name.equals(discriminatorName)
    }


    /**
     * Obtains the property encoder for the given property type
     *
     * @param type The property encoder type
     * @return The encoder or null if it doesn't exist
     */
    protected <T extends PersistentProperty> PropertyEncoder<T> getPropertyEncoder(Class<T> type) {
        return ENCODERS.get(type)
    }

    /**
     * Obtains the property encoder for the given property type
     *
     * @param type The property encoder type
     * @return The encoder or null if it doesn't exist
     */
    protected <T extends PersistentProperty> PropertyDecoder<T> getPropertyDecoder(Class<T> type) {
        return DECODERS.get(type)
    }
}
