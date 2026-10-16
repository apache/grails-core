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
package org.grails.core

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.util.Environment
import grails.web.Action
import spock.lang.Specification

/**
 * @author James Kleeh
 */
class DefaultGrailsControllerClassSpec extends Specification {

    private String originalEnv

    void setup() {
        originalEnv = System.getProperty(Environment.KEY)
    }

    void cleanup() {
        if (originalEnv != null) {
            System.setProperty(Environment.KEY, originalEnv)
        }
        else {
            System.clearProperty(Environment.KEY)
        }
    }

    static final String SINGLETON = "singleton"
    static final String PROTOTYPE = "prototype"
    static final String SESSION = "session"

    void "test getScope when scope is not specified on the controller, but it specified in config"(final String configScopeValue) {
        given:
        def controllerClass = new DefaultGrailsControllerClass(NotSpecifiedController)
        def grailsApplication = new DefaultGrailsApplication()
        grailsApplication.getConfig().put(Settings.CONTROLLERS_DEFAULT_SCOPE, configScopeValue)
        controllerClass.setGrailsApplication(grailsApplication)

        expect: "the configuration value is used"
        controllerClass.getScope() == configScopeValue
        (SINGLETON == configScopeValue) == controllerClass.isSingleton()
        (SINGLETON != configScopeValue) != controllerClass.isSingleton()

        where:
        configScopeValue << [SINGLETON, PROTOTYPE, SESSION]
    }

    void "test getScope when scope is not specified on the controller, and not specified in config"() {
        given:
        def controllerClass = new DefaultGrailsControllerClass(NotSpecifiedController)
        controllerClass.setGrailsApplication(new DefaultGrailsApplication())

        expect: "the default scope is singleton"
        controllerClass.getScope() == SINGLETON
        controllerClass.isSingleton()
    }

    void "test getScope when scope is specified on the controller, and not specified in config"() {
        given:
        def controllerClass = new DefaultGrailsControllerClass(PrototypeController)

        expect:
        controllerClass.getScope() == PROTOTYPE
        !controllerClass.isSingleton()
    }

    void "test getScope when scope is specified both in the controller and config"(final String configScopeValue) {
        given:
        def controllerClass = new DefaultGrailsControllerClass(PrototypeController)
        def grailsApplication = new DefaultGrailsApplication()
        grailsApplication.getConfig().put(Settings.CONTROLLERS_DEFAULT_SCOPE, configScopeValue)
        controllerClass.setGrailsApplication(grailsApplication)

        expect: "controller's setting to have priority"
        controllerClass.getScope() == PROTOTYPE
        !controllerClass.isSingleton()

        where:
        configScopeValue << [SINGLETON, SESSION]
    }

    void "test invoke executes an action method and returns its result without miscasting the controller"() {
        given: "outside development mode, invoke() dispatches through a MethodHandle rather than reflection"
        System.setProperty(Environment.KEY, Environment.PRODUCTION.name)
        def controllerClass = new DefaultGrailsControllerClass(ActionController)
        def controller = new ActionController()

        expect: "the action is invoked on the correct controller instance, not miscast"
        controllerClass.invoke(controller, 'sayHello') == 'hello from ActionController'

        and: "a null action name dispatches to the default action, which is the only action here"
        controllerClass.invoke(controller, null) == 'hello from ActionController'
    }

    void "test invoke dispatches an action inherited from a parent controller and one returning a non-String"() {
        given:
        System.setProperty(Environment.KEY, Environment.PRODUCTION.name)
        def controllerClass = new DefaultGrailsControllerClass(ChildController)
        def controller = new ChildController()

        expect:
        controllerClass.invoke(controller, 'sayHello') == 'hello from ActionController'
        controllerClass.invoke(controller, 'model') == [child: true]
        controllerClass.invoke(controller, 'nothing') == null
    }

    void "test invoke repeats cheaply and sees per-instance state on every dispatch"() {
        given:
        System.setProperty(Environment.KEY, Environment.PRODUCTION.name)
        def controllerClass = new DefaultGrailsControllerClass(CountingController)
        def controller = new CountingController()

        when:
        5.times { controllerClass.invoke(controller, 'count') }

        then:
        controller.invocations == 5
        controllerClass.invoke(new CountingController(), 'count') == 1
    }

    void "test invoke rejects an unknown action name"() {
        given:
        def controllerClass = new DefaultGrailsControllerClass(ActionController)

        when:
        controllerClass.invoke(new ActionController(), 'missing')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Invalid action name: missing'
    }

    void "test invoke propagates the exception an action throws"() {
        given:
        System.setProperty(Environment.KEY, Environment.PRODUCTION.name)
        def controllerClass = new DefaultGrailsControllerClass(ChildController)

        when:
        controllerClass.invoke(new ChildController(), 'fail')

        then:
        IllegalStateException e = thrown()
        e.message == 'boom'
    }

    class NotSpecifiedController {
    }

    class PrototypeController {
        static scope = "prototype"
    }

    class ActionController {

        @Action
        String sayHello() {
            return 'hello from ActionController'
        }

    }

    class ChildController extends ActionController {

        @Action
        Map model() {
            return [child: true]
        }

        @Action
        void nothing() {
        }

        @Action
        Object fail() {
            throw new IllegalStateException('boom')
        }

    }

    class CountingController {

        int invocations

        @Action
        int count() {
            return ++invocations
        }

    }
}
