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

    class NotSpecifiedController {
    }

    class PrototypeController {
        static scope = "prototype"
    }
}
