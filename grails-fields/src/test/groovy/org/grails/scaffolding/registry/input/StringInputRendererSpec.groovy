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
import org.grails.scaffolding.model.property.Constrained
import spock.lang.Shared
import spock.lang.Subject

@Subject(StringInputRenderer)
class StringInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    StringInputRenderer renderer

    void setup() {
        renderer = new StringInputRenderer()
    }

    void "test supports"() {
        given:
        DomainProperty prop

        when:
        prop = Mock(DomainProperty) {
            1 * getType() >> String
        }

        then:
        renderer.supports(prop)

        when:
        prop = Mock(DomainProperty) {
            1 * getType() >> null
        }

        then:
        renderer.supports(prop)
    }

    void "test render"() {
        given:
        DomainProperty property
        ClosureCapture closureCapture

        when:
        property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * isPassword() >> true
                1 * getMatches() >> null
                1 * getMaxSize() >> null
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "password"]

        when:
        property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * isPassword() >> false
                1 * isEmail() >> true
                1 * getMatches() >> null
                1 * getMaxSize() >> null
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "email"]

        when:
        property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * isPassword() >> false
                1 * isEmail() >> false
                1 * isUrl() >> true
                1 * getMatches() >> null
                1 * getMaxSize() >> null
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "url"]

        when:
        property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * isPassword() >> false
                1 * isEmail() >> false
                1 * isUrl() >> false
                1 * getMatches() >> null
                1 * getMaxSize() >> null
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "text"]

        when:
        property = Mock(DomainProperty) {
            1 * getConstrained() >> Mock(Constrained) {
                1 * isPassword() >> false
                1 * isEmail() >> false
                1 * isUrl() >> false
                2 * getMatches() >> "abc"
                2 * getMaxSize() >> 20
            }
        }
        closureCapture = getClosureCapture(renderer.renderInput([:], property))

        then:
        closureCapture.calls[0].name == "input"
        closureCapture.calls[0].args[0] == ["type": "text", "pattern": "abc", "maxlength": 20]
    }
}