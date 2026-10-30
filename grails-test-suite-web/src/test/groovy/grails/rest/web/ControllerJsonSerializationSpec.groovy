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
package grails.rest.web

import com.fasterxml.jackson.annotation.JsonIgnore
import grails.artefact.Artefact
import grails.gorm.MultiTenant
import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.testing.gorm.DomainUnitTest
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.AbstractView
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.plugins.web.rest.render.SpringMessageConverters
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import grails.converters.JSON
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.web.mime.MimeType
import grails.rest.render.json.JsonRenderer
import grails.testing.web.controllers.ControllerUnitTest
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.mock.http.MockHttpOutputMessage
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.DatabindException
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonSerialize
import spock.lang.Specification

class ControllerJsonSerializationSpec extends Specification implements ControllerUnitTest<JsonResponseController>,
        DomainUnitTest<JsonResponseDomain> {

    Set<String> getIncludePlugins() {
        ['core'] as Set<String>
    }

    void 'web serialization keeps the curated plugin graph with explicit plugin selection'() {
        expect:
        applicationContext.getBean(grails.plugins.GrailsPluginManager).allPlugins*.name as Set ==
                ['core', 'restResponder'] as Set
        grailsApplication.getArtefacts('UrlMappings').length == 0
        applicationContext.containsBean('grailsXmlRenderer')
        applicationContext.containsBean('namedJsonConfigurationRegistry')
        applicationContext.getBean(JsonNamingCustomizer).appliedCustomizer.is(
                applicationContext.getBean(GrailsJsonMapperCustomizer))
    }

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class JsonConfiguration {

        @Bean
        JsonNamingCustomizer jsonNamingCustomizer() {
            new JsonNamingCustomizer()
        }

        @Bean
        JsonRenderer<JsonRendererBeanBody> jsonRendererBeanBodyRenderer() {
            new JsonRenderer<JsonRendererBeanBody>(JsonRendererBeanBody)
        }

        @Bean
        JsonRenderer<JsonRendererCredentials> jsonRendererCredentialsRenderer() {
            new JsonRenderer<JsonRendererCredentials>(JsonRendererCredentials).tap { excludes = ['password'] }
        }

        @Bean
        JsonRenderer<JsonRendererProfile> jsonRendererProfileRenderer() {
            new JsonRenderer<JsonRendererProfile>(JsonRendererProfile)
        }
    }

    void 'test mapper honors customizers from nested configuration classes'() {
        given:
        response.format = 'json'

        when:
        controller.respond(new JsonResponseBody(title: 'Grails', firstName: 'Ada'))

        then:
        response.json == [title: 'Grails', first_name: 'Ada']
    }

    void 'a value a serializer writes with writePOJO is written with the Grails mapper, as the value it is nested in'() {
        given: 'a bean whose serializer writes a domain class instance with JsonGenerator.writePOJO'
        def book = new JsonResponseDomain(title: 'Grails').save()

        when:
        controller.respond(new PojoWritingBody(book: book))

        then: 'as respond writes the instance itself'
        response.json == [book: [id: book.id]]
    }

    void 'an object a serializer writes with writePOJO is found when it repeats one the response is writing'() {
        given: 'an owner whose holder is written by a serializer that writes the owner back with writePOJO'
        def owner = new PojoCycleOwner(name: 'first')
        owner.holder = new PojoCycleHolder(owner: owner)

        when:
        controller.respond(owner)

        then: 'the owner is written once, and referenced where it repeats, as render json: writes it'
        response.json == [holder: [owner: [_ref: '..', class: PojoCycleOwner.name]], name: 'first']
    }

    void 'a named JSON write that fails part-way leaves the response uncommitted, so that the error page applies'() {
        given:
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('plain') { }

        when: 'render json:'
        controller.render(json: new FailingJsonBody(title: 'Grails'))

        then:
        thrown(DatabindException)
        !response.committed

        when: 'respond with a named configuration, which reports it as respond does without one, as Spring MVC does'
        response.reset()
        response.format = 'json'
        controller.respond(new FailingJsonBody(title: 'Grails'), jsonConfiguration: 'plain')

        then:
        HttpMessageNotWritableException e = thrown()
        e.message.startsWith('Could not write JSON: ')
        e.cause instanceof DatabindException
        !response.committed
    }

    void 'a respond Spring MVC writes that fails part-way leaves the response uncommitted, so that the error page applies, for #description'() {
        when:
        controller.respond(value)

        then:
        thrown(HttpMessageNotWritableException)
        !response.committed

        where:
        description | value
        'a bean'    | new FailingJsonBody(title: 'Grails')
        'a problem' | ProblemDetail.forStatus(409).tap { it.setProperty('unreadable', new FailingJsonBody(title: 'Grails')) }
    }

    void 'a domain class response leaves out the tenant id GORM ignores, whatever name the naming strategy gives it'() {
        given: 'a multi-tenant domain class, whose tenantId GORM names in the @JsonIgnoreProperties it adds'
        mockDomain(TenantResponseDomain)
        def tenanted = new TenantResponseDomain(name: 'first', tenantId: 'acme')
        tenanted.id = 1

        when: 'the mapper writes property names in snake case'
        controller.respond(tenanted)

        then:
        response.json == [id: 1, name: 'first']
    }

    void 'a domain class response leaves out a @JsonIgnore property, as the shared Boot mapper does'() {
        given:
        def book = new JsonResponseDomain(title: 'Grails').save()

        when:
        controller.respond(book)

        then:
        response.json == [id: book.id]

        and:
        def mapper = applicationContext.getBean(JsonMapper)
        !mapper.readValue(mapper.writeValueAsString(book), Map).containsKey('title')
    }

    void setup() {
        response.format = 'json'
    }

    void 'respond uses real Jackson conversion for strings, GStrings and bytes'() {
        when:
        controller.respond(value)

        then:
        response.contentAsString == expected

        where:
        value                         | expected
        'ok'                          | '"ok"'
        "Saved ${'Grails'}"            | '"Saved Grails"'
        [message: "Saved ${'Grails'}"] | '{"message":"Saved Grails"}'
        [65, 66] as byte[]             | '"QUI="'
    }

    void 'respond errors uses the production problem details shape'() {
        given:
        def errors = new BeanPropertyBindingResult(new JsonResponseBody(title: 'private'), 'book')
        errors.rejectValue('title', 'invalid', 'Title is invalid')

        when:
        controller.respond(errors)

        then:
        response.status == 422
        response.contentType == 'application/problem+json'
        response.json.status == 422
        response.json.errors.first().message == 'Title is invalid'
        !response.json.errors.first().containsKey('rejectedValue')
        !response.json.containsKey('properties')
    }

    void 'validation errors are written as a problem whatever configuration respond names, which must be registered'() {
        given:
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('plain') { }
        def errors = new BeanPropertyBindingResult(new JsonResponseBody(title: 'private'), 'book')
        errors.rejectValue('title', 'invalid', 'Title is invalid')

        when:
        controller.respond(errors, jsonConfiguration: 'plain')

        then:
        response.status == 422
        response.contentType == 'application/problem+json'
        response.json.status == 422
        response.json.errors.first().message == 'Title is invalid'

        when: 'the errors of a value'
        response.reset()
        response.format = 'json'
        controller.respond(new JsonValidatedBody(title: 'private', errors: errors), jsonConfiguration: 'plain')

        then:
        response.status == 422
        response.contentType == 'application/problem+json'
        response.json.errors.first().message == 'Title is invalid'

        when: 'a configuration that is not registered'
        response.reset()
        response.format = 'json'
        controller.respond(new JsonValidatedBody(title: 'private', errors: errors), jsonConfiguration: 'missing')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Named JSON configuration [missing] is not registered.'
    }

    void 'includes and excludes leave a problem as Spring MVC writes it'() {
        given:
        def errors = new BeanPropertyBindingResult(new JsonResponseBody(title: 'private'), 'book')
        errors.rejectValue('title', 'invalid', 'Title is invalid')

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.CONFLICT, 'Taken'), excludes: ['title', 'detail'])

        then:
        response.status == 409
        response.json.title == 'Conflict'
        response.json.detail == 'Taken'

        when: 'the problem respond makes of validation errors'
        response.reset()
        response.format = 'json'
        controller.respond(errors, includes: [])

        then:
        response.status == 422
        response.json.status == 422
        response.json.title
        response.json.errors.first().message == 'Title is invalid'
    }

    void 'respond of an explicit problem preserves its HTTP semantics'() {
        given:
        request.requestURI = '/books'
        def problem = org.springframework.http.ProblemDetail.forStatus(422)

        when:
        controller.respond(problem)

        then:
        response.status == 422
        response.contentType == 'application/problem+json'
        response.json.status == 422
        response.json.instance == '/books'
    }

    void 'a status respond is given decides the status of a problem response, and the problem keeps its own'() {
        when:
        String output = outputWhile { controller.respond(org.springframework.http.ProblemDetail.forStatus(409), status: 400) }

        then:
        response.status == 400
        response.contentType == 'application/problem+json'
        response.json.status == 409

        and: 'Spring MVC reports the mismatch once, as for a ResponseEntity'
        output.readLines().count { it.contains("but its status doesn't match the ProblemDetail status: 409") } == 1
    }

    void 'a status respond is given that has no HttpStatus constant is sent, as a ResponseEntity sends it, for #description'() {
        when:
        controller.respond(value, status: 499)

        then:
        response.status == 499

        where:
        description | value
        'a problem' | org.springframework.http.ProblemDetail.forStatus(409)
        'a value'   | [title: 'Grails']
    }

    private static String outputWhile(Closure<?> work) {
        PrintStream original = System.out
        def buffer = new ByteArrayOutputStream()
        System.setOut(new PrintStream(buffer, true))
        try {
            work.call()
        }
        finally {
            System.setOut(original)
        }
        buffer.toString()
    }

    void 'a problem a client asks for as #format is written as problem JSON, as Spring MVC writes it'() {
        given:
        request.requestURI = '/books'
        response.format = format

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatus(409))

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.status == 409
        response.json.instance == '/books'

        where:
        format << ['html', 'csv']
    }

    void 'without jackson-dataformat-xml a client that asks for a problem in XML is answered in JSON, as Spring MVC answers it'() {
        given: 'grails-xml without the library Spring MVC needs for its XML converter'
        assert !org.springframework.util.ClassUtils.isPresent('tools.jackson.dataformat.xml.XmlMapper', getClass().classLoader)
        response.format = null
        request.addHeader('Accept', 'application/problem+xml')

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.CONFLICT, 'taken'), formats: ['xml', 'json'])

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.detail == 'taken'
    }

    void 'a client whose Accept header Spring MVC cannot read is answered with the status alone for #description, and as in Grails 8 for any other value'() {
        given: 'a quality value above 1'
        response.format = null
        request.addHeader('Accept', 'application/json;q=2')

        when:
        controller.respond(value, formats: ['json'])

        then:
        response.status == status
        response.contentAsString == body

        where:
        description | value                                                                                      || status | body
        'a problem' | org.springframework.http.ProblemDetail.forStatus(org.springframework.http.HttpStatus.CONFLICT) || 409    | ''
        'a map'     | [title: 'Grails']                                                                          || 200    | '{"title":"Grails"}'
    }

    void 'a client that accepts only #accept is answered as one that asks for #format'() {
        given: 'the Accept header, rather than the format this spec sets up, decides'
        response.format = null
        request.addHeader('Accept', accept)

        when:
        def chosen = controller.withFormat {
            html { 'html' }
            "${other}" { other }
        }

        then: 'withFormat runs no block where it has none for the format'
        chosen == null

        when:
        controller.respond([title: 'Grails'], formats: [other])

        then: 'respond answers an action that does not respond in it with 406'
        response.status == 406

        where:
        accept                     | format | other
        'application/problem+json' | 'json' | 'xml'
        'application/problem+xml'  | 'xml'  | 'json'
    }

    void 'a problem respond names a view for, which a client asks for in a format nothing writes problems in, is rendered by the JSON view of the name'() {
        given: 'the view in HTML and in JSON, and in no other format'
        views['/jsonResponse/conflict.gsp'] = 'conflict'
        views['/jsonResponse/conflict.json.gsp'] = '{"conflict":true}'
        request.requestURI = '/books'
        response.format = 'csv'
        def problem = org.springframework.http.ProblemDetail.forStatus(409)

        when:
        controller.respond(problem, view: 'conflict')

        then: 'as for a client that asks for JSON'
        response.status == 409
        response.contentType == 'application/json;charset=UTF-8'
        view == 'conflict'
        model.problemDetail.is(problem)
    }

    void 'respond renders the JSON GSP view of the action in a unit test, as in an application'() {
        given:
        views['/jsonResponse/show.json.gsp'] = '{"shown":true}'
        webRequest.actionName = 'show'
        response.format = 'json'
        def body = new JsonResponseBody(title: 'Grails')

        when:
        controller.respond(body)

        then:
        response.contentType == 'application/json;charset=UTF-8'
        view == 'show'
        model.jsonResponseBody.is(body)
    }

    void 'a problem respond names a view for, which a client asks for in a format nothing writes problems in, is written as problem JSON without a JSON view'() {
        given: 'the view in HTML only'
        views['/jsonResponse/conflict.gsp'] = 'conflict'
        request.requestURI = '/books'
        response.format = 'csv'

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatus(409), view: 'conflict')

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.status == 409
    }

    void 'a problem is rendered as HTML with the view respond names, with the status it carries'() {
        given:
        views['/jsonResponse/conflict.gsp'] = 'conflict'
        response.format = 'html'
        def problem = org.springframework.http.ProblemDetail.forStatus(409)

        when:
        controller.respond(problem, view: 'conflict')

        then:
        response.status == 409
        view == 'conflict'
        model.problemDetail.is(problem)
    }

    void 'a problem is rendered as HTML with the GSP #page respond names as #viewName'() {
        given:
        views[page] = 'conflict'
        response.format = 'html'
        def problem = org.springframework.http.ProblemDetail.forStatus(409)

        when:
        controller.respond(problem, view: viewName)

        then:
        response.status == 409
        view == viewName
        model.problemDetail.is(problem)

        where:
        page                              | viewName
        '/jsonResponse/conflict.html.gsp' | 'conflict'
        '/jsonResponse/conflict.gsp'      | 'conflict.gsp'
        '/errors/conflict.gsp'            | '/errors/conflict'
    }

    void 'a problem respond names a view for, which a client asks for in HTML, is written as problem JSON without an HTML view'() {
        given:
        request.requestURI = '/books'
        response.format = 'html'

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatus(409), view: 'conflict')

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.status == 409
    }

    void 'a problem respond names a view for, which a client asks for in HTML, is rendered by the JSON view of the name without an HTML view'() {
        given: 'the view in JSON only'
        views['/jsonResponse/conflict.json.gsp'] = '{"conflict":true}'
        request.requestURI = '/books'
        response.format = 'html'
        def problem = org.springframework.http.ProblemDetail.forStatus(409)

        when:
        controller.respond(problem, view: 'conflict')

        then: 'as for a client that asks for JSON'
        response.status == 409
        response.contentType == 'application/json;charset=UTF-8'
        view == 'conflict'
        model.problemDetail.is(problem)
    }

    void 'a problem respond names a view for, which a client that accepts anything asks for, is written as problem JSON without a view of the name'() {
        given:
        request.requestURI = '/books'
        response.format = null
        request.addHeader('Accept', '*/*')

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatus(409), view: 'conflict')

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.status == 409
    }

    void 'render and respond share named configurations in ControllerUnitTest'() {
        given:
        def registry = applicationContext.getBean(NamedJsonConfigurationRegistry)
        registry.register('upper') { it.serializer(JsonResponseBody, new UpperTitleSerializer()) }

        when:
        controller.render(json: new JsonResponseBody(title: 'Grails'), jsonConfiguration: 'upper')

        then:
        response.json == [title: 'GRAILS']

        when:
        response.reset()
        response.format = 'json'
        controller.respond(new JsonResponseBody(title: 'Grails'), jsonConfiguration: 'upper')

        then:
        response.json == [title: 'GRAILS']
    }

    void 'render json and respond apply a projection to a bean'() {
        when:
        controller.render(json: new JsonResponseBody(title: 'Grails', firstName: 'Ada'), excludes: ['firstName'])

        then:
        response.json == [title: 'Grails']

        when:
        response.reset()
        response.format = 'json'
        controller.respond(new JsonResponseBody(title: 'Grails', firstName: 'Ada'), includes: ['title'])

        then:
        response.json == [title: 'Grails']
    }

    void 'empty includes write none of the properties of a bean, as in Grails 8'() {
        given:
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('plain') { }

        when:
        controller.respond(new JsonResponseBody(title: 'Grails', firstName: 'Ada'), includes: [])

        then:
        response.contentAsString == '{}'

        when: 'with a named configuration'
        response.reset()
        response.format = 'json'
        controller.respond(new JsonResponseBody(title: 'Grails', firstName: 'Ada'), includes: [], jsonConfiguration: 'plain')

        then:
        response.contentAsString == '{}'

        when: 'render json:'
        response.reset()
        controller.render(json: new JsonResponseBody(title: 'Grails', firstName: 'Ada'), includes: [])

        then:
        response.contentAsString == '{}'
    }

    void 'an application JsonRenderer bean follows the Spring JSON setting'() {
        when: 'the bean writes with the Grails mapper, so the snake case customizer applies'
        controller.respond(new JsonRendererBeanBody(firstName: 'Ada'))

        then:
        response.json == [first_name: 'Ada']

        when: 'and it can select a registered named configuration'
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('beanUpper') {
            it.serializer(JsonRendererBeanBody, new UpperFirstNameSerializer())
        }
        response.reset()
        response.format = 'json'
        controller.respond(new JsonRendererBeanBody(firstName: 'Ada'), jsonConfiguration: 'beanUpper')

        then:
        response.json == [firstName: 'ADA']
    }

    void 'an application JsonRenderer bean keeps its own excludes on the Spring path'() {
        when:
        controller.respond(new JsonRendererCredentials(user: 'ada', password: 's3cret'))

        then:
        response.json == [user: 'ada']
    }

    void 'an application JsonRenderer bean keeps its own excludes with a named configuration'() {
        given:
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('plain') { }

        when:
        controller.respond(new JsonRendererCredentials(user: 'ada', password: 's3cret'), jsonConfiguration: 'plain')

        then:
        response.json == [user: 'ada']
    }

    void 'a request includes overrides the excludes of a JsonRenderer bean only where the bean sets none'() {
        expect: 'the bean is the renderer of the type it is registered for'
        applicationContext.getBean(RendererRegistry).findRenderer(MimeType.JSON, new JsonRendererProfile())
                .is(applicationContext.getBean('jsonRendererProfileRenderer'))

        when: 'the bean sets no excludes'
        controller.respond(new JsonRendererProfile(name: 'ada', email: 'ada@example.com'), includes: ['name'])

        then:
        response.json == [name: 'ada']

        when: 'the bean sets excludes'
        response.reset()
        response.format = 'json'
        controller.respond(new JsonRendererCredentials(user: 'ada', password: 's3cret'), includes: ['user', 'password'])

        then: 'the bean excludes still win, as they do on the legacy path'
        response.json == [user: 'ada']
    }

    void 'a named configuration does not hide a problem'() {
        given:
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('unused') {
            it.serializer(JsonResponseBody, new UpperTitleSerializer())
        }

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatus(409), jsonConfiguration: 'unused')

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.status == 409
    }

    void 'render json without a name uses the default mapper'() {
        when:
        controller.render(json: [message: "Saved ${'Grails'}"])

        then:
        response.json == [message: 'Saved Grails']
        response.contentType.equalsIgnoreCase('application/json;charset=UTF-8')
    }
}

class ControllerAcceptOrderSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class NamingConfiguration {

        @Bean
        JsonNamingCustomizer jsonNamingCustomizer() {
            new JsonNamingCustomizer()
        }
    }

    void 'a client that lists a type the action does not respond in ahead of JSON gets JSON, written by Spring MVC, #description'() {
        given:
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('plain') { }
        request.addHeader('Accept', 'application/xml, application/json')

        when:
        controller.respond(new JsonResponseBody(title: 'Grails', firstName: 'Ada'), [formats: ['json']] + arguments)

        then: 'labelled JSON, and written by the Grails mapper, so with the naming strategy of the application'
        response.contentType.startsWith('application/json')
        response.json == [title: 'Grails', first_name: 'Ada']

        where:
        description                     | arguments
        'without a configuration'       | [:]
        'with a named configuration'    | [jsonConfiguration: 'plain']
    }
}

class ControllerJsonMapperOverrideSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    @Configuration
    static class MapperConfiguration {
        @Bean
        JsonMapper probeMapper() {
            JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
        }
    }

    void 'a mapper bean from nested configuration makes Boot back off'() {
        given:
        response.format = 'json'

        when:
        controller.respond(new JsonResponseBody(title: 'Grails', firstName: 'Ada'))

        then:
        applicationContext.getBeansOfType(JsonMapper).keySet() == ['probeMapper'] as Set
        response.json == [title: 'Grails', first_name: 'Ada']
    }
}

class ControllerProblemDefaultsSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    void 'with the default settings, a problem a client asks for as #format is written as problem JSON'() {
        given:
        request.requestURI = '/books'
        response.format = format

        when:
        controller.respond(ProblemDetail.forStatus(409))

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.status == 409
        response.json.instance == '/books'

        where:
        format << ['json', 'html', 'csv']
    }
}

class ControllerProblemConverterSpec extends Specification implements ControllerUnitTest<JsonResponseController>,
        DataTest {

    Closure doWithConfig() {
        { config -> config['grails.converters.json.domain.include.class'] = true }
    }

    void setupSpec() {
        mockDomains(ProblemResponseBook, ProblemResponseAuthor)
    }

    void "a problem holding a domain class instance is written by Spring MVC's converter, whatever the grails.converters.json settings"() {
        given:
        def author = new ProblemResponseAuthor(name: 'Ada').save(failOnError: true)
        def book = new ProblemResponseBook(title: 'Grails', author: author).save(failOnError: true)
        def problem = { ->
            ProblemDetail.forStatus(409).tap {
                it.instance = URI.create('/books/1')
                it.setProperty('book', book)
            }
        }
        def converter = (HttpMessageConverter<Object>) applicationContext.getBean(SpringMessageConverters).converters.find {
            it instanceof JacksonJsonHttpMessageConverter
        }
        def written = new MockHttpOutputMessage()
        converter.write(problem(), MediaType.APPLICATION_PROBLEM_JSON, written)

        when:
        response.format = 'json'
        controller.respond(problem())

        then:
        response.status == 409
        response.contentAsString == written.bodyAsString
    }
}

class ControllerWrapRootValueSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    Closure doWithConfig() {
        { config ->
            config['grails.web.rendering.json.spring'] = true
            config['grails.converters.json.legacy'] = false
            config['spring.jackson.serialization.wrap-root-value'] = true
            config['grails.converters.json.circular.reference.behaviour'] = 'PATH'
        }
    }

    void 'the converter wraps the value it renders as respond wraps it, where the mapper wraps root values'() {
        given:
        def body = new JsonResponseBody(title: 'Grails', firstName: 'Ada')
        response.format = 'json'
        controller.respond(body)
        def responded = response.contentAsString
        response.reset()

        when:
        controller.render(body as JSON)

        then:
        responded.startsWith('{"JsonResponseBody":')
        response.contentAsString == responded
    }

    void 'the converter writes the path of a value a wrapped root value repeats as respond writes it'() {
        given:
        def parent = new WrappedCycleNode(name: 'parent')
        def child = new WrappedCycleNode(name: 'child', parent: parent)
        child.children = [child]
        parent.children = [child]
        response.format = 'json'
        controller.respond(parent)
        def responded = response.contentAsString
        response.reset()

        when:
        controller.render(parent as JSON)

        then:
        responded.contains('"ref":"root.WrappedCycleNode"')
        response.contentAsString == responded
    }
}

