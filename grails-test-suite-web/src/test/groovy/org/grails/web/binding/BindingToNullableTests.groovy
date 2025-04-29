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

package org.grails.web.binding

import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification
import grails.artefact.Artefact
import grails.persistence.Entity

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class BindingToNullableTests extends Specification implements ControllerUnitTest<NullBindingPersonController>, DomainUnitTest<NullBindingPerson> {

    void testDataBindingBlankStringToNull() {
        controller.params.name = "fred"
        controller.params.dateOfBirth = ''

        when:
        def model = controller.update()

        then:
        controller.response.redirectedUrl != null
    }

    void testDataBindingToNull() {
        controller.params.name = "fred"
        controller.params.dateOfBirth = 'invalid'

        when:
        def model = controller.update()

        then:
        !controller.response.redirectedUrl
        model.personInstance.name == "fred"
        model.personInstance.hasErrors()
        model.personInstance.errors.getFieldError("dateOfBirth").code == "typeMismatch"
    }
}

@Entity
class NullBindingPerson {
    String name
    Date dateOfBirth

    static constraints = {
        dateOfBirth nullable: true
    }
}

@Artefact('Controller')
class NullBindingPersonController {

    def update = {
        def p = new NullBindingPerson()
        p.properties = params
        if (p.hasErrors()) {
            [personInstance:p]
        }
        else {
            redirect action:"foo"
        }
    }
}

