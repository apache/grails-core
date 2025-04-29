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

import grails.artefact.Artefact
import grails.rest.RestfulController
import grails.testing.web.GrailsWebUnitTest
import grails.testing.web.UrlMappingsUnitTest
import junit.framework.ComparisonFailure
import org.springframework.web.context.WebApplicationContext
import spock.lang.Issue
import spock.lang.Specification

/**
 * Tests for the UrlMappingsTestMixin class
 */
class UrlMappingsTestMixinTests extends Specification implements GrailsWebUnitTest {

//    @Test
//    void testMultipeUrlMappings() {
//        mockController(GrailsUrlMappingsTestCaseFakeController)
//        mockUrlMappings(MyUrlMappings)
//        groovyPages['/grailsUrlMappingsTestCaseFake/view.gsp'] = 'contents'
//        groovyPages['/grailsUrlMappingsTestCaseFake/viewXXX.gsp'] = 'contents'
//        groovyPages['/view.gsp'] = 'contents'
//
//        shouldFail(AssertionFailedError) {
//            assertUrlMapping("/nonexistent", controller: "grailsUrlMappingsTestCaseFake")
//        }
//
//        shouldFail(AssertionFailedError) {
//            assertUrlMapping("/action1", controller: "blah")
//        }
//
//        shouldFail(AssertionFailedError) {
//            assertUrlMapping("/action1", controller: "grailsUrlMappingsTestCaseFake", action: "xxx")
//        }
//
//        shouldFail(AssertionFailedError) {
//            assertUrlMapping("/action1", action: "action1")
//        }
//
//        shouldFail(ComparisonFailure) {
//            try {
//                assertUrlMapping("/action1", controller: "grailsUrlMappingsTestCaseFake", action: "action2")
//            }
//            catch (e) {}
//        }
//
//        assertUrlMapping("/action1", controller: "grailsUrlMappingsTestCaseFake", action: "action1")
//        assertUrlMapping("/action2", controller: "grailsUrlMappingsTestCaseFake", action: "action2")
//
//        assertForwardUrlMapping("/default", controller: "grailsUrlMappingsTestCaseFake", action: "action1")
//        shouldFail(ComparisonFailure) {
//            assertReverseUrlMapping("/default", controller: "grailsUrlMappingsTestCaseFake", action: "action1")
//        }
//
//        shouldFail(AssertionFailedError) {
//            assertUrlMapping(300, controller: "grailsUrlMappingsTestCaseFake", action: "action1")
//        }
//
//        assertUrlMapping(500, controller: "grailsUrlMappingsTestCaseFake", action: "action1")
//
//        assertForwardUrlMapping("/controllerView", controller: "grailsUrlMappingsTestCaseFake", view: "view")
//        shouldFail(ComparisonFailure) {
//            assertForwardUrlMapping("/controllerView", controller: "grailsUrlMappingsTestCaseFake", view: "viewXXX")
//        }
//
//        shouldFail(ComparisonFailure) {
//            assertUrlMapping("/absoluteView", controller: "grailsUrlMappingsTestCaseFake", view: "view")
//        }
//
//        assertUrlMapping("/absoluteView", view: "view")
//        assertUrlMapping("/absoluteView", view: "/view")
//        assertUrlMapping("/absoluteViewWithSlash", view: "view")
//        assertUrlMapping("/absoluteViewWithSlash", view: "/view")
//
//        assertUrlMapping("/params/value1/value2", controller: "grailsUrlMappingsTestCaseFake", action: "action3") {
//            param1 = "value1"
//            param2 = "value2"
//        }
//
//        shouldFail(ComparisonFailure) {
//            assertUrlMapping("/params/value3/value4", controller: "grailsUrlMappingsTestCaseFake", action: "action3") {
//                param1 = "value1"
//                param2 = "value2"
//            }
//        }
//
//        shouldFail(ComparisonFailure) {
//            assertUrlMapping("/params/value1/value2", controller: "grailsUrlMappingsTestCaseFake", action: "action3") {
//                param1 = "value1"
//                param2 = "value2"
//                xxx = "value3"
//            }
//        }
//
//        assertUrlMapping("/params/value1", controller: "grailsUrlMappingsTestCaseFake", action: "action3") {
//            param1 = "value1"
//        }
//    }

}

