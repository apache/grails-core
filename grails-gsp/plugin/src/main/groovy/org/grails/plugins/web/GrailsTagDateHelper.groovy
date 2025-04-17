/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.web

/**
 * An interface for defining behavior that Grails tags require surrounding dates
 *
 * @author James Kleeh
 * @since 3.2.1
 */
interface GrailsTagDateHelper {

    /**
     * Retrieve a time zone object from a parameter
     *
     * @param timeZone
     * @return a time zone to be passed to other methods
     */
    Object getTimeZone(Object timeZone)

    /**
     * Retrieve a date format object to be passed to the {@link #format} method
     *
     * @param format The string format pattern
     * @param timeZone The timeZone retrieved from {@link #getTimeZone}
     * @param locale The locale
     */
    Object getFormatFromPattern(String format, Object timeZone, Locale locale)

    /**
     * Retrieve a date format object without time to be passed to the {@link #format} method
     *
     * @param style The string type of format //FULL,LONG,MEDIUM,SHORT
     * @param timeZone The timeZone retrieved from {@link #getTimeZone}
     * @param locale The locale
     */
    Object getDateFormat(String style, Object timeZone, Locale locale)

    /**
     * Retrieve a time format object without time to be passed to the {@link #format} method
     *
     * @param style The string type of format //FULL,LONG,MEDIUM,SHORT
     * @param timeZone The timeZone retrieved from {@link #getTimeZone}
     * @param locale The locale
     */
    Object getTimeFormat(String style, Object timeZone, Locale locale)

    /**
     * Retrieve a date format object with time to be passed to the {@link #format} method
     *
     * @param dateStyle The string type of date format //FULL,LONG,MEDIUM,SHORT
     * @param timeStyle The string type of time format //FULL,LONG,MEDIUM,SHORT
     * @param timeZone The timeZone retrieved from {@link #getTimeZone}
     * @param locale The locale
     */
    Object getDateTimeFormat(String dateStyle, String timeStyle, Object timeZone, Locale locale)

    /**
     * Formats a given date
     *
     * @param formatter The formatter retrieved from any one of these methods: {@link #getFormatFromPattern}, {@link #getDateFormat}, {@link #getTimeFormat}, {@link #getDateTimeFormat}
     * @param date The date to be formatted
     * @return The string representation of the date
     */
    String format(Object formatter, Object date)

    /**
     * @param clazz The type of date to be used in a date picker
     * @return Whether or not the date is supported by the implementation
     */
    Boolean supportsDatePicker(Class clazz)

    /**
     * Creates a GregorianCalendar based off of the date object. This should only get called if {@link #supportsDatePicker} returns true.
     *
     * @param date The date to convert
     * @return A GregorianCalendar instance
     */
    GregorianCalendar buildCalendar(Object date)
}