package org.grails.datastore.bson.codecs.decoders

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.grails.datastore.bson.codecs.PropertyDecoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.Identity
import org.springframework.dao.DataIntegrityViolationException


/**
 * A {@PropertyDecoder} capable of decoding the {@link org.grails.datastore.mapping.model.types.Identity}
 */
@CompileStatic
class IdentityDecoder implements PropertyDecoder<Identity> {

    public static final Map<Class, IdentityTypeDecoder> IDENTITY_DECODERS = [:]
    public static final Map<BsonType, IdentityTypeDecoder> DEFAULT_DECODERS = [:]

    static {
        IDENTITY_DECODERS[ObjectId] = new IdentityTypeDecoder() {

            @Override
            BsonType bsonType() {
                BsonType.OBJECT_ID
            }

            @Override
            void decode(BsonReader bsonReader, Identity property, EntityAccess access) {
                access.setIdentifierNoConversion( bsonReader.readObjectId() )
            }
        }
        IDENTITY_DECODERS[Long] = new IdentityTypeDecoder() {

            @Override
            BsonType bsonType() {
                BsonType.INT64
            }

            @Override
            void decode(BsonReader bsonReader, Identity property, EntityAccess access) {
                access.setIdentifierNoConversion( bsonReader.readInt64() )
            }
        }

        IDENTITY_DECODERS[Integer] = new IdentityTypeDecoder() {

            @Override
            BsonType bsonType() {
                BsonType.INT32
            }

            @Override
            void decode(BsonReader bsonReader, Identity property, EntityAccess access) {
                access.setIdentifierNoConversion( bsonReader.readInt32() )
            }
        }

        def stringDecoder = new IdentityTypeDecoder() {

            @Override
            BsonType bsonType() {
                BsonType.STRING
            }

            @Override
            void decode(BsonReader bsonReader, Identity property, EntityAccess access) {
                access.setIdentifierNoConversion(bsonReader.readString())
            }
        }
        IDENTITY_DECODERS[String] = stringDecoder

        DEFAULT_DECODERS[BsonType.INT32] = new IdentityTypeDecoder() {

            @Override
            BsonType bsonType() {
                BsonType.INT32
            }

            @Override
            void decode(BsonReader bsonReader, Identity property, EntityAccess access) {
                access.setIdentifier(bsonReader.readInt32())
            }
        }
        DEFAULT_DECODERS[BsonType.STRING] = new IdentityTypeDecoder() {

            @Override
            BsonType bsonType() {
                BsonType.STRING
            }

            @Override
            void decode(BsonReader bsonReader, Identity property, EntityAccess access) {
                access.setIdentifier(bsonReader.readString())
            }
        }
        DEFAULT_DECODERS[BsonType.INT64] = new IdentityTypeDecoder() {

            @Override
            BsonType bsonType() {
                BsonType.INT64
            }

            @Override
            void decode(BsonReader bsonReader, Identity property, EntityAccess access) {
                access.setIdentifier(bsonReader.readInt64())
            }
        }
        DEFAULT_DECODERS[BsonType.OBJECT_ID] = new IdentityTypeDecoder() {

            @Override
            BsonType bsonType() {
                BsonType.OBJECT_ID
            }

            @Override
            void decode(BsonReader bsonReader, Identity property, EntityAccess access) {
                access.setIdentifier(bsonReader.readObjectId())
            }
        }
    }

    @Override
    void decode(BsonReader bsonReader, Identity property, EntityAccess access, DecoderContext decoderContext, CodecRegistry codecRegistry) {
        BsonType bsonType = bsonReader.currentBsonType
        IdentityTypeDecoder decoder = IDENTITY_DECODERS.get(property.type)
        if(decoder == null) {
            throw new IllegalStateException("Invalid identity type [$property.type}] for entity ${property.owner.name}")
        }

        if(bsonType != decoder.bsonType()) {
            decoder = DEFAULT_DECODERS.get(bsonType)
            if(decoder == null) {
                throw new DataIntegrityViolationException("Invalid underlying identifier type [$bsonType] reading entity ${property.owner.name}. Please verify the integrity of your data.")
            }
        }

        decoder.decode(bsonReader, property, access)
    }

    interface IdentityTypeDecoder {
        BsonType bsonType()

        void decode(BsonReader bsonReader, Identity property, EntityAccess access)
    }
}