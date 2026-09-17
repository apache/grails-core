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
package org.grails.datastore.gorm.validation.jakarta

import spock.lang.Specification

class MethodKeySpec extends Specification {

    void "test equals and hashCode for keys with the same name and parameter types"() {
        given:
        def a = new MethodKey('save', [String, Integer] as Class[])
        def b = new MethodKey('save', [String, Integer] as Class[])

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    void "test equals returns false for a different method name"() {
        given:
        def a = new MethodKey('save', [String] as Class[])
        def b = new MethodKey('delete', [String] as Class[])

        expect:
        a != b
    }

    void "test equals returns false for different parameter types"() {
        given:
        def a = new MethodKey('save', [String] as Class[])
        def b = new MethodKey('save', [Integer] as Class[])

        expect:
        a != b
    }

    void "test equals returns false when compared to a different type or null"() {
        given:
        def key = new MethodKey('save', [String] as Class[])

        expect:
        key != 'save'
        key != null
    }

    void "test a key can be used as a map key"() {
        given:
        Map<MethodKey, String> map = [:]
        map[new MethodKey('save', [String] as Class[])] = 'first'

        expect:
        map[new MethodKey('save', [String] as Class[])] == 'first'
    }
}
