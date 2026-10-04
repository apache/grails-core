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

import io.swagger.v3.oas.models.Operation

import grails.openapi.expanded.v1.ParcelController as V1ParcelController
import grails.openapi.expanded.v2.ParcelController as V2ParcelController

import spock.lang.Specification

class VersionedMappingSpec extends Specification {

    private static final Closure MAPPINGS = {
        '/parcels'(version: '1.0', resources: 'parcel', namespace: 'v1')
        '/parcels'(version: '2.0', resources: 'parcel', namespace: 'v2')
    }

    void 'describes the version answered where none is asked for, and the header that asks for it'() {
        when:
        def openApi = generator([:]).generate()
        Operation listing = openApi.paths['/parcels'].get

        then:
        listing.operationId == 'v2_parcel_index_get'
        with(listing.parameters.find { it.name == 'Accept-Version' }) {
            it.in == 'header'
            !required
            schema.enum == ['2.0']
        }
    }

    void 'a group selects a version by the header that asks for it'() {
        when:
        def openApi = generator(['grails.openapi.groups.v1.headers-to-match': 'Accept-Version=1.0']).generate('v1')
        Operation listing = openApi.paths['/parcels'].get

        then: 'the older version, which is answered only where it is asked for'
        listing.operationId == 'v1_parcel_index_get'
        with(listing.parameters.find { it.name == 'Accept-Version' }) {
            required
            schema.enum == ['1.0']
        }

        and: 'none of the other version'
        openApi.paths.values()*.readOperations().flatten()*.operationId.every { it.startsWith('v1_') }
    }

    void 'a mapping without a version takes no version header'() {
        when:
        def openApi = OpenApiFixture.document([DrawerController], [Drawer]) {
            '/drawers'(resources: 'drawer')
        }

        then:
        !openApi.paths['/drawers'].get.parameters?.any { it.name == 'Accept-Version' }
    }

    private static GrailsOpenApiGenerator generator(Map<String, Object> config) {
        OpenApiFixture.generator(OpenApiFixture.holder(MAPPINGS),
                OpenApiFixture.application([V1ParcelController, V2ParcelController]),
                OpenApiFixture.context([Parcel]), config)
    }
}
