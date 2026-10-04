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
package grails.test.mixin

import spock.lang.Specification

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest

class UrlMappingsWithNonPublicConstructorSpec extends Specification implements UrlMappingsUnitTest<PrivateConstructorUrlMappings> {

    Class[] getControllersToMock() {
        [PrivateConstructorController]
    }

    void 'url mappings with a non-public constructor can be tested'() {
        expect:
        urlMappingsHolder
        assertForwardUrlMapping('/privateConstructor/show', controller: 'privateConstructor', action: 'show')
    }
}

@Artefact('Controller')
class PrivateConstructorController {

    def show() {}
}

class PrivateConstructorUrlMappings {

    static mappings = {
        '/privateConstructor/show'(controller: 'privateConstructor', action: 'show')
    }

    private PrivateConstructorUrlMappings() {}
}
