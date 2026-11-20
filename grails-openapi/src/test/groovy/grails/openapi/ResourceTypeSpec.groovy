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

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import io.swagger.v3.oas.annotations.responses.ApiResponse

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.openapi.namespaced.v1.GateController as V1GateController
import grails.openapi.namespaced.v2.GateController as V2GateController
import grails.rest.RestfulController
import grails.validation.Validateable

import spock.lang.Specification

class ResourceTypeSpec extends Specification {

    void 'describes the resource a RestfulController declares, not a domain class of the same name'() {
        when: 'the controller serves a command, and a domain class happens to share its name'
        def openApi = OpenApiFixture.document([ShipmentController], [Shipment]) {
            '/shipments'(resources: 'shipment')
        }

        then: 'the command is what the operations describe'
        openApi.paths['/shipments/{id}'].get.responses['200'].content['application/json'].schema.$ref ==
                '#/components/schemas/ShipmentView'
        openApi.paths['/shipments'].post.requestBody.content['application/json'].schema.$ref ==
                '#/components/schemas/ShipmentView'

        and: 'the domain class is not published'
        !openApi.components.schemas.containsKey('Shipment')
    }

    void 'keeps the properties a command renames the description of'() {
        when:
        def openApi = OpenApiFixture.document([ShipmentController], [Shipment]) {
            '/shipments'(resources: 'shipment')
        }

        then: 'a rename of the description is kept, and a Jackson rename, which Grails binds without, is not'
        with(openApi.components.schemas['ShipmentView'].properties) {
            keySet() == ['shipment_number', 'carrier', 'depot', 'label'] as Set
            shipment_number.description == 'Renamed through the schema'
        }

        and: 'the constraints still apply to the property they are declared for'
        openApi.components.schemas['ShipmentView'].properties.depot.maxLength == 7
        openApi.components.schemas['ShipmentView'].required == ['depot']

        and: 'a property that can only be read is not asked of a request'
        openApi.components.schemas['ShipmentView'].properties.label.readOnly
    }

    void 'describes a class that is neither a domain class nor a command object by the names Grails renders it by'() {
        when: 'a class an action names as its response, which Grails renders'
        def openApi = OpenApiFixture.document([WaybillController], []) {
            '/waybills/latest'(controller: 'waybill', action: 'latest')
        }

        then: 'a rename of the description is kept, and a Jackson rename, which the converters ignore, is not'
        with(openApi.components.schemas['WaybillSummary'].properties) {
            keySet() == ['waybill_number', 'carrier'] as Set
            waybill_number.description == 'Renamed through the schema'
        }
    }

    void 'describes no response type for a controller that declares none'() {
        when: 'a controller that is not a RestfulController shares its name with a domain class'
        def openApi = OpenApiFixture.document([ShipmentPhotoController], [ShipmentPhoto]) {
            '/photos'(controller: 'shipmentPhoto', action: 'index')
        }

        then: 'the domain class is not guessed at'
        openApi.paths['/photos'].get.responses['200'].content == null
        openApi.components == null
    }

    void 'describes the controller of each namespace at its own route'() {
        when:
        def openApi = OpenApiFixture.document([V1GateController, V2GateController], []) {
            '/api/v1/gate'(controller: 'gate', action: 'index', namespace: 'v1')
            '/api/v2/gate'(controller: 'gate', action: 'index', namespace: 'v2')
        }

        then:
        openApi.paths['/api/v1/gate'].get.summary == 'v1 gate'
        openApi.paths['/api/v2/gate'].get.summary == 'v2 gate'

        and: 'the operations are told apart by their namespace'
        openApi.paths['/api/v1/gate'].get.operationId == 'v1_gate_index_get'
        openApi.paths['/api/v2/gate'].get.operationId == 'v2_gate_index_get'
    }

    void 'reaches the only controller of a name from a mapping that names no namespace'() {
        when:
        def openApi = OpenApiFixture.document([V2GateController], []) {
            '/gate'(controller: 'gate', action: 'index')
        }

        then:
        openApi.paths['/gate'].get.summary == 'v2 gate'
    }
}

class WaybillSummary {

    @SchemaAnnotation(name = 'waybill_number', description = 'Renamed through the schema')
    String number

    @JsonProperty('carrier_name')
    String carrier
}

@Artefact('Controller')
class WaybillController {

    @ApiResponse(responseCode = '200', content = @Content(schema = @SchemaAnnotation(implementation = WaybillSummary)))
    def latest() { }
}

@Entity
class Shipment {
    String internalRouting
}

@SchemaAnnotation(name = 'ShipmentView')
class ShipmentCommand implements Validateable {

    @SchemaAnnotation(name = 'shipment_number', description = 'Renamed through the schema')
    String number

    @JsonProperty('carrier_name')
    String carrier

    String depot

    String getLabel() {
        "${depot}-${number}"
    }

    static constraints = {
        number nullable: true
        carrier nullable: true
        depot nullable: false, maxSize: 7
    }
}

@Artefact('Controller')
class ShipmentController extends RestfulController<ShipmentCommand> {
    ShipmentController() { super(ShipmentCommand) }
}

@Entity
class ShipmentPhoto {
    String path
}

@Artefact('Controller')
class ShipmentPhotoController {
    def index() { }
}
