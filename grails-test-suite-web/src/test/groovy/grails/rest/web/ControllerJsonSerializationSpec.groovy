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
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.testing.web.controllers.ControllerUnitTest
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
        applicationContext.containsBean('xmlRenderer')
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
    }

    void 'test mapper honors customizers from nested configuration classes'() {
        given:
        response.format = 'json'

        when:
        controller.respond(new JsonResponseBody(title: 'Grails', firstName: 'Ada'))

        then:
        response.json == [title: 'Grails', first_name: 'Ada']
    }

    void 'Grails domain compatibility is scoped away from the shared Boot mapper'() {
        given:
        def book = new JsonResponseDomain(title: 'Grails').save()

        when:
        controller.respond(book)

        then:
        response.json == [id: book.id, title: 'Grails']

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

class UpperTitleSerializer extends ValueSerializer<JsonResponseBody> {
    @Override
    void serialize(JsonResponseBody value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeStringProperty('title', value.title.toUpperCase(Locale.ROOT))
        generator.writeEndObject()
    }
}
