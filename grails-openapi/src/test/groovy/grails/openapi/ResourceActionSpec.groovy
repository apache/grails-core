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

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.models.OpenAPI

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.rest.RestfulController

import spock.lang.Specification

/**
 * The actions RestfulController declares are described by the part each plays in the resource,
 * whoever implements them, and any other action of a resource controller as any controller's is.
 */
class ResourceActionSpec extends Specification {

    void 'describes an action a RestfulController overrides as the action it overrides'() {
        when: 'the controller overrides its listing and its save, as it must to annotate them'
        OpenAPI openApi = hamperDocument()

        then: 'the listing still pages, and lists the resource'
        openApi.paths['/hampers'].get.parameters*.name as Set == ['max', 'offset', 'sort', 'order'] as Set
        openApi.paths['/hampers'].get.responses['200'].content['application/json'].schema.items.$ref ==
                '#/components/schemas/Hamper'

        and: 'the save still creates the resource it is sent, or answers with why it cannot'
        openApi.paths['/hampers'].post.responses.keySet() == ['201', '422'] as Set
        openApi.paths['/hampers'].post.requestBody.content['application/json'].schema.$ref ==
                '#/components/schemas/Hamper'
    }

    void 'withdraws the paging of a listing a RestfulController overrides where it does not page'() {
        when:
        OpenAPI openApi = OpenApiFixture.document([TrunkController], [Hamper]) {
            '/trunks'(resources: 'trunk')
        }

        then:
        !openApi.paths['/trunks'].get.parameters

        and: 'it still lists the resource'
        openApi.paths['/trunks'].get.responses['200'].content['application/json'].schema.items.$ref ==
                '#/components/schemas/Hamper'
    }

    void 'describes an action of its own of a RestfulController as any controller action is'() {
        when:
        OpenAPI openApi = hamperDocument()

        then: 'no resource is guessed at as what it answers with, nor any paging'
        openApi.paths['/hampers/search'].get.responses.keySet() == ['200'] as Set
        openApi.paths['/hampers/search'].get.responses['200'].content == null
        !(openApi.paths['/hampers/search'].get.parameters*.name ?: []).contains('max')

        and: 'nor as what it binds'
        openApi.paths['/hampers/restock'].post.requestBody == null
        openApi.paths['/hampers/restock'].post.responses.keySet() == ['200'] as Set
    }

    void 'reaches an action of its own of a RestfulController at the identifier it declares'() {
        when: 'the default mapping reaches an action that takes an id'
        OpenAPI openApi = hamperDocument()

        then: 'as it would any controller action that takes one'
        openApi.paths['/hamper/label/{id}'].get.responses.keySet() == ['200', '404'] as Set
        !openApi.paths.containsKey('/hamper/label')
    }

    void 'describes the resource actions of a controller generated for a REST application as a RestfulController does'() {
        when:
        OpenAPI openApi = OpenApiFixture.document([SatchelController], [Satchel]) {
            '/satchels'(resources: 'satchel')
            get '/satchels/export'(controller: 'satchel', action: 'export')
        }

        then: 'its resource actions answer as a RestfulController does'
        openApi.paths['/satchels'].get.parameters*.name as Set == ['max', 'offset', 'sort', 'order'] as Set
        openApi.paths['/satchels'].post.responses.keySet() == ['201', '422'] as Set
        openApi.paths['/satchels/{id}'].delete.responses.keySet() == ['204', '404'] as Set

        and: 'an action of its own answers as any controller action does'
        openApi.paths['/satchels/export'].get.responses.keySet() == ['200'] as Set
        openApi.paths['/satchels/export'].get.responses['200'].content == null
    }

    void 'describes where the resource is only where RestfulController itself says so'() {
        when: 'a RestfulController that overrides its save, and inherits its update and patch'
        OpenAPI openApi = hamperDocument()

        then: 'its own update says where the resource it updated is, and so does its patch, which update serves'
        with(openApi.paths['/hampers/{id}'].put.responses['200'].headers['Location']) {
            description == 'The URL of the updated resource'
            schema.format == 'uri'
        }
        openApi.paths['/hampers/{id}'].patch.responses['200'].headers['Location']

        and: 'the save it overrides need not say where it created the resource, as a generated controller does not'
        !openApi.paths['/hampers'].post.responses['201'].headers
    }

    void 'describes where the resource is only where the patch is served by RestfulController\'s own update'() {
        when: 'a RestfulController that overrides its update, which its patch delegates to'
        OpenAPI openApi = OpenApiFixture.document([CaddyController], [Hamper]) {
            '/caddies'(resources: 'caddy')
        }

        then:
        !openApi.paths['/caddies/{id}'].put.responses['200'].headers
        !openApi.paths['/caddies/{id}'].patch.responses['200'].headers

        and: 'its own save still says where it created the resource'
        openApi.paths['/caddies'].post.responses['201'].headers['Location'].description == 'The URL of the created resource'
    }

    void 'describes no location for a controller generated for a REST application, which sends none'() {
        when:
        OpenAPI openApi = OpenApiFixture.document([SatchelController], [Satchel]) {
            '/satchels'(resources: 'satchel')
        }

        then:
        !openApi.paths['/satchels'].post.responses['201'].headers
        !openApi.paths['/satchels/{id}'].put.responses['200'].headers
    }

    private static OpenAPI hamperDocument() {
        OpenApiFixture.document([HamperController], [Hamper]) {
            '/hampers'(resources: 'hamper')
            get '/hampers/search'(controller: 'hamper', action: 'search')
            post '/hampers/restock'(controller: 'hamper', action: 'restock')
            "/$controller/$action?/$id?(.$format)?" {}
        }
    }
}

@Entity
class Hamper {
    String contents
}

@Artefact('Controller')
class HamperController extends RestfulController<Hamper> {

    HamperController() { super(Hamper) }

    @Operation(summary = 'The hampers in stock')
    @Override
    Object index(Integer max) {
        super.index(max)
    }

    @Operation(summary = 'Stocks a hamper')
    @Override
    Object save() {
        super.save()
    }

    def search() { }

    def restock() { }

    def label(Long id) { }
}

@Artefact('Controller')
class TrunkController extends RestfulController<Hamper> {

    TrunkController() { super(Hamper) }

    @Parameter(name = 'max', hidden = true)
    @Parameter(name = 'offset', hidden = true)
    @Parameter(name = 'sort', hidden = true)
    @Parameter(name = 'order', hidden = true)
    @Override
    Object index(Integer max) {
        respond([])
    }
}

@Artefact('Controller')
class CaddyController extends RestfulController<Hamper> {

    CaddyController() { super(Hamper) }

    @Override
    Object update() {
        super.update()
    }
}

@Entity
class Satchel {
    String strap
}

@Artefact('Controller')
class SatchelController {

    static responseFormats = ['json']

    def index(Integer max) { }

    def show(Long id) { }

    def save(Satchel satchel) { }

    def update(Satchel satchel) { }

    def delete(Long id) { }

    def export() { }
}
