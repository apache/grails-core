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
import grails.gorm.transactions.Transactional
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.validation.Validateable
import spock.lang.Issue
import spock.lang.Specification

class BindCommandObjectsSpec extends Specification implements ControllerUnitTest<BindCommandController>, DomainUnitTest<MyAuthor>  {
    static final String NAME = 'My name is...'

    @Issue('https://github.com/apache/grails-data-mapping/issues/1145')
    void "Test bind command to domain with constraints"() {
        when:
        def model = controller.bindDomain()

        then:
        model.domain
        !model.domain.hasErrors()
        model.domain.name == '111'
    }


    @Issue('https://github.com/apache/grails-core/issues/11054')
    void "Test bind domain to command"() {
        when:
        controller.createDomain()
        def model = controller.bindDomainToCommand()

        then:
        model.domain
        model.domain.name == BindCommandObjectsSpec.NAME
        model.command.name == BindCommandObjectsSpec.NAME
        model.command.placeOfBirth
    }
}

@Artefact('Controller')
class BindCommandController {
    def bindDomain(){
        def domain = new MyAuthor(hairColour: "black")
        def command = new AuthorFieldCommand(name: "111")
        bindData(domain, command, [exclude: ['placeOfBirth']])
        domain.validate()
        [domain:domain]
    }

    @Transactional
    def createDomain(){
        def city = new MyCity(name: 'BIG').save()
        def a = new MyAuthor(name: BindCommandObjectsSpec.NAME, placeOfBirth: city, hairColour: 'red' ).save(flush:true)

    }

    def bindDomainToCommand(){
        def domain = MyAuthor.findByName(BindCommandObjectsSpec.NAME)
        def command = new AuthorFieldCommand()
        bindData(command, domain)
        [command:command, domain:domain]
    }
}


@Entity
class MyAuthor {
    String name
    String hairColour
    MyCity placeOfBirth

    static constraints = {
        name nullable:true
        placeOfBirth nullable:true
    }
}
@Entity
class MyCity {
    String name
}


class AuthorFieldCommand implements Validateable{
    MyCity placeOfBirth
    String name
    static constraints = {
        placeOfBirth nullable:true
    }
}

