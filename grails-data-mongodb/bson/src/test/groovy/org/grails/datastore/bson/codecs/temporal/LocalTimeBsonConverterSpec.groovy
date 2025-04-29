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
package org.grails.datastore.bson.codecs.temporal

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalTime

class LocalTimeBsonConverterSpec extends Specification implements LocalTimeBsonConverter {

    @Shared
    LocalTime localTime

    void setupSpec() {
        localTime = LocalTime.of(6,5,4,3)
    }

    void "test read"() {
        given:
        BsonReader bsonReader = Mock(BsonReader) {
            1 * readInt64() >> 21904000000003
        }

        when:
        LocalTime converted = read(bsonReader)

        then:
        converted.hour == 6
        converted.minute == 5
        converted.second == 4
        converted.nano == 3
    }

    void "test write"() {
        given:
        BsonWriter bsonWriter = Mock(BsonWriter)

        when:
        write(bsonWriter, localTime)

        then:
        1 * bsonWriter.writeInt64(21904000000003)
    }

    void "test bson type"() {
        expect:
        bsonType() == BsonType.INT64
    }
}