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
package grails.plugin.json.converters

import java.time.Year
import java.util.function.Function

import spock.lang.Specification

class SimpleTypeJsonConverterSpec extends Specification {

    void "handles returns true for the given type and its subtypes"() {
        given:
        def converter = new SimpleTypeJsonConverter(Number, { it } as Function)

        expect:
        with(converter) {
            handles(Integer)
            handles(Long)
            handles(Number)
            !handles(String)
        }
    }

    void "handles returns true only for an exact match when the type has no subtypes"() {
        given:
        def converter = new SimpleTypeJsonConverter(Year, { it } as Function)

        expect:
        with(converter) {
            handles(Year)
            !handles(Integer)
        }
    }

    void "convert delegates to the value extractor"() {
        given:
        def converter = new SimpleTypeJsonConverter(Year, { ((Year) it).value } as Function)

        expect:
        converter.convert(Year.of(2026), 'year') == 2026
    }

}
