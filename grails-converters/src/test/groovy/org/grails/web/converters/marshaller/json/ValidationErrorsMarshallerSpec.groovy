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
package org.grails.web.converters.marshaller.json

import grails.converters.JSON

import org.grails.web.json.JSONWriter
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError

import spock.lang.Specification

class ValidationErrorsMarshallerSpec extends Specification {

    void "Test marshalObject handles org.springframework.validation.ObjectError"() {
        given:
            ObjectError objectError = new ObjectError('test', 'Error happening on test object.')

            List<ObjectError> allErrors = [objectError]

            ValidationErrorsMarshaller marshaller = new ValidationErrorsMarshaller()
            Errors errors = Mock(Errors) {
                1 * getAllErrors() >> allErrors
            }

            JSON json = new JSON()

            StringWriter stringWriter = new StringWriter()
            json.writer = new JSONWriter(stringWriter)

        when:
            marshaller.marshalObject(errors, json)

        then:
            assert stringWriter.toString() == '{"errors":[{"object":"test","message":"Error happening on test object."}]}'
    }
}
