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

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.annotation.Entity
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

/**
 * The mappings a generated REST API application is given, which name the action but leave the
 * controller to the request.
 */
class RestApiMappingSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'describes the routes a REST API application actually serves'() {
        given:
        def openApi = new OpenAPI()

        when:
        restApiCustomizer().customise(openApi)

        then: 'the collection routes the mapping serves'
        openApi.paths['/gadget'].get
        openApi.paths['/gadget'].post

        and: 'and the instance routes'
        with(openApi.paths['/gadget/{id}']) {
            get
            put
            patch
            delete
        }
    }

    void 'does not describe routes a REST API application does not serve'() {
        given:
        def openApi = new OpenAPI()

        when:
        restApiCustomizer().customise(openApi)

        then: 'there is no controller/action/id mapping, so no such path is described'
        openApi.paths.keySet().every { !it.startsWith('/gadget/show') && !it.startsWith('/gadget/save') }
        !openApi.paths.containsKey('/gadget/index')
    }

    void 'takes the method from the mapping rather than the convention'() {
        given:
        def openApi = new OpenAPI()

        when:
        restApiCustomizer().customise(openApi)

        then: 'save is reached by POST on the collection path'
        openApi.paths['/gadget'].post.responses.keySet().contains('201')

        and: 'delete by DELETE on the instance path'
        openApi.paths['/gadget/{id}'].delete.responses.keySet().contains('204')
    }

    void 'describes nothing when no mapping reaches the controller'() {
        given:
        def openApi = new OpenAPI()

        when: 'an application whose mappings name neither the controller nor a dynamic one'
        customizerFor {
            '/'(view: '/index')
        }.customise(openApi)

        then:
        openApi.paths == null || openApi.paths.isEmpty()
    }

    void 'gives the default mapping routes a distinct operation id'() {
        given:
        def openApi = new OpenAPI()

        when: 'both a resources mapping and the default mapping are present'
        customizerFor {
            '/gadgets'(resources: 'gadget')
            "/$controller/$action?/$id?(.$format)?" {}
        }.customise(openApi)

        then: 'no two operations share an identifier'
        def ids = openApi.paths.values().collectMany { it.readOperationsMap().values() }*.operationId
        ids.size() == ids.toSet().size()
    }

    void 'honors a prefix the mapping is grouped under'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizerFor {
            group('/api/v1') {
                "/$controller/$action?/$id?(.$format)?"()
            }
        }.customise(openApi)

        then: 'the paths are the ones the group serves, not the ones the convention would build'
        openApi.paths.keySet().every { it.startsWith('/api/v1/gadget') }
        openApi.paths.containsKey('/api/v1/gadget/show/{id}')
        openApi.paths.containsKey('/api/v1/gadget/index')
    }

    void 'declares a parameter for every template variable and no other'() {
        given:
        def openApi = new OpenAPI()

        when: 'a mapping carrying a variable other than the identifier'
        customizerFor {
            get "/api/$apiVersion/$controller"(action: 'index')
        }.customise(openApi)

        then:
        openApi.paths.containsKey('/api/{apiVersion}/gadget')

        and: 'the variable the path declares is described, and no identifier is invented'
        def names = openApi.paths['/api/{apiVersion}/gadget'].get.parameters
                .findAll { it.in == 'path' }*.name
        names == ['apiVersion']
    }

    void 'gives every operation a distinct identifier however it is reached'() {
        given:
        def openApi = new OpenAPI()

        when: 'a resources mapping and a mapping that names the action reach the same controller'
        customizerFor {
            '/gadgets'(resources: 'gadget')
            get "/$controller(.$format)?"(action: 'index')
        }.customise(openApi)

        then:
        def ids = openApi.paths.values().collectMany { it.readOperationsMap().values() }*.operationId
        ids.size() == ids.toSet().size()
    }

    private static UrlMappingsOpenApiCustomizer restApiCustomizer() {
        customizerFor {
            delete "/$controller/$id(.$format)?"(action: 'delete')
            get "/$controller(.$format)?"(action: 'index')
            get "/$controller/$id(.$format)?"(action: 'show')
            post "/$controller(.$format)?"(action: 'save')
            put "/$controller/$id(.$format)?"(action: 'update')
            patch "/$controller/$id(.$format)?"(action: 'patch')
        }
    }

    private static UrlMappingsOpenApiCustomizer customizerFor(Closure mappings) {
        def application = new DefaultGrailsApplication(GadgetController).tap { it.initialise() }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings(mappings))

        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Gadget)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        new UrlMappingsOpenApiCustomizer(holder).tap {
            grailsApplication = application
            mappingContext = context
        }
    }
}

@Entity
class Gadget {
    String label
}

@Artefact('Controller')
class GadgetController extends RestfulController<Gadget> {
    GadgetController() { super(Gadget) }
}
