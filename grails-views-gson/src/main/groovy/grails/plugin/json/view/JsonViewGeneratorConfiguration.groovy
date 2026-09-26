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
     * A {@link java.text.SimpleDateFormat} pattern for {@link Date} and {@link Calendar} values. When it is
     * not set they are written the same way as Spring Boot writes them, a UTC instant with millisecond
     * precision such as {@code 2024-06-15T14:30:45.123Z} (see {@link org.grails.web.json.JsonDateFormat}).
     */
    String dateFormat

    /**
     * The time zone that {@link Date} and {@link Calendar} values are written in: with the zone's offset, as Spring Boot's
     * {@code spring.jackson.time-zone} does, or with {@link #dateFormat} when it is set.
     */
    String timeZone = 'GMT'

    /**
     * The locale for {@link #dateFormat}.
     */
    String locale = 'en/US'
}