class AnotherUrlMappings {
    static mappings = {
        "/$controller/$action?/$id?" {}
        "/alias/$param1/"(controller: "grailsUrlMappingsTestCaseFake", action: "action1")
    }
}

class AnotherUrlMappingsSpec extends Specification implements UrlMappingsUnitTest<AnotherUrlMappings> {

    Class[] getControllersToMock() {
        [GrailsUrlMappingsTestCaseFakeController]
    }

    void testGrails5222Again() {
        when:
        assertForwardUrlMapping("/alias/param1value", controller: "grailsUrlMappingsTestCaseFake", action: "action1") {
            param1 = "invalidparam1value"
        }

        then:
        thrown(ComparisonFailure)
    }
}

@Artefact("Controller")
class GrailsUrlMappingsTestCaseFakeController {
   static defaultAction = 'action1'
   def action1(){}
   def action2(){}
   def action3(){}
}

@Artefact("Controller")
class UserController {
    def publicProfile() {}
    def update() {}
    def show() {}
    def list() {}
}

class MyUrlMappings {
    static mappings = { applicationContext ->
        assert applicationContext != null
        assert applicationContext instanceof WebApplicationContext
        "/action1"(controller: "grailsUrlMappingsTestCaseFake", action: "action1")
        "/action2"(controller: "grailsUrlMappingsTestCaseFake", action: "action2")
        "/default"(controller: "grailsUrlMappingsTestCaseFake")
        "500"(controller: "grailsUrlMappingsTestCaseFake", action: "action1")
        "/controllerView"(controller: "grailsUrlMappingsTestCaseFake", view: "view")
        "/absoluteView"(view: "view")
        "/absoluteViewWithSlash"(view: "/view")
        "/params/$param1/$param2?"(controller: "grailsUrlMappingsTestCaseFake", action: "action3")
    }
}

class MyUrlMappingsSpec extends Specification implements UrlMappingsUnitTest<MyUrlMappings> {

    Class[] getControllersToMock() {
        [GrailsUrlMappingsTestCaseFakeController]
    }

    void testMapUri() {
        when:
        def controller = mapURI('/action1')

        then:
        controller != null
        controller instanceof GrailsUrlMappingsTestCaseFakeController

        when:
        controller = mapURI('/rubbish')

        then:
        controller == null
    }
}

class GRAILS5222UrlMappings {
    static mappings = {
        "/user/$idText?"{
            controller = "user"
            action = "publicProfile"
        }
    }
}

class GRAILS5222UrlMappingsSpec extends Specification implements UrlMappingsUnitTest<GRAILS5222UrlMappings> {

    Class[] getControllersToMock() {
        [UserController]
    }

    void testGRAILS5222() {
        when:
        assertForwardUrlMapping("/user", controller: "user", action: "publicProfile") {
            idText = "1234"
        }

        then:
        thrown(ComparisonFailure)
    }
}

class GRAILS9863UrlMappings {
    static mappings = {
        "/p/user" (controller:"user", action:"index", plugin:"sample")
        "/n/user" (controller:"user", action:"index", namespace:"sample")
    }
}

class GRAILS9863UrlMappingsSpec extends Specification implements UrlMappingsUnitTest<GRAILS9863UrlMappings> {

    @Issue('https://github.com/apache/grails-core/issues/9863')
    void testGRAILS9863() {
        when:
        assertReverseUrlMapping("/p/user", controller:"user", action:"index", plugin:"sample")
        assertReverseUrlMapping("/n/user", controller:"user", action:"index", namespace: "sample")
        assertReverseUrlMapping("/user/index", controller:"user", action:"index")

        then:
        noExceptionThrown()
    }
}

class GRAILS9110UrlMappings {
    static mappings = {
        "/user"(controller:"user", action:"publicProfile") {
            boolParam = true
            strParam = "string"
            numParam = 123
            objParam = [test:true]
            dateParam = new Date(1)
        }
    }
}

class GRAILS9110UrlMappingsSpec extends Specification implements UrlMappingsUnitTest<GRAILS9110UrlMappings> {

    Class[] getControllersToMock() {
        [UserController]
    }

