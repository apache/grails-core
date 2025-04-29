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

import grails.artefact.Artefact
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.junit.Test
import spock.lang.Specification

/**
 * @author Rob Fletcher
 * @since 1.3.0
 */
class CheckboxBindingTests extends Specification implements ControllerUnitTest<CheckboxBindingController>, DomainUnitTest<Pizza> {

    void testBindingCheckedValuesToObject() {
        given:
        params.name = "Capricciosa"
        params."_delivery" = ""
        params."delivery" = "on"
        params."options._extraAnchovies" = ""
        params."options.extraAnchovies" = "on"
        params."options._stuffedCrust" = ""
        params."options.stuffedCrust" = "on"

        when:
        def model = controller.save()

        then:
        model.pizza.name == "Capricciosa"
        model.pizza.delivery
        model.pizza.options.extraAnchovies
        model.pizza.options.stuffedCrust
    }

    void testBindingUncheckedValuesToObject() {
        given:
        params.name = "Capricciosa"
        params."_delivery" = ""
        params.options = [_extraAnchovies: '', _stuffedCrust: '']

        when:
        def model = controller.save()

        then:
        model.pizza.name == "Capricciosa"
        !model.pizza.delivery
        !model.pizza.options.extraAnchovies
        !model.pizza.options.stuffedCrust
    }
}

@Entity
class Pizza {
    String name
    boolean delivery = true
    Options options = new Options()
    static embedded = ["options"]
}

class Options {
    boolean extraAnchovies = true
    boolean stuffedCrust = true
}

@Artefact('Controller')
class CheckboxBindingController {
    def save = {
        def p = new Pizza(params)
        [pizza: p]
    }
}
