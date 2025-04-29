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

package org.grails.web.metaclass

import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.util.MockRequestDataValueProcessor

import org.grails.web.servlet.GrailsFlashScope
import spock.lang.Specification

class ChainMethodWithRequestDataValueProcessorSpec extends Specification implements ControllerUnitTest<TestChainController>, DomainUnitTest<TestChainBook> {

    Closure doWithSpring() {{ ->
        requestDataValueProcessor MockRequestDataValueProcessor
    }}

    void 'test chain method with model and request data value processor'() {
        when:
        controller.save()

        then:
        controller.flash.chainModel.book
        controller.flash.chainModel[GrailsFlashScope.ERRORS_PREFIX+System.identityHashCode(controller.flash.chainModel.book)]
        '/testChain/create?requestDataValueProcessorParamName=paramValue' == response.redirectedUrl
    }
}
