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
    // only covers nested association binding, auto-inclusion of extra typed properties, and
    // Object/def-typed exclusion (DefaultASTDatabindingHelper#shouldFieldBeInWhiteList).
    void 'domain binding includes simple, extra typed, and association properties but excludes Object/def-typed properties'() {
        given:
        Map source = [
                name: 'Ada',
                extra: 'typed extra',
                address: [street: 'Analytical Engine Way'],
                untypedProperty: 'not bindable',
                untypedDefProperty: 'also not bindable'
        ]

        when:
        WhitelistDomain domain = new WhitelistDomain()
        DataBindingUtils.bindObjectToInstance(domain, source)

        then:
        domain.name == 'Ada'
        domain.extra == 'typed extra'
        domain.address.street == 'Analytical Engine Way'
        domain.untypedProperty == null
        domain.untypedDefProperty == null
    }

    void 'Validateable command binding includes declared special properties but excludes Object/def-typed properties'() {
        given:
        Date dateCreated = new Date()
        Date lastUpdated = new Date()
        params.name = 'Grace'
        params.extra = 'typed extra'
        params.'address.street' = 'Compiler Lane'
        params.id = '99'
        params.version = '7'
        params.dateCreated = dateCreated
        params.lastUpdated = lastUpdated
        params.untypedProperty = 'not bindable'
        params.untypedDefProperty = 'also not bindable'

        when:
        WhitelistCommand command = controller.bindCommand().command

        then:
        command.name == 'Grace'
        command.extra == 'typed extra'
        command.address.street == 'Compiler Lane'
        command.id == 99L
        command.version == 7L
        command.dateCreated == dateCreated
        command.lastUpdated == lastUpdated
        command.untypedProperty == null
        command.untypedDefProperty == null
    }
}

@Entity
class WhitelistDomain {
    String name
    String extra
    WhitelistAddress address = new WhitelistAddress()
    Object untypedProperty
    def untypedDefProperty
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
    String extra
    WhitelistAddress address = new WhitelistAddress()
    Long id
    Long version
    Date dateCreated
    Date lastUpdated
    Object untypedProperty
    def untypedDefProperty
}
