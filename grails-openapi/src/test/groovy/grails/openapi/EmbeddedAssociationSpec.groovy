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

import io.swagger.v3.oas.annotations.media.Schema

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.rest.RestfulController

import spock.lang.Specification

class EmbeddedAssociationSpec extends Specification {

    void 'describes an embedded association in full, since it is part of the resource'() {
        when:
        def openApi = OpenApiFixture.document([DepotController], [Depot]) {
            '/depots'(resources: 'depot')
        }

        then:
        openApi.components.schemas['Depot'].properties.site.$ref == '#/components/schemas/DepotAddress'
        openApi.components.schemas['DepotAddress'].properties.keySet() == ['street', 'city'] as Set
    }

    void 'describes a nullable embedded association as the associated schema or null'() {
        when:
        def openApi = OpenApiFixture.document(['springdoc.api-docs.version': version], [DepotController], [Depot]) {
            '/depots'(resources: 'depot')
        }
        def properties = openApi.components.schemas['Depot'].properties

        then: 'one that cannot be null is the reference itself'
        properties.site.$ref == '#/components/schemas/DepotAddress'

        and: 'one that can be null is the reference or null, as the version says it'
        check(properties.address)

        where:
        version       | check
        'openapi_3_1' | { it.oneOf*.$ref == ['#/components/schemas/DepotAddress', null] && it.oneOf[1].types == ['null'] as Set }
        'openapi_3_0' | { it.allOf*.$ref == ['#/components/schemas/DepotAddress'] && it.nullable }
    }

    void 'describes what is said of a property described by a reference beside it in 3.1, and on all of it in 3.0'() {
        when:
        def openApi = OpenApiFixture.document(['springdoc.api-docs.version': version], [WharfController], [Wharf]) {
            '/wharves'(resources: 'wharf')
        }
        Map written = new JsonSlurper().parseText(GrailsOpenApiGenerator.serialize(openApi, 'json')) as Map
        Map schema = written.components.schemas.Wharf as Map
        Map properties = schema.get('properties') as Map

        then: 'the description of a nullable one'
        properties.address.description == 'Where letters go'
        properties.address.nullable == (version == 'openapi_3_0' ? true : null)

        and: 'the description of one that cannot be null'
        properties.site.description == 'Where it stands'
        properties.site.'$ref' == (version == 'openapi_3_1' ? '#/components/schemas/WharfAddress' : null)

        and: 'one data binding does not bind is read only, so a client is not asked to send it'
        properties.origin.readOnly == true
        'origin' in schema.required

        and: 'each still refers to the associated schema'
        ['address', 'site', 'origin'].every { String name ->
            Map property = properties[name] as Map
            (property.'$ref' ?: (property.allOf ?: property.oneOf)*.'$ref'.find()) == '#/components/schemas/WharfAddress'
        }

        where:
        version << ['openapi_3_0', 'openapi_3_1']
    }
}

@Entity
class Depot {
    String code
    DepotAddress address
    DepotAddress site

    static embedded = ['address', 'site']

    static constraints = {
        address nullable: true
        site nullable: false
    }
}

class DepotAddress {
    String street
    String city
}

@Artefact('Controller')
class DepotController extends RestfulController<Depot> {
    DepotController() { super(Depot) }
}

@Entity
class Wharf {
    // What the databinding transform generates for a domain class compiled in an application.
    public static final List $legacyDatabindingWhiteList = ['code', 'address', 'site']

    String code

    @Schema(description = 'Where letters go')
    WharfAddress address

    @Schema(description = 'Where it stands')
    WharfAddress site

    WharfAddress origin

    static embedded = ['address', 'site', 'origin']

    static constraints = {
        address nullable: true
        site nullable: false
        origin nullable: false, bindable: false
    }
}

class WharfAddress {
    String street
}

@Artefact('Controller')
class WharfController extends RestfulController<Wharf> {
    WharfController() { super(Wharf) }
}
