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
package org.grails.core.io

import spock.lang.Specification

import org.springframework.core.io.ByteArrayResource

class StaticResourceLocatorSpec extends Specification {

    void 'findResourceForClassName returns a resource added via addClassResource'() {
        given:
        def locator = new StaticResourceLocator()
        def resource = new ByteArrayResource('class bytes'.bytes)

        when:
        locator.addClassResource('com.example.Foo', resource)

        then:
        locator.findResourceForClassName('com.example.Foo').is(resource)
    }

    void 'findResourceForClassName returns null for an unknown class name'() {
        given:
        def locator = new StaticResourceLocator()

        expect:
        locator.findResourceForClassName('com.example.Unknown') == null
    }

    void 'findResourceForURI is not implemented and always returns null'() {
        given:
        def locator = new StaticResourceLocator()

        expect:
        locator.findResourceForURI('/anything') == null
    }

    void 'setSearchLocation and setSearchLocations are no-ops'() {
        given:
        def locator = new StaticResourceLocator()

        when:
        locator.setSearchLocation('/some/path')
        locator.setSearchLocations(['/a', '/b'])

        then:
        noExceptionThrown()
    }

}
