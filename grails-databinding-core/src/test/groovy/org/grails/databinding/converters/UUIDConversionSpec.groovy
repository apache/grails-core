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


import grails.databinding.SimpleMapDataBindingSource

import grails.databinding.SimpleDataBinder

import spock.lang.Specification

class UUIDConversionSpec extends Specification {

    void 'Binding String to a UUID'() {
        given:
        def binder = new SimpleDataBinder()
        binder.registerConverter new UUIDConverter()
        def testClass = new UUIDTestClass()

        and:
        def givenUUID = '534f7cee-bf88-45f3-96f2-9cae0828cd16'

        when:
        binder.bind testClass, [uuid: givenUUID] as SimpleMapDataBindingSource

        then:
        testClass.uuid instanceof UUID
        testClass.uuid.toString() == givenUUID
    }

    void 'Binding badly formatted string to a UUID'() {
        given:
        def binder = new SimpleDataBinder()
        binder.registerConverter new UUIDConverter()
        def testClass = new UUIDTestClass()

        and:
        def givenUUID = '123-not-a-uuid-3291'

        when:
        binder.bind testClass, [uuid: givenUUID] as SimpleMapDataBindingSource

        then:
        notThrown(IllegalArgumentException)
        testClass.uuid == null
    }

    void 'Binding null to UUID'() {
        given:
        def binder = new SimpleDataBinder()
        binder.registerConverter new UUIDConverter()
        def testClass = new UUIDTestClass()

        and:
        def givenUUID = null

        when:
        binder.bind testClass, [uuid: givenUUID] as SimpleMapDataBindingSource

        then:
        notThrown(IllegalArgumentException)
        testClass.uuid == null
    }
}

class UUIDTestClass {
    UUID uuid
}
