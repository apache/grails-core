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
package org.grails.datastore.bson.codecs

import groovy.transform.CompileStatic
import org.bson.BsonArray
import org.bson.BsonBinary
import org.bson.BsonBoolean
import org.bson.BsonDateTime
import org.bson.BsonDecimal128
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonNull
import org.bson.BsonObjectId
import org.bson.BsonReader
import org.bson.BsonRegularExpression
import org.bson.BsonString
import org.bson.BsonTimestamp
import org.bson.BsonType
import org.bson.BsonValue
import org.bson.BsonWriter
import org.bson.codecs.BooleanCodec
import org.bson.codecs.BsonValueCodecProvider
import org.bson.codecs.ByteArrayCodec
import org.bson.codecs.Codec
import org.bson.codecs.DateCodec
import org.bson.codecs.DecoderContext
import org.bson.codecs.DocumentCodec
import org.bson.codecs.DoubleCodec
import org.bson.codecs.EncoderContext
import org.bson.codecs.IntegerCodec
import org.bson.codecs.LongCodec
import org.bson.codecs.ObjectIdCodec
import org.bson.codecs.StringCodec
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.ObjectId
import org.codehaus.groovy.runtime.GStringImpl
import org.springframework.core.convert.converter.Converter

import java.util.regex.Pattern

/**
 * Additional {@link Codec} extensions. This class implements {@CodecProvider} and integrates with the {@CodecRegistry} to make it easier to convert objects to and from BSON documents
 *
 * @author Graeme Rocher
 * @since 4.1
 */
@CompileStatic
class CodecExtensions implements CodecProvider {
    private static final Map<Class, Codec> ADDITIONAL_CODECS = [:]
    private static final Map<Class<? extends BsonValue>, List<Converter>> BSON_VALUE_CONVERTERS = new LinkedHashMap<Class<? extends BsonValue>, List<Converter>>().withDefault { Class<? extends BsonValue> cls ->
        new ArrayList<Converter>() as List<Converter>
    }
    private static final Map<BsonType, Codec<?>> BSON_TYPE_CODECS = [
            (BsonType.ARRAY) : new ListCodec(),
            (BsonType.DOCUMENT) : new DocumentCodec(),
            (BsonType.BINARY) : new ByteArrayCodec(),
            (BsonType.BOOLEAN) : new BooleanCodec(),
            (BsonType.DATE_TIME) : new DateCodec(),
            (BsonType.DOUBLE) : new DoubleCodec(),
            (BsonType.INT32) : new IntegerCodec(),
            (BsonType.INT64) : new LongCodec(),
            (BsonType.STRING) : new StringCodec(),
            (BsonType.OBJECT_ID) : new ObjectIdCodec()
    ]

