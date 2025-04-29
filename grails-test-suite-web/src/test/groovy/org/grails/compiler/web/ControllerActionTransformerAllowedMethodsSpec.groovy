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

package org.grails.compiler.web

import grails.artefact.Artefact
import grails.artefact.Enhanced
import grails.testing.web.controllers.ControllerUnitTest

import jakarta.servlet.http.HttpServletResponse
import spock.lang.Issue
import spock.lang.Specification

class ControllerActionTransformerAllowedMethodsSpec extends Specification implements ControllerUnitTest<SomeAllowedMethodsController> {

    @Issue('GRAILS-8426')
    void 'Test @AllowedMethodsHandledAtCompileTime is added'() {
        when:
        def annotation = SomeAllowedMethodsController.getAnnotation(Enhanced)

        then:
        annotation
        
        and:
        'allowedMethods' in annotation.enhancedFor()
    }
    
    @Issue('GRAILS-8426')
    void 'Test accessing an action that is not specified in allowedMethods'() {
        when:
        controller.anyMethodAllowed()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'Success'
    }
    
    @Issue('GRAILS-8426')
    void 'Test accessing an action that is limited to a single request method using the valid request method'() {
        when:
        request.method = 'POST'
        controller.onlyPostAllowed()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'Success'
    }
    
    @Issue('GRAILS-8426')
    void 'Test accessing an action that is limited to a single request method that is not specified in all upper case using the valid request method'() {
        when:
        request.method = 'POST'
        controller.mixedCasePost()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'Success'
    }
    
    @Issue('GRAILS-8426')
    void 'Test accessing an action that is limited to a single request method that is not specified in all upper case using an invalid request method'() {
        when:
        request.method = 'GET'
        controller.mixedCasePost()
        
        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
    }
    
    @Issue('GRAILS-8426')
    void 'Test accessing an action that is limited to a List of request methods using an ivalid request method'() {
        when:
        request.method = 'GET'
        controller.postOrPutAllowed()
        
        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
    }
    
    @Issue('GRAILS-8426')
    void 'Test accessing an action that is limited to a List of request methods using the first specified valid valid request method'() {
        when:
        request.method = 'POST'
        controller.postOrPutAllowed()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'Success'
    }
    
    @Issue('GRAILS-8426')
    void 'Test accessing an action that is limited to a List of request methods using the last specified valid valid request method'() {
        when:
        request.method = 'PUT'
        controller.postOrPutAllowed()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'Success'
    }
    
    @Issue('GRAILS-8426')
    void 'Test accessing an action that is limited to a single request method using an invalid request method'() {
        when:
        request.method = 'PUT'
        controller.onlyPostAllowed()
        
        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
    }
    
    @Issue('GRAILS-11444')
    void 'Test invoking a restricted action method from an unrestricted action method'() {
        when: 'an unrestricted action method invokes a restricted action method'
        controller.callPostMethod()
        
        then: 'the allowedMethods should not be checked by the restricted method'
        response.status == HttpServletResponse.SC_OK
    }
    
    @Issue('GRAILS-11444')
    void 'Test invoking a restricted action method from another restricted action method'() {
        when: 'a restricted action method invokes another restricted action method'
        request.method = 'PUT'
        controller.callPostMethodFromPutMethod()
            
        then: 'the allowedMethods should not be checked by the second method'
        response.status == HttpServletResponse.SC_OK
    }
    
    @Issue('GRAILS-11444')
    void 'Test invoking an unrestrected action method which invokes several other restricted actions'() {
        when: 'an action invokes several other restricted actions'
        request.method = 'GET'
        controller.callSeveralRestrictedActions()
        
        then: 'only the first action imposes the allowedMethods check'
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'Success From callSeveralRestrictedActions'
    }
    
    @Issue('GRAILS-11444')
    void 'Test allowedMethods handling for a unit test which initiates several requests'() {
        when: 'an action invokes several other restricted actions'
        request.method = 'GET'
        controller.callSeveralRestrictedActions()
        
        then: 'only the first action imposes the allowedMethods check'
        response.status == HttpServletResponse.SC_OK
        
        when: 'an unrestricted action method invokes a restricted action method'
        response.reset()
        controller.callPostMethod()
        
        then: 'the allowedMethods should not be checked by the restricted method'
        response.status == HttpServletResponse.SC_OK

        when: 'an invalid request method is used'
        response.reset()
        request.method = 'POST'
        controller.callPostMethodFromPutMethod()
        
        then: 'the method is not allowed'
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
        
        when: 'a restricted action method invokes another restricted action method'
        response.reset()
        request.method = 'PUT'
        controller.callPostMethodFromPutMethod()
            
        then: 'the allowedMethods should not be checked by the second method'
        response.status == HttpServletResponse.SC_OK
    }
}

@Artefact('Controller')
class SomeAllowedMethodsController {
    
    static allowedMethods = [callPostMethodFromPutMethod: 'PUT', 
                             onlyPostAllowed: 'POST', 
                             postOrPutAllowed: ['POST', 'PUT'], 
                             mixedCasePost: 'pOsT',
                             postOne: 'POST',
                             postTwo: 'POST']
    
    def anyMethodAllowed() {
        render 'Success'
    }
    
    def onlyPostAllowed() {
        render 'Success'
    }
    
    def postOrPutAllowed() {
        render 'Success'
    }
    
    def mixedCasePost() {
        render 'Success'
    }
    
    def callPostMethod() {
        onlyPostAllowed()
    }
    
    def callPostMethodFromPutMethod() {
        onlyPostAllowed()
    }
    
    def callSeveralRestrictedActions() {
        postOne()
        postTwo()
        render 'Success From callSeveralRestrictedActions'
    }
    
    def postOne() {}
    def postTwo() {}
}
