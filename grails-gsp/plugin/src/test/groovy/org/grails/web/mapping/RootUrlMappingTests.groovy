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
package org.grails.web.mapping

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

class RootUrlMappingTests extends Specification implements UrlMappingsUnitTest<StoreUrlMappings> {


    def testMappingToController() {
        when:
        def template = '<g:link controller="store">Show the time !</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/">Show the time !</a>'
    }

}

@Artefact("UrlMappings")
class StoreUrlMappings {
    static mappings = {
        "/"(controller:"store")
        "/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }
        "/"(view:"/index")
        "500"(view:'/error')
    }
}

@Artefact("Controller")
class StoreController {

    def index = { }

    def showTime = {
        render "${new Date()}"
    }
}
