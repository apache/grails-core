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
package org.grails.web.binding

import java.text.DateFormat

import groovy.transform.CompileStatic
import org.springframework.beans.propertyeditors.CustomDateEditor
import org.springframework.util.Assert

import grails.util.GrailsStringUtils

/**
 * Structured editor for editing dates that takes 5 fields that represent the year, month, day, hour
 * and minute and constructs a Date instance
 *
 * @author Graeme Rocher
 * @since 1.0.4
 */
@CompileStatic
class StructuredDateEditor extends CustomDateEditor implements StructuredPropertyEditor {

    StructuredDateEditor(DateFormat dateFormat, boolean b) {
        super(dateFormat, b)
    }

    StructuredDateEditor(DateFormat dateFormat, boolean b, int i) {
        super(dateFormat, b, i)
    }

    List<String> getRequiredFields() {
        return Arrays.asList('year')
    }

    List<String> getOptionalFields() {
        return Arrays.asList('month', 'day', 'hour', 'minute')
    }

    @SuppressWarnings('rawtypes')
    Object assemble(Class type, Map fieldValues) throws IllegalArgumentException {
        Assert.isTrue(fieldValues.containsKey('year'), "Can't populate a date without a year")

        String yearString = (String) fieldValues.get('year')
        String monthString = (String) fieldValues.get('month')
        String dayString = (String) fieldValues.get('day')
        String hourString = (String) fieldValues.get('hour')
        String minuteString = (String) fieldValues.get('minute')
        if (GrailsStringUtils.isBlank(yearString) &&
                GrailsStringUtils.isBlank(monthString) &&
                GrailsStringUtils.isBlank(dayString) &&
                GrailsStringUtils.isBlank(hourString) &&
                GrailsStringUtils.isBlank(minuteString)) {
            return null
        }
        int year
        try {
            Assert.isTrue(!GrailsStringUtils.isBlank(yearString), "Can't populate a date without a year")

            year = Integer.parseInt(yearString)

            int month = getIntegerValue(fieldValues, 'month', 1)
            int day = getIntegerValue(fieldValues, 'day', 1)
            int hour = getIntegerValue(fieldValues, 'hour', 0)
            int minute = getIntegerValue(fieldValues, 'minute', 0)

            Calendar c = new GregorianCalendar(year, month - 1, day, hour, minute)
            if (type == Date) {
                return c.getTime()
            }
            if (type == java.sql.Date) {
                return new java.sql.Date(c.getTime().getTime())
            }
            return c
        }
        catch (NumberFormatException nfe) {
            throw new IllegalArgumentException('Unable to parse structured date from request for date ["+propertyName+"]"')
        }
    }

    @SuppressWarnings('rawtypes')
    private int getIntegerValue(Map values, String name, int defaultValue) throws NumberFormatException {
        if (values.get(name) != null) {
            return Integer.parseInt((String) values.get(name))
        }
        return defaultValue
    }

}
