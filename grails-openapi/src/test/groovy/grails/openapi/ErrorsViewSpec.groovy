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

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.ObjectSchema

import grails.artefact.Artefact
import grails.gorm.annotation.Entity
import grails.plugin.json.view.JsonViewConfiguration
import grails.plugin.json.view.mvc.JsonViewResolver
import grails.rest.RestfulController

import spock.lang.Specification
import spock.lang.TempDir

class ErrorsViewSpec extends Specification {

    private static final String ERRORS = '#/components/schemas/ValidationErrors'

    @TempDir
    File views

    void 'describes the errors the converters render, in every data format, without JSON views'() {
        when:
        def openApi = document()

        then:
        errors(openApi, '/kettles')['application/json'].schema.$ref == ERRORS
        errors(openApi, '/kettles')['text/xml'].schema.$ref == ERRORS
        openApi.components.schemas['ValidationErrors'].properties.keySet() == ['errors'] as Set
    }

    void 'describes the errors the converters render in XML as they render them'() {
        when:
        def schema = document().components.schemas['ValidationErrors']
        def error = schema.properties.errors.items

        then: 'an errors element holding an error element for each, not wrapped again'
        schema.xml.name == 'errors'
        !schema.properties.errors.xml.wrapped
        error.xml.name == 'error'

        and: 'naming the object and the field in its attributes'
        error.properties.object.xml.attribute
        error.properties.field.xml.attribute
        !error.properties.message.xml
    }

    void 'describes the errors the errors view renders in JSON, and those the converters render in XML'() {
        given: 'an application generated with JSON views'
        view('object/_object.gson', OBJECT_VIEW)
        view('errors/_errors.gson', ERRORS_VIEW)

        when:
        def openApi = document()

        then: 'the errors view renders the JSON'
        errors(openApi, '/kettles')['application/json'].schema.$ref == ERRORS
        openApi.components.schemas['ValidationErrors'].oneOf.size() == 2

        and: 'the converters the XML, which the schema of the view does not describe'
        with(errors(openApi, '/kettles')['text/xml'].schema) {
            !$ref
            xml.name == 'errors'
        }
    }

    void 'describes no JSON errors where JSON views fall back to the object view, which answers with success'() {
        given: 'JSON views without an errors view, which render the errors with the view for any object'
        view('object/_object.gson', OBJECT_VIEW)

        when:
        def openApi = document()

        then: 'the JSON is not a 422'
        !errors(openApi, '/kettles').containsKey('application/json')

        and: 'the converters render the XML'
        errors(openApi, '/kettles')['text/xml'].schema.$ref == ERRORS
        openApi.components.schemas['ValidationErrors'].properties.keySet() == ['errors'] as Set
    }

    void 'describes no 422 for a controller responding only in JSON where JSON views fall back to the object view'() {
        given:
        view('object/_object.gson', OBJECT_VIEW)

        when:
        def openApi = document()

        then: 'it never answers with 422'
        !openApi.paths['/teapots'].post.responses.containsKey('422')
        openApi.paths['/teapots'].post.responses.containsKey('201')
    }

    void 'describes the errors a controller renders with an errors view of its own without a shape'() {
        given:
        view('object/_object.gson', OBJECT_VIEW)
        view('errors/_errors.gson', ERRORS_VIEW)
        view('kettle/_errors.gson', ERRORS_VIEW)
        view('admin/gizmo/_errors.gson', ERRORS_VIEW)

        when:
        def openApi = document()

        then: 'a controller with its own errors view'
        errors(openApi, '/kettles')['application/json'].schema == null
        errors(openApi, '/kettles')['text/xml'].schema.xml.name == 'errors'

        and: 'a namespaced controller with its own errors view'
        errors(openApi, '/admin/gizmos')['application/json'].schema == null

        and: 'a controller rendering with the errors view'
        errors(openApi, '/sprockets')['application/json'].schema.$ref == ERRORS
    }