    void testGrails9110() {
        when:
        assertForwardUrlMapping("/user", controller:"user", action:"publicProfile") {
            param1 = "true"
        }

        then:
        thrown(ComparisonFailure)

        when:
        assertForwardUrlMapping("/user", controller:"user", action:"publicProfile") {
            boolParam = true
            strParam = "string"
            numParam = 123
            objParam = [test:true]
            dateParam = new Date(1)
        }

        then:
        noExceptionThrown()
    }
}

class MethodTestUrlMappings {
    static mappings = {
        "/users/$name"(controller: 'user', action: 'update', method: 'put')
        "/users/$name"(controller: 'user', action: 'show', method: 'get')
        "/users"(controller: 'user', action: 'list', method: 'get')
    }
}

class MethodTestUrlMappingsSpec extends Specification implements UrlMappingsUnitTest<MethodTestUrlMappings> {

    Class[] getControllersToMock() {
        [UserController]
    }

    void testMethodMappings() {
        when:
        request.method = 'GET'
        assertUrlMapping('/users/timmy', controller: 'user', action: 'show', method: 'get') { name = 'timmy' }
        assertUrlMapping('/users', controller: 'user', action: 'list', method: 'get')

        request.method = 'PUT'
        assertUrlMapping('/users/timmy', controller: 'user', action: 'update', method: 'put') { name = 'timmy' }

        then:
        noExceptionThrown()
    }
}

@Artefact("Controller")
class PersonController extends RestfulController<String> {
    PersonController() {
        super(''.class)
    }
}

class ResourceTestUrlMappings {
    static mappings = {
        '/person'(resources: 'person')
    }
}

class ResourceTestUrlMappingsSpec extends Specification implements UrlMappingsUnitTest<ResourceTestUrlMappings> {

    Class[] getControllersToMock() {
        [PersonController]
    }

    @Issue('https://github.com/apache/grails-core/issues/9065')
    void testResourcesUrlMapping() {
        when:
        request.method = 'GET'
        assertForwardUrlMapping('/person', controller: 'person', action: 'index')
        assertForwardUrlMapping('/person/create', controller: 'person', action: 'create')
        assertForwardUrlMapping('/person/personId', controller: 'person', action: 'show') {
            id = 'personId'
        }
        assertForwardUrlMapping('/person/personId/edit', controller: 'person', action: 'edit') {
            id = 'personId'
        }

        request.method = 'POST'
        assertForwardUrlMapping('/person', controller: 'person', action: 'save')

        request.method = 'PUT'
        assertForwardUrlMapping('/person/personId', controller: 'person', action: 'update') {
            id = 'personId'
        }

        request.method = 'PATCH'
        assertForwardUrlMapping('/person/personId', controller: 'person', action: 'patch') {
            id = 'personId'
        }

        request.method = 'DELETE'
        assertForwardUrlMapping('/person/personId', controller: 'person', action: 'delete') {
            id = 'personId'
        }

        then:
        noExceptionThrown()
    }
}

@Artefact("Controller")
class ExceptionTestErrorsController {
    def handleNullPointer() {}
    def handleIllegalArgument() {}
    def handleDefault() {}
}

class ExceptionTestUrlMappings {
    static mappings = {
        '500'(controller: 'exceptionTestErrors', action: 'handleNullPointer', exception: NullPointerException)
        '500'(controller: 'exceptionTestErrors', action: 'handleIllegalArgument', exception: IllegalArgumentException)
        '500'(controller: 'exceptionTestErrors', action: 'handleDefault')
    }
}

class ExceptionTestUrlMappingsSpec extends Specification implements UrlMappingsUnitTest<ExceptionTestUrlMappings> {

    Class[] getControllersToMock() {
        [ExceptionTestErrorsController]
    }

    @Issue('https://github.com/apache/grails-core/issues/10226')
    void testExceptionUrlMapping() {
        when:
        assertForwardUrlMapping(500, controller: 'exceptionTestErrors', action: 'handleNullPointer', exception: new NullPointerException())
        assertForwardUrlMapping(500, controller: 'exceptionTestErrors', action: 'handleIllegalArgument', exception: new IllegalArgumentException())
        assertForwardUrlMapping(500, controller: 'exceptionTestErrors', action: 'handleDefault')

        then:
        noExceptionThrown()
    }
}