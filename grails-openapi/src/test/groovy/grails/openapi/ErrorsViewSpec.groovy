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

import groovy.json.JsonSlurper

import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.MockHttpOutputMessage
import org.springframework.validation.BeanPropertyBindingResult

import grails.artefact.Artefact
import grails.rest.render.errors.ValidationProblemDetailFactory
import grails.gorm.annotation.Entity
import grails.plugin.json.view.JsonViewConfiguration
import grails.plugin.json.view.mvc.JsonViewResolver
import grails.rest.RestfulController

import spock.lang.Specification
import spock.lang.TempDir

class ErrorsViewSpec extends Specification {

    private static final String ERRORS = '#/components/schemas/ValidationErrors'

    private static final String PROBLEM = '#/components/schemas/ValidationProblem'

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

    void 'describes the errors the converters render where JSON views have no errors view, which the object view does not render'() {
        given: 'JSON views without an errors view'
        view('object/_object.gson', OBJECT_VIEW)

        when:
        def openApi = document()

        then: 'the JSON and the XML are the 422 of the converters'
        errors(openApi, '/kettles')['application/json'].schema.$ref == ERRORS
        errors(openApi, '/kettles')['text/xml'].schema.$ref == ERRORS
        openApi.components.schemas['ValidationErrors'].properties.keySet() == ['errors'] as Set
    }

    void 'describes the 422 of a controller responding only in JSON where JSON views have no errors view'() {
        given:
        view('object/_object.gson', OBJECT_VIEW)

        when:
        def openApi = document()

        then:
        errors(openApi, '/teapots')['application/json'].schema.$ref == ERRORS
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

    void 'describes the errors of a JSON client as the problem respond writes with grails.web.rendering.json.spring'() {
        when:
        def openApi = document('grails.web.rendering.json.spring': true)
        def problem = openApi.components.schemas['ValidationProblem']

        then: 'respond answers a client asking for any JSON type with the problem'
        errors(openApi, '/kettles').keySet() == ['application/problem+json', 'text/xml'] as Set
        errors(openApi, '/kettles')['application/problem+json'].schema.$ref == PROBLEM
        problem.properties.keySet() == ['type', 'title', 'status', 'detail', 'instance', 'errors'] as Set
        problem.required as Set == ['title', 'status', 'detail', 'errors'] as Set
        problem.properties.errors.items.properties.keySet() == ['object', 'field', 'rejectedValue', 'codes', 'message'] as Set

        and: 'the converters keep rendering the XML'
        errors(openApi, '/kettles')['text/xml'].schema.$ref == ERRORS
        openApi.components.schemas['ValidationErrors'].properties.errors.items.properties.containsKey('rejected-value')
    }

    void 'the problem schema describes the problem respond writes for validation errors, as Spring writes it'() {
        given: 'validation errors as respond writes them, with the instance it gives them'
        def errors = new BeanPropertyBindingResult(new Kettle(name: ''), 'kettle')
        errors.rejectValue('name', 'blank', 'Name cannot be blank')
        def problem = new ValidationProblemDetailFactory().create(errors)
        problem.instance = URI.create('/kettles')
        def written = new MockHttpOutputMessage()
        new JacksonJsonHttpMessageConverter().write(problem, MediaType.APPLICATION_PROBLEM_JSON, written)
        Map json = (Map) new JsonSlurper().parseText(written.bodyAsString)

        when:
        def schema = document('grails.web.rendering.json.spring': true).components.schemas['ValidationProblem']
        def error = schema.properties.errors.items

        then: 'every member written is described, and every one required is written'
        schema.properties.keySet().containsAll(json.keySet())
        json.keySet().containsAll(schema.required)
        error.properties.keySet().containsAll(((Map) json.errors[0]).keySet())
        ((Map) json.errors[0]).keySet().containsAll(error.required)
    }

    void 'describes the 422 of a controller responding only in JSON as the problem with grails.web.rendering.json.spring'() {
        when:
        def openApi = document('grails.web.rendering.json.spring': true)

        then:
        errors(openApi, '/teapots').keySet() == ['application/problem+json'] as Set
        errors(openApi, '/teapots')['application/problem+json'].schema.$ref == PROBLEM
    }

    void 'describes the errors the JSON converter writes with grails.converters.json.legacy false'() {
        when:
        def openApi = document('grails.converters.json.legacy': false)

        then: 'with their codes and without the rejected value'
        errors(openApi, '/kettles')['application/json'].schema.$ref == '#/components/schemas/JsonValidationErrors'
        openApi.components.schemas['JsonValidationErrors'].properties.errors.items.properties.keySet() ==
                ['object', 'field', 'codes', 'message'] as Set

        and: 'the converters keep rendering the XML as Grails 8 did'
        errors(openApi, '/kettles')['text/xml'].schema.$ref == ERRORS
    }

    void "settings a generator is created without describe the errors as the converter writes them by default"() {
        expect: 'the default of grails.converters.json.legacy, as the converter reads it, whatever the JVM is started with'
        new OpenApiSettings().legacyJson == OpenApiSettings.from(new MockEnvironment()).legacyJson
    }

    void "the errors view and a controller's own view still render the JSON with grails.web.rendering.json.spring"() {
        given:
        view('object/_object.gson', OBJECT_VIEW)
        view('errors/_errors.gson', ERRORS_VIEW)
        view('kettle/_errors.gson', ERRORS_VIEW)

        when:
        def openApi = document('grails.web.rendering.json.spring': true)

        then:
        errors(openApi, '/kettles')['application/json'].schema == null
        errors(openApi, '/sprockets')['application/json'].schema.$ref == ERRORS
        !openApi.components.schemas.containsKey('ValidationProblem')
    }

    void "the base document's ValidationErrors describes the errors in every media type, also with grails.web.rendering.json.spring"() {
        given:
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
        def openApi = document('grails.openapi.base-document': base.toURI().toString(), 'grails.web.rendering.json.spring': true)

        then:
        errors(openApi, '/kettles').keySet() == ['application/json', 'text/xml'] as Set
        errors(openApi, '/kettles').values()*.schema*.$ref.every { it == ERRORS }
    }

    void 'describes the problem under its qualified name where the document already describes something else as ValidationProblem'() {
        given:
        def openApi = new OpenAPI().components(new Components().addSchemas('ValidationProblem',
                new ObjectSchema().addProperty('count', new IntegerSchema())))

        when:
        generator('grails.web.rendering.json.spring': true).contribute(openApi, null)

        then:
        openApi.components.schemas['ValidationProblem'].properties.keySet() == ['count'] as Set
        errors(openApi, '/kettles')['application/problem+json'].schema.$ref == '#/components/schemas/grails.validation.ValidationProblem'
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
