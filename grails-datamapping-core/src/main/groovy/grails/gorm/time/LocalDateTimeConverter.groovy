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

package grails.gorm.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

import groovy.transform.CompileStatic

/**
 * A trait to convert a {@link java.time.LocalDateTime} to and from a long
 *
 * @author James Kleeh
 */
@CompileStatic
trait LocalDateTimeConverter implements TemporalConverter<LocalDateTime> {

    @Override
    Long convert(LocalDateTime value) {
        value.toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    @Override
    LocalDateTime convert(Long value) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.of('UTC'))
    }
}
