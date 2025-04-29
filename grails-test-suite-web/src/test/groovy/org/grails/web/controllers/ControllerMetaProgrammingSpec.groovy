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

package org.grails.web.controllers

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Issue
import spock.lang.Specification

class ControllerMetaProgrammingSpec extends Specification implements ControllerUnitTest<SubController> {

    def setupSpec() {
        BaseController.metaClass.someHelperMethod = {->
            delegate.metaprogrammedMethodCalled = true
        }
    }
    
    @Issue('GRAILS-11202')
    void 'Test runtime metaprogramming a controller helper method'() {
        when:
        controller.index()
        
        then:
        !controller.realMethodCalled
        controller.metaprogrammedMethodCalled
    }
}

@Artefact('Controller')
class BaseController {
    boolean realMethodCalled = false
    boolean metaprogrammedMethodCalled = false
    def index() {
        someHelperMethod()
    }
    
    protected someHelperMethod() {
        realMethodCalled = true
    }
}

@Artefact('Controller')
class SubController extends BaseController {
    def index() {
        super.index()
    }
}
