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

package org.grails.scaffolding.registry.input

import groovy.transform.CompileStatic
import org.grails.scaffolding.model.property.DomainProperty

/**
 * The default renderer for rendering {@link TimeZone} properties
 *
 * @author James Kleeh
 */
@CompileStatic
class TimeZoneInputRenderer implements MapToSelectInputRenderer<TimeZone> {

    String getOptionValue(TimeZone timeZone) {
        Date date = new Date()
        String shortName = timeZone.getDisplayName(timeZone.inDaylightTime(date), TimeZone.SHORT)
        String longName = timeZone.getDisplayName(timeZone.inDaylightTime(date), TimeZone.LONG)

        int offset = timeZone.rawOffset
        BigDecimal hour = offset / (60 * 60 * 1000)
        BigDecimal minute = offset / (60 * 1000)
        double min = Math.abs(minute.toDouble()) % 60

        "${shortName}, ${longName} ${hour}:${min} [${timeZone.ID}]"
    }

    String getOptionKey(TimeZone timeZone) {
        timeZone.ID
    }

    Map<String, String> getOptions() {
        TimeZone.availableIDs.collectEntries {
            TimeZone timeZone = TimeZone.getTimeZone(it)
            [(getOptionKey(timeZone)): getOptionValue(timeZone)]
        }
    }

    TimeZone getDefaultOption() {
        TimeZone.default
    }

    @Override
    boolean supports(DomainProperty property) {
        property.type in TimeZone
    }

}
