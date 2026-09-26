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

import spock.lang.Shared
import spock.lang.Specification

class ValidationAndPatchSpec extends Specification {

    @Shared
    def openApi = OpenApiFixture.document([WidgetController], [Widget, Crate]) {
        '/widgets'(resources: 'widget')
    }

    void 'describes the errors a failed validation answers with'() {
        expect: 'every action that validates what it binds refers to them'
        ['post': '/widgets', 'put': '/widgets/{id}', 'patch': '/widgets/{id}'].every { String method, String path ->
            openApi.paths[path]."${method}".responses['422'].content['application/json'].schema.$ref ==
                    '#/components/schemas/ValidationErrors'
        }

        and: 'they are the errors Grails renders'
        with(openApi.components.schemas[GrailsOpenApiGenerator.VALIDATION_ERRORS_SCHEMA]) {
            required == ['errors']
            properties.errors.items.properties.keySet() == ['object', 'field', 'rejected-value', 'message'] as Set
            properties.errors.items.required as Set == ['object', 'message'] as Set
        }
    }

    void 'requires nothing of a patch, which binds only what it is sent'() {
        expect:
        openApi.paths['/widgets/{id}'].patch.requestBody.content['application/json'].schema.$ref ==
                '#/components/schemas/WidgetPatch'
        openApi.components.schemas['Widget'].required

        and: 'the patch describes the same properties, none of them required'
        with(openApi.components.schemas['WidgetPatch']) {
            required == null
            properties.keySet() == openApi.components.schemas['Widget'].properties.keySet()
            properties.id.readOnly
        }

        and: 'sent in XML as the same element'
        openApi.components.schemas['WidgetPatch'].xml.name == 'widget'


        and: 'a full update still requires what the resource requires'
        openApi.paths['/widgets/{id}'].put.requestBody.content['application/json'].schema.$ref ==
                '#/components/schemas/Widget'
    }
}
