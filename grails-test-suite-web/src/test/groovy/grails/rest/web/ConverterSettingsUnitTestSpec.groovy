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

import groovy.json.JsonSlurper

import org.springframework.validation.BeanPropertyBindingResult
import tools.jackson.databind.json.JsonMapper

import grails.artefact.Artefact
import grails.converters.JSON
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.web.converters.exceptions.ConverterException
import spock.lang.Specification

/**
 * A controller unit test's {@code doWithConfig} decides the converter settings the converters beans are registered
 * with, as an application's configuration does.
 */
class ConverterLegacySettingUnitTestSpec extends Specification implements ControllerUnitTest<ConverterSettingsController> {

    Closure doWithConfig() {
        { config -> config['grails.converters.json.legacy'] = false }
    }

    void 'validation errors are written without the values that were rejected, as the application would write them'() {
        given:
        def errors = new BeanPropertyBindingResult(new ConverterSettingsBody(name: 'secret'), 'body')
        errors.rejectValue('name', 'invalid', 'Name is invalid')

        when:
        controller.render(new JSON(errors))
        def written = new JsonSlurper().parseText(response.contentAsString)

        then:
        written.errors[0].keySet() as List == ['object', 'field', 'codes', 'message']
        !response.contentAsString.contains('secret')
    }
}

class ConverterDomainModuleSettingUnitTestSpec extends Specification
        implements ControllerUnitTest<ConverterSettingsController>, DomainUnitTest<ConverterSettingsBook> {

    Closure doWithConfig() {
        { config -> config['grails.converters.json.domain.jackson.enabled'] = true }
    }

    Class[] getDomainClassesToMock() {
        [ConverterSettingsBook, ConverterSettingsShelf] as Class[]
    }

    void "Spring Boot's mapper renders a domain class instance as the JSON converter does"() {
        given:
        def shelf = new ConverterSettingsShelf(name: 'top').save()
        def book = new ConverterSettingsBook(title: 'Grails', shelf: shelf).save()

        when:
        def written = new JsonSlurper().parseText(applicationContext.getBean(JsonMapper).writeValueAsString(book))

        then: 'the association is a reference of its id'
        written == [id: book.id, title: 'Grails', shelf: [id: shelf.id]]
    }
}

class ConverterReadFeatureSettingUnitTestSpec extends Specification implements ControllerUnitTest<ConverterSettingsController> {

    Closure doWithConfig() {
        { config ->
            config['grails.converters.json.legacy'] = false
            config['spring.jackson.json.read.allow-java-comments'] = true
        }
    }

    void "Spring Boot's JSON read features decide what request.JSON accepts"() {
        given:
        request.contentType = 'application/json'
        request.content = '{"name":"Ada" /* a note */}'.bytes

        expect:
        request.JSON == [name: 'Ada']
    }
}

class ConverterStrictParsingUnitTestSpec extends Specification implements ControllerUnitTest<ConverterSettingsController> {

    Closure doWithConfig() {
        { config -> config['grails.converters.json.legacy'] = false }
    }

    void 'request.JSON rejects comments unless a read feature accepts them'() {
        given:
        request.contentType = 'application/json'
        request.content = '{"name":"Ada" /* a note */}'.bytes

        when:
        request.JSON

        then:
        thrown(ConverterException)
    }
}

@Artefact('Controller')
class ConverterSettingsController { }

class ConverterSettingsBody {
    String name
}

@Entity
class ConverterSettingsBook {
    String title
    ConverterSettingsShelf shelf
}

@Entity
class ConverterSettingsShelf {
    String name
}
