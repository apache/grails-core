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
import grails.web.Action
import spock.lang.Specification

/**
 * @author James Kleeh
 */
class DefaultGrailsControllerClassSpec extends Specification {

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

    void "an action that fails throws its own exception from a #description"() {
        when:
        new DefaultGrailsControllerClass(controller).invoke(controller.newInstance(), 'failing')

        then:
        def exception = thrown(IllegalStateException)
        exception.message == 'thrown by the action'

        where:
        description                     | controller
        'public controller'             | ActionsController
        'controller that is not public' | Class.forName('org.grails.core.nonpublic.NonPublicController')
    }

    void "an action of a controller that is not public is invoked"() {
        given:
        def controller = Class.forName('org.grails.core.nonpublic.NonPublicController')

        expect:
        new DefaultGrailsControllerClass(controller).invoke(controller.newInstance(), 'index') == 'answered by a controller that is not public'
    }

    void "the #action action returns #expected"() {
        given:
        def controllerClass = new DefaultGrailsControllerClass(ActionsController)

        expect:
        controllerClass.invoke(new ActionsController(name: 'the controller'), action) == expected

        where:
        action        || expected
        'show'        || 'shown by the controller'
        'count'       || 42
        'nothing'     || null
        'staticModel' || [answeredBy: 'a static action']
    }

    void "the default action is invoked when no action is named"() {
        expect:
        new DefaultGrailsControllerClass(ActionsController).invoke(new ActionsController(name: 'the controller'), null) == 'index of the controller'
    }

    void "an action name that is not an action is rejected"() {
        when:
        new DefaultGrailsControllerClass(ActionsController).invoke(new ActionsController(), 'notAnAction')

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == 'Invalid action name: notAnAction'
    }

    void "an inherited action runs the code of the controller it is invoked on"() {
        given:
        def controllerClass = new DefaultGrailsControllerClass(OverridingController)
        def controller = new OverridingController()

        expect:
        controllerClass.actions == ['overridden', 'inherited'] as Set
        controllerClass.invoke(controller, 'overridden') == 'overridden by the subclass'
        controllerClass.invoke(controller, 'inherited') == 'inherited from the superclass'
    }

    class NotSpecifiedController {
    }

    class PrototypeController {
        static scope = "prototype"
    }

    static class ActionsController {
        String name

        @Action
        String index() {
            "index of ${name}"
        }

        @Action
        String show() {
            "shown by ${name}"
        }

        @Action
        int count() {
            42
        }

        @Action
        void nothing() {
        }

        @Action
        static Map staticModel() {
            [answeredBy: 'a static action']
        }

        @Action
        void failing() {
            throw new IllegalStateException('thrown by the action')
        }

        String notAnAction() {
            'not an action'
        }
    }

    static abstract class BaseController {
        @Action
        String overridden() {
            'declared by the superclass'
        }

        @Action
        String inherited() {
            'inherited from the superclass'
        }
    }

    static class OverridingController extends BaseController {
        @Override
        @Action
        String overridden() {
            'overridden by the subclass'
        }
    }
}
