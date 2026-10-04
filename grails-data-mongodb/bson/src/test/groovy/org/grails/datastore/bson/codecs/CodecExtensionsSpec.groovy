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
package org.grails.datastore.bson.codecs

import java.util.regex.Pattern

import org.bson.BsonArray
import org.bson.BsonBinary
import org.bson.BsonBoolean
import org.bson.BsonDateTime
import org.bson.BsonDecimal128
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonMaxKey
import org.bson.BsonNull
import org.bson.BsonObjectId
import org.bson.BsonReader
import org.bson.BsonRegularExpression
import org.bson.BsonString
import org.bson.BsonTimestamp
import org.bson.BsonType
import org.bson.BsonValue
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.BsonStringCodec
import org.bson.codecs.DocumentCodec
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.codehaus.groovy.runtime.GStringImpl
import spock.lang.Specification
import spock.lang.Unroll

class CodecExtensionsSpec extends Specification {

    CodecExtensions provider = new CodecExtensions()
    CodecRegistry codecRegistry = Stub(CodecRegistry)

    @Unroll
    void "provides a #expectedType.simpleName for #type.simpleName"() {
        expect:
        provider.get(type, codecRegistry).class == expectedType

        where:
        type          | expectedType
        IntRange      | CodecExtensions.IntRangeCodec
        GStringImpl   | CodecExtensions.GStringCodec
        GString       | CodecExtensions.GStringCodec
        Locale        | CodecExtensions.LocaleCodec
        Currency      | CodecExtensions.CurrencyCodec
        List          | CodecExtensions.ListCodec
        ArrayList     | CodecExtensions.ListCodec
        Map           | CodecExtensions.MapCodec
        LinkedHashMap | CodecExtensions.MapCodec
        HashMap       | CodecExtensions.MapCodec
    }

    void "provides no codec for an unregistered type"() {
        expect:
        provider.get(Thread, codecRegistry) == null
    }

    void "wires the registry into a CodecRegistryAware codec it hands out"() {
        when:
        CodecExtensions.MapCodec codec = (CodecExtensions.MapCodec) provider.get(Map, codecRegistry)

        then:
        codec.codecRegistry.is(codecRegistry)
    }

    @Unroll
    void "getCodecForBsonType provides a #expectedType.simpleName for #bsonType"() {
        expect:
        CodecExtensions.getCodecForBsonType(bsonType, codecRegistry).class == expectedType

        where:
        bsonType            | expectedType
        BsonType.ARRAY       | CodecExtensions.ListCodec
        BsonType.DOCUMENT    | DocumentCodec
        BsonType.BOOLEAN     | org.bson.codecs.BooleanCodec
        BsonType.STRING      | org.bson.codecs.StringCodec
        BsonType.OBJECT_ID   | org.bson.codecs.ObjectIdCodec
    }

    void "getCodecForBsonType wires the registry into a CodecRegistryAware codec it hands out"() {
        when:
        CodecExtensions.ListCodec codec = (CodecExtensions.ListCodec) CodecExtensions.getCodecForBsonType(BsonType.ARRAY, codecRegistry)

        then:
        codec.codecRegistry.is(codecRegistry)
    }

    void "getCodecForBsonType returns null for a bson type with no registered codec"() {
        expect:
        CodecExtensions.getCodecForBsonType(BsonType.JAVASCRIPT, codecRegistry) == null
    }

    void "getBsonConverters returns every registered converter, flattened"() {
        when:
        def converters = CodecExtensions.getBsonConverters()

        then:
        // BsonString and BsonInt32 each have two converters registered; every other listed
        // type has one - see the static registration block in CodecExtensions.
        converters.size() >= 15
    }

    void "getBsonConverter returns null for a BsonValue type with no registered converter"() {
        expect:
        CodecExtensions.getBsonConverter(BsonMaxKey) == null
    }

    @Unroll
    void "converts #source.class.simpleName to its plain Java equivalent"() {
        expect:
        CodecExtensions.getBsonConverter(source.class).convert(source) == expected

        where:
        source                                       | expected
        new BsonBinary([1, 2, 3] as byte[])          | ([1, 2, 3] as byte[])
        new BsonObjectId(new ObjectId('5f1d8f8f8f8f8f8f8f8f8f8f')) | new ObjectId('5f1d8f8f8f8f8f8f8f8f8f8f')
        new BsonString('hello')                      | 'hello'
        new BsonBoolean(true)                        | true
        new BsonDouble(3.14d)                        | 3.14d
        new BsonInt32(42)                             | 42
        new BsonInt64(42L)                            | 42L
        new BsonDecimal128(new Decimal128(new BigDecimal('42.50'))) | new BigDecimal('42.50')
    }

    void "converts a BsonTimestamp to a Date using seconds-to-millis"() {
        given:
        BsonTimestamp timestamp = new BsonTimestamp(100, 0)

        expect:
        CodecExtensions.getBsonConverter(BsonTimestamp).convert(timestamp) == new Date(100 * 1000L)
    }

