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
package org.grails.web.converters.jackson

import java.util.function.Supplier

import tools.jackson.databind.json.JsonMapper

import org.springframework.context.MessageSource
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.ObjectError

import spock.lang.Specification

class ConverterJsonModuleSpec extends Specification {

    void 'a module created #description writes validation errors with their default messages'() {
        given:
        def mapper = JsonMapper.builder().addModule(new ConverterJsonModule(messageSource)).build()
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))

        expect:
        mapper.writeValueAsString(errors) == '{"errors":[{"object":"book","codes":["book.invalid"],"message":"Book is invalid"}]}'

        where:
        description                                  | messageSource
        'without a message source'                   | null
        'with a supplier that has no message source' | { -> null } as Supplier<MessageSource>
    }
}
