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

import java.time.LocalDate
import java.time.Month

class LocalDateBsonConverterSpec extends Specification implements LocalDateBsonConverter {

    @Shared
    LocalDate localDate

    void setupSpec() {
        localDate = LocalDate.of(1941, 1, 5)
    }

    void "test read"() {
        given:
        BsonReader bsonReader = Mock(BsonReader) {
            1 * readDateTime() >> -914803200000
        }

        when:
        LocalDate converted = read(bsonReader)

        then:
        converted.year == 1941
        converted.month == Month.JANUARY
        converted.dayOfMonth == 5
    }

    void "test write"() {
        given:
        BsonWriter bsonWriter = Mock(BsonWriter)

        when:
        write(bsonWriter, localDate)

        then:
        1 * bsonWriter.writeDateTime(-914803200000)
    }

    void "test bson type"() {
        expect:
        bsonType() == BsonType.DATE_TIME
    }
}