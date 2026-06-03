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
import grails.gorm.dirty.checking.DirtyCheck
import grails.persistence.Entity
import grails.testing.web.controllers.ControllerUnitTest
import groovy.transform.CompileStatic
import spock.lang.Issue
import spock.lang.Specification

/**
 * Reproduces the controller {@code bindData(domain, params)} scenario from the bug report: a domain class
 * that extends an abstract {@code @DirtyCheck} base class must never bind {@code id} or {@code version} by default.
 */
class DirtyCheckBaseBindDataSpec extends Specification implements ControllerUnitTest<DirtyCheckLedgerController> {

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test bindData does not bind id or version when the domain extends a @DirtyCheck base'() {
        given: 'request params that include id and version alongside a regular property'
        params.id = '99'
        params.version = '5'
        params.description = 'Opening balance'

        when: 'the controller binds the params to a new domain instance'
        def model = controller.create()
        def entry = model.entry

        then: 'the regular property is bound'
        entry.description == 'Opening balance'

        and: 'id and version are not bound, matching the documented default behaviour'
        entry.id == null
        entry.version == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test the documented exclude workaround continues to prevent id binding'() {
        given:
        params.id = '99'
        params.description = 'Closing balance'

        when:
        def model = controller.createWithExcludeWorkaround()
        def entry = model.entry

        then:
        entry.description == 'Closing balance'
        entry.id == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test an explicit include list binds only the listed property and never id'() {
        given:
        params.id = '99'
        params.description = 'Adjustment'

        when:
        def model = controller.createWithIncludes()
        def entry = model.entry

        then: 'only the included property is bound'
        entry.description == 'Adjustment'

        and: 'id is still not bound'
        entry.id == null
    }

    @Issue('https://github.com/apache/grails-core/issues/15681')
    void 'Test binding a Map directly through bindData does not bind id'() {
        when: 'a plain Map (rather than the request params) is bound'
        def model = controller.createFromMap()
        def entry = model.entry

        then:
        entry.description == 'From map'
        entry.id == null
        entry.version == null
    }
}

@DirtyCheck
@CompileStatic
abstract class AbstractAuditableLedgerBase {
    String description
}

@Entity
class DirtyCheckLedgerEntry extends AbstractAuditableLedgerBase {
    static constraints = {
        description nullable: true
    }
}

@Artefact('Controller')
class DirtyCheckLedgerController {

    def create() {
        def entry = new DirtyCheckLedgerEntry()
        bindData(entry, params)
        [entry: entry]
    }

    def createWithExcludeWorkaround() {
        def entry = new DirtyCheckLedgerEntry()
        bindData(entry, params, [exclude: ['id']])
        [entry: entry]
    }

    def createWithIncludes() {
        def entry = new DirtyCheckLedgerEntry()
        bindData(entry, params, [include: ['description']])
        [entry: entry]
    }

    def createFromMap() {
        def entry = new DirtyCheckLedgerEntry()
        bindData(entry, [id: '99', version: '5', description: 'From map'])
        [entry: entry]
    }
}
