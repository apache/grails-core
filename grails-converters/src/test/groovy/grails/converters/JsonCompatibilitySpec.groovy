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
package grails.converters

import java.time.Instant

import org.springframework.validation.BeanPropertyBindingResult

import grails.core.DefaultGrailsApplication
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller

import spock.lang.Specification

class JsonCompatibilitySpec extends Specification {

    void setup() {
        def initializer = new ConvertersConfigurationInitializer()
        initializer.grailsApplication = new DefaultGrailsApplication()
        initializer.initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void 'primitive, temporal, enum, array, collection, map and byte values retain their JSON shape'() {
        given:
        def value = [
                text: 'grails',
                count: 2,
                enabled: true,
                missing: null,
                instant: Instant.parse('2020-01-02T03:04:05Z'),
                mode: JsonCompatibilityMode.FAST,
                array: [1, 2] as int[],
                collection: ['a', 'b'],
                nested: [answer: 42],
                bytes: [65, 66] as byte[],
        ]

        when:
        def parsed = JSON.parse(new JSON(value).toString())

        then:
        parsed.text == 'grails'
        parsed.count == 2
        parsed.enabled
        parsed.missing == null
        parsed.instant == '2020-01-02T03:04:05Z'
        parsed.mode == 'FAST'
        parsed.array == [1, 2]
        parsed.collection == ['a', 'b']
        parsed.nested.answer == 42
        parsed.bytes == [65, 66]
    }

    void 'custom marshaller priority and closure output remain public extension points'() {
        given:
        JSON.registerObjectMarshaller(JsonCompatibilityBean, 100) { JsonCompatibilityBean bean ->
            [renamed: bean.value.toUpperCase(Locale.ROOT)]
        }

        expect:
        JSON.parse(new JSON(new JsonCompatibilityBean(value: 'custom')).toString()) == [renamed: 'CUSTOM']
    }

    void 'legacy object marshaller registration overloads are deprecated but not scheduled for removal'() {
        when:
        def methods = JSON.declaredMethods.findAll { it.name == 'registerObjectMarshaller' }

        then:
        methods.size() == 4
        methods.every { method ->
            Deprecated deprecated = method.getAnnotation(Deprecated)
            deprecated?.since() == '8.0' && !deprecated.forRemoval()
        }
    }

    void 'legacy named and default configuration APIs are deprecated but remain available'() {
        when:
        def methods = JSON.declaredMethods.findAll {
            it.name in ['getNamedConfig', 'use', 'createNamedConfig', 'withDefaultConfiguration']
        }

        then:
        methods.size() == 5
        methods.every { method ->
            Deprecated deprecated = method.getAnnotation(Deprecated)
            deprecated?.since() == '8.0' && !deprecated.forRemoval()
        }
    }

    void 'named configurations override marshallers only inside their scope'() {
        given:
        JSON.registerObjectMarshaller(Date) { 'default-date' }
        JSON.createNamedConfig('compatibility-date') { configuration ->
            configuration.registerObjectMarshaller(Date) { 'named-date' }
        }
        def value = [created: new Date(0)]

        when:
        def before = JSON.parse(new JSON(value).toString())
        def named
        JSON.use('compatibility-date') {
            named = JSON.parse(new JSON(value).toString())
        }
        def after = JSON.parse(new JSON(value).toString())

        then:
        before == [created: 'default-date']
        named == [created: 'named-date']
        after == before
    }

    void 'circular maps retain the established empty-object reference shape'() {
        given:
        def circular = [:]
        circular.self = circular

        expect:
        JSON.parse(new JSON(circular).toString()) == [self: [:]]
    }

    void 'Hibernate-shaped proxies are unwrapped before legacy marshalling'() {
        given:
        def target = new JsonCompatibilityBean(value: 'unwrapped')
        def proxy = new JsonCompatibilityProxy(hibernateLazyInitializer: [implementation: target])

        expect:
        JSON.parse(new JSON([bean: proxy]).toString()) == [bean: [value: 'unwrapped']]
    }

    void 'validation errors retain their legacy JSON field shape'() {
        given:
        JSON.registerObjectMarshaller(new ValidationErrorsMarshaller(), 100)
        def errors = new BeanPropertyBindingResult(new JsonCompatibilityBean(), 'bean')
        errors.rejectValue('value', 'blank', 'Value is required')

        expect:
        JSON.parse(new JSON(errors).toString()) == [errors: [[
                object: 'bean',
                field: 'value',
                'rejected-value': null,
                message: 'Value is required',
        ]]]
    }
}

enum JsonCompatibilityMode {
    FAST
}

class JsonCompatibilityBean {
    String value
}

class JsonCompatibilityProxy {
    Map hibernateLazyInitializer
}
