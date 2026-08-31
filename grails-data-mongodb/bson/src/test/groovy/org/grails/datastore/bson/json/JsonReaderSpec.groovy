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

import org.bson.BsonType
import org.bson.json.JsonParseException
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Covers {@link JsonReader}, a simplified fork of the MongoDB driver's own JsonReader that removes
 * processing of MongoDB Extended JSON constructs ($oid, $date, ObjectId(...), etc - visitExtendedJSON()
 * always treats a '{' as a plain nested document). Because of that, only the BsonTypes actually
 * reachable via its parsing logic are covered here: STRING, DOUBLE, INT32, INT64, BOOLEAN, NULL,
 * UNDEFINED, REGULAR_EXPRESSION, ARRAY and DOCUMENT. OBJECT_ID/BINARY/TIMESTAMP/DB_POINTER/MAX_KEY/
 * MIN_KEY have doRead* overrides only to satisfy the AbstractBsonReader contract - there is no JSON
 * syntax this parser recognizes that ever produces those types, so they are not exercised here.
 */
class JsonReaderSpec extends Specification {

    void "reads a simple flat document"() {
        given:
        JsonReader reader = new JsonReader('{"name":"Fred","age":42,"active":true}')

        expect:
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
        reader.readBsonType() == BsonType.END_OF_DOCUMENT
        reader.readEndDocument()
    }

    void "reads an empty document"() {
        given:
        JsonReader reader = new JsonReader('{}')

        expect:
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readStartDocument()
        reader.readBsonType() == BsonType.END_OF_DOCUMENT
        reader.readEndDocument()
    }

    void "reads an empty array"() {
        given:
        JsonReader reader = new JsonReader('{"items":[]}')

        expect:
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readStartDocument()
        reader.readBsonType() == BsonType.ARRAY
        reader.readName() == 'items'
        reader.readStartArray()
        reader.readBsonType() == BsonType.END_OF_DOCUMENT
        reader.readEndArray()
        reader.readEndDocument()
    }

    void "reads a nested array of mixed simple values"() {
        given:
        JsonReader reader = new JsonReader('{"items":[1,"two",3.0,false,null]}')

        expect:
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readStartDocument()
        reader.readBsonType() == BsonType.ARRAY
        reader.readName() == 'items'
        reader.readStartArray()
        reader.readBsonType() == BsonType.INT32
        reader.readInt32() == 1
        reader.readBsonType() == BsonType.STRING
        reader.readString() == 'two'
        reader.readBsonType() == BsonType.DOUBLE
        reader.readDouble() == 3.0d
        reader.readBsonType() == BsonType.BOOLEAN
        !reader.readBoolean()
        reader.readBsonType() == BsonType.NULL
        reader.readNull()
        reader.readBsonType() == BsonType.END_OF_DOCUMENT
        reader.readEndArray()
        reader.readEndDocument()
    }

    void "reads a nested document"() {
        given:
        JsonReader reader = new JsonReader('{"address":{"city":"London"}}')

        expect:
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readStartDocument()
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readName() == 'address'
        reader.readStartDocument()
        reader.readBsonType() == BsonType.STRING
        reader.readName() == 'city'
        reader.readString() == 'London'
        reader.readBsonType() == BsonType.END_OF_DOCUMENT
        reader.readEndDocument()
        reader.readBsonType() == BsonType.END_OF_DOCUMENT
        reader.readEndDocument()
    }

    void "reads an escaped double quote"() {
        given:
        // JSON text: "a\"b"
        JsonReader reader = new JsonReader('"a\\"b"')

        expect:
        reader.readBsonType() == BsonType.STRING
        reader.readString() == 'a"b'
    }

    void "reads an escaped backslash"() {
        given:
        // JSON text: "a\\b"
        JsonReader reader = new JsonReader('"a\\\\b"')

        expect:
        reader.readBsonType() == BsonType.STRING
        reader.readString() == 'a\\b'
    }

    void "reads an escaped newline, tab, carriage return, backspace and form feed"() {
        given:
        // JSON text: "\n\t\r\b\f"
        JsonReader reader = new JsonReader('"\\n\\t\\r\\b\\f"')

        expect:
        reader.readBsonType() == BsonType.STRING
        reader.readString() == '\n\t\r\b\f'
    }