class WrappedCycleNode {
    String name
    WrappedCycleNode parent
    List<WrappedCycleNode> children
}

class ControllerProblemNamespacedPageSpec extends Specification implements ControllerUnitTest<NamespacedProblemController> {

    void 'a problem is rendered as HTML with the GSP of the name in the controller namespace'() {
        given:
        views['/admin/namespacedProblem/conflict.gsp'] = 'conflict'
        response.format = 'html'
        def problem = ProblemDetail.forStatus(409)

        when:
        controller.respond(problem, view: 'conflict')

        then:
        response.status == 409
        view == 'conflict'
        model.problemDetail.is(problem)
    }
}

@Artefact('Controller')
class NamespacedProblemController {
    static namespace = 'admin'
}

class ControllerProblemNonGspViewSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    @Configuration
    static class ViewConfiguration {

        @Bean
        ViewResolver otherViewResolver() {
            new ViewResolver() {
                @Override
                View resolveViewName(String viewName, Locale locale) {
                    viewName == 'conflict' ? new StubView(contentType: 'application/json') :
                            viewName == 'missingPage' ? new StubView() : null
                }
            }
        }
    }

    void 'a problem respond names a view for, which a client asks for in HTML, is written as problem JSON where #description'() {
        given:
        request.requestURI = '/books'
        response.format = 'html'

        when:
        controller.respond(ProblemDetail.forStatus(409), view: viewName)

        then:
        response.status == 409
        response.contentType == 'application/problem+json'
        response.json.status == 409

        where:
        description                                                   | viewName
        'the view of the name is a JSON or markup view, not a GSP'    | 'conflict'
        'the view of the name is another kind of view, such as a JSP' | 'missingPage'
    }
}

