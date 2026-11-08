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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAmount

import javax.xml.datatype.XMLGregorianCalendar

import groovy.json.DefaultJsonGenerator
import groovy.json.JsonGenerator
import groovy.transform.CompileStatic
import org.apache.groovy.json.internal.CharBuf

import org.grails.web.json.JsonDateFormat

/**
 * The {@link JsonGenerator} of JSON views. It writes {@link Date} and {@link Calendar} map keys the same way as
 * those values, with the configured {@code grails.views.json.generator.dateFormat}, time zone and locale, and
 * {@link ZonedDateTime} map keys as {@link JsonDateFormat#formatKey(Object)} does, rather than with their
 * {@code toString()}, as Spring Boot's default Jackson rendering does. Keys that format to the same text are all
 * written, as Jackson writes them.
 *
 * @since 8.0
 */
@CompileStatic
class JsonViewGenerator extends DefaultJsonGenerator {

    /**
     * @param options the generator options
     */
    JsonViewGenerator(JsonGenerator.Options options) {
        super(options)
    }

    /**
     * Whether values of the type are dates or times that one of the converters of this generator writes, such as the
     * date and time converters of JSON views, so that {@code g.render} writes them as it writes other simple values.
     * A converter that an application registers for any other type does not change how {@code g.render} renders it.
     *
     * @param type a value type
     * @return whether values of the type are dates or times that a converter of this generator writes
     */
    boolean hasDateTimeConverter(Class<?> type) {
        isDateTimeType(type) && findConverter(type) != null
    }

    private static boolean isDateTimeType(Class<?> type) {
        TemporalAccessor.isAssignableFrom(type) || TemporalAmount.isAssignableFrom(type) ||
                ZoneId.isAssignableFrom(type) || TimeZone.isAssignableFrom(type) || Date.isAssignableFrom(type) ||
                XMLGregorianCalendar.isAssignableFrom(type) || javax.xml.datatype.Duration.isAssignableFrom(type)
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
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, dateLocale)
        formatter.timeZone = timezone
        formatter.format(date)
    }
}
