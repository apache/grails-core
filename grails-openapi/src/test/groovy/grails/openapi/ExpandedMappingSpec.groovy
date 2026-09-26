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
import grails.gorm.annotation.Entity
import grails.openapi.expanded.v1.ParcelController as V1ParcelController
import grails.openapi.expanded.v2.ParcelController as V2ParcelController
import grails.rest.RestfulController

import spock.lang.Specification

class ExpandedMappingSpec extends Specification {

    void 'describes each controller at the namespace a mapping captures'() {
        when:
        def openApi = OpenApiFixture.document([V1ParcelController, V2ParcelController, DrawerController], [Parcel, Drawer]) {
            "/$namespace/$controller/$action?/$id?(.$format)?" {}
        }

        then:
        openApi.paths['/v1/parcel/index'].get
        openApi.paths['/v2/parcel/show/{id}'].get

        and: 'a controller without a namespace is not reached where the mapping requires one'
        !openApi.paths.keySet().any { it.contains('drawer') }
        !openApi.paths.keySet().any { it.contains('{namespace}') }
    }

    void 'a mapping without the namespace reaches the controller Grails resolves for the name'() {
        when:
        def openApi = OpenApiFixture.document([V1ParcelController, V2ParcelController, DrawerController], [Parcel, Drawer]) {
            "/$controller/$action?/$id?(.$format)?" {}
        }

        then:
        openApi.paths['/drawer/index'].get

        and: 'two controllers of one name are told apart only by a namespace, which this mapping does not give'
        !openApi.paths.containsKey('/parcel/index')
    }

    void 'describes a mapping that accepts any method as the methods the controller allows its action'() {
        when:
        def openApi = OpenApiFixture.document([DrawerController, OrderDeskController], [Drawer]) {
            "/api/drawers"(controller: 'drawer', action: 'save')
            "/orders/submit"(controller: 'orderDesk', action: 'submit')
        }

        then: 'the method RestfulController answers save with, and the one allowedMethods declares'
        operations(openApi) == ['POST /api/drawers', 'POST /orders/submit'] as Set

        and: 'so the body the action binds is described'
        openApi.paths['/orders/submit'].post.requestBody.content['application/json'].schema.$ref ==
                '#/components/schemas/OrderSlip'
    }

    void 'leaves out a method the controller refuses for the action'() {
        when: 'resources maps POST to update, which the controller allows only PUT'
        def openApi = OpenApiFixture.document([CabinetController], [Drawer]) {
            '/cabinets'(resources: 'cabinet')
        }

        then:
        openApi.paths['/cabinets/{id}'].put
        openApi.paths['/cabinets/{id}'].post == null
    }

    void 'describes an action for each method it allows'() {
        when:
        def openApi = OpenApiFixture.document([DrawerController], [Drawer]) {
            "/$controller/$action?/$id?(.$format)?" {}
        }

        then: 'RestfulController allows update both PUT and POST'
        openApi.paths['/drawer/update/{id}'].put
        openApi.paths['/drawer/update/{id}'].post
    }

    void 'describes an action a mapping takes from the path only for the method the mapping answers'() {
        when:
        def openApi = OpenApiFixture.document([DrawerController], [Drawer]) {
            get "/api/$controller/$action?/$id?"()
        }

        then: 'a GET mapping does not reach save or delete, whatever they answer elsewhere'
        openApi.paths['/api/drawer/index'].get
        openApi.paths['/api/drawer/show/{id}'].get
        !openApi.paths.containsKey('/api/drawer/save')
        !openApi.paths.containsKey('/api/drawer/delete/{id}')
    }

    void 'describes each action a mapping takes from the path of a controller it names'() {
        when:
        def openApi = OpenApiFixture.document([TopicController], []) {
            "/topics/$action/$id?"(controller: 'topic')
        }

        then: 'each action, with the identifier where it declares one'
        operations(openApi) == ['GET /topics/latest', 'GET /topics/archive/{id}', 'GET /topics/purge/{id}'] as Set
    }

    void 'describes each action a mapping of a named controller takes from the path for the method it declares'() {
        when:
        def openApi = OpenApiFixture.document([TopicController], []) {
            post "/topics/$action/$id?"(controller: 'topic')
        }

        then: 'each action as the method the mapping answers, not the one a mapping for any method implies'
        operations(openApi) == ['POST /topics/latest', 'POST /topics/archive/{id}', 'POST /topics/purge/{id}'] as Set
    }

    void 'describes the default action where a mapping of a named controller leaves the action optional'() {
        when:
        def openApi = OpenApiFixture.document([DashboardController], []) {
            "/admin/$action?/$id?"(controller: 'dashboard')
        }

        then: 'each action at its own path, and the default action at the path without one'
        operations(openApi) == ['GET /admin/index', 'GET /admin/purge', 'GET /admin'] as Set
    }

    void 'describes the default action at the path of the controller alone where the default mapping leaves the action out'() {
        when:
        def openApi = OpenApiFixture.document([DrawerController, V1ParcelController], [Drawer, Parcel]) {
            "/$controller/$action?/$id?(.$format)?" {}
            "/$namespace/$controller/$action?/$id?" {}
        }

        then: 'Grails answers GET /drawer with the index, as it answers GET /drawer/index'
        openApi.paths['/drawer'].get.operationId == 'drawer_index_get'
        openApi.paths['/drawer/index'].get.operationId == 'drawer_index_get_byAction'

        and: 'nothing after the action is taken without it'
        !openApi.paths.containsKey('/drawer/{id}')

        and: 'a mapping that captures the namespace reaches it at the namespace of the controller'
        openApi.paths['/v1/parcel'].get
        !openApi.paths.containsKey('/{namespace}/drawer')
    }

    void 'describes no default action where the controller declares none'() {
        when:
        def openApi = OpenApiFixture.document([TopicController], []) {
            "/topics/$action?"(controller: 'topic')
        }

        then:
        !openApi.paths.containsKey('/topics')
    }

    void 'describes an action chosen by the method of the request as an operation for each'() {
        when:
        def openApi = OpenApiFixture.document([TopicController], []) {
            "/notes/$id"(controller: 'topic', action: [GET: 'archive', DELETE: 'purge'])
        }

        then:
        openApi.paths['/notes/{id}'].get.operationId == 'topic_archive_get'
        openApi.paths['/notes/{id}'].delete.operationId == 'topic_purge_delete'
    }

    void 'skips a mapping whose action is decided as each request is made'() {
        when:
        def openApi = OpenApiFixture.document([TopicController], []) {
            "/decided"(controller: 'topic', action: { 'latest' })
            "/latest"(controller: 'topic', action: 'latest')
        }

        then:
        operations(openApi) == ['GET /latest'] as Set
    }

    void 'describes a REST controller that is not a RestfulController the way it serves its resource'() {
        when: 'mapped the way a generated REST application maps its controllers'
        def openApi = OpenApiFixture.document([BinController, LibraryPageController], [Bin]) {
            delete "/$controller/$id(.$format)?"(action: 'delete')
            get "/$controller(.$format)?"(action: 'index')
            get "/$controller/$id(.$format)?"(action: 'show')
            post "/$controller(.$format)?"(action: 'save')
            put "/$controller/$id(.$format)?"(action: 'update')
        }

        then: 'the controller that binds a domain class in save and update serves it'
        openApi.paths['/bin'].get.responses['200'].content['application/json'].schema.items.$ref == '#/components/schemas/Bin'
        openApi.paths['/bin'].get.parameters*.name.containsAll(['max', 'offset', 'sort', 'order'])
        openApi.paths['/bin/{id}'].get.responses['200'].content['application/json'].schema.$ref == '#/components/schemas/Bin'
        openApi.paths['/bin'].post.responses.keySet() == ['201', '422'] as Set
        openApi.paths['/bin/{id}'].delete.responses['204']
        openApi.paths['/bin/{id}'].get.parameters.find { it.name == 'id' }.schema.format == 'int64'

        and: 'a controller rendering views for a browser is not described'
        !openApi.paths.keySet().any { it.startsWith('/libraryPage') }
    }

