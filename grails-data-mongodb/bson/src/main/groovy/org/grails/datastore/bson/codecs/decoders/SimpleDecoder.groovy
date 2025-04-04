package org.grails.datastore.bson.codecs.decoders

import groovy.transform.CompileStatic
import org.bson.BsonReader
import org.bson.BsonRegularExpression
import org.bson.BsonType
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.Binary
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.grails.datastore.bson.codecs.PropertyDecoder
import org.grails.datastore.bson.codecs.encoders.InstantEncoder
import org.grails.datastore.bson.codecs.encoders.LocalDateEncoder
import org.grails.datastore.bson.codecs.encoders.LocalDateTimeEncoder
import org.grails.datastore.bson.codecs.encoders.LocalTimeEncoder
import org.grails.datastore.bson.codecs.encoders.OffsetDateTimeEncoder
import org.grails.datastore.bson.codecs.encoders.OffsetTimeEncoder
import org.grails.datastore.bson.codecs.encoders.PeriodEncoder
import org.grails.datastore.bson.codecs.encoders.ZonedDateTimeEncoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Simple

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.ZonedDateTime

/**
 * A {@PropertyDecoder} capable of decoding {@link org.grails.datastore.mapping.model.types.Simple} properties
 */
@CompileStatic
class SimpleDecoder implements PropertyDecoder<Simple> {
    public static final Map<Class, TypeDecoder> SIMPLE_TYPE_DECODERS
    public static final TypeDecoder DEFAULT_DECODER = new TypeDecoder() {
        @Override
        BsonType bsonType() {
            BsonType.STRING
        }

        @Override
        void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
            entityAccess.setProperty( property.name, reader.readString())
        }
    }
    public static final Map<BsonType, TypeDecoder> DEFAULT_DECODERS = new HashMap<BsonType, TypeDecoder>().withDefault { Class ->
        DEFAULT_DECODER
    }

    static interface TypeDecoder {

        BsonType bsonType()

        void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess)
    }

    static {
        SIMPLE_TYPE_DECODERS = new HashMap<Class, TypeDecoder>().withDefault { Class ->
            DEFAULT_DECODER
        }

        DEFAULT_DECODERS.put(BsonType.REGULAR_EXPRESSION, new TypeDecoder() {
            @Override
            BsonType bsonType() {
                BsonType.REGULAR_EXPRESSION
            }

            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {

                BsonRegularExpression regularExpression = reader.readRegularExpression()
                entityAccess.setProperty( property.name, regularExpression.pattern )
            }
        })

        DEFAULT_DECODERS.put(BsonType.DECIMAL128, new TypeDecoder() {
            @Override
            BsonType bsonType() {
                BsonType.DECIMAL128
            }

            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                Decimal128 dec = reader.readDecimal128()
                entityAccess.setProperty( property.name, dec )
            }
        })
        def convertingIntReader =  new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                entityAccess.setProperty( property.name, reader.readInt32() )
            }

            @Override
            BsonType bsonType() {
                BsonType.INT32
            }
        }

        DEFAULT_DECODERS.put(convertingIntReader.bsonType(), convertingIntReader)

        SIMPLE_TYPE_DECODERS[Short] = convertingIntReader
        SIMPLE_TYPE_DECODERS[short.class] = convertingIntReader
        SIMPLE_TYPE_DECODERS[Byte] = convertingIntReader
        SIMPLE_TYPE_DECODERS[byte.class] = convertingIntReader


        def intDecoder = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                entityAccess.setPropertyNoConversion( property.name, reader.readInt32() )
            }

            @Override
            BsonType bsonType() {
                BsonType.INT32
            }
        }

        SIMPLE_TYPE_DECODERS[Integer] = intDecoder
        SIMPLE_TYPE_DECODERS[int.class] = intDecoder

        def longDecoder = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                entityAccess.setPropertyNoConversion( property.name, reader.readInt64() )
            }

            @Override
            BsonType bsonType() {
                BsonType.INT64
            }
        }

        def convertingLongDecoder = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                entityAccess.setProperty( property.name, reader.readInt64() )
            }

            @Override
            BsonType bsonType() {
                BsonType.INT64
            }
        }

        DEFAULT_DECODERS.put(convertingLongDecoder.bsonType(), convertingLongDecoder)

        SIMPLE_TYPE_DECODERS[Long] = longDecoder
        SIMPLE_TYPE_DECODERS[long.class] = longDecoder

        def doubleDecoder = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                entityAccess.setPropertyNoConversion( property.name, reader.readDouble() )
            }

            @Override
            BsonType bsonType() {
                BsonType.DOUBLE
            }
        }

        def convertingDoubleDecoder = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                entityAccess.setPropertyNoConversion( property.name, reader.readDouble() )
            }

            @Override
            BsonType bsonType() {
                BsonType.DOUBLE
            }
        }

        DEFAULT_DECODERS.put(convertingDoubleDecoder.bsonType(), convertingDoubleDecoder)

        SIMPLE_TYPE_DECODERS[Double] = doubleDecoder
        SIMPLE_TYPE_DECODERS[double.class] = doubleDecoder

        def booleanDecoder = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                entityAccess.setPropertyNoConversion( property.name, reader.readBoolean() )
            }

            @Override
            BsonType bsonType() {
                BsonType.BOOLEAN
            }
        }

        DEFAULT_DECODERS.put(booleanDecoder.bsonType(), booleanDecoder)
        SIMPLE_TYPE_DECODERS[Boolean] = booleanDecoder
        SIMPLE_TYPE_DECODERS[boolean.class] = booleanDecoder


        def binaryDecoder = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                def binary = reader.readBinaryData()
                entityAccess.setPropertyNoConversion(property.name, binary.data)
            }

            @Override
            BsonType bsonType() {
                BsonType.BINARY
            }
        }

        DEFAULT_DECODERS.put(binaryDecoder.bsonType(), binaryDecoder)
        SIMPLE_TYPE_DECODERS[([] as byte[]).getClass()] = binaryDecoder

        SIMPLE_TYPE_DECODERS[Date] = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                def time = reader.readDateTime()
                entityAccess.setPropertyNoConversion( property.name, new Date(time))
            }

            @Override
            BsonType bsonType() {
                BsonType.DATE_TIME
            }
        }

        SIMPLE_TYPE_DECODERS[Calendar] = new TypeDecoder() {
            @Override
            BsonType bsonType() {
                BsonType.DATE_TIME
            }

            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {
                def time = reader.readDateTime()
                def calendar = new GregorianCalendar()
                calendar.setTimeInMillis(time)
                entityAccess.setPropertyNoConversion( property.name, calendar)
            }
        }

        SIMPLE_TYPE_DECODERS[LocalDate] = new LocalDateDecoder()
        SIMPLE_TYPE_DECODERS[LocalDateTime] = new LocalDateTimeDecoder()
        SIMPLE_TYPE_DECODERS[LocalTime] = new LocalTimeDecoder()
        SIMPLE_TYPE_DECODERS[OffsetDateTime] = new OffsetDateTimeDecoder()
        SIMPLE_TYPE_DECODERS[OffsetTime] = new OffsetTimeDecoder()
        SIMPLE_TYPE_DECODERS[ZonedDateTime] = new ZonedDateTimeDecoder()
        SIMPLE_TYPE_DECODERS[Period] = new PeriodDecoder()
        SIMPLE_TYPE_DECODERS[Instant] = new InstantDecoder()

        SIMPLE_TYPE_DECODERS[Binary] = new TypeDecoder() {
            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {

                entityAccess.setPropertyNoConversion(
                        property.name,
                        new Binary(reader.readBinaryData().data)
                )
            }

            @Override
            BsonType bsonType() {
                BsonType.BINARY
            }
        }

        SIMPLE_TYPE_DECODERS[ObjectId] = new TypeDecoder() {
            @Override
            BsonType bsonType() {
                BsonType.OBJECT_ID
            }

            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {

                entityAccess.setPropertyNoConversion(
                        property.name,
                        reader.readObjectId()
                )


            }
        }

        SIMPLE_TYPE_DECODERS[BigDecimal] = new TypeDecoder() {
            @Override
            BsonType bsonType() {
                BsonType.DECIMAL128
            }

            @Override
            void decode(BsonReader reader, PersistentProperty property, EntityAccess entityAccess) {

                entityAccess.setPropertyNoConversion(
                        property.name,
                        reader.readDecimal128().bigDecimalValue()
                )
            }
        }
    }

    @Override
    void decode(BsonReader reader, Simple property, EntityAccess entityAccess, DecoderContext decoderContext, CodecRegistry codecRegistry) {
        def type = property.type

        TypeDecoder decoder = SIMPLE_TYPE_DECODERS[type]

        if(type.isArray()) {
            if(!decoder.is(DEFAULT_DECODER)) {
                decoder.decode reader, property, entityAccess
            }
            else {
                def arrayDecoder = codecRegistry.get(List)
                def bsonArray = arrayDecoder.decode(reader, decoderContext)
                entityAccess.setProperty(property.name, bsonArray)
            }
        }
        else {
            BsonType bsonType = reader.currentBsonType
            if(bsonType != decoder.bsonType()) {
                DEFAULT_DECODERS.get(bsonType).decode(reader, property, entityAccess)
            }
            else {
                decoder.decode reader, property, entityAccess
            }
        }
    }
}