    static {
        ADDITIONAL_CODECS[IntRange] = new IntRangeCodec()
        ADDITIONAL_CODECS[GStringImpl] = new GStringCodec()
        ADDITIONAL_CODECS[Locale] = new LocaleCodec()
        ADDITIONAL_CODECS[Currency] = new CurrencyCodec()
        ADDITIONAL_CODECS[GString] = new GStringCodec()
        ADDITIONAL_CODECS[List] = new ListCodec()
        ADDITIONAL_CODECS[Map] = new MapCodec()
        ADDITIONAL_CODECS[LinkedHashMap] = new MapCodec()
        ADDITIONAL_CODECS[HashMap] = new MapCodec()
        ADDITIONAL_CODECS[ArrayList] = new ListCodec()
        BSON_VALUE_CONVERTERS[BsonBinary] << new Converter<BsonBinary, byte[]>() {
            @Override
            byte[] convert(BsonBinary source) {
                return source.data
            }
        }
        BSON_VALUE_CONVERTERS[BsonObjectId] << new Converter<BsonObjectId, ObjectId>() {
            @Override
            ObjectId convert(BsonObjectId source) {
                source.value
            }
        }
        BSON_VALUE_CONVERTERS[BsonTimestamp] << new Converter<BsonTimestamp, Date>() {
            @Override
            Date convert(BsonTimestamp source) {
                new Date(source.time * 1000)
            }
        }
        BSON_VALUE_CONVERTERS[BsonDateTime] << new Converter<BsonDateTime, Date>() {
            @Override
            Date convert(BsonDateTime source) {
                new Date(source.value)
            }
        }
        BSON_VALUE_CONVERTERS[BsonString] << new Converter<BsonString, CharSequence>() {
            @Override
            CharSequence convert(BsonString source) {
                source.value
            }
        }
        BSON_VALUE_CONVERTERS[BsonString] << new Converter<BsonString, String>() {
            @Override
            String convert(BsonString source) {
                source.value
            }
        }
        BSON_VALUE_CONVERTERS[BsonRegularExpression] << new Converter<BsonRegularExpression, Pattern>() {
            @Override
            Pattern convert(BsonRegularExpression source) {
                Pattern.compile(source.pattern)
            }
        }
        BSON_VALUE_CONVERTERS[BsonBoolean] << new Converter<BsonBoolean, Boolean>() {
            @Override
            Boolean convert(BsonBoolean source) {
                source.value
            }
        }
        BSON_VALUE_CONVERTERS[BsonNull] << new Converter<BsonNull, Object>() {
            @Override
            Object convert(BsonNull source) {
                return null
            }
        }
        BSON_VALUE_CONVERTERS[BsonDouble] << new Converter<BsonDouble, Double>() {
            @Override
            Double convert(BsonDouble source) {
                source.doubleValue()
            }
        }
        BSON_VALUE_CONVERTERS[BsonDouble] << new Converter<BsonDouble, Float>() {
            @Override
            Float convert(BsonDouble source) {
                source.doubleValue().floatValue()
            }
        }
        BSON_VALUE_CONVERTERS[BsonInt32] << new Converter<BsonInt32, Integer>() {
            @Override
            Integer convert(BsonInt32 source) {
                return source.intValue()
            }
        }
        BSON_VALUE_CONVERTERS[BsonInt32] << new Converter<BsonInt32, Short>() {
            @Override
            Short convert(BsonInt32 source) {
                source.intValue().shortValue()
            }
        }
        BSON_VALUE_CONVERTERS[BsonInt64] << new Converter<BsonInt64, Long>() {
            @Override
            Long convert(BsonInt64 source) {
                source.longValue()
            }
        }
        BSON_VALUE_CONVERTERS[BsonArray] << new Converter<BsonArray, List>() {
            @Override
            List convert(BsonArray source) {
                List list = []
                for(BsonValue v in source) {
                    if(v != null) {
                        def converter = BSON_VALUE_CONVERTERS[v.getClass()]?.first()
                        list << (converter ? converter.convert(v) : v)
                    }
                    else {
                        list << v
                    }
                }
                return list
            }
        }
        BSON_VALUE_CONVERTERS[BsonArray] << new Converter<BsonArray, Object[]>() {
            @Override
            Object[] convert(BsonArray source) {
                Object[] array = new Object[source.size()]
                int i = 0
                for(BsonValue v in source) {
                    if(v != null) {
                        def converter = BSON_VALUE_CONVERTERS[v.getClass()]?.first()
                        array[i++] = (converter ? converter.convert(v) : v)
                    }
                    else {
                        array[i++] = v
                    }
                }
                return array
            }
        }
        BSON_VALUE_CONVERTERS[BsonDocument] << new Converter<BsonDocument, Map<String, Object>>() {
            @Override
            Map<String, Object> convert(BsonDocument source) {
                Map<String, Object> map = [:]

                for(key in source.keySet()) {
                    def v = source[key]
                    if(v != null) {
                        def converter = BSON_VALUE_CONVERTERS[v.getClass()]?.first()
                        map[key] = (converter ? converter.convert(v) : v)
                    }
                    else {
                        map[key] = v
                    }

                }
                return map
            }
        }

        BSON_VALUE_CONVERTERS[BsonDecimal128] << new Converter<BsonDecimal128, BigDecimal>() {
            @Override
            BigDecimal convert(BsonDecimal128 source) {
                return source.decimal128Value().bigDecimalValue()
            }
        }
    }

    static Collection<Converter> getBsonConverters() {
        return (Collection<Converter>)BSON_VALUE_CONVERTERS.values().flatten()
    }

    static Converter getBsonConverter(Class<? extends BsonValue> type) {
        def converters = BSON_VALUE_CONVERTERS.get(type)
        if(!converters.isEmpty()) {
            return converters.first()
        }
        return null
    }


    static Codec getCodecForBsonType(BsonType bsonType, CodecRegistry registry) {
        def codec = BSON_TYPE_CODECS.get(bsonType)
        if(codec != null) {
            if(codec instanceof CodecRegistryAware) {
                codec.codecRegistry = registry
            }
            return codec
        }
        return null
    }

    @Override
    def <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        def codec = ADDITIONAL_CODECS.get(clazz)
        if(codec != null) {
            if(codec instanceof CodecRegistryAware) {
                codec.codecRegistry = registry
            }
            return codec
        }
        return null
    }

    static class MapCodec implements Codec<Map<String, Object>>, CodecRegistryAware {
        CodecRegistry codecRegistry

        @Override
        Map<String, Object> decode(BsonReader reader, DecoderContext decoderContext) {

            Map<String, Object> map = [:]
            reader.readStartDocument()
            BsonType bsonType = reader.readBsonType()
            while(bsonType != BsonType.END_OF_DOCUMENT) {
                def key = reader.readName()
                BsonValue bsonValue = readValue(reader, decoderContext)
                Object value = null
                if(bsonValue != null) {
                    def converter = BSON_VALUE_CONVERTERS.get(bsonValue.getClass())?.first()
                    value = converter ? converter.convert(bsonValue) : bsonValue
                }
                map[key] = value
                bsonType = reader.readBsonType()
            }
            reader.readEndDocument()
            return map
        }

        @Override
        void encode(BsonWriter writer, Map<String, Object> values, EncoderContext encoderContext) {
            writer.writeStartDocument()
            for(entry in values) {
                def v = entry.value
                writer.writeName(entry.key)
                if(v == null) {
                    writer.writeNull()
                }
                else {
                    Codec<Object> c = (Codec<Object>)codecRegistry.get( v.getClass() )
                    c.encode(writer, v, encoderContext)
                }
            }
            writer.writeEndDocument()
        }

        @Override
        Class<Map<String, Object>> getEncoderClass() {
            Map
        }

        protected BsonValue readValue(final BsonReader reader, final DecoderContext decoderContext) {
            return codecRegistry.get(BsonValueCodecProvider.getClassForBsonType(reader.getCurrentBsonType())).decode(reader, decoderContext);
        }
    }
    static class ListCodec implements Codec<List>, CodecRegistryAware {
        CodecRegistry codecRegistry

        @Override
        List decode(BsonReader reader, DecoderContext decoderContext) {
            List list = new ArrayList()
            reader.readStartArray()
            BsonType bsonType = reader.readBsonType()
            while(bsonType != BsonType.END_OF_DOCUMENT) {

                BsonValue bsonValue = readValue(reader, decoderContext)
                Object value = null
                if(bsonValue != null) {
                    def converters = BSON_VALUE_CONVERTERS.get(bsonValue.getClass())
                    Converter converter = !converters.isEmpty() ? converters?.first() : null
                    value = converter != null ? converter.convert(bsonValue) : bsonValue
                }
                list << value
                bsonType = reader.readBsonType()
            }
            reader.readEndArray()
            return list
        }

        @Override
        void encode(BsonWriter writer, List values, EncoderContext encoderContext) {
            writer.writeStartArray()
            for(v in values) {
                if(v == null) {
                    writer.writeNull()
                }
                else {
                    Codec<Object> c = (Codec<Object>)codecRegistry.get( v.getClass() )
                    c.encode(writer, v, encoderContext)
                }
            }
            writer.writeEndArray()
        }

        @Override
        Class<List> getEncoderClass() {
            return List
        }

        protected BsonValue readValue(final BsonReader reader, final DecoderContext decoderContext) {
            def currentBsonType = reader.getCurrentBsonType()
            def targetClass = BsonValueCodecProvider.getClassForBsonType(currentBsonType)

            def codec = codecRegistry.get(targetClass)
            return codec.decode(reader, decoderContext);
        }
    }

    static class IntRangeCodec implements Codec<IntRange>{


        @Override
        void encode(BsonWriter writer, IntRange value, EncoderContext encoderContext) {
            Integer from = (Integer)value.from
            Integer to = (Integer)value.to

            writer.writeStartArray()
            writer.writeInt32(from)
            writer.writeInt32(to)
            writer.writeEndArray()
        }

        @Override
        IntRange decode(BsonReader reader, DecoderContext decoderContext) {
            reader.readStartArray()
            def from = reader.readInt32()
            def to = reader.readInt32()
            reader.readEndArray()
            return new IntRange(from, to)
        }

        @Override
        Class<IntRange> getEncoderClass() {
            IntRange
        }
    }

    static class LocaleCodec implements Codec<Locale> {

        @Override
        Locale decode(BsonReader reader, DecoderContext decoderContext) {
            new Locale( reader.readString() )
        }

        @Override
        void encode(BsonWriter writer, Locale value, EncoderContext encoderContext) {
            writer.writeString(value.toString())
        }

        @Override
        Class<Locale> getEncoderClass() {
            Locale
        }
    }

    static class CurrencyCodec implements Codec<Currency> {

        @Override
        Currency decode(BsonReader reader, DecoderContext decoderContext) {
            Currency.getInstance( reader.readString() )
        }

        @Override
        void encode(BsonWriter writer, Currency value, EncoderContext encoderContext) {
            writer.writeString(value.currencyCode)
        }

        @Override
        Class<Currency> getEncoderClass() {
            Currency
        }
    }

    static class GStringCodec implements Codec<GString> {

        @Override
        GString decode(BsonReader reader, DecoderContext decoderContext) {
            return new GStringImpl([] as Object[], [reader.readString()] as String[])
        }

        @Override
        void encode(BsonWriter writer, GString value, EncoderContext encoderContext) {
            writer.writeString(value.toString())
        }

        @Override
        Class<GString> getEncoderClass() {
            GString
        }
    }
}
