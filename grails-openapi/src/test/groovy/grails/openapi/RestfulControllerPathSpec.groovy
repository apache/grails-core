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
package grails.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.persistence.Entity
import grails.rest.RestfulController
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils

import spock.lang.Specification

class RestfulControllerPathSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'describes a RestfulController that no URL mapping names'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the collection actions carry no id'
        openApi.paths['/memo/index'].get
        openApi.paths['/memo/save'].post

        and: 'the instance actions do'
        openApi.paths['/memo/show/{id}'].get
        openApi.paths['/memo/update/{id}'].put
        openApi.paths['/memo/patch/{id}'].patch
        openApi.paths['/memo/delete/{id}'].delete
    }

    void 'derives the HTTP method from the controller allowedMethods'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'save is a POST rather than the GET a read action gets'
        openApi.paths['/memo/save'].readOperationsMap().keySet() == [PathItem.HttpMethod.POST] as Set

        and: 'delete is a DELETE'
        openApi.paths['/memo/delete/{id}'].readOperationsMap().keySet() == [PathItem.HttpMethod.DELETE] as Set

        and: 'a read action stays GET'
        openApi.paths['/memo/index'].readOperationsMap().keySet() == [PathItem.HttpMethod.GET] as Set
    }

    void 'documents both routes when a mapping also names the controller'() {
        given: 'an application that maps memos explicitly and keeps the default mapping'
        def openApi = new OpenAPI()

        when:
        customizer {
            '/memos'(resources: 'memo')
            "/$controller/$action?/$id?(.$format)?" {}
        }.customise(openApi)

        then: 'the declared mapping is documented'
        openApi.paths['/memos'].get
        openApi.paths['/memos/{id}'].get

        and: 'so is the route the default mapping serves, because both answer'
        openApi.paths['/memo/show/{id}'].get
    }

    void 'attaches the resource schema to the described actions'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'index responds with a collection'
        with(openApi.paths['/memo/index'].get.responses['200'].content['application/json'].schema) {
            type == 'array'
            items.$ref == '#/components/schemas/Memo'
        }

        and: 'save accepts the request schema'
        openApi.paths['/memo/save'].post.requestBody
                .content['application/json'].schema.$ref == '#/components/schemas/Memo'

        and: 'an instance action can miss'
        openApi.paths['/memo/show/{id}'].get.responses['404']
    }

    void 'ignores a controller that is not a RestfulController'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.paths.keySet().every { !it.startsWith('/plain') }
    }

    private static UrlMappingsOpenApiCustomizer customizer(Closure mappings = {
        "/$controller/$action?/$id?(.$format)?" {}
    }) {
        def application = new DefaultGrailsApplication(MemoController, PlainController, Memo).tap {
            it.initialise()
        }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def holder = new DefaultUrlMappingsHolder(evaluator.evaluateMappings(mappings))

        // DefaultGrailsApplication's mapping context proxy refuses access before GORM starts, so
        // the entities are mapped directly the way the other specs do.
        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Memo)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        new UrlMappingsOpenApiCustomizer(holder).tap {
            grailsApplication = application
            mappingContext = context
        }
    }
}

@Entity
class Memo {
    String subject
}

@Artefact('Controller')
class MemoController extends RestfulController<Memo> {
    MemoController() { super(Memo) }
}

@Artefact('Controller')
class PlainController {
    def index() {}
}
