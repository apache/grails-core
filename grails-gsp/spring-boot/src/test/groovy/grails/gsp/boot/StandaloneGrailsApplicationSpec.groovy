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
package grails.gsp.boot

import spock.lang.Specification

import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.MapPropertySource

import grails.core.ArtefactHandler
import grails.core.GrailsClass

/**
 * Tests that {@link StandaloneGrailsApplication} reads its configuration from the Spring environment and
 * otherwise behaves as an application with no artefacts.
 */
class StandaloneGrailsApplicationSpec extends Specification {

    StandaloneGrailsApplication application = new StandaloneGrailsApplication()

    void 'the configuration is empty without a parent context and is cached'() {
        when:
        def config = application.getConfig()

        then:
        config.isEmpty()
        application.getConfig().is(config)
    }

    void 'the configuration reads the property sources of a configurable environment'() {
        given:
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', ['my.setting': 'value']))
        application.setApplicationContext(Stub(ApplicationContext) {
            getEnvironment() >> environment
        })

        expect:
        application.getConfig().getProperty('my.setting') == 'value'
    }

    void 'the configuration is empty when the environment is not configurable'() {
        given:
        application.setApplicationContext(Stub(ApplicationContext) {
            getEnvironment() >> Stub(Environment)
        })

        expect:
        application.getConfig().isEmpty()
    }

    void 'the application has no classes, artefacts, handlers or mapping context'() {
        expect:
        application.getAllClasses().length == 0
        application.getAllArtefacts().length == 0
        application.getMappingContext() == null
        application.getArtefacts('Controller').length == 0
        application.getArtefactHandlers().length == 0
        application.getResourceForClass(String) == null
        application.getArtefact('Controller', 'x') == null
        application.getArtefactType(String) == null
        application.getArtefactInfo('Controller') == null
        application.getArtefactForFeature('Controller', 'x') == null
        application.getArtefactByLogicalPropertyName('Controller', 'x') == null
        application.getArtefactHandler('Controller') == null
        !application.isArtefact(String)
        !application.isArtefactOfType('Controller', String)
        !application.isArtefactOfType('Controller', 'java.lang.String')
        !application.hasArtefactHandler('Controller')
        !application.isInitialised()
    }

    void 'adding artefacts and lifecycle calls are accepted and ignored'() {
        when:
        application.setMappingContext(null)
        application.refresh()
        application.rebuild()
        application.initialise()
        application.registerArtefactHandler(Stub(ArtefactHandler))
        application.addArtefact(String)
        application.addOverridableArtefact(String)

        then:
        application.addArtefact('Controller', String) == null
        application.addArtefact('Controller', Stub(GrailsClass)) == null
        !application.isInitialised()
    }
}
