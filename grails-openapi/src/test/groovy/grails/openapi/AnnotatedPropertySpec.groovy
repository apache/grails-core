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

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import io.swagger.v3.oas.annotations.responses.ApiResponse

import grails.artefact.Artefact
import grails.rest.RestfulController
import grails.validation.Validateable

import spock.lang.Specification
import spock.lang.Unroll

/**
 * What the {@code @Schema} of a property, or of a parameter, says of it, as the written document says it.
 */
class AnnotatedPropertySpec extends Specification {

    @Unroll
    void 'a property whose @Schema makes it optional is not required by a defaulted constraint in OpenAPI #version'() {
        when: 'a Validateable declaring no constraints, so each property is constrained nullable: false by default'
        Map schema = written(version, [InventorySummaryController]) {
            '/inventory'(resources: 'inventorySummary')
        }.components.schemas['InventorySummary']

        then: 'a property its annotation makes nullable or not required is not required'
        schema.required == ['allowBooking']

        and: 'the annotation that made a property optional still says it can be null'
        nullable(version, schema.get('properties').stateAbbreviation)
        nullable(version, schema.get('properties').price)
        !nullable(version, schema.get('properties').note)

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }

    @Unroll
    void 'what a property @Schema says of whether it is required wins over its declared constraint in OpenAPI #version'() {
        when:
        Map schema = written(version, [ReservationController]) {
            '/reservations'(resources: 'reservation')
        }.components.schemas['Reservation']

        then: 'required where the annotation says so, though the constraint lets it be null, and not where it is nullable'
        schema.required == ['code']

        and: 'each can still be null where the constraint or the annotation says it can'
        nullable(version, schema.get('properties').code)
        nullable(version, schema.get('properties').guest)
        nullable(version, schema.get('properties').note)

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }

    void 'writes the type a property @Schema declares in an OpenAPI 3.1 document'() {
        when: 'a class that is neither a domain class nor a Validateable, as a response names'
        Map properties = written('openapi_3_1', [BalanceController]) {
            '/balance'(controller: 'balance', action: 'show')
        }.components.schemas['Money'].get('properties')

        then: 'the declared type is written, as the declared types are'
        properties.bare == [type: 'number']
        properties.typed == [type: 'number', format: 'double']
        properties.typedLong == [type: 'integer', format: 'int64']
        properties.types31 == [type: 'number', format: 'double']
    }

    void 'writes the type a property @Schema declares with the null it can be in an OpenAPI 3.1 document'() {
        when:
        Map schemas = written('openapi_3_1', [InventorySummaryController, ReservationController]) {
            '/inventory'(resources: 'inventorySummary')
            '/reservations'(resources: 'reservation')
        }.components.schemas

        then: 'the null the annotation allows'
        schemas['InventorySummary'].get('properties').price == [type: ['number', 'null'], format: 'double']
        schemas['InventorySummary'].get('properties').stateAbbreviation == [type: ['string', 'null'], maxLength: 2]

        and: 'the null the constraint allows'
        schemas['Reservation'].get('properties').seats == [type: ['integer', 'null'], format: 'int32']
    }

    void 'writes the type a property @Schema declares in an OpenAPI 3.0 document'() {
        when:
        Map properties = written('openapi_3_0', [BalanceController]) {
            '/balance'(controller: 'balance', action: 'show')
        }.components.schemas['Money'].get('properties')

        then:
        properties.typed == [type: 'number', format: 'double']
        properties.typedLong == [type: 'integer', format: 'int64']
    }

    @Unroll
    void 'a reference a property @Schema makes nullable is the referenced object or null in OpenAPI #version'() {
        when: 'a Validateable declaring no constraints, and a class that is neither it nor an entity'
        Map schemas = written(version, [RouteController]) {
            '/routes/stop'(controller: 'route', action: 'stop')
            '/routes/leg'(controller: 'route', action: 'leg')
        }.components.schemas
        Map stop = schemas['RouteStop']

        then: 'it is not required'
        !(stop.required ?: []).contains('address')

        and: 'it permits the address or null, where swagger-core wrote a schema nothing satisfies, or none that allowed null'
        nullableReference(version, stop.get('properties').address, [:])
        nullableReference(version, schemas['RouteLeg'].get('properties').destination, [:])

        and: 'what the annotation says of it besides is kept'
        nullableReference(version, schemas['RouteLeg'].get('properties').origin, [description: 'Where the leg starts'])

        and: 'a reference both its annotation and its constraint make nullable is described once'
        nullableReference(version, stop.get('properties').depot, [:])

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }

    @Unroll
    void 'writes the values a property @Schema declares as the type they describe in OpenAPI #version'() {
        when:
        Map properties = written(version, [TariffController]) {
            '/tariff'(controller: 'tariff', action: 'show')
        }.components.schemas['Tariff'].get('properties')

        then: 'numbers and booleans as such, as the declared type describes them'
        properties.level == [type: 'integer', format: 'int32', enum: [1, 2], default: 1]
        properties.rank == [type: 'integer', format: 'int32', enum: [1, 2], default: 2, example: 1]
        properties.ratio == [type: 'number', enum: [1.5, 2.5], default: 1.5, example: 2.5]
        properties.active == [type: 'boolean', default: true, example: false]

        and: 'a string as a string'
        properties.grade == [type: 'string', enum: ['a', 'b'], default: 'a', example: '1']

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }

    @Unroll
    void 'describes a parameter an annotation adds as the type its schema declares in OpenAPI #version'() {
        when:
        Map operation = written(version, [TariffController]) {
            '/tariff/page'(controller: 'tariff', action: 'page')
        }.paths['/tariff/page'].get

        then: 'rather than as a string'
        with(operation.parameters.find { it.name == 'band' }.schema) {
            type == 'integer'
            format == 'int32'
        }
        operation.parameters.find { it.name == 'within' }.schema.items == [type: 'integer', format: 'int64']

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }

