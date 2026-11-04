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

import groovy.json.JsonSlurper

import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.rest.RestfulController
import grails.validation.Validateable

import spock.lang.Shared
import spock.lang.Specification

class ControllerAnnotationSpec extends Specification {

    @Shared
    def openApi = OpenApiFixture.document([ConsignmentController], [Consignment]) {
        '/consignments'(resources: 'consignment')
        "/consignments/$number/letter"(controller: 'consignment', action: 'letter')
    }

    void 'a response the controller declares is described for each of its actions'() {
        expect:
        ['/consignments': 'get', '/consignments/{id}': 'delete'].every { String path, String method ->
            openApi.paths[path]."${method}".responses['403'].description == 'Permission denied'
        }

        and: 'with the body it declares, whose type is described'
        openApi.paths['/consignments'].get.responses['422'].content['application/json'].schema.$ref ==
                '#/components/schemas/ApiError'
        openApi.components.schemas['ApiError'].properties.keySet() == ['message'] as Set
    }

    void 'an action refines what its controller declares'() {
        expect: 'the response the action declares replaces the controller one of the same status'
        openApi.paths['/consignments'].get.responses['403'].description == 'Permission denied'
        openApi.paths['/consignments/{id}'].get.responses['403'].description == 'Not your consignment'
    }

    void 'a query parameter an action declares is described'() {
        when:
        def profile = openApi.paths['/consignments'].get.parameters.find { it.name == 'profile' }

        then:
        profile.in == 'query'
        profile.description == 'The customer profile to act for'
        OpenApiFixture.typeOf(profile.schema) == 'integer'
        profile.schema.format == 'int64'
        profile.example.toString() == '123'
    }

    void 'a request body an action declares replaces the one derived'() {
        expect:
        with(openApi.paths['/consignments'].post.requestBody) {
            description == 'The consignments to create'
            content['application/json'].schema.$ref == '#/components/schemas/ConsignmentBatch'
        }

        and: 'its type is described, including the types it refers to'
        openApi.components.schemas['ConsignmentBatch'].properties.consignments.items.$ref ==
                '#/components/schemas/Consignment'
    }

    void 'a response that is not JSON is described as declared'() {
        when:
        def content = openApi.paths['/consignments/{number}/letter'].get.responses['200'].content

        then:
        content.keySet() == ['application/pdf'] as Set
        OpenApiFixture.typeOf(content['application/pdf'].schema) == 'string'
        content['application/pdf'].schema.format == 'binary'

        and: 'written with its type, which a 3.1 document is written from the types of'
        Map written = (Map) new JsonSlurper().parseText(GrailsOpenApiGenerator.serialize(openApi, 'json'))
        written.paths['/consignments/{number}/letter'].get.responses['200'].content['application/pdf'].schema ==
                [type: 'string', format: 'binary']
    }

    void 'a collection response is described as declared'() {
        expect:
        with(openApi.paths['/consignments'].get.responses['200'].content['application/json'].schema) {
            OpenApiFixture.typeOf(it) == 'array'
            items.$ref == '#/components/schemas/Consignment'
        }
    }

    void 'the security the controller and an action declare is described'() {
        expect:
        openApi.paths['/consignments'].get.security*.keySet().flatten() as Set == ['Bearer'] as Set
        openApi.paths['/consignments/{id}'].delete.security*.keySet().flatten() as Set == ['Bearer', 'Admin'] as Set
    }

    void 'the tags a controller declares group its operations'() {
        expect:
        openApi.paths['/consignments'].get.tags == ['Consignments', 'Logistics']
        openApi.tags*.name as Set == ['Consignments', 'Logistics'] as Set
    }

    void 'what an Operation annotation declares beyond its summary is described'() {
        when:
        def operation = openApi.paths['/consignments/{number}/letter'].get

        then:
        operation.externalDocs.url == 'https://example.com/letters'
        operation.parameters.find { it.name == 'number' }.description == 'The consignment number'
        operation.parameters.find { it.name == 'number' }.required
        operation.responses['410'].description == 'The letter has been withdrawn'
    }
}

@Entity
class Consignment {
    String reference
}

class ApiError {
    String message
}

class ConsignmentBatch implements Validateable {
    List<Consignment> consignments
}

@Tag(name = 'Consignments', description = 'Moving containers')
@Tag(name = 'Logistics')
@ApiResponse(responseCode = '403', description = 'Permission denied')
@ApiResponse(responseCode = '422', description = 'Could not be processed',
        content = @Content(mediaType = 'application/json', schema = @Schema(implementation = ApiError)))
@SecurityRequirement(name = 'Bearer')
@Artefact('Controller')
class ConsignmentController extends RestfulController<Consignment> {

    ConsignmentController() { super(Consignment) }

    @Parameter(name = 'profile', in = ParameterIn.QUERY, description = 'The customer profile to act for',
            schema = @Schema(implementation = Long), example = '123')
    @ApiResponse(responseCode = '200', description = 'The consignments',
            content = @Content(mediaType = 'application/json',
                    array = @ArraySchema(schema = @Schema(implementation = Consignment))))
    @Override
    Object index(Integer max) { null }

    @ApiResponse(responseCode = '403', description = 'Not your consignment')
    @Override
    Object show() { null }

    @RequestBody(description = 'The consignments to create',
            content = @Content(mediaType = 'application/json', schema = @Schema(implementation = ConsignmentBatch)))
    @Override
    Object save() { null }

    @SecurityRequirement(name = 'Admin')
    @Override
    Object delete() { null }

    @Operation(summary = 'Download the consignment letter',
            externalDocs = @ExternalDocumentation(url = 'https://example.com/letters'),
            parameters = [@Parameter(name = 'number', in = ParameterIn.PATH, description = 'The consignment number')],
            responses = [@ApiResponse(responseCode = '410', description = 'The letter has been withdrawn')])
    @ApiResponse(responseCode = '200', description = 'A PDF letter',
            content = @Content(mediaType = 'application/pdf', schema = @Schema(type = 'string', format = 'binary')))
    def letter() { }
}