class StubView extends AbstractView {

    @Override
    protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) {
    }
}

class ControllerProblemPageSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    @Configuration
    static class ProblemPageConfiguration {
        @Bean
        ProblemPageRenderer problemPageRenderer() {
            new ProblemPageRenderer()
        }
    }

    void 'an HTML renderer an application registers for problems renders them, with the status the problem carries'() {
        given:
        response.format = 'html'
        request.requestURI = '/books'
        def problem = ProblemDetail.forStatus(409)

        when:
        controller.respond(problem)

        then:
        view == '/errors/problem'
        model.problem.is(problem)
        response.status == 409
        problem.instance == URI.create('/books')
    }
}

class ControllerProblemJsonRendererSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    @Configuration
    static class ProblemJsonConfiguration {
        @Bean
        ProblemJsonRenderer problemJsonRenderer() {
            new ProblemJsonRenderer()
        }
    }

    void 'a JSON renderer an application registers for problems renders them, with the status the problem carries'() {
        given:
        response.format = 'json'

        when:
        controller.respond(ProblemDetail.forStatus(409))

        then:
        response.contentAsString == '{"custom":409}'
        response.status == 409
    }
}

class ControllerSerializablePageSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    @Configuration
    static class SerializablePageConfiguration {
        @Bean
        ProblemPageRenderer serializablePageRenderer() {
            new ProblemPageRenderer(Serializable)
        }
    }

    void 'an HTML renderer an application registers for a type problems share renders them'() {
        given:
        response.format = 'html'
        def problem = ProblemDetail.forStatus(409)

        when:
        controller.respond(problem)

        then:
        view == '/errors/problem'
        model.problem.is(problem)
    }
}

class ControllerTextJsonSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    void 'respond writes an Accept of text/json through Jackson'() {
        given:
        request.addHeader('Accept', 'text/json')

        when: 'a value the legacy converter would write as a number array'
        controller.respond([65, 66] as byte[])

        then:
        response.contentType.startsWith('text/json')
        response.contentAsString == '"QUI="'
    }
}

class LegacyValidationErrorsConfigurationSpec extends Specification implements ControllerUnitTest<JsonResponseController> {

    void 'with the legacy JSON respond, validation errors are written as they are without the configuration respond names'() {
        given:
        applicationContext.getBean(NamedJsonConfigurationRegistry).register('plain') { }
        def errors = new BeanPropertyBindingResult(new JsonResponseBody(title: 'private'), 'book')
        errors.rejectValue('title', 'invalid', 'Title is invalid')
        response.format = 'json'
        controller.respond(errors)
        def without = response.contentAsString

        when:
        response.reset()
        response.format = 'json'
        controller.respond(errors, jsonConfiguration: 'plain')

        then:
        response.status == 422
        response.contentAsString == without
        response.json.errors.first().message == 'Title is invalid'
    }
}

@Artefact('Controller')
class JsonResponseController { }

class ProblemPageRenderer extends DefaultHtmlRenderer<Object> {

