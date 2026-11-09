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

import io.swagger.v3.oas.annotations.Operation as OperationAnnotation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.openapi.namespaced.v1.GateController as V1GateController
import grails.openapi.namespaced.v2.GateController as V2GateController
import grails.rest.RestfulController

import spock.lang.Specification

class DocumentSelectionSpec extends Specification {

    private static final Closure MAPPINGS = {
        '/api/v1/crates'(resources: 'crateStack')
        '/api/v1/pallets'(resources: 'pallet')
        '/api/v1/gate'(controller: 'gate', action: 'index', namespace: 'v1')
        '/api/v2/gate'(controller: 'gate', action: 'index', namespace: 'v2')
    }

    void 'describes every reachable action by default'() {
        when:
        def openApi = document([:])

        then:
        openApi.paths.keySet() == ['/api/v1/crates', '/api/v1/crates/{id}', '/api/v1/pallets',
                                   '/api/v1/pallets/{id}', '/api/v1/gate', '/api/v2/gate'] as Set
    }

    void 'selects by path'() {
        when:
        def openApi = document(config)

        then:
        openApi.paths.keySet() == expected as Set

        where:
        config                                                                        || expected
        ['grails.openapi.paths-to-match': '/api/v1/crates/**']                        || ['/api/v1/crates', '/api/v1/crates/{id}']
        ['grails.openapi.paths-to-match[0]': '/api/v2/**']                            || ['/api/v2/gate']
        ['grails.openapi.paths-to-exclude': '/api/v1/**']                             || ['/api/v2/gate']
        ['springdoc.paths-to-match': '/api/v2/**']                                    || ['/api/v2/gate']
    }

    void 'selects by the package of the controller'() {
        when:
        def openApi = document(config)

        then:
        openApi.paths.keySet() == expected as Set

        where:
        config                                                                        || expected
        ['grails.openapi.packages-to-scan': 'grails.openapi.namespaced']              || ['/api/v1/gate', '/api/v2/gate']
        ['grails.openapi.packages-to-scan': 'grails.openapi.namespaced.v2']           || ['/api/v2/gate']
        ['grails.openapi.packages-to-exclude': 'grails.openapi.namespaced']           || ['/api/v1/crates', '/api/v1/crates/{id}',
                                                                                          '/api/v1/pallets', '/api/v1/pallets/{id}']
    }

    void 'selects by the media types an operation produces and consumes'() {
        when:
        def openApi = OpenApiFixture.document(config, [CrateStackController, TicketController, V1GateController],
                [CrateStack, Ticket]) {
            '/crates'(resources: 'crateStack')
            '/tickets'(resources: 'ticket')
            '/gate'(controller: 'gate', action: 'index', namespace: 'v1')
        }

        then:
        operations(openApi) == expected as Set

        where:
        config                                                           || expected
        ['grails.openapi.produces-to-match': 'application/json']         || ['GET /crates', 'POST /crates', 'GET /crates/{id}',
                                                                             'PUT /crates/{id}', 'POST /crates/{id}',
                                                                             'PATCH /crates/{id}', 'DELETE /crates/{id}',
                                                                             'GET /gate']
        ['grails.openapi.produces-to-match': 'text/xml,application/json'] || ['GET /tickets', 'POST /tickets', 'GET /tickets/{id}',
                                                                              'PUT /tickets/{id}', 'POST /tickets/{id}',
                                                                              'PATCH /tickets/{id}', 'DELETE /tickets/{id}']
        ['grails.openapi.consumes-to-match': 'application/json']         || ['POST /crates', 'PUT /crates/{id}', 'POST /crates/{id}',
                                                                             'PATCH /crates/{id}']
        ['grails.openapi.headers-to-match': 'X-Api-Version=1']           || []
        ['springdoc.consumes-to-match': 'application/json,text/xml']     || ['POST /tickets', 'PUT /tickets/{id}', 'POST /tickets/{id}',
                                                                             'PATCH /tickets/{id}']
    }

