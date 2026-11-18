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
package org.grails.web.json;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formats {@link Date} and {@link Calendar} values for JSON the way Jackson's default
 * {@code StdDateFormat} does, which is how Spring Boot renders them: a UTC instant that always has
 * millisecond precision and a {@code Z} suffix, e.g. {@code 2024-06-15T14:30:45.000Z}.
 *
 * <p>Works from epoch milliseconds, so {@link java.sql.Date}, {@link java.sql.Time} and
 * {@link java.sql.Timestamp} (whose sub-millisecond nanos are dropped) format the same way as a plain
 * {@code Date}; {@code java.sql.Date#toInstant()} and {@code java.sql.Time#toInstant()} would throw.
 * Fields are read from a {@link GregorianCalendar}, the calendar system of {@code java.util.Date}, so
 * dates before the 1582 Gregorian cutover use the Julian calendar. Years after 9999 get a {@code +}
 * sign and years before 1 AD use ISO 8601 numbering (1 BC is {@code +0000}, 2 BC is {@code -0001}).
 *
 * @since 8.0
 */
public final class JsonDateFormat {

    private static final TimeZone UTC = TimeZone.getTimeZone(ZoneOffset.UTC);

    private JsonDateFormat() {
    }

    /**
     * @param epochMillis milliseconds since 1970-01-01T00:00:00Z
     * @return the UTC timestamp, e.g. {@code 2024-06-15T14:30:45.123Z}
     */
    public static String format(long epochMillis) {
        return format(epochMillis, UTC);
    }

    /**
     * Formats in a time zone, as Jackson does when a default time zone is configured (Spring Boot's
     * {@code spring.jackson.time-zone}): the local date and time in the zone followed by its offset,
     * e.g. {@code 2024-06-15T10:30:45.123-04:00}, or by {@code Z} where the offset is zero.
     *
     * @param epochMillis milliseconds since 1970-01-01T00:00:00Z
     * @param zone the time zone to write the date and time in
     * @return the timestamp
     */
    public static String format(long epochMillis, TimeZone zone) {
        Calendar calendar = new GregorianCalendar(zone, Locale.ROOT);
        calendar.setTimeInMillis(epochMillis);
        StringBuilder sb = new StringBuilder(26);
        int year = calendar.get(Calendar.YEAR);
        if (calendar.get(Calendar.ERA) == GregorianCalendar.BC) {
            int isoYear = 1 - year;
            sb.append(isoYear == 0 ? '+' : '-');
            pad(sb, -isoYear, 4);
        }
        else {
            if (year > 9999) {
                sb.append('+');
            }
            pad(sb, year, 4);
        }
        sb.append('-');
        pad(sb, calendar.get(Calendar.MONTH) + 1, 2);
        sb.append('-');
        pad(sb, calendar.get(Calendar.DAY_OF_MONTH), 2);
        sb.append('T');
        pad(sb, calendar.get(Calendar.HOUR_OF_DAY), 2);
        sb.append(':');
        pad(sb, calendar.get(Calendar.MINUTE), 2);
        sb.append(':');
        pad(sb, calendar.get(Calendar.SECOND), 2);
        sb.append('.');
        pad(sb, calendar.get(Calendar.MILLISECOND), 3);
        int offsetMinutes = zone.getOffset(epochMillis) / 60_000;
        if (offsetMinutes == 0) {
            return sb.append('Z').toString();
        }
        sb.append(offsetMinutes < 0 ? '-' : '+');
        pad(sb, Math.abs(offsetMinutes) / 60, 2);
        sb.append(':');
        pad(sb, Math.abs(offsetMinutes) % 60, 2);
        return sb.toString();
    }

    /**
     * Whether Jackson writes a map key of this type differently from its {@code toString()}.
     *
     * @param key a map key
     * @return true for {@link Date}, {@link Calendar} and {@link ZonedDateTime} keys
     */
    public static boolean isDateKey(Object key) {
        return key instanceof Date || key instanceof Calendar || key instanceof ZonedDateTime;
    }

    /**
     * The JSON object key Jackson (and so Spring Boot) writes for a map key: a {@link Date}
     * (including the {@code java.sql} types) or {@link Calendar} key is formatted as by
     * {@link #format(long)}, a {@link ZonedDateTime} key with
     * {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME}, and any other key with {@code toString()}.
     *
     * @param key a non-null map key
     * @return the JSON object key
     */
    public static String formatKey(Object key) {
        if (key instanceof Date date) {
            return format(date.getTime());
        }
        if (key instanceof Calendar calendar) {
            return format(calendar.getTimeInMillis());
        }
        if (key instanceof ZonedDateTime zonedDateTime) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zonedDateTime);
        }
        return key.toString();
    }

    private static void pad(StringBuilder sb, int value, int width) {
        String digits = Integer.toString(value);
        for (int i = digits.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(digits);
    }
}
