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
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class BindStringArrayToGenericListTests extends Specification implements ControllerUnitTest<MenuController>, DomainUnitTest<Menu> {

    void testBindStringArrayToGenericList() {
        when:
        params.name = "day"
        params.items = ['rice', 'soup']as String[]
        def model = controller.save()

        then:
        ['rice', 'soup'] == model.menu.items
    }
}

@Artefact('Controller')
class MenuController {

    def save = {
        def m = new Menu(params)
        [menu:m]
    }
}

@Entity
class Menu {

    String name
    static hasMany = [items: String]

    List<String> items
}
