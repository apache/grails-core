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
import spock.lang.Specification

/**
 * Regression coverage for two JsonScanner number-parsing bugs:
 *
 * <p>SAW_EXPONENT_DIGITS was the only numeric scanner state whose terminator switch omitted an
 * explicit end-of-input case (every sibling state treats EOF the same as a closing delimiter), so
 * a bare exponent-notation number with nothing following it (e.g. a top-level "1e2") threw
 * JsonParseException instead of parsing.
 *
 * <p>SAW_MINUS_I appended the character it read on every iteration of its "-Infinity" match loop,
 * including the terminator character read immediately after matching the literal's final 'y' - so
 * the buffer handed to Double.parseDouble always carried one extra trailing character, and
 * "-Infinity" failed to parse in every context (end-of-input, before a comma, before a closing
 * bracket).
 */
class JsonReaderSpec extends Specification {

    void "reads a bare exponent-notation number with nothing following it"() {
        given:
        JsonReader reader = new JsonReader('1e2')

        expect:
        reader.readBsonType() == BsonType.DOUBLE
        reader.readDouble() == 100.0d
    }

    void "reads -Infinity as negative infinity, at end-of-input and followed by a delimiter"() {
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
}