    ProblemPageRenderer(Class<?> targetType = ProblemDetail) {
        super(targetType)
    }

    @Override
    void render(Object object, RenderContext context) {
        context.setViewName('/errors/problem')
        context.setModel([problem: object])
    }
}

class ProblemJsonRenderer implements Renderer<ProblemDetail> {

    final Class<ProblemDetail> targetType = ProblemDetail

    final MimeType[] mimeTypes = [MimeType.JSON] as MimeType[]

    @Override
    void render(Object object, RenderContext context) {
        context.setContentType('application/json')
        context.writer.write("{\"custom\":${((ProblemDetail) object).status}}")
    }
}

@JsonSerialize(using = PojoWritingSerializer)
class PojoWritingBody {
    Object book
}

class PojoWritingSerializer extends ValueSerializer<PojoWritingBody> {
    @Override
    void serialize(PojoWritingBody value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeName('book')
        generator.writePOJO(value.book)
        generator.writeEndObject()
    }
}

class PojoCycleOwner {
    String name
    PojoCycleHolder holder
}

@JsonSerialize(using = PojoCycleHolderSerializer)
class PojoCycleHolder {
    PojoCycleOwner owner
}

class PojoCycleHolderSerializer extends ValueSerializer<PojoCycleHolder> {
    @Override
    void serialize(PojoCycleHolder value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeName('owner')
        generator.writePOJO(value.owner)
        generator.writeEndObject()
    }
}

class FailingJsonBody {
    String title

    String getUnreadable() {
        throw new IllegalStateException('the value cannot be read')
    }
}

class JsonResponseBody {
    String title
    String firstName
}

class JsonValidatedBody {
    String title
    Errors errors
}

@Entity
class TenantResponseDomain implements MultiTenant<TenantResponseDomain> {
    String name
    String tenantId
}

@Entity
class ProblemResponseBook {
    String title
    ProblemResponseAuthor author
}

@Entity
class ProblemResponseAuthor {
    String name
}

@Entity
class JsonResponseDomain {
    @JsonIgnore
    String title
}

class JsonNamingCustomizer implements JsonMapperBuilderCustomizer {
    @Autowired
    GrailsJsonMapperCustomizer grailsCustomizer
    GrailsJsonMapperCustomizer appliedCustomizer

    @Override
    void customize(JsonMapper.Builder builder) {
        appliedCustomizer = grailsCustomizer
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    }
}

class JsonRendererBeanBody {
    String firstName
}

class JsonRendererCredentials {
    String user
    String password
}

class JsonRendererProfile {
    String name
    String email
}

class UpperFirstNameSerializer extends ValueSerializer<JsonRendererBeanBody> {
    @Override
    void serialize(JsonRendererBeanBody value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeStringProperty('firstName', value.firstName.toUpperCase(Locale.ROOT))
        generator.writeEndObject()
    }
}

class UpperTitleSerializer extends ValueSerializer<JsonResponseBody> {
    @Override
    void serialize(JsonResponseBody value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeStringProperty('title', value.title.toUpperCase(Locale.ROOT))
        generator.writeEndObject()
    }
}
