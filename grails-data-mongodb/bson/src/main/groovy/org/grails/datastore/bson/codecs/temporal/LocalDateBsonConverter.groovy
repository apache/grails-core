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

import java.time.LocalDate

import groovy.transform.CompileStatic

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter

import grails.gorm.time.LocalDateConverter

/**
 * A trait to read and write a {@link LocalDate} to MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
trait LocalDateBsonConverter implements TemporalBsonConverter<LocalDate>, LocalDateConverter {

    @Override
    void write(BsonWriter writer, LocalDate value) {
        writer.writeDateTime(convert(value))
    }

    @Override
    LocalDate read(BsonReader reader) {
        convert(reader.readDateTime())
    }

    @Override
    BsonType bsonType() {
        BsonType.DATE_TIME
    }
}
