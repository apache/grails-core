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

import spock.lang.Specification

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.converters.marshaller.ObjectMarshaller

class TargetTypeMarshallerSpec extends Specification {

    void setup() {
        def grailsApplication = new DefaultGrailsApplication()
        grailsApplication.initialise()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void 'a failure of the marshaller it marshals with, #description, is reported as a converter failure'() {
        given:
        JSON.registerObjectMarshaller(new TargetTypeMarshaller(Failing, new FailingMarshaller(failure: failure)))

        when:
        new JSON([value: new Failing()]).render(new StringWriter())

        then:
        ConverterException e = thrown()
        e.is(failure) || e.cause.is(failure)

        where:
        description             | failure
        'a runtime exception'   | new IllegalStateException('unreadable')
        'a checked exception'   | new IOException('disk')
        'a converter exception' | new ConverterException('already reported')
    }
}

class Failing {
}

class FailingMarshaller implements ObjectMarshaller<JSON> {

    Exception failure

    @Override
    boolean supports(Object object) {
        object instanceof Failing
    }

    @Override
    void marshalObject(Object object, JSON converter) {
        throw failure
    }
}