    void 'a group selects by the media types an operation produces'() {
        given:
        def generator = OpenApiFixture.generator(OpenApiFixture.holder {
            '/crates'(resources: 'crateStack')
            '/tickets'(resources: 'ticket')
        }, OpenApiFixture.application([CrateStackController, TicketController]),
                OpenApiFixture.context([CrateStack, Ticket]),
                ['grails.openapi.groups.xml.produces-to-match[0]': 'application/json',
                 'grails.openapi.groups.xml.produces-to-match[1]': 'text/xml'])

        expect:
        generator.generate('xml').paths.keySet() == ['/tickets', '/tickets/{id}'] as Set
    }

    void 'describes only what is annotated when asked to'() {
        when:
        def openApi = document('grails.openapi.annotated-only': true)

        then: 'an action with an Operation, and every action of a controller with a Tag'
        openApi.paths.keySet() == ['/api/v1/pallets', '/api/v1/pallets/{id}', '/api/v1/crates',
                                   '/api/v1/gate', '/api/v2/gate'] as Set

        and: 'but not the actions of the crate controller that declare nothing'
        openApi.paths['/api/v1/crates'].readOperationsMap().keySet()*.name() == ['GET']
    }

    void 'describes the form actions when asked to'() {
        expect:
        !document([:]).paths.containsKey('/api/v1/crates/create')

        and:
        with(document('grails.openapi.include-form-actions': true).paths) {
            it['/api/v1/crates/create'].get
            it['/api/v1/crates/{id}/edit'].get
        }
    }

    void 'describes each configured group as a document of its own'() {
        given:
        def generator = OpenApiFixture.generator(OpenApiFixture.holder(MAPPINGS),
                OpenApiFixture.application([CrateStackController, PalletController, V1GateController, V2GateController]),
                OpenApiFixture.context([CrateStack, Pallet]),
                ['grails.openapi.groups.gates.paths-to-match'  : '/api/*/gate',
                 'grails.openapi.groups.gates.display-name'    : 'Gates',
                 'grails.openapi.groups.pallets.packages-to-scan': 'grails.openapi',
                 'grails.openapi.groups.pallets.paths-to-exclude': '/api/*/gate,/api/v1/crates/**'])

        expect:
        generator.settings.groups*.group == ['gates', 'pallets']

        and:
        with(generator.generate('gates')) {
            paths.keySet() == ['/api/v1/gate', '/api/v2/gate'] as Set
            info.title == 'Gates'
        }
        generator.generate('pallets').paths.keySet() == ['/api/v1/pallets', '/api/v1/pallets/{id}'] as Set

        and: 'the default document is unaffected'
        generator.generate().paths.size() == 6
    }

    void 'refuses a group that is not configured'() {
        when:
        OpenApiFixture.generator(OpenApiFixture.holder(MAPPINGS)).generate('missing')

        then:
        thrown(IllegalArgumentException)
    }

    void 'describes nothing when disabled'() {
        when:
        def openApi = document('grails.openapi.enabled': false)

        then:
        openApi.paths == null
    }

    private static Set<String> operations(OpenAPI openApi) {
        (openApi.paths ?: [:]).collectMany { String path, PathItem item ->
            item.readOperationsMap().keySet().collect { "${it} ${path}".toString() }
        } as Set<String>
    }

    private static document(Map<String, Object> config) {
        OpenApiFixture.document(config, [CrateStackController, PalletController, V1GateController, V2GateController],
                [CrateStack, Pallet], MAPPINGS)
    }
}

@Entity
class CrateStack {
    String label
}

@Entity
class Pallet {
    String label
}

@Artefact('Controller')
class CrateStackController extends RestfulController<CrateStack> {

    CrateStackController() { super(CrateStack) }

    @OperationAnnotation(summary = 'List the crates')
    @Override
    Object index(Integer max) { null }
}

@Tag(name = 'Pallets')
@Artefact('Controller')
class PalletController extends RestfulController<Pallet> {
    PalletController() { super(Pallet) }
}
