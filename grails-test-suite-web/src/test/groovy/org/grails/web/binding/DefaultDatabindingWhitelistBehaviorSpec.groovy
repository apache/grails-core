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
import grails.testing.web.controllers.ControllerUnitTest
import grails.validation.Validateable
import grails.web.databinding.DataBindingUtils
import spock.lang.Specification

class DefaultDatabindingWhitelistBehaviorSpec extends Specification implements ControllerUnitTest<WhitelistBehaviorController> {

    // Domain exclusion of id/version/dateCreated/lastUpdated is already pinned by
    // DefaultASTDatabindingHelperDomainClassSpecialPropertiesSpec (GRAILS-11173, #15681); this spec
    // only covers the Object/def-typed exclusion and nested association binding.
    void 'domain binding includes simple and association properties but excludes Object/def-typed properties'() {
        given:
        Map source = [
                name: 'Ada',
                address: [street: 'Analytical Engine Way'],
                untypedProperty: 'not bindable'
        ]

        when:
        WhitelistDomain domain = new WhitelistDomain()
        DataBindingUtils.bindObjectToInstance(domain, source)

        then:
        domain.name == 'Ada'
        domain.address.street == 'Analytical Engine Way'
        domain.untypedProperty == null
    }

    void 'Validateable command binding includes declared special properties but excludes Object/def-typed properties'() {
        given:
        Date dateCreated = new Date()
        Date lastUpdated = new Date()
        params.name = 'Grace'
        params.'address.street' = 'Compiler Lane'
        params.id = '99'
        params.version = '7'
        params.dateCreated = dateCreated
        params.lastUpdated = lastUpdated
        params.untypedProperty = 'not bindable'

        when:
        WhitelistCommand command = controller.bindCommand().command

        then:
        command.name == 'Grace'
        command.address.street == 'Compiler Lane'
        command.id == 99L
        command.version == 7L
        command.dateCreated == dateCreated
        command.lastUpdated == lastUpdated
        command.untypedProperty == null
    }
}

@Entity
class WhitelistDomain {
    String name
    WhitelistAddress address = new WhitelistAddress()
    Object untypedProperty
}

@Entity
class WhitelistAddress {
    String street
}

@Artefact('Controller')
class WhitelistBehaviorController {
    def bindCommand(WhitelistCommand command) {
        [command: command]
    }
}

class WhitelistCommand implements Validateable {
    String name
    WhitelistAddress address = new WhitelistAddress()
    Long id
    Long version
    Date dateCreated
    Date lastUpdated
    Object untypedProperty
}