    void "reads an escaped forward slash"() {
        given:
        // JSON text: "a\/b"
        JsonReader reader = new JsonReader('"a\\/b"')

        expect:
        reader.readBsonType() == BsonType.STRING
        reader.readString() == 'a/b'
    }

    void "reads a unicode escape sequence"() {
        given:
        // JSON text: "ABC" -> "ABC"
        JsonReader reader = new JsonReader('"\\u0041BC"')

        expect:
        reader.readBsonType() == BsonType.STRING
        reader.readString() == 'ABC'
    }

    void "reads a plain string with no escapes"() {
        given:
        JsonReader reader = new JsonReader('"hello world"')

        expect:
        reader.readBsonType() == BsonType.STRING
        reader.readString() == 'hello world'
    }

    void "an unterminated string throws a JsonParseException"() {
        given:
        JsonReader reader = new JsonReader('"unterminated')

        when:
        reader.readBsonType()
        reader.readString()

        then:
        thrown(JsonParseException)
    }

    void "an invalid escape sequence throws a JsonParseException"() {
        given:
        // JSON text: "bad\qescape" - '\q' is not a recognized escape
        JsonReader reader = new JsonReader('"bad\\qescape"')

        when:
        reader.readBsonType()

        then:
        thrown(JsonParseException)
    }

    @Unroll
    void "reads the integer literal #literal as INT32 #expected"() {
        given:
        JsonReader reader = new JsonReader(literal)

        expect:
        reader.readBsonType() == BsonType.INT32
        reader.readInt32() == expected

        where:
        literal  | expected
        '42'     | 42
        '-42'    | -42
        '0'      | 0
        '2147483647' | Integer.MAX_VALUE
        '-2147483648' | Integer.MIN_VALUE
    }

    void "reads an integer literal beyond the int32 range as INT64"() {
        given:
        JsonReader reader = new JsonReader('9999999999')

        expect:
        reader.readBsonType() == BsonType.INT64
        reader.readInt64() == 9999999999L
    }

    @Unroll
    void "reads the double literal #literal as #expected"() {
        given:
        JsonReader reader = new JsonReader("[${literal}]")
        reader.readBsonType()
        reader.readStartArray()

        expect:
        reader.readBsonType() == BsonType.DOUBLE
        reader.readDouble() == expected

        where:
        literal    | expected
        '3.14'     | 3.14d
        '-3.14'    | -3.14d
        '0.0'      | 0.0d
        '1e2'      | 100.0d
        '1E2'      | 100.0d
        '1.5e-2'   | 0.015d
        '1e+2'     | 100.0d
    }

    void "reads a bare exponent-notation number with nothing following it"() {
        // Regression test: SAW_EXPONENT_DIGITS previously had no branch for end-of-input (unlike
        // every sibling numeric scanner state, which all treat EOF the same as a closing
        // delimiter), so "1e2" alone used to throw while "1e2]"/"1e2,"/"1e2 " all succeeded.
        given:
        JsonReader reader = new JsonReader('1e2')

        expect:
        reader.readBsonType() == BsonType.DOUBLE
        reader.readDouble() == 100.0d
    }

    @Unroll
    void "reads the unquoted literal #literal"() {
        given:
        JsonReader reader = new JsonReader(literal)

        expect:
        reader.readBsonType() == expectedType

        where:
        literal     | expectedType
        'true'      | BsonType.BOOLEAN
        'false'     | BsonType.BOOLEAN
        'null'      | BsonType.NULL
        'undefined' | BsonType.UNDEFINED
        'NaN'       | BsonType.DOUBLE
        'Infinity'  | BsonType.DOUBLE
    }

    void "reads true as a boolean value of true"() {
        given:
        JsonReader reader = new JsonReader('true')

        expect:
        reader.readBsonType() == BsonType.BOOLEAN
        reader.readBoolean()
    }

    void "reads NaN as a double value of NaN"() {
        given:
        JsonReader reader = new JsonReader('NaN')

        expect:
        reader.readBsonType() == BsonType.DOUBLE
        Double.isNaN(reader.readDouble())
    }

