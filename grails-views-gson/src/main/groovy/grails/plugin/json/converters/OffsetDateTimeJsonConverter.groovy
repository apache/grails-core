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

package grails.plugin.json.converters

import grails.plugin.json.builder.JsonGenerator
import groovy.transform.CompileStatic

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * A class to render a {@link OffsetDateTime} as json
 *
 * @author James Kleeh
 */
@CompileStatic
class OffsetDateTimeJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        OffsetDateTime == type
    }

    @Override
    Object convert(Object value, String key) {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((OffsetDateTime)value)
    }
}
