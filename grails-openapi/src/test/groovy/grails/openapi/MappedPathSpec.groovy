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

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.rest.RestfulController

import spock.lang.Specification

class MappedPathSpec extends Specification {

    void 'describes both paths a mapping with an optional variable serves'() {
        when:
        def openApi = OpenApiFixture.document([ParcelController], []) {
            post "/parcels/$relatedId?"(controller: 'parcel', action: 'upload')
        }

        then:
        openApi.paths['/parcels/{relatedId}'].post.parameters*.name == ['relatedId']
        openApi.paths['/parcels'].post.parameters == null

        and: 'each keeps an identifier of its own'
        openApi.paths['/parcels/{relatedId}'].post.operationId != openApi.paths['/parcels'].post.operationId
    }

    void 'does not describe a mapping whose wildcard captures nothing'() {
        when:
        def openApi = OpenApiFixture.document([ParcelController], []) {
            '/'(controller: 'parcel', action: 'notFound')
            '/**'(controller: 'parcel', action: 'notFound')
            '/static/*'(controller: 'parcel', action: 'notFound')
        }

        then: 'the root is a path like any other'
        openApi.paths.keySet() == ['/'] as Set
    }

    void 'describes the parameters an action binds by name'() {
        when:
        def openApi = OpenApiFixture.document([ParcelController], []) {
            "/parcels/$id/track"(controller: 'parcel', action: 'track')
        }
        def parameters = openApi.paths['/parcels/{id}/track'].get.parameters

        then: 'the path variable is described once, as a path parameter'
        parameters.findAll { it.name == 'id' }*.in == ['path']

        and: 'the others are request parameters of their declared type'
        with(parameters.find { it.name == 'carrier' }) {
            it.in == 'query'
            OpenApiFixture.typeOf(schema) == 'string'
        }
        OpenApiFixture.typeOf(parameters.find { it.name == 'limit' }.schema) == 'integer'
    }

    void 'dispatches a mapping that names only the controller to its default action'() {
        when:
        def openApi = OpenApiFixture.document([ParcelBoxController], [ParcelBox]) {
            '/boxes'(controller: 'parcelBox')
        }

        then: 'which is the listing, described as one'
        with(openApi.paths['/boxes'].get) {
            operationId == 'parcelBox_index_get'
            parameters*.name.containsAll(['max', 'offset'])
            OpenApiFixture.typeOf(responses['200'].content['application/json'].schema) == 'array'
        }
    }

    void 'describes a numeric identifier as the integer it is'() {
        when:
        def openApi = OpenApiFixture.document([ParcelBoxController], [ParcelBox]) {
            '/boxes'(resources: 'parcelBox')
        }

        then:
        with(openApi.paths['/boxes/{id}'].get.parameters.find { it.name == 'id' }.schema) {
            OpenApiFixture.typeOf(it) == 'integer'
            format == 'int64'
        }
    }
}

@Artefact('Controller')
class ParcelController {
    def upload() { }
    def notFound() { }
    def track(String carrier, Integer limit, String id) { }
}

@Entity
class ParcelBox {
    String label
}

@Artefact('Controller')
class ParcelBoxController extends RestfulController<ParcelBox> {
    ParcelBoxController() { super(ParcelBox) }
}
