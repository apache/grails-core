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

import grails.artefact.Artefact
import grails.converters.JSON
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.marshaller.ClosureObjectMarshaller

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/**
 * Grails 9 writes {@code respond} responses through the legacy JSON converter unless
 * {@code grails.web.rendering.json.spring} is set, so the marshallers an application registers with
 * that converter apply to them. Grails 10 changes the default.
 */
class RespondJsonConverterSelectionSpec extends Specification implements ControllerUnitTest<JsonSelectionController> {

    void 'a registered JSON marshaller applies to respond by default'() {
        given:
        JSON.registerObjectMarshaller(JsonSelectionBody) { JsonSelectionBody body -> [legacy: body.title] }
        response.format = 'json'

        when:
        controller.respond(new JsonSelectionBody(title: 'Grails'))

        then:
        response.json == [legacy: 'Grails']
    }
}

class RespondJsonMarshallerRegistererSpec extends Specification implements ControllerUnitTest<JsonSelectionController> {

    @Configuration
    static class MarshallerConfiguration {

        @Bean
        ObjectMarshallerRegisterer jsonSelectionMarshallerRegisterer() {
            new ObjectMarshallerRegisterer(
                    converterClass: JSON,
                    marshaller: new ClosureObjectMarshaller<JSON>(JsonSelectionBody,
                            { JsonSelectionBody body -> [legacy: body.title] }))
        }
    }

    void 'an ObjectMarshallerRegisterer bean applies to respond by default'() {
        given:
        response.format = 'json'

        when:
        controller.respond(new JsonSelectionBody(title: 'Grails'))

        then:
        response.json == [legacy: 'Grails']
    }
}

class RespondLegacyJsonSettingSpec extends Specification implements ControllerUnitTest<JsonSelectionController> {

    Closure doWithConfig() {{ config ->
        config['grails.web.rendering.json.spring'] = false
    }}

    void 'setting grails.web.rendering.json.spring to false writes respond through the legacy converter'() {
        given:
        response.format = 'json'

        when:
        controller.respond([65, 66] as byte[])

        then:
        response.json == [65, 66]
    }
}

@Artefact('Controller')
class JsonSelectionController { }

class JsonSelectionBody {
    String title
}
