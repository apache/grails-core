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

    Closure doWithConfig() {
        { config -> config['grails.web.rendering.json.spring'] = true }
    }

    Closure doWithSpring() {
        return { -> jsonNamingCustomizer(JsonNamingCustomizer) }
    }

    void 'test mapper honors application customizers'() {
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

    void 'render json without a name uses the default mapper'() {
        when:
        controller.render(json: [message: "Saved ${'Grails'}"])

        then:
        response.json == [message: 'Saved Grails']
        response.contentType.equalsIgnoreCase('application/json;charset=UTF-8')
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
    @Override
    void customize(JsonMapper.Builder builder) {
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
