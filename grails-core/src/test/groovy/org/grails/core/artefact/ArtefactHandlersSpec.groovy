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
package org.grails.core.artefact

import spock.lang.Specification

import grails.core.GrailsClass
import org.grails.core.DefaultGrailsControllerClass
import org.grails.core.DefaultGrailsServiceClass
import org.grails.core.DefaultGrailsUrlMappingsClass

class ArtefactHandlersSpec extends Specification {

    void 'ControllerArtefactHandler is wired for the Controller suffix'() {
        given:
        ControllerArtefactHandler handler = new ControllerArtefactHandler()

        expect:
        handler.type == ControllerArtefactHandler.TYPE
        handler.type == 'Controller'
        handler.pluginName == ControllerArtefactHandler.PLUGIN_NAME
        handler.pluginName == 'controllers'
        handler.isArtefactClass(SomeController)
        !handler.isArtefactClass(AbstractController)
        !handler.isArtefactClass(UnrelatedPlainClass)

        and:
        GrailsClass grailsClass = handler.newArtefactClass(SomeController)
        grailsClass instanceof DefaultGrailsControllerClass
        grailsClass.clazz == SomeController
    }

    void 'ServiceArtefactHandler is wired for the Service suffix'() {
        given:
        ServiceArtefactHandler handler = new ServiceArtefactHandler()

        expect:
        handler.type == ServiceArtefactHandler.TYPE
        handler.type == 'Service'
        handler.pluginName == ServiceArtefactHandler.PLUGIN_NAME
        handler.pluginName == 'services'
        handler.isArtefactClass(SomeService)
        !handler.isArtefactClass(AbstractService)
        !handler.isArtefactClass(AnotherUnrelatedPlainClass)

        and:
        GrailsClass grailsClass = handler.newArtefactClass(SomeService)
        grailsClass instanceof DefaultGrailsServiceClass
        grailsClass.clazz == SomeService
    }

    void 'UrlMappingsArtefactHandler is wired for the UrlMappings suffix'() {
        given:
        UrlMappingsArtefactHandler handler = new UrlMappingsArtefactHandler()

        expect:
        handler.type == UrlMappingsArtefactHandler.TYPE
        handler.type == 'UrlMappings'
        handler.isArtefactClass(SomeUrlMappings)
        !handler.isArtefactClass(NoMappingsAtAll)

        and:
        GrailsClass grailsClass = handler.newArtefactClass(SomeUrlMappings)
        grailsClass instanceof DefaultGrailsUrlMappingsClass
        grailsClass.clazz == SomeUrlMappings
    }

    static class SomeController {

    }

    abstract static class AbstractController {

    }

    static class UnrelatedPlainClass {

    }

    static class SomeService {

    }

    abstract static class AbstractService {

    }

    static class AnotherUnrelatedPlainClass {

    }

    static class SomeUrlMappings {

    }

    static class NoMappingsAtAll {

    }

}
