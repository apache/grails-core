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
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.models.OpenAPI

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.rest.RestfulController

import spock.lang.Specification
import spock.lang.Unroll

/**
 * The success statuses an action declares are the ones it answers with.
 */
class DeclaredResponseSpec extends Specification {

    void 'a success status an action declares replaces the one derived for it'() {
        when: 'a save that queues what it is sent, and answers 200 rather than creating it'
        OpenAPI openApi = OpenApiFixture.document([ParcelDeskController], [Consignee]) {
            '/consignees'(resources: 'parcelDesk')
        }

        then: 'it answers as it declares, rather than 201 with where it created the resource'
        openApi.paths['/consignees'].post.responses.keySet() == ['200', '422'] as Set
        openApi.paths['/consignees'].post.responses['200'].description == 'Queued'
    }

    void 'a success status an action of any controller declares replaces the one derived for it'() {
        when:
        OpenAPI openApi = OpenApiFixture.document([CourierController], []) {
            post '/dispatches'(controller: 'courier', action: 'accept')
            post '/dispatches/batch'(controller: 'courier', action: 'batch')
        }

        then: 'declared on the action'
        openApi.paths['/dispatches'].post.responses.keySet() == ['202'] as Set

        and: 'or in its Operation'
        openApi.paths['/dispatches/batch'].post.responses.keySet() == ['201', '207'] as Set
    }

    void 'an error status an action declares is added to the derived success'() {
        when:
        OpenAPI openApi = OpenApiFixture.document([CourierController], []) {
            post "/dispatches/$id/cancel"(controller: 'courier', action: 'cancel')
        }

        then:
        openApi.paths['/dispatches/{id}/cancel'].post.responses.keySet() == ['200', '404', '409'] as Set
    }

    void 'a success status the controller declares for each of its actions is added to what is derived'() {
        when:
        OpenAPI openApi = OpenApiFixture.document([MirrorController], []) {
            '/mirrors'(controller: 'mirror', action: 'index')
        }

        then:
        openApi.paths['/mirrors'].get.responses.keySet() == ['200', '203'] as Set
    }

    @Unroll
    void 'a success status an action declares keeps the one its controller declares at #path'() {
        when: 'the controller declares the 200 an action would otherwise have derived'
        OpenAPI openApi = OpenApiFixture.document([ForwarderController], []) {
            post '/forwarding/direct'(controller: 'forwarder', action: 'direct')
            post '/forwarding/operation'(controller: 'forwarder', action: 'operation')
        }
        Map responses = openApi.paths[path].post.responses

        then: 'what the controller declares for each action is not taken for what was derived'
        responses.keySet() == ['200', '202'] as Set
        responses['200'].description == 'Completed synchronously'

        where:
        path << ['/forwarding/direct', '/forwarding/operation']
    }
}

@Entity
class Consignee {
    String name
}

@Artefact('Controller')
class ParcelDeskController extends RestfulController<Consignee> {

    ParcelDeskController() { super(Consignee) }

    @ApiResponse(responseCode = '200', description = 'Queued')
    @Override
    Object save() {
        render status: 200
    }
}

@Artefact('Controller')
class CourierController {

    @ApiResponse(responseCode = '202', description = 'Accepted for dispatch')
    def accept() { }

    @Operation(responses = [
            @ApiResponse(responseCode = '201', description = 'All dispatched'),
            @ApiResponse(responseCode = '207', description = 'Some dispatched')])
    def batch() { }

    @ApiResponse(responseCode = '409', description = 'Already dispatched')
    def cancel() { }
}

@ApiResponse(responseCode = '203', description = 'Served from a mirror')
@Artefact('Controller')
class MirrorController {

    def index() { }
}

@ApiResponse(responseCode = '200', description = 'Completed synchronously')
@Artefact('Controller')
class ForwarderController {

    @ApiResponse(responseCode = '202', description = 'Queued')
    def direct() { }

    @Operation(responses = [@ApiResponse(responseCode = '202', description = 'Queued')])
    def operation() { }
}
