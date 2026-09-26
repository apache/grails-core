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

package grails.plugin.json.view

import java.text.SimpleDateFormat
import java.time.ZonedDateTime

import groovy.json.DefaultJsonGenerator
import groovy.json.JsonGenerator
import groovy.transform.CompileStatic
import org.apache.groovy.json.internal.CharBuf

import org.grails.web.json.JsonDateFormat

/**
 * The {@link JsonGenerator} of JSON views. It writes date and time values the same way as
 * Spring Boot's default Jackson rendering:
 *
 * <ul>
 *   <li>{@link Date} and {@link Calendar} values are written by {@link JsonDateFormat} in the configured
 *   {@code grails.views.json.generator.timeZone} (a UTC instant such as {@code 2024-06-15T14:30:45.123Z} in the
 *   default {@code GMT}), unless a {@code grails.views.json.generator.dateFormat} pattern is configured, in which
 *   case they are written with that pattern, time zone and locale.</li>
 *   <li>{@link Date} and {@link Calendar} map keys are written the same way as those values, and
 *   {@link ZonedDateTime} map keys as {@link JsonDateFormat#formatKey(Object)} does, rather than with
 *   their {@code toString()}. Keys that format to the same text are all written, as Jackson writes them.</li>
 * </ul>
 *
 * @since 8.0
 */
@CompileStatic
class JsonViewGenerator extends DefaultJsonGenerator {

    private final boolean springBootDates

    /**
     * @param options the generator options
     * @param springBootDates whether to write Date and Calendar values with {@link JsonDateFormat}
     *        rather than the date format of the options
     */
    JsonViewGenerator(JsonGenerator.Options options, boolean springBootDates) {
        super(options)
        this.springBootDates = springBootDates
    }

    /**
     * @param type a value type
     * @return whether one of the converters of this generator writes values of the type
     */
    boolean hasConverter(Class<?> type) {
        findConverter(type) != null
    }

    /**
     * @param key a non-null map key
     * @return the JSON object key this generator writes for the map key
     */
    String formatMapKey(Object key) {
        if (key instanceof Date) {
            return formatDate((Date) key)
        }
        if (key instanceof Calendar) {
            return formatDate(((Calendar) key).time)
        }
        JsonDateFormat.formatKey(key)
    }

    @Override
    protected void writeDate(Date date, CharBuf buffer) {
        buffer.addQuoted(formatDate(date))
    }

    @Override
    protected void writeMap(Map<?, ?> map, CharBuf buffer) {
        if (!hasDateKey(map)) {
            super.writeMap(map, buffer)
            return
        }
        // the entries are written one by one, rather than through a map of formatted keys, so that keys
        // which format to the same text are all written
        buffer.addChar('{' as char)
        boolean first = true
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.key == null) {
                throw new IllegalArgumentException("Maps with null keys can't be converted to JSON")
            }
            String key = formatMapKey(entry.key)
            Object value = entry.value
            if (isExcludingValues(value) || isExcludingFieldsNamed(key)) {
                continue
            }
            if (!first) {
                buffer.addChar(',' as char)
            }
            writeMapEntry(key, value, buffer)
            first = false
        }
        buffer.addChar('}' as char)
    }

    private static boolean hasDateKey(Map<?, ?> map) {
        for (Object key : map.keySet()) {
            if (JsonDateFormat.isDateKey(key)) {
                return true
            }
        }
        false
    }

    private String formatDate(Date date) {
        if (springBootDates) {
            return JsonDateFormat.format(date.time, timezone)
        }
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, dateLocale)
        formatter.timeZone = timezone
        formatter.format(date)
    }
}
