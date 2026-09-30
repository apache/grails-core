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

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.models.OpenAPI

import grails.artefact.Artefact
import grails.validation.Validateable
import grails.web.RequestParameter

import spock.lang.Specification

class ActionParameterSpec extends Specification {

    void 'types a path variable as the action parameter bound from it'() {
        when:
        def openApi = document {
            "/lockers/$id"(controller: 'lockerDesk', action: 'open')
        }

        then:
        with(parameter(openApi, '/lockers/{id}', 'id').schema) {
            OpenApiFixture.typeOf(it) == 'integer'
            it.format == 'int64'
        }
    }

    void 'types the identifier a nested mapping names another resource by as its entity declares it'() {
        when:
        def openApi = OpenApiFixture.document([DrawerController, BinController], [Drawer, Bin]) {
            '/drawers'(resources: 'drawer') {
                '/bins'(resources: 'bin')
            }
        }

        then:
        with(parameter(openApi, '/drawers/{drawerId}/bins', 'drawerId').schema) {
            OpenApiFixture.typeOf(it) == 'integer'
            it.format == 'int64'
        }
    }

    void 'constrains a path variable as the mapping does'() {
        when:
        def openApi = document {
            "/codes/$code/$shelf"(controller: 'lockerDesk', action: 'lookup') {
                constraints {
                    code(matches: /[A-Z]{3}/)
                    shelf(inList: ['top', 'bottom'])
                }
            }
        }

        then: 'the whole segment must match, as Grails matches it'
        parameter(openApi, '/codes/{code}/{shelf}', 'code').schema.pattern == '^(?:[A-Z]{3})$'
        parameter(openApi, '/codes/{code}/{shelf}', 'shelf').schema.enum == ['top', 'bottom']
    }

    void 'names a parameter as the request parameter it is bound from'() {
        when:
        def openApi = document {
            "/lockers/search"(controller: 'lockerDesk', action: 'search')
        }

        then:
        openApi.paths['/lockers/search'].get.parameters*.name == ['q']
    }

    void 'describes a command object bound on a request without a body as the query parameters it binds'() {
        when:
        def openApi = document {
            get "/lockers/find"(controller: 'lockerDesk', action: 'find')
        }
        def parameters = openApi.paths['/lockers/find'].get.parameters

        then: 'each property that a request parameter can carry, by the name Grails binds it by'
        parameters*.name as Set == ['label', 'size', 'tags', 'codeName', 'SKU'] as Set
        parameters.find { it.name == 'codeName' }.required
        with(parameters.find { it.name == 'label' }) {
            required
            schema.maxLength == 10
        }
        OpenApiFixture.typeOf(parameters.find { it.name == 'tags' }.schema) == 'array'

        and: 'no body'
        openApi.paths['/lockers/find'].get.requestBody == null
    }

    void 'describes nothing for a parameter Grails does not bind'() {
        when: 'an interface and Object, which the controller transform leaves unbound'
        def openApi = document {
            post "/lockers/odd"(controller: 'lockerDesk', action: 'odd')
        }

        then:
        !openApi.paths['/lockers/odd'].post.parameters
        openApi.paths['/lockers/odd'].post.requestBody == null
    }

    private static OpenAPI document(Closure mappings) {
        OpenApiFixture.document([LockerDeskController], [], mappings)
    }

    private static parameter(OpenAPI openApi, String path, String name) {
        openApi.paths[path].readOperations().first().parameters.find { it.name == name }
    }
}

class LockerAddress {
    String street
}

class LockerSearch implements Validateable {
    String label
    Integer size
    List<String> tags
    LockerAddress address

    @Schema(name = 'code_name')
    String codeName

    String SKU

    static constraints = {
        label nullable: false, maxSize: 10
        size nullable: true
        tags nullable: true
        address nullable: true
        codeName nullable: false
        SKU nullable: true
    }
}

@Artefact('Controller')
class LockerDeskController {
    def open(Long id) { }
    def lookup(String code, String shelf) { }
    def search(@RequestParameter('q') String query) { }
    def find(LockerSearch search) { }
    def odd(Runnable callback, Object anything) { }
}
