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

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
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

class RestfulResponseSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void 'save is documented as created rather than ok'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.paths['/notes'].post.responses.keySet().contains('201')
        !openApi.paths['/notes'].post.responses.containsKey('200')
    }

    void 'delete is documented as no content with no body'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        with(openApi.paths['/notes/{id}'].delete.responses) {
            containsKey('204')
            !containsKey('200')
            get('204').content == null
        }
    }

    void 'an action that validates what it binds documents the validation failure'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.paths['/notes'].post.responses['422']
        openApi.paths['/notes/{id}'].put.responses['422']

        and: 'a read action does not'
        !openApi.paths['/notes/{id}'].get.responses.containsKey('422')
    }

    void 'the mapped and the default routes describe the same responses'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'reached through the resources mapping'
        openApi.paths['/notes'].post.responses.keySet().contains('201')

        and: 'and through the default mapping'
        openApi.paths['/note/save'].post.responses.keySet().contains('201')
        openApi.paths['/note/delete/{id}'].delete.responses.keySet().contains('204')
    }

    void 'an ApiResponse content annotation names the response schema'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'the declared view replaces the resource convention'
        openApi.paths['/note/summary'].get.responses['200']
                .content['application/json'].schema.$ref == '#/components/schemas/NoteSummary'

        and: 'and its schema is registered'
        openApi.components.schemas.containsKey('NoteSummary')
    }

    void 'documents the paging and sorting a listing accepts'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then: 'a client can page, which it could not learn from the path alone'
        openApi.paths['/notes'].get.parameters*.name as Set == ['max', 'offset', 'sort', 'order'] as Set

        and: 'with the ceiling the controller enforces'
        with(openApi.paths['/notes'].get.parameters.find { it.name == 'max' }.schema) {
            maximum == 100G
            it.default == 10
        }

        and: 'and the directions it accepts'
        openApi.paths['/notes'].get.parameters.find { it.name == 'order' }.schema.enum == ['asc', 'desc']
    }

    void 'does not offer paging on an action that addresses one resource'() {
        given:
        def openApi = new OpenAPI()

        when:
        customizer().customise(openApi)

        then:
        openApi.paths['/notes/{id}'].get.parameters*.name == ['id']
    }

    private static UrlMappingsOpenApiCustomizer customizer() {
        def application = new DefaultGrailsApplication(NoteController).tap { it.initialise() }
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)
        def holder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            '/notes'(resources: 'note')
            "/$controller/$action?/$id?(.$format)?" {}
        })

        MappingContext context = new KeyValueMappingContext('test')
        context.addPersistentEntity(Note)
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))

        new UrlMappingsOpenApiCustomizer(holder).tap {
            grailsApplication = application
            mappingContext = context
        }
    }
}

@Entity
class Note {
    String body
}

class NoteSummary {
    int total
}

@Artefact('Controller')
class NoteController extends RestfulController<Note> {

    NoteController() { super(Note) }

    @ApiResponseAnnotation(responseCode = '200',
            content = @Content(schema = @SchemaAnnotation(implementation = NoteSummary)))
    def summary() { }
}
