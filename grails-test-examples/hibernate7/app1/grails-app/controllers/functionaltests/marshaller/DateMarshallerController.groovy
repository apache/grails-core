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
package functionaltests.marshaller

import grails.converters.JSON
import grails.converters.XML

/**
 * Controller for functional testing of Date and Calendar marshalling
 * through the Grails converters pipeline (JSON and XML).
 *
 * Uses deterministic epoch-based dates so expected output is
 * timezone-independent.
 */
class DateMarshallerController {

    static responseFormats = ['json', 'xml']

    /**
     * Returns a Date at epoch (1970-01-01T00:00:00Z) as JSON or XML.
     * Exercises json/DateMarshaller and xml/DateMarshaller.
     */
    def date() {
        def data = [dateField: new Date(0)]
        withFormat {
            json { render data as JSON }
            xml { render data as XML }
        }
    }

    /**
     * Returns a Calendar at epoch as JSON or XML.
     * Exercises json/CalendarMarshaller.
     */
    def calendar() {
        def cal = Calendar.getInstance(TimeZone.getTimeZone('UTC'))
        cal.timeInMillis = 0
        def data = [calField: cal]
        withFormat {
            json { render data as JSON }
            xml { render data as XML }
        }
    }

    /**
     * Returns a Date with non-zero milliseconds to verify the fractional-seconds path.
     * 1234567890123L = 2009-02-13T23:31:30.123Z
     */
    def dateWithMillis() {
        def data = [dateField: new Date(1234567890123L)]
        withFormat {
            json { render data as JSON }
            xml { render data as XML }
        }
    }
}
