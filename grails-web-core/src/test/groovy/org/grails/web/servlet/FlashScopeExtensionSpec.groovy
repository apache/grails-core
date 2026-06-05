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
package org.grails.web.servlet

import groovy.transform.CompileStatic

import grails.web.mvc.FlashScope

import spock.lang.Specification

class FlashScopeExtensionSpec extends Specification {

    private FlashScope newFlash() {
        // false => do not register with the session, so put/get work without a bound request
        new GrailsFlashScope(false)
    }

    void "Test typed converters read and convert flash attributes"() {
        given:
            FlashScope flash = newFlash()
            flash.put('age', '42')
            flash.put('active', 'true')
            flash.put('tz', 'America/Los_Angeles')

        expect:
            flash.int('age') == 42
            flash.long('age') == 42L
            flash.boolean('active') == true
            flash.string('tz') == 'America/Los_Angeles'
    }

    void "Test typed converters are null-safe and honor defaults"() {
        given:
            FlashScope flash = newFlash()

        expect:
            flash.int('missing') == null
            flash.int('missing', 7) == 7
            flash.boolean('missing') == null
            flash.boolean('missing', true) == true
            flash.string('missing') == null
            flash.string('missing', 'fallback') == 'fallback'
            flash.list('missing') == []
    }

    void "Test converters resolve under static compilation"() {
        given:
            FlashScope flash = newFlash()
            flash.put('age', '21')

        expect:
            StaticCaller.readAge(flash) == 21
            StaticCaller.readTimeZone(flash) == 'America/Los_Angeles'
    }

    @CompileStatic
    static class StaticCaller {
        static Integer readAge(FlashScope flash) {
            flash.int('age')
        }

        static String readTimeZone(FlashScope flash) {
            flash.string('userTimeZoneId', 'America/Los_Angeles')
        }
    }
}