    void 'describes the errors with the schema the base document declares for them, in every media type'() {
        given: 'an application rendering its errors another way'
        view('object/_object.gson', OBJECT_VIEW)
        view('kettle/_errors.gson', ERRORS_VIEW)
        File base = new File(views, 'base.yml')
        base.text = """\
            openapi: 3.1.0
            info:
              title: Kettles
              version: 1.0.0
            components:
              schemas:
                ValidationErrors:
                  type: object
                  properties:
                    problems:
                      type: array
                      items:
                        type: string
            """.stripIndent()

        when:
        def openApi = document('grails.openapi.base-document': base.toURI().toString())

        then:
        ['/kettles', '/sprockets'].every { String path ->
            errors(openApi, path).values()*.schema*.$ref.every { it == ERRORS }
        }
        errors(openApi, '/kettles').keySet() == ['application/json', 'text/xml'] as Set
        openApi.components.schemas['ValidationErrors'].properties.keySet() == ['problems'] as Set
    }

    void 'describes the errors under the name of their class where the document describes something else as ValidationErrors'() {
        given: 'a document springdoc started, describing a class of the application named ValidationErrors'
        def openApi = new OpenAPI().components(new Components().addSchemas('ValidationErrors',
                new ObjectSchema().addProperty('count', new IntegerSchema())))

        when:
        generator().contribute(openApi, null)

        then: 'the class keeps its name and its schema'
        openApi.components.schemas['ValidationErrors'].properties.keySet() == ['count'] as Set

        and: 'the errors are described apart from it'
        errors(openApi, '/kettles').values()*.schema*.$ref.every { it == '#/components/schemas/grails.validation.ValidationErrors' }
        openApi.components.schemas['grails.validation.ValidationErrors'].properties.keySet() == ['errors'] as Set
    }

    private static final String OBJECT_VIEW = '''\
        @Field Object object

        json g.render(object)
        '''.stripIndent()

    private static final String ERRORS_VIEW = '''\
        import org.springframework.validation.Errors

        model {
            Errors errors
        }

        json {
            total errors.errorCount
        }
        '''.stripIndent()

    private void view(String path, String template) {
        File file = new File(views, path)
        file.parentFile.mkdirs()
        file.text = template
    }

    private OpenAPI document(Map<String, Object> config = [:]) {
        generator(config).generate()
    }

    private GrailsOpenApiGenerator generator(Map<String, Object> config = [:]) {
        def resolver = new JsonViewResolver(new JsonViewConfiguration(templatePath: views.path))
        def controllers = [KettleController, GizmoController, SprocketController, TeapotController]
        OpenApiFixture.generator(OpenApiFixture.holder {
            '/kettles'(resources: 'kettle')
            '/teapots'(resources: 'teapot')
            '/admin/gizmos'(resources: 'gizmo', namespace: 'admin')
            '/sprockets'(resources: 'sprocket')
        }, OpenApiFixture.application(controllers, [resolver]), OpenApiFixture.context([Kettle]), config)
    }

    private static Content errors(OpenAPI openApi, String path) {
        openApi.paths[path].post.responses['422'].content
    }
}

@Entity
class Kettle {
    String name
}

@Artefact('Controller')
class KettleController extends RestfulController<Kettle> {
    static responseFormats = ['json', 'xml']

    KettleController() { super(Kettle) }
}

@Artefact('Controller')
class GizmoController extends RestfulController<Kettle> {
    static namespace = 'admin'
    static responseFormats = ['json', 'xml']

    GizmoController() { super(Kettle) }
}

@Artefact('Controller')
class TeapotController extends RestfulController<Kettle> {
    static responseFormats = ['json']

    TeapotController() { super(Kettle) }
}

@Artefact('Controller')
class SprocketController extends RestfulController<Kettle> {
    static responseFormats = ['json', 'xml']

    SprocketController() { super(Kettle) }
}
