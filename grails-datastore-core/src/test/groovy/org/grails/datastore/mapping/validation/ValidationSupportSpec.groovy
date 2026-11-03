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
package org.grails.datastore.mapping.validation

import org.springframework.beans.factory.BeanFactory
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import spock.lang.Specification

import org.grails.datastore.mapping.model.PersistentEntity

class ValidationSupportSpec extends Specification {

    void "validation exceptions format every error into the message"() {
        given:
        String ls = System.getProperty('line.separator')
        ValidationErrors errors = new ValidationErrors(new Person(), 'person')
        errors.rejectValue('name', 'nullable')
        errors.reject('object.error')

        when:
        ValidationException exception = new ValidationException('Validation failed', errors)

        then:
        exception.errors.is(errors)
        exception.message == 'Validation failed : ' + ls +
                ls + ' - ' + errors.allErrors[0] + ls +
                ls + ' - ' + errors.allErrors[1] + ls
        ValidationException.formatErrors(errors, null) ==
                ls + ' - ' + errors.allErrors[0] + ls + ls + ' - ' + errors.allErrors[1] + ls
        ValidationException.formatErrors(new ValidationErrors(new Person()), 'm') == 'm : ' + ls
    }

    void "validation exceptions can be instantiated through the resolved exception type"() {
        given:
        Errors errors = new ValidationErrors(new Person())

        when:
        RuntimeException exception = ValidationException.newInstance('failed', errors)

        then:
        ValidationException.VALIDATION_EXCEPTION_TYPE == ValidationException
        exception.class == ValidationException.VALIDATION_EXCEPTION_TYPE
        exception.message.startsWith('failed : ')
    }

    void "the bean factory validator registry looks up validators by entity name"() {
        given:
        Validator validator = Stub(Validator)
        BeanFactory beanFactory = Mock(BeanFactory)
        PersistentEntity entity = Stub(PersistentEntity) { getName() >> 'com.example.Person' }
        BeanFactoryValidatorRegistry registry = new BeanFactoryValidatorRegistry(beanFactory)

        when:
        Validator found = registry.getValidator(entity)

        then:
        1 * beanFactory.containsBean('com.example.PersonValidator') >> true
        1 * beanFactory.getBean('com.example.PersonValidator', Validator) >> validator
        found.is(validator)

        when:
        found = registry.getValidator(entity)

        then:
        1 * beanFactory.containsBean('com.example.PersonValidator') >> false
        0 * beanFactory.getBean(*_)
        found == null

        expect:
        registry.messageSource instanceof StaticMessageSource
    }

    void "the bean factory validator registry uses the bean factory as message source when it is one"() {
        given:
        StaticApplicationContext context = new StaticApplicationContext()

        expect:
        new BeanFactoryValidatorRegistry(context).messageSource.is(context)
        context instanceof MessageSource
    }

    void "cascade validate types resolve from mapped names case insensitively"() {
        expect:
        CascadeValidateType.fromMappedName('owned') == CascadeValidateType.OWNED
        CascadeValidateType.fromMappedName('Dirty') == CascadeValidateType.DIRTY
        CascadeValidateType.fromMappedName('NONE') == CascadeValidateType.NONE
        CascadeValidateType.fromMappedName('default') == CascadeValidateType.DEFAULT
        CascadeValidateType.values()*.name() == ['DEFAULT', 'NONE', 'OWNED', 'DIRTY']

        when:
        CascadeValidateType.fromMappedName('bogus')

        then:
        thrown(IllegalArgumentException)
    }

}