    void "converts a BsonDateTime to a Date directly from its millis value"() {
        given:
        BsonDateTime dateTime = new BsonDateTime(12345L)

        expect:
        CodecExtensions.getBsonConverter(BsonDateTime).convert(dateTime) == new Date(12345L)
    }

    void "converts a BsonRegularExpression to a compiled Pattern"() {
        given:
        BsonRegularExpression regex = new BsonRegularExpression('a.*b')

        expect:
        CodecExtensions.getBsonConverter(BsonRegularExpression).convert(regex).pattern() == 'a.*b'
    }

    void "converts a BsonNull to null"() {
        expect:
        CodecExtensions.getBsonConverter(BsonNull).convert(BsonNull.VALUE) == null
    }

    void "the first registered BsonString converter yields a CharSequence"() {
        expect:
        CodecExtensions.getBsonConverter(BsonString).convert(new BsonString('hi')) instanceof CharSequence
    }

    void "converts a flat BsonArray to a List, recursively converting each element"() {
        given:
        BsonArray array = new BsonArray([new BsonInt32(1), new BsonString('two'), BsonNull.VALUE])

        expect:
        CodecExtensions.getBsonConverter(BsonArray).convert(array) == [1, 'two', null]
    }

    void "converts a BsonArray to an Object array, recursively converting each element"() {
        given:
        BsonArray array = new BsonArray([new BsonInt32(1), new BsonString('two')])

        expect:
        CodecExtensions.getBsonConverter(BsonArray).convert(array) == [1, 'two'] as Object[]
    }

    void "converts a BsonDocument to a Map, recursively converting each value"() {
        given:
        BsonDocument document = new BsonDocument()
        document.put('a', new BsonInt32(1))
        document.put('b', new BsonString('two'))

        expect:
        CodecExtensions.getBsonConverter(BsonDocument).convert(document) == [a: 1, b: 'two']
    }

    void "MapCodec decodes each entry, converting its bson value to the plain Java equivalent"() {
        given:
        CodecExtensions.MapCodec codec = new CodecExtensions.MapCodec()
        BsonStringCodec stringCodec = new BsonStringCodec()
        CodecRegistry registry = Stub(CodecRegistry)
        registry.get(BsonString) >> stringCodec
        codec.codecRegistry = registry
        BsonReader reader = Mock(BsonReader) {
            getCurrentBsonType() >> BsonType.STRING
            readBsonType() >>> [BsonType.STRING, BsonType.END_OF_DOCUMENT]
            readName() >> 'foo'
            readString() >> 'bar'
        }

        when:
        def result = codec.decode(reader, DecoderContext.builder().build())

        then:
        1 * reader.readStartDocument()
        1 * reader.readEndDocument()
        result == [foo: 'bar']
    }

    void "MapCodec decodes a null bson value as a null map entry"() {
        given:
        CodecExtensions.MapCodec codec = new CodecExtensions.MapCodec()
        CodecRegistry registry = Mock(CodecRegistry)
        Codec<BsonValue> nullCodec = Mock(Codec)
        codec.codecRegistry = registry
        BsonReader reader = Mock(BsonReader) {
            getCurrentBsonType() >> BsonType.NULL
            readBsonType() >>> [BsonType.NULL, BsonType.END_OF_DOCUMENT]
            readName() >> 'foo'
        }

        when:
        def result = codec.decode(reader, DecoderContext.builder().build())

        then:
        1 * registry.get(_) >> nullCodec
        1 * nullCodec.decode(reader, _) >> null
        result == [foo: null]
    }

    void "MapCodec encodes each non-null entry through the registry's codec for its runtime type"() {
        given:
        CodecExtensions.MapCodec codec = new CodecExtensions.MapCodec()
        CodecRegistry registry = Mock(CodecRegistry)
        Codec<String> stringCodec = Mock(Codec)
        codec.codecRegistry = registry
        BsonWriter writer = Mock(BsonWriter)

        when:
        codec.encode(writer, [foo: 'bar'], EncoderContext.builder().build())

        then:
        1 * writer.writeStartDocument()
        1 * writer.writeName('foo')
        1 * registry.get(String) >> stringCodec
        1 * stringCodec.encode(writer, 'bar', _)
        1 * writer.writeEndDocument()
    }

    void "MapCodec encodes a null entry value as bson null, without a codec lookup"() {
        given:
        CodecExtensions.MapCodec codec = new CodecExtensions.MapCodec()
        CodecRegistry registry = Mock(CodecRegistry)
        codec.codecRegistry = registry
        BsonWriter writer = Mock(BsonWriter)

        when:
        codec.encode(writer, [foo: null], EncoderContext.builder().build())

        then:
        1 * writer.writeNull()
        0 * registry.get(_)
    }

    void "MapCodec reports Map as its encoder class"() {
        expect:
        new CodecExtensions.MapCodec().encoderClass == Map
    }

