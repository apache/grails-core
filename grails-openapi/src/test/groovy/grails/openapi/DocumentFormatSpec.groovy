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

import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Yaml

import spock.lang.Specification

class DocumentFormatSpec extends Specification {

    void 'describes OpenAPI 3.1 by default, as springdoc does'() {
        when:
        def openApi = generator([:]).generate()

        then:
        openApi.openapi == '3.1.0'
        GrailsOpenApiGenerator.serialize(openApi, 'yaml').startsWith('openapi: 3.1.0')
    }

    void 'describes OpenAPI 3.0 when springdoc is configured for it'() {
        when:
        def openApi = generator(['springdoc.api-docs.version': 'openapi_3_0']).generate()

        then:
        openApi.openapi == '3.0.1'
        openApi.components.schemas['Widget'].properties.name.type == 'string'
    }

    void 'writes what each version says in the form that version reads'() {
        when:
        Map written = Json.mapper().readValue(GrailsOpenApiGenerator.serialize(generator(config).generate(), 'json'), Map)
        Map widget = (Map) ((Map) ((Map) written.components).schemas).Widget
        Map properties = (Map) widget.get('properties')

        then: 'a value that may be null, and a type'
        properties.weight.type == weightType
        properties.weight.nullable == nullable

        and: 'the identifier path variable, and an association reference'
        ((Map) ((List) ((Map) ((Map) written.paths)['/widgets/{id}']).get.parameters).find { it.name == 'id' }).schema.type == 'integer'
        properties.crate.type == crateType

        where:
        config                                        || weightType          | nullable | crateType
        [:]                                           || ['number', 'null']  | null     | ['object', 'null']
        ['springdoc.api-docs.version': 'openapi_3_0'] || 'number'            | true     | 'object'
    }

    void 'writes the document as YAML or JSON'() {
        given:
        def openApi = generator(['springdoc.api-docs.version': 'openapi_3_0']).generate()

        when:
        def yaml = Yaml.mapper().readValue(GrailsOpenApiGenerator.serialize(openApi, 'yaml'), Map)
        def json = Json.mapper().readValue(GrailsOpenApiGenerator.serialize(openApi, 'json'), Map)

        then:
        yaml == json
        json.paths.containsKey('/widgets/{id}')
    }

    private static GrailsOpenApiGenerator generator(Map<String, Object> config) {
        OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'widget') },
                OpenApiFixture.application([WidgetController]), OpenApiFixture.context([Widget, Crate]), config)
    }
}