    @Unroll
    void 'writes the values a parameter @Schema declares as the type they describe in OpenAPI #version'() {
        when:
        Map operation = written(version, [TariffController]) {
            '/tariff/page'(controller: 'tariff', action: 'page')
        }.paths['/tariff/page'].get

        then:
        operation.parameters.find { it.name == 'band' }.schema ==
                [type: 'integer', format: 'int32', enum: [1, 2], default: 1]

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }

    @Unroll
    void 'writes the values a response @Schema declares as the type they describe in OpenAPI #version'() {
        when:
        Map content = written(version, [TariffController]) {
            '/tariff/band'(controller: 'tariff', action: 'band')
        }.paths['/tariff/band'].get.responses['200'].content

        then:
        content['application/json'].schema == [type: 'integer', format: 'int32', enum: [1, 2], default: 1]

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }

    private static boolean nullableReference(String version, Map property, Map besides) {
        Map reference = ['$ref': '#/components/schemas/RouteAddress']
        version == 'openapi_3_0'
                ? property == [nullable: true, allOf: [reference]] + besides
                : property == [oneOf: [reference, [type: 'null']]] + besides
    }

    private static Map written(String version, List<Class<?>> controllers, Closure mappings) {
        def openApi = OpenApiFixture.document(['springdoc.api-docs.version': version], controllers, [], mappings)
        (Map) new JsonSlurper().parseText(GrailsOpenApiGenerator.serialize(openApi, 'json'))
    }

    private static boolean nullable(String version, Map property) {
        version == 'openapi_3_0' ? property.nullable == true : 'null' in property.type
    }
}

@SchemaAnnotation(name = 'InventorySummary')
class InventorySummaryCommand implements Validateable {

    @SchemaAnnotation(maxLength = 2, nullable = true)
    String stateAbbreviation

    @SchemaAnnotation(type = 'number', format = 'double', nullable = true)
    BigDecimal price

    @SchemaAnnotation(requiredMode = SchemaAnnotation.RequiredMode.NOT_REQUIRED)
    String note

    Boolean allowBooking
}

@Artefact('Controller')
class InventorySummaryController extends RestfulController<InventorySummaryCommand> {
    InventorySummaryController() { super(InventorySummaryCommand) }
}

@SchemaAnnotation(name = 'Reservation')
class ReservationCommand implements Validateable {

    @SchemaAnnotation(requiredMode = SchemaAnnotation.RequiredMode.REQUIRED)
    String code

    @SchemaAnnotation(nullable = true)
    String guest

    String note

    @SchemaAnnotation(type = 'integer', format = 'int32')
    Long seats

    static constraints = {
        code nullable: true
        guest nullable: false
        note nullable: true
        seats nullable: true
    }
}

@Artefact('Controller')
class ReservationController extends RestfulController<ReservationCommand> {
    ReservationController() { super(ReservationCommand) }
}

class Money {

    BigDecimal bare

    @SchemaAnnotation(type = 'number', format = 'double')
    BigDecimal typed

    @SchemaAnnotation(type = 'integer', format = 'int64')
    Long typedLong

    @SchemaAnnotation(types = ['number'], format = 'double')
    BigDecimal types31
}

@Artefact('Controller')
class BalanceController {

    @ApiResponse(responseCode = '200', content = @Content(schema = @SchemaAnnotation(implementation = Money)))
    def show() { }
}

class RouteAddress {
    String street
}

class RouteStop implements Validateable {

    @SchemaAnnotation(nullable = true)
    RouteAddress address

    @SchemaAnnotation(nullable = true)
    RouteAddress depot

    static constraints = {
        depot nullable: true
    }
}

class RouteLeg {

    @SchemaAnnotation(nullable = true)
    RouteAddress destination

    @SchemaAnnotation(nullable = true, description = 'Where the leg starts')
    RouteAddress origin
}

@Artefact('Controller')
class RouteController {

    @ApiResponse(responseCode = '200', content = @Content(schema = @SchemaAnnotation(implementation = RouteStop)))
    def stop() { }

    @ApiResponse(responseCode = '200', content = @Content(schema = @SchemaAnnotation(implementation = RouteLeg)))
    def leg() { }
}

class Tariff {

    @SchemaAnnotation(type = 'integer', format = 'int32', allowableValues = ['1', '2'], defaultValue = '1')
    Integer level

    @SchemaAnnotation(allowableValues = ['1', '2'], defaultValue = '2', example = '1')
    Integer rank

    @SchemaAnnotation(allowableValues = ['1.5', '2.5'], defaultValue = '1.5', example = '2.5')
    BigDecimal ratio

    @SchemaAnnotation(defaultValue = 'true', example = 'false')
    Boolean active

    @SchemaAnnotation(allowableValues = ['a', 'b'], defaultValue = 'a', example = '1')
    String grade
}

@Artefact('Controller')
class TariffController {

    @ApiResponse(responseCode = '200', content = @Content(schema = @SchemaAnnotation(implementation = Tariff)))
    def show() { }

    @Parameter(name = 'band', in = ParameterIn.QUERY,
            schema = @SchemaAnnotation(type = 'integer', format = 'int32', allowableValues = ['1', '2'], defaultValue = '1'))
    @Parameter(name = 'within', in = ParameterIn.QUERY,
            array = @ArraySchema(schema = @SchemaAnnotation(type = 'integer', format = 'int64')))
    def page() { }

    @ApiResponse(responseCode = '200', content = @Content(mediaType = 'application/json',
            schema = @SchemaAnnotation(type = 'integer', format = 'int32', allowableValues = ['1', '2'], defaultValue = '1')))
    def band() { }
}
