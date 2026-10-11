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
package grails.validation

import org.springframework.validation.BeanPropertyBindingResult
import spock.lang.Specification

class ValidationExceptionSpec extends Specification {

    void 'getMessage returns the message with each error formatted underneath'() {
        given:
        def target = new Object()
        def errors = new BeanPropertyBindingResult(target, 'target')
        errors.reject('some.error.code', 'Some error message')

        when:
        def ex = new ValidationException('Validation failed', errors)

        then:
        ex.message.startsWith('Validation failed:\n')
        ex.message.contains('Some error message')
        ex.errors.is(errors)
    }

    void 'formatErrors without a message omits the message prefix'() {
        given:
        def target = new Object()
        def errors = new BeanPropertyBindingResult(target, 'target')
        errors.reject('some.error.code', 'Some error message')

        expect:
        !ValidationException.formatErrors(errors).startsWith(':\n')
        ValidationException.formatErrors(errors).contains('Some error message')
    }

    void 'formatErrors with a blank message omits the message prefix'() {
        given:
        def target = new Object()
        def errors = new BeanPropertyBindingResult(target, 'target')
        errors.reject('some.error.code', 'Some error message')

        expect:
        ValidationException.formatErrors(errors, '') == ValidationException.formatErrors(errors)
    }
}