    void "reads Infinity as positive infinity"() {
        given:
        JsonReader reader = new JsonReader('Infinity')

        expect:
        reader.readBsonType() == BsonType.DOUBLE
        reader.readDouble() == Double.POSITIVE_INFINITY
    }

    void "reads -Infinity as negative infinity, at end-of-input and followed by a delimiter"() {
        // Regression test: scanNumber's SAW_MINUS_I case used to append the character it reads on
        // every iteration of its match loop, including the terminator read right after matching
        // the final 'y' - so the buffer handed to Double.parseDouble always had one extra trailing
        // character (whatever follows the literal: a delimiter, or the internal EOF marker), and
        // parsing always threw regardless of what followed '-Infinity'.
        expect:
        new JsonReader('-Infinity').readBsonType() == BsonType.DOUBLE

        when:
        JsonReader reader = new JsonReader('[-Infinity]')
        reader.readBsonType()
        reader.readStartArray()

        then:
        reader.readBsonType() == BsonType.DOUBLE
        reader.readDouble() == Double.NEGATIVE_INFINITY
    }

    void "reads a regular expression literal with no flags"() {
        given:
        JsonReader reader = new JsonReader('/^foo.*bar$/')

        expect:
        reader.readBsonType() == BsonType.REGULAR_EXPRESSION
        def regex = reader.readRegularExpression()
        regex.pattern == '^foo.*bar$'
        regex.options == ''
    }

    void "reads a regular expression literal with flags"() {
        given:
        JsonReader reader = new JsonReader('/foo/im')

        expect:
        reader.readBsonType() == BsonType.REGULAR_EXPRESSION
        def regex = reader.readRegularExpression()
        regex.pattern == 'foo'
        regex.options == 'im'
    }

    void "reads a regular expression literal containing an escaped forward slash"() {
        given:
        JsonReader reader = new JsonReader('/foo\\/bar/')

        expect:
        reader.readBsonType() == BsonType.REGULAR_EXPRESSION
        reader.readRegularExpression().pattern == 'foo\\/bar'
    }

    void "an invalid regular expression option throws a JsonParseException"() {
        given:
        JsonReader reader = new JsonReader('/foo/z')

        when:
        reader.readBsonType()

        then:
        thrown(JsonParseException)
    }

    void "a document missing a colon after the field name throws a JsonParseException"() {
        given:
        JsonReader reader = new JsonReader('{"name" "Fred"}')

        when:
        reader.readBsonType()
        reader.readStartDocument()
        reader.readBsonType()

        then:
        thrown(JsonParseException)
    }

    void "a value that is not valid JSON throws a JsonParseException"() {
        given:
        JsonReader reader = new JsonReader('#not-json')

        when:
        reader.readBsonType()

        then:
        thrown(JsonParseException)
    }

    void "an invalid number literal throws a JsonParseException"() {
        given:
        JsonReader reader = new JsonReader('1.2.3')

        when:
        reader.readBsonType()

        then:
        thrown(JsonParseException)
    }

    void "skipValue advances past a scalar value without needing to read it"() {
        given:
        JsonReader reader = new JsonReader('{"a":1,"b":2}')

        when:
        reader.readBsonType()
        reader.readStartDocument()
        reader.readBsonType()
        reader.skipName()
        reader.skipValue()

        then:
        reader.readBsonType() == BsonType.INT32
        reader.readName() == 'b'
        reader.readInt32() == 2
    }

    void "skipValue recursively skips a nested document"() {
        given:
        JsonReader reader = new JsonReader('{"nested":{"a":1,"b":2},"after":3}')

        when:
        reader.readBsonType()
        reader.readStartDocument()
        reader.readBsonType()
        reader.skipName()
        reader.skipValue()

        then:
        reader.readBsonType() == BsonType.INT32
        reader.readName() == 'after'
        reader.readInt32() == 3
    }

    void "an unquoted field name is accepted for a document key"() {
        given:
        JsonReader reader = new JsonReader('{name:"Fred"}')

        expect:
        reader.readBsonType() == BsonType.DOCUMENT
        reader.readStartDocument()
        reader.readBsonType() == BsonType.STRING
        reader.readName() == 'name'
        reader.readString() == 'Fred'
    }
}
