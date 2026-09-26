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
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.info.Info

import spock.lang.Specification
import spock.lang.TempDir

class BaseDocumentSpec extends Specification {

    @TempDir
    File directory

    void 'starts from the base document'() {
        when:
        def openApi = generator('classpath:openapi/base.yml').generate()

        then: 'what describes the API as a whole comes from the base document'
        openApi.info.title == 'Sales API'
        openApi.info.description == 'Inventory and orders for sales customers.'
        openApi.servers*.url == ['https://api.example.com/v1']
        openApi.security*.keySet().flatten() == ['Bearer']
        openApi.tags.find { it.name == 'orders' }.description == 'Retrieving and creating orders'
        openApi.extensions['x-tagGroups'][0].name == 'Endpoints'
        openApi.components.securitySchemes.Bearer.scheme == 'bearer'

        and: 'what it describes by hand is kept beside what is derived'
        openApi.paths['/login'].post.operationId == 'login'
        openApi.components.schemas.containsKey('Token')
        openApi.paths['/widgets'].get
    }

    void 'describes a path of the base document only in the documents whose paths select it'() {
        given:
        def generator = OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'widget') },
                OpenApiFixture.application([WidgetController]), OpenApiFixture.context([Widget, Crate]),
                ['grails.openapi.base-document'                 : 'classpath:openapi/base.yml',
                 'grails.openapi.groups.widgets.paths-to-match': '/widgets/**'])

        expect:
        generator.generate().paths.containsKey('/login')
        !generator.generate('widgets').paths.containsKey('/login')
        generator.generate('widgets').paths.containsKey('/widgets')
    }

    void 'takes the version the application declares when the base document declares none'() {
        given:
        def application = OpenApiFixture.application([WidgetController])
        application.config.merge([info: [app: [version: '3.4.5', name: 'Warehouse']]])
        def generator = OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'widget') },
                application, null, ['grails.openapi.base-document': 'classpath:openapi/base.yml'])

        expect:
        generator.generate().info.version == '3.4.5'
    }

    void 'names the document after the application without a base document'() {
        given:
        def application = OpenApiFixture.application([WidgetController])
        application.config.merge([info: [app: [version: '3.4.5', name: 'Warehouse']]])

        when:
        def openApi = OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'widget') }, application)
                .generate()

        then:
        openApi.info.title == 'Warehouse'
        openApi.info.version == '3.4.5'
    }

    void 'titles a document springdoc started as the application, as one generated at build time is'() {
        given: 'springdoc starts a document with a placeholder title and version'
        def application = OpenApiFixture.application([WidgetController])
        application.config.merge([info: [app: [version: '3.4.5', name: 'Warehouse']]])
        def generator = OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'widget') }, application)
        def served = new OpenAPI(SpecVersion.V31).info(new Info().title('OpenAPI definition').version('v0'))

        when:
        generator.contribute(served, null)

        then:
        served.info.title == generator.generate().info.title
        served.info.title == 'Warehouse'
        served.info.version == '3.4.5'
    }

    void 'keeps a title a document was given another way'() {
        given:
        def served = new OpenAPI(SpecVersion.V31).info(new Info().title('Sales').version('v0'))

        when:
        OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'widget') },
                OpenApiFixture.application([WidgetController])).contribute(served, null)

        then:
        served.info.title == 'Sales'
        served.info.version == 'v0'
    }

    void 'reads a base document written in JSON'() {
        given:
        File base = new File(directory, 'base.json')
        base.text = '{"openapi": "3.1.0", "info": {"title": "Sales API", "version": "9.9.9"}}'

        expect:
        generator(base.toURI().toString()).generate().info.version == '9.9.9'
    }

    void 'merges the base document into a document springdoc started'() {
        given:
        def openApi = new OpenAPI(SpecVersion.V31).info(new Info().title('OpenAPI definition').version('v0'))

        when:
        generator('classpath:openapi/base.yml').contribute(openApi, null)

        then:
        openApi.info.title == 'Sales API'
        openApi.paths['/login']
        openApi.paths['/widgets']
    }

    void 'fails when the base document does not exist'() {
        when:
        generator('classpath:openapi/missing.yml').generate()

        then:
        IllegalStateException e = thrown()
        e.message.contains('classpath:openapi/missing.yml')
    }

    private static GrailsOpenApiGenerator generator(String location) {
        OpenApiFixture.generator(OpenApiFixture.holder { '/widgets'(resources: 'widget') },
                OpenApiFixture.application([WidgetController]), OpenApiFixture.context([Widget, Crate]),
                ['grails.openapi.base-document': location])
    }
}
