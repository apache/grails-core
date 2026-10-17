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
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.rest.render.RenderContext
import grails.rest.render.json.JsonRenderer
import grails.testing.web.controllers.ControllerUnitTest
import org.springframework.http.ProblemDetail
import org.springframework.validation.BeanPropertyBindingResult
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
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
        response.contentType == 'application/problem+json;charset=UTF-8'
        response.json.status == 422
        response.json.errors.first().message == 'Title is invalid'
        !response.json.errors.first().containsKey('rejectedValue')
        !response.json.containsKey('properties')
    }

    void 'respond of an explicit problem preserves its HTTP semantics'() {
        given:
        request.requestURI = '/books'
        def problem = org.springframework.http.ProblemDetail.forStatus(422)

        when:
        controller.respond(problem)

        then:
        response.status == 422
        response.contentType == 'application/problem+json;charset=UTF-8'
        response.json.status == 422
        response.json.instance == '/books'
    }

    void 'a problem a client asks for as #format is written as problem JSON, as Spring MVC writes it'() {
        given:
        request.requestURI = '/books'
        response.format = format

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatus(409))

        then:
        response.status == 409
        response.contentType == 'application/problem+json;charset=UTF-8'
        response.json.status == 409
        response.json.instance == '/books'

        where:
        format << ['html', 'csv']
    }

    void 'a problem respond names a view for, which a client asks for in a format no view renders, is written as problem JSON'() {
        given:
        request.requestURI = '/books'
        response.format = 'csv'

        when:
        controller.respond(org.springframework.http.ProblemDetail.forStatus(409), view: 'conflict')

        then:
        response.status == 409
        response.contentType == 'application/problem+json;charset=UTF-8'
        response.json.status == 409
    }

    void 'a problem is rendered as HTML with the view respond names, with the status it carries'() {
        given:
        response.format = 'html'
        def problem = org.springframework.http.ProblemDetail.forStatus(409)

        when:
        controller.respond(problem, view: 'conflict')

        then:
        response.status == 409
        view == 'conflict'
        model.problemDetail.is(problem)
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
        response.contentType == 'application/problem+json;charset=UTF-8'
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
        response.contentType == 'application/problem+json;charset=UTF-8'
        response.json.status == 409
        response.json.instance == '/books'

        where:
        format << ['json', 'html', 'csv']
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

    void 'an HTML renderer an application registers for problems renders them'() {
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

@Artefact('Controller')
class JsonResponseController { }

class ProblemPageRenderer extends DefaultHtmlRenderer<ProblemDetail> {

    ProblemPageRenderer() {
        super(ProblemDetail)
    }

    @Override
    void render(Object object, RenderContext context) {
        context.setViewName('/errors/problem')
        context.setModel([problem: object])
    }
}

class JsonResponseBody {
    String title
    String firstName
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
