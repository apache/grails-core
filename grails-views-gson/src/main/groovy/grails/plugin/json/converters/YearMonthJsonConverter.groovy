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

import java.time.YearMonth

import groovy.json.JsonGenerator
import groovy.transform.CompileStatic

/**
 * A class to render a {@link YearMonth} as json: its ISO-8601 {@link YearMonth#toString()} form
 * (e.g. {@code 2026-09}), the same as Spring Boot's default Jackson rendering.
 *
 * @since 8.0
 */
@CompileStatic
class YearMonthJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        YearMonth == type
    }

    @Override
    Object convert(Object value, String key) {
        value.toString()
    }
}
