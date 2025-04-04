package org.grails.datastore.bson.codecs.encoders

import groovy.transform.CompileStatic
import org.bson.BsonBinary
import org.bson.BsonWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.Binary
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.grails.datastore.bson.codecs.PropertyEncoder
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.internal.MappingUtils
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
 * An encoder for simple types persistable by MongoDB
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class SimpleEncoder implements PropertyEncoder<Simple> {

    static interface TypeEncoder {
        void encode(BsonWriter writer, PersistentProperty property, Object value)
    }

    public static final Map<Class, TypeEncoder> SIMPLE_TYPE_ENCODERS
    public static final TypeEncoder DEFAULT_ENCODER = new TypeEncoder() {
        @Override
        void encode(BsonWriter writer, PersistentProperty property, Object value) {
            writer.writeString( value.toString() )
        }
    }

    static {


        SIMPLE_TYPE_ENCODERS = new HashMap<Class, TypeEncoder>().withDefault { Class c ->
            DEFAULT_ENCODER
        }

        TypeEncoder smallNumberEncoder = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeInt32( ((Number)value).intValue() )
            }
        }
        SIMPLE_TYPE_ENCODERS[CharSequence] = DEFAULT_ENCODER
        SIMPLE_TYPE_ENCODERS[String] = DEFAULT_ENCODER
        SIMPLE_TYPE_ENCODERS[StringBuffer] = DEFAULT_ENCODER
        SIMPLE_TYPE_ENCODERS[StringBuilder] = DEFAULT_ENCODER
        SIMPLE_TYPE_ENCODERS[Byte] = smallNumberEncoder
        SIMPLE_TYPE_ENCODERS[byte.class] = smallNumberEncoder
        SIMPLE_TYPE_ENCODERS[Integer] = smallNumberEncoder
        SIMPLE_TYPE_ENCODERS[int.class] = smallNumberEncoder
        SIMPLE_TYPE_ENCODERS[Short] = smallNumberEncoder
        SIMPLE_TYPE_ENCODERS[short.class] = smallNumberEncoder
        TypeEncoder doubleEncoder = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeDouble( (Double)value )
            }
        }
        SIMPLE_TYPE_ENCODERS[Double] = doubleEncoder
        SIMPLE_TYPE_ENCODERS[double.class] = doubleEncoder
        TypeEncoder longEncoder = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeInt64( (Long)value )
            }
        }
        SIMPLE_TYPE_ENCODERS[Long] = longEncoder
        SIMPLE_TYPE_ENCODERS[long.class] = longEncoder
        TypeEncoder booleanEncoder = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeBoolean( (Boolean)value )
            }
        }
        SIMPLE_TYPE_ENCODERS[Boolean] = booleanEncoder
        SIMPLE_TYPE_ENCODERS[boolean.class] = booleanEncoder
        SIMPLE_TYPE_ENCODERS[Calendar] = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeDateTime( ((Calendar)value).timeInMillis )
            }
        }
        SIMPLE_TYPE_ENCODERS[Date] = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeDateTime( ((Date)value).time )
            }
        }
        SIMPLE_TYPE_ENCODERS[TimeZone] = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeString( ((TimeZone)value).ID )
            }
        }

        SIMPLE_TYPE_ENCODERS[LocalDate] = new LocalDateEncoder()
        SIMPLE_TYPE_ENCODERS[LocalDateTime] = new LocalDateTimeEncoder()
        SIMPLE_TYPE_ENCODERS[LocalTime] = new LocalTimeEncoder()
        SIMPLE_TYPE_ENCODERS[OffsetDateTime] = new OffsetDateTimeEncoder()
        SIMPLE_TYPE_ENCODERS[OffsetTime] = new OffsetTimeEncoder()
        SIMPLE_TYPE_ENCODERS[ZonedDateTime] = new ZonedDateTimeEncoder()
        SIMPLE_TYPE_ENCODERS[Period] = new PeriodEncoder()
        SIMPLE_TYPE_ENCODERS[Instant] = new InstantEncoder()

        SIMPLE_TYPE_ENCODERS[([] as byte[]).getClass()] = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeBinaryData( new BsonBinary((byte[])value))
            }
        }
        SIMPLE_TYPE_ENCODERS[Binary] = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeBinaryData( new BsonBinary(((Binary)value).data))
            }
        }
        SIMPLE_TYPE_ENCODERS[ObjectId] = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeObjectId((ObjectId)value)
            }
        }
    }


    @Override
    void encode(BsonWriter writer, Simple property, Object value, EntityAccess parentAccess, EncoderContext encoderContext, CodecRegistry codecRegistry) {
        def type = property.type
        def encoder = SIMPLE_TYPE_ENCODERS[type]
        writer.writeName( MappingUtils.getTargetKey(property) )
        if(type.isArray()) {
            if(!encoder.is(DEFAULT_ENCODER)) {
                encoder.encode(writer, property, value)
            }
            else {
                writer.writeStartArray()
                for( o in value ) {
                    encoder = SIMPLE_TYPE_ENCODERS[type.componentType]
                    encoder.encode(writer, property, o)
                }
                writer.writeEndArray()
            }
        }
        else {
            encoder.encode(writer, property, value)
        }
    }

    /**
     * Enables Decimal128 encoding for simple types
     */
    static void enableBigDecimalEncoding() {
        SIMPLE_TYPE_ENCODERS[BigInteger] = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeDecimal128(new Decimal128(((BigInteger) value).toBigDecimal()))
            }
        }
        SIMPLE_TYPE_ENCODERS[BigDecimal] = new TypeEncoder() {
            @Override
            void encode(BsonWriter writer, PersistentProperty property, Object value) {
                writer.writeDecimal128(new Decimal128((BigDecimal) value))
            }
        }
    }

}