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
package grails.config.external

import spock.lang.Specification

class WriteFilteringMapSpec extends Specification {
    def "get written values with seeded map"() {
        given:
        def original = [a: [b: 'b-value']]
        def filter = new WriteFilteringMap(original)
        when:
        filter.a.c = 'c-value'
        then:
        filter.getWrittenValues().size() == 1
        filter.getWrittenValues().get('a.c') == 'c-value'
    }

    def "get written values without seeding"() {
        given:
        def filter = new WriteFilteringMap()
        when:
        filter.a.c = 'c-value'
        then:
        filter.getWrittenValues().size() == 1
        filter.getWrittenValues().get('a.c') == 'c-value'
    }

    def "get written values from changing existing value"() {
        given:
        def original = [a: [b: 'b-value']]
        def filter = new WriteFilteringMap(original)
        when:
        filter.a.b = 'new-b-value'
        then:
        filter.getWrittenValues().size() == 1
        filter.getWrittenValues().get('a.b') == 'new-b-value'
    }

    def "get written values from deep nesting"() {
        given: "A seeded map"
        def original = [a: [b: 'b-value', c: [d: 'd-value']]]
        def filter = new WriteFilteringMap(original)
        expect: "that the filter map already contains these values"
        filter.a.b == 'b-value'
        filter.a.c.d == 'd-value'
        when: "setting new and existing values"
        filter.a.b = 'new-b-value'
        filter.a.c.e = 'new-e-value'
        filter.a.c.f.g.h = 'new-h-value'
        then: "these values are in the writen values"
        filter.getWrittenValues().size() == 3
        filter.getWrittenValues().get('a.b') == 'new-b-value'
        filter.getWrittenValues().get('a.c.e') == 'new-e-value'
        filter.getWrittenValues().get('a.c.f.g.h') == 'new-h-value'
    }
}
