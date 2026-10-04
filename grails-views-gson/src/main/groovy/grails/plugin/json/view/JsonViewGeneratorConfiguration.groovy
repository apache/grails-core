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

import groovy.transform.CompileStatic

/**
 * Created by jameskleeh on 11/8/16.
 */
@CompileStatic
class JsonViewGeneratorConfiguration {

    Boolean escapeUnicode = false

    /**
     * The {@link java.text.SimpleDateFormat} pattern for {@link Date} and {@link Calendar} values and map keys. As
     * Spring Boot does, it writes a UTC instant with millisecond precision in the default {@link #timeZone}, such as
     * {@code 2024-06-15T14:30:45.123Z}, and in another zone the date and time there with the zone's offset, such as
     * {@code 2024-06-15T10:30:45.123-04:00}.
     */
    String dateFormat = /yyyy-MM-dd'T'HH:mm:ss.SSSXXX/

    /**
     * The time zone that {@link #dateFormat} writes {@link Date} and {@link Calendar} values in.
     */
    String timeZone = 'GMT'

    /**
     * The locale for {@link #dateFormat}.
     */
    String locale = 'en/US'
}
