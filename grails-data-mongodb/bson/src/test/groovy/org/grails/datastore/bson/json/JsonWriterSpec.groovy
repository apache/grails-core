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
package org.grails.datastore.bson.json

import org.bson.BsonBinary
import org.bson.BsonRegularExpression
import org.bson.BsonType
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import spock.lang.Specification

/**
 * Covers {@link JsonWriter}, a simplified fork of the MongoDB driver's own JsonWriter. Most types
 * round-trip cleanly with {@link JsonReader}; ObjectId/DateTime/Decimal128/Binary are one-directional
 * here (the writer can serialize them, but the reader has no Extended JSON support to parse them back
 * - see JsonReaderSpec's class comment), so those are covered as standalone writes only.
 */
class JsonWriterSpec extends Specification {

    private static String write(Closure<Void> writeOperations) {
        StringWriter target = new StringWriter()
        JsonWriter writer = new JsonWriter(target)
        writeOperations(writer)
        writer.flush()
        target.toString()
    }

    void "writes an empty document"() {
        expect:
        write { it.writeStartDocument(); it.writeEndDocument() } == '{}'
    }

    void "writes a flat document with mixed value types"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeString('name', 'Fred')
            it.writeInt32('age', 42)
            it.writeBoolean('active', true)
            it.writeEndDocument()
        } == '{"name":"Fred","age":42,"active":true}'
    }

    void "writes an empty array"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeStartArray('items')
            it.writeEndArray()
            it.writeEndDocument()
        } == '{"items":[]}'
    }

    void "writes array elements without names, comma-separated"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeStartArray('items')
            it.writeInt32(1)
            it.writeInt32(2)
            it.writeInt32(3)
            it.writeEndArray()
            it.writeEndDocument()
        } == '{"items":[1,2,3]}'
    }

    void "writes a nested document"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeStartDocument('address')
            it.writeString('city', 'London')
            it.writeEndDocument()
            it.writeEndDocument()
        } == '{"address":{"city":"London"}}'
    }

    void "writes null"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeNull('value')
            it.writeEndDocument()
        } == '{"value":null}'
    }

    void "writes undefined"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeUndefined('value')
            it.writeEndDocument()
        } == '{"value":undefined}'
    }

    void "writes a double"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeDouble('value', 3.14d)
            it.writeEndDocument()
        } == '{"value":3.14}'
    }

    void "writes an int64 within the int32 range unquoted"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeInt64('value', 42L)
            it.writeEndDocument()
        } == '{"value":42}'
    }

    void "writes an int64 beyond the int32 range as a quoted string"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeInt64('value', 9999999999L)
            it.writeEndDocument()
        } == '{"value":"9999999999"}'
    }

    void "escapes special characters when writing a string"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeString('value', 'a"b\\c\nd\te')
            it.writeEndDocument()
        } == '{"value":"a\\"b\\\\c\\nd\\te"}'
    }

    void "writes a regular expression as a slash-delimited literal by default"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeRegularExpression('pattern', new BsonRegularExpression('^foo.*bar$', 'im'))
            it.writeEndDocument()
        } == '{"pattern":/^foo.*bar$/im}'
    }

    void "escapes an embedded forward slash when writing a regular expression"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeRegularExpression('pattern', new BsonRegularExpression('foo/bar'))
            it.writeEndDocument()
        } == '{"pattern":/foo\\/bar/}'
    }

    void "writes an empty regular expression pattern as (?:)"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeRegularExpression('pattern', new BsonRegularExpression(''))
            it.writeEndDocument()
        } == '{"pattern":/(?:)/}'
    }

    void "writes a regular expression as an extended-json document in strict output mode"() {
        given:
        StringWriter target = new StringWriter()
        JsonWriter writer = new JsonWriter(target, JsonWriterSettings.builder().outputMode(JsonMode.STRICT).build())

        when:
        writer.writeStartDocument()
        writer.writeRegularExpression('pattern', new BsonRegularExpression('foo', 'i'))
        writer.writeEndDocument()
        writer.flush()

        then:
        target.toString() == '{"pattern":{"$regex":"foo","$options":"i"}}'
    }

    void "writes an ObjectId using its hex string form"() {
        given:
        ObjectId id = new ObjectId()

        expect:
        write {
            it.writeStartDocument()
            it.writeObjectId('_id', id)
            it.writeEndDocument()
        } == "{\"_id\":${id}}"
    }

    void "writes a Decimal128 as a quoted string"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeDecimal128('value', new Decimal128(new BigDecimal('42.50')))
            it.writeEndDocument()
        } == '{"value":"42.50"}'
    }

    void "writes binary data as unquoted base64 text"() {
        // Unlike Decimal128/ObjectId/DateTime, the base64 text is written without surrounding
        // quotes, so this output is not valid JSON on its own - a minor asymmetry, noted but not
        // fixed here since this is a test-only stage and nothing in this codebase parses it back.
        expect:
        write {
            it.writeStartDocument()
            it.writeBinaryData('value', new BsonBinary([1, 2, 3] as byte[]))
            it.writeEndDocument()
        } == '{"value":AQID}'
    }

    void "writes a date-time as an ISO-8601 string"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeDateTime('value', 0L)
            it.writeEndDocument()
        } == '{"value":"1970-01-01T00:00+0000"}'
    }

    void "javaScript, symbol, timestamp, maxKey and minKey are silent no-ops rather than errors"() {
        expect:
        write {
            it.writeStartDocument()
            it.writeName('a')
            it.writeJavaScript('function(){}')
            it.writeName('b')
            it.writeSymbol('sym')
            it.writeName('c')
            it.writeTimestamp(new org.bson.BsonTimestamp(1, 1))
            it.writeName('d')
            it.writeMaxKey()
            it.writeName('e')
            it.writeMinKey()
            it.writeEndDocument()
        } == '{}'
    }

    void "a document built with JsonWriter round-trips through JsonReader with equal values"() {
        given:
        String json = write {
            it.writeStartDocument()
            it.writeString('name', 'Fred')
            it.writeInt32('age', 42)
            it.writeBoolean('active', true)
            it.writeDouble('score', 3.14d)
            it.writeNull('note')
            it.writeStartArray('tags')
            it.writeString('a')
            it.writeString('b')
            it.writeEndArray()
            it.writeEndDocument()
        }

        when:
        JsonReader reader = new JsonReader(json)

        then:
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readStartDocument()
        reader.readBsonType() == BsonType.STRING
        reader.readName() == 'name'
        reader.readString() == 'Fred'
        reader.readBsonType() == BsonType.INT32
        reader.readName() == 'age'
        reader.readInt32() == 42
        reader.readBsonType() == BsonType.BOOLEAN
        reader.readName() == 'active'
        reader.readBoolean()
        reader.readBsonType() == BsonType.DOUBLE
        reader.readName() == 'score'
        reader.readDouble() == 3.14d
        reader.readBsonType() == BsonType.NULL
        reader.readName() == 'note'
        reader.readNull()
        reader.readBsonType() == BsonType.ARRAY
        reader.readName() == 'tags'
        reader.readStartArray()
        reader.readBsonType() == BsonType.STRING
        reader.readString() == 'a'
        reader.readBsonType() == BsonType.STRING
        reader.readString() == 'b'
        reader.readBsonType() == BsonType.END_OF_DOCUMENT
        reader.readEndArray()
        reader.readBsonType() == BsonType.END_OF_DOCUMENT
        reader.readEndDocument()
    }

    void "a string with characters requiring escaping round-trips through JsonReader unchanged"() {
        given:
        String original = 'quote:" backslash:\\ newline:\n tab:\t'
        String json = write {
            it.writeStartDocument()
            it.writeString('value', original)
            it.writeEndDocument()
        }

        when:
        JsonReader reader = new JsonReader(json)

        then:
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readStartDocument()
        reader.readBsonType() == BsonType.STRING
        reader.readName() == 'value'
        reader.readString() == original
    }

    void "a non-strict regular expression round-trips through JsonReader with the same pattern and options"() {
        given:
        String json = write {
            it.writeStartDocument()
            it.writeRegularExpression('pattern', new BsonRegularExpression('^foo.*bar$', 'im'))
            it.writeEndDocument()
        }

        when:
        JsonReader reader = new JsonReader(json)

        then:
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readStartDocument()
        reader.readBsonType() == BsonType.REGULAR_EXPRESSION
        reader.readName() == 'pattern'
        def regex = reader.readRegularExpression()
        regex.pattern == '^foo.*bar$'
        regex.options == 'im'
    }
}
