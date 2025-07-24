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

import java.time.LocalTime

import groovy.transform.CompileStatic

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter

import grails.gorm.time.LocalTimeConverter

/**
 * A trait to read and write a {@link LocalTime} to MongoDB
 *
 * @author James Kleeh
 */
@CompileStatic
trait LocalTimeBsonConverter implements TemporalBsonConverter<LocalTime>, LocalTimeConverter {

    @Override
    void write(BsonWriter writer, LocalTime value) {
        writer.writeInt64(convert(value))
    }

    @Override
    LocalTime read(BsonReader reader) {
        convert(reader.readInt64())
    }

    @Override
    BsonType bsonType() {
        BsonType.INT64
    }
}
