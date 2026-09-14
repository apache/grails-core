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

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Parameter as ParameterAnnotation
import io.swagger.v3.oas.annotations.tags.Tag as TagAnnotation
import io.swagger.v3.oas.annotations.Operation as OperationAnnotation
import io.swagger.v3.oas.annotations.responses.ApiResponse as ApiResponseAnnotation
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

class ActionAnnotationSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'an Operation annotation supplies the summary and description'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.paths['/annotated/index'].get) {
            summary == 'List the widgets'
            description == 'Returns every widget in the catalogue.'
        }
    }

    void 'an Operation annotation can override the derived operation id and tags'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.paths['/annotated/index'].get) {
            operationId == 'listWidgets'
            tags == ['Catalogue']
        }
    }

    void 'an ApiResponse annotation adds a documented response'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.paths['/annotated/show/{id}'].get.responses['403'].description == 'Not your widget'

        and: 'the derived responses are kept'
        openApi.paths['/annotated/show/{id}'].get.responses['200']
        openApi.paths['/annotated/show/{id}'].get.responses['404']
    }

    void 'a Hidden action is withheld from the document'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the annotated action is absent'
        !openApi.paths.containsKey('/annotated/delete/{id}')

        and: 'its siblings are not'
        openApi.paths['/annotated/index']
    }

    void 'a Hidden controller is withheld entirely'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.paths.keySet().every { !it.startsWith('/internal') }
    }

    void 'an unannotated action keeps what the module derived'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.paths['/annotated/create'].get) {
            summary == null
            operationId == 'annotated_create_get_byAction'
            tags == ['Widgets']
        }
    }

    void 'applies the annotations to a route a mapping names'() {
        given: 'a mapping that names the controller, rather than the default mapping'
        def openApi = new OpenAPI()

        when:
        mappedCustomizer().customise(openApi)

        then: 'the summary and tags come from the annotation'
        with(openApi.paths['/catalogue'].get) {
            summary == 'List the widgets'
            operationId == 'listWidgets'
            tags == ['Catalogue']
        }
    }

    void 'withholds a Hidden action from a route a mapping names'() {
        given:
        def openApi = new OpenAPI()

        when:
        mappedCustomizer().customise(openApi)

        then:
        !openApi.paths.containsKey('/catalogue/{id}')

        and:
        openApi.paths['/catalogue']
    }

    private static UrlMappingsOpenApiCustomizer mappedCustomizer() {
        def application = new DefaultGrailsApplication(AnnotatedController).tap { it.initialise() }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            get '/catalogue'(controller: 'annotated', action: 'index')
            delete '/catalogue/$id'(controller: 'annotated', action: 'delete')
        })

        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(AnnotatedWidget)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        new UrlMappingsOpenApiCustomizer(holder).tap {
            grailsApplication = application
            mappingContext = context
        }
    }

    void 'describes a tag a controller declares'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the grouping carries its description, not only its name'
        with(openApi.tags.find { it.name == 'Widgets' }) {
            it
            description == 'Everything in the catalogue'
        }

        and: 'and the operations appear under it, rather than under the controller name'
        openApi.paths['/annotated/create'].get.tags == ['Widgets']
    }

    void 'describes a path parameter an action declares'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.paths['/annotated/show/{id}'].get.parameters.find { it.name == 'id' }) {
            description == 'The identifier of the widget'
            example == '42'
        }
    }

    private static UrlMappingsOpenApiCustomizer customizer() {
        def application = new DefaultGrailsApplication(AnnotatedController, InternalController).tap {
            it.initialise()
        }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            "/$controller/$action?/$id?(.$format)?" {}
        })

        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(AnnotatedWidget)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        new UrlMappingsOpenApiCustomizer(holder).tap {
            grailsApplication = application
            mappingContext = context
        }
    }
}

@Entity
class AnnotatedWidget {
    String name
}

@TagAnnotation(name = 'Widgets', description = 'Everything in the catalogue')
@Artefact('Controller')
class AnnotatedController extends RestfulController<AnnotatedWidget> {

    AnnotatedController() { super(AnnotatedWidget) }

    @OperationAnnotation(summary = 'List the widgets',
            description = 'Returns every widget in the catalogue.',
            operationId = 'listWidgets',
            tags = ['Catalogue'])
    @Override
    def index() { }

    @ApiResponseAnnotation(responseCode = '403', description = 'Not your widget')
    @ParameterAnnotation(name = 'id', description = 'The identifier of the widget', example = '42')
    @Override
    def show() { }

    @Hidden
    @Override
    def delete() { }
}

@Hidden
@Artefact('Controller')
class InternalController extends RestfulController<AnnotatedWidget> {
    InternalController() { super(AnnotatedWidget) }
}
