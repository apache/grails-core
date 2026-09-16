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
package grails.rest.render.errors

import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.HttpStatus

import spock.lang.Specification

class ValidationProblemDetailFactorySpec extends Specification {

    void 'creates an RFC 9457 validation problem without exposing rejected values'() {
        given:
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new FieldError('book', 'title', 'secret', false,
                ['book.title.blank', 'blank'] as String[], null, 'Title must not be blank'))
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))

        when:
        def problem = new ValidationProblemDetailFactory().create(errors)
        def entries = problem.properties.errors

        then:
        problem.status == 422
        problem.title == 'Validation failed'
        problem.detail == 'Request validation failed with 2 errors.'
        entries == [
                [object: 'book', field: 'title', codes: ['book.title.blank', 'blank'],
                 message: 'Title must not be blank'],
                [object: 'book', codes: ['book.invalid'], message: 'Book is invalid'],
        ]
        !entries.first().containsKey('rejectedValue')
    }

    void 'the problem status matches the status the response is sent with'() {
        given: "a renderer configured to answer validation failures with 400"
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))

        when:
        def problem = new ValidationProblemDetailFactory().create(errors, HttpStatus.BAD_REQUEST)

        then: "RFC 9457 requires the status member to agree with the HTTP status"
        problem.status == 400

        and: "the default overload still reports 422"
        new ValidationProblemDetailFactory().create(errors).status == 422
    }

    void 'messages are resolved through the MessageSource for the current locale'() {
        given: "an error whose default message is a template, as Grails constraints produce"
        LocaleContextHolder.locale = Locale.ENGLISH
        def messageSource = new StaticMessageSource()
        messageSource.addMessage('book.title.blank', Locale.ENGLISH, 'Title must not be blank')
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new FieldError('book', 'title', null, false,
                ['book.title.blank'] as String[], null, 'Property [{0}] cannot be null'))

        when:
        def problem = new ValidationProblemDetailFactory(false, messageSource).create(errors)

        then: "the bundle entry wins over the raw default template"
        problem.properties.errors.first().message == 'Title must not be blank'

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    void 'an unresolved code still substitutes the default message arguments'() {
        given: "no bundle entry, so the default message is used -- but as a resolved template"
        def messageSource = new StaticMessageSource()
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new FieldError('book', 'title', null, false,
                ['book.title.nullable'] as String[], ['title'] as Object[],
                'Property [{0}] cannot be null'))

        expect:
        new ValidationProblemDetailFactory(false, messageSource).create(errors)
                .properties.errors.first().message == 'Property [title] cannot be null'
    }

    void 'without a MessageSource the default message is used verbatim'() {
        given:
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new FieldError('book', 'title', null, false,
                ['book.title.nullable'] as String[], ['title'] as Object[],
                'Property [{0}] cannot be null'))

        expect: "the no-context constructor still works, placeholders and all"
        new ValidationProblemDetailFactory().create(errors)
                .properties.errors.first().message == 'Property [{0}] cannot be null'
    }

    void 'includes rejected values only when explicitly requested'() {
        given:
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new FieldError('book', 'title', 'submitted-value', false,
                ['book.title.invalid'] as String[], null, 'Title is invalid'))

        expect:
        new ValidationProblemDetailFactory(true).create(errors)
                .properties.errors.first().rejectedValue == 'submitted-value'
    }
}
