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
package org.grails.scaffolding.registry.input

import org.grails.scaffolding.ClosureCapture
import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import spock.lang.Shared
import spock.lang.Subject

@Subject(EnumInputRenderer)
class EnumInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    EnumInputRenderer renderer

    void setup() {
        renderer = new EnumInputRenderer()
    }

    void "test supports"() {
        given:
        DomainProperty property

        when:
        property = Mock(DomainProperty) {
            1 * getType() >> Fruit
        }

        then:
        renderer.supports(property)
    }

    void "test render"() {
        given:
        DomainProperty property
        ClosureCapture closureCapture

        when:
        property = Mock(DomainProperty) {
            2 * getType() >> Fruit
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "select"
        closureCapture.calls[0].args[0] == [:]
        closureCapture.calls[0][0].name == "option"
        closureCapture.calls[0][0].args[0] == "APPLE"
        closureCapture.calls[0][0].args[1] == [value: "APPLE"]
        closureCapture.calls[0][1].name == "option"
        closureCapture.calls[0][1].args[0] == "ORANGE"
        closureCapture.calls[0][1].args[1] == [value: "ORANGE"]

        when:
        property = Mock(DomainProperty) {
            2 * getType() >> Car
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "select"
        closureCapture.calls[0].args[0] == [:]
        closureCapture.calls[0][0].name == "option"
        closureCapture.calls[0][0].args[0] == "Alfa Romeo"
        closureCapture.calls[0][0].args[1] == [value: "ALFA_ROMEO"]
        closureCapture.calls[0][1].name == "option"
        closureCapture.calls[0][1].args[0] == "Subaru"
        closureCapture.calls[0][1].args[1] == [value: "SUBARU"]
    }

    enum Fruit { APPLE, ORANGE }
    enum Car {
        ALFA_ROMEO("Alfa Romeo"),
        SUBARU("Subaru")

        private String val
        Car(String val) {
            this.val = val
        }
        String toString() {
            val
        }
    }
}