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
package org.grails.databinding.converters

import groovy.transform.CompileStatic

import grails.databinding.DataBindingSource
import grails.databinding.StructuredBindingEditor

/**
 * @author Jeff Brown
 * @since 2.3
 */
@CompileStatic
abstract class AbstractStructuredDateBindingEditor<T> implements StructuredBindingEditor<T> {

    T assemble(String propertyName, DataBindingSource fieldValues) throws IllegalArgumentException {
        final prefix = propertyName + '_'
        assert fieldValues.containsProperty(prefix + "year"), "Can't populate a date without a year"

        def yearString = (String)fieldValues.getPropertyValue(prefix + "year")
        def monthString = (String) fieldValues.getPropertyValue(prefix + "month")
        def dayString = (String) fieldValues.getPropertyValue(prefix + "day")
        def hourString = (String) fieldValues.getPropertyValue(prefix + "hour")
        def minuteString = (String) fieldValues.getPropertyValue(prefix + "minute")
        if (!yearString &&
            !monthString &&
            !dayString &&
            !hourString &&
            !minuteString) {
            return null
        }
        def year
        try {
            assert yearString, "Can't populate a date without a year"

            year = Integer.parseInt(yearString)

            int month = getIntegerValue(fieldValues, prefix + "month", 1)
            int day = getIntegerValue(fieldValues, prefix + "day", 1)
            int hour = getIntegerValue(fieldValues, prefix + "hour", 0)
            int minute = getIntegerValue(fieldValues, prefix + "minute", 0)

            def c = new GregorianCalendar(year,month - 1,day,hour,minute)
            return getDate(c)
        }
        catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Unable to parse structured date from request for date [\"+propertyName+\"]\"")
        }
    }

    List<String> getRequiredFields() {
        ['year']
    }

    List<String> getOptionalFields() {
        ['month', 'day', 'hour', 'minute']
    }

    T getPropertyValue(obj, String propertyName, DataBindingSource source) {
        assemble(propertyName, source)
    }

    private int getIntegerValue(DataBindingSource values, String name, int defaultValue) throws NumberFormatException {
        if (values.getPropertyValue(name) != null) {
            return Integer.parseInt((String) values.getPropertyValue(name))
        }
        defaultValue
    }

    abstract T getDate(Calendar c)
}