    void "ListCodec decodes each element, converting its bson value to the plain Java equivalent"() {
        given:
        CodecExtensions.ListCodec codec = new CodecExtensions.ListCodec()
        BsonStringCodec stringCodec = new BsonStringCodec()
        CodecRegistry registry = Stub(CodecRegistry)
        registry.get(BsonString) >> stringCodec
        codec.codecRegistry = registry
        BsonReader reader = Mock(BsonReader) {
            getCurrentBsonType() >> BsonType.STRING
            readBsonType() >>> [BsonType.STRING, BsonType.END_OF_DOCUMENT]
            readString() >> 'bar'
        }

        when:
        def result = codec.decode(reader, DecoderContext.builder().build())

        then:
        1 * reader.readStartArray()
        1 * reader.readEndArray()
        result == ['bar']
    }

    void "ListCodec encodes each non-null element through the registry's codec for its runtime type"() {
        given:
        CodecExtensions.ListCodec codec = new CodecExtensions.ListCodec()
        CodecRegistry registry = Mock(CodecRegistry)
        Codec<String> stringCodec = Mock(Codec)
        codec.codecRegistry = registry
        BsonWriter writer = Mock(BsonWriter)

        when:
        codec.encode(writer, ['bar'], EncoderContext.builder().build())

        then:
        1 * writer.writeStartArray()
        1 * registry.get(String) >> stringCodec
        1 * stringCodec.encode(writer, 'bar', _)
        1 * writer.writeEndArray()
    }

    void "ListCodec encodes a null element as bson null, without a codec lookup"() {
        given:
        CodecExtensions.ListCodec codec = new CodecExtensions.ListCodec()
        CodecRegistry registry = Mock(CodecRegistry)
        codec.codecRegistry = registry
        BsonWriter writer = Mock(BsonWriter)

        when:
        codec.encode(writer, [null], EncoderContext.builder().build())

        then:
        1 * writer.writeNull()
        0 * registry.get(_)
    }

    void "ListCodec reports List as its encoder class"() {
        expect:
        new CodecExtensions.ListCodec().encoderClass == List
    }

    void "IntRangeCodec encodes the range's bounds as a two-element array"() {
        given:
        BsonWriter writer = Mock(BsonWriter)

        when:
        new CodecExtensions.IntRangeCodec().encode(writer, 1..5, EncoderContext.builder().build())

        then:
        1 * writer.writeStartArray()
        1 * writer.writeInt32(1)
        1 * writer.writeInt32(5)
        1 * writer.writeEndArray()
    }

    void "IntRangeCodec decodes a two-element array back into a range"() {
        given:
        BsonReader reader = Mock(BsonReader) {
            readInt32() >>> [1, 5]
        }

        expect:
        new CodecExtensions.IntRangeCodec().decode(reader, DecoderContext.builder().build()) == (1..5)
    }

    void "IntRangeCodec reports IntRange as its encoder class"() {
        expect:
        new CodecExtensions.IntRangeCodec().encoderClass == IntRange
    }

    void "LocaleCodec round-trips a Locale through its string form"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        BsonReader reader = Stub(BsonReader) {
            readString() >> 'en'
        }

        expect:
        new CodecExtensions.LocaleCodec().decode(reader, DecoderContext.builder().build()) == new Locale('en')

        when:
        new CodecExtensions.LocaleCodec().encode(writer, new Locale('en'), EncoderContext.builder().build())

        then:
        1 * writer.writeString('en')
    }

    void "LocaleCodec reports Locale as its encoder class"() {
        expect:
        new CodecExtensions.LocaleCodec().encoderClass == Locale
    }

    void "CurrencyCodec round-trips a Currency through its currency code"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        BsonReader reader = Stub(BsonReader) {
            readString() >> 'USD'
        }

        expect:
        new CodecExtensions.CurrencyCodec().decode(reader, DecoderContext.builder().build()) == Currency.getInstance('USD')

        when:
        new CodecExtensions.CurrencyCodec().encode(writer, Currency.getInstance('USD'), EncoderContext.builder().build())

        then:
        1 * writer.writeString('USD')
    }

    void "CurrencyCodec reports Currency as its encoder class"() {
        expect:
        new CodecExtensions.CurrencyCodec().encoderClass == Currency
    }

    void "GStringCodec round-trips a GString through its string form"() {
        given:
        BsonWriter writer = Mock(BsonWriter)
        BsonReader reader = Stub(BsonReader) {
            readString() >> 'hello'
        }

        when:
        GString decoded = new CodecExtensions.GStringCodec().decode(reader, DecoderContext.builder().build())

        then:
        decoded.toString() == 'hello'

        when:
        new CodecExtensions.GStringCodec().encode(writer, decoded, EncoderContext.builder().build())

        then:
        1 * writer.writeString('hello')
    }

    void "GStringCodec reports GString as its encoder class"() {
        expect:
        new CodecExtensions.GStringCodec().encoderClass == GString
    }
}