    void 'does not describe a controller rendering HTML that a mapping reaches without naming it'() {
        when:
        def openApi = OpenApiFixture.document([SignInController, BinController], [Bin]) {
            "/$controller/$action?/$id?(.$format)?" {}
        }

        then: 'a controller responding in HTML too renders views for a browser'
        !openApi.paths.keySet().any { it.startsWith('/signIn') }

        and: 'one responding in data formats alone is a REST controller'
        openApi.paths.keySet().any { it.startsWith('/bin') }
    }

    void 'decides each action by its own entry where responseFormats is a map'() {
        when:
        def openApi = OpenApiFixture.document([KioskController], []) {
            "/$controller/$action?/$id?(.$format)?" {}
        }

        then: 'the actions responding in data formats alone are described, the default one also at the controller path'
        openApi.paths['/kiosk/index'].get
        openApi.paths['/kiosk'].get
        openApi.paths['/kiosk/status'].get

        and: 'one responding in HTML too, or declaring nothing, renders views for a browser'
        !openApi.paths.containsKey('/kiosk/page')
        !openApi.paths.containsKey('/kiosk/help')
    }

    void 'reads responseFormats as respond does, which takes a list and not an array'() {
        when:
        def openApi = OpenApiFixture.document([ScreenController], []) {
            "/$controller/$action?/$id?(.$format)?" {}
        }

        then: 'respond answers in any format the application configures, HTML too, so it is not a REST controller'
        !openApi.paths.keySet().any { it.startsWith('/screen') }
    }

    void 'does not describe a mapping to a controller the application does not have'() {
        when:
        def openApi = OpenApiFixture.document([TopicController], []) {
            "/missing"(controller: 'nowhere', action: 'index')
            "/latest"(controller: 'topic', action: 'latest')
        }

        then:
        operations(openApi) == ['GET /latest'] as Set
    }

    private static Set<String> operations(OpenAPI openApi) {
        (openApi.paths ?: [:]).collectMany { String path, PathItem item ->
            item.readOperationsMap().keySet().collect { "${it} ${path}".toString() }
        } as Set<String>
    }
}

@Entity
class Parcel {
    String label
}

@Entity
class Drawer {
    String label
}

@Artefact('Controller')
class DrawerController extends RestfulController<Drawer> {
    DrawerController() { super(Drawer) }
}

class OrderSlip {
    String reference
}

@Artefact('Controller')
class OrderDeskController {

    static allowedMethods = [submit: 'POST']

    def submit(OrderSlip slip) { }
}

@Artefact('Controller')
class CabinetController extends RestfulController<Drawer> {

    static allowedMethods = [save: 'POST', update: 'PUT', patch: 'PATCH', delete: 'DELETE']

    CabinetController() { super(Drawer) }
}

@Artefact('Controller')
class DashboardController {
    def index() { }
    def purge() { }
}

@Artefact('Controller')
class SignInController {
    static responseFormats = ['html', 'json']

    def index() { }
    def auth() { }
    def denied() { }
}

@Artefact('Controller')
class KioskController {
    static responseFormats = [index: ['json'], status: ['xml'], page: ['html', 'json']]

    def index() { }
    def status() { }
    def page() { }
    def help() { }
}

@Artefact('Controller')
class ScreenController {
    static responseFormats = ['json'] as String[]

    def index() { }
}

@Artefact('Controller')
class TopicController {
    def latest() { }
    def archive(Long id) { }
    def purge(Long id) { }
}

@Entity
class Bin {
    String label
}

/**
 * The shape of a controller the rest-api profile generates.
 */
@Artefact('Controller')
class BinController {

    static responseFormats = ['json', 'xml']
    static allowedMethods = [save: 'POST', update: 'PUT', delete: 'DELETE']

    def index(Integer max) { }
    def show(Serializable id) { }
    def save(Bin bin) { }
    def update(Bin bin) { }
    def delete(Serializable id) { }
}

@Artefact('Controller')
class LibraryPageController {
    def index() { }
    def show(Long id) { }
}
