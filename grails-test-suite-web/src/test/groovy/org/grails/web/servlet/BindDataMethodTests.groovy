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
package org.grails.web.servlet

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * Tests for the bindData method
 *
 */
class BindDataMethodTests extends Specification implements ControllerUnitTest<BindingController> {

    void 'Test bindData with Map'() {
        when:
        def model = controller.bindWithMap()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
    }

    void 'Test bindData With Excludes'() {
        when:
        def model = controller.bindWithExcludes()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
        target.email == null
    }

    void 'Test bindData With Includes'() {
        when:
        def model = controller.bindWithIncludes()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
        target.email == null
    }

    void 'Test bindData With Empty Includes/Excludes Map'() {
        when:
        def model = controller.bindWithEmptyIncludesExcludesMap()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
        target.email == 'dowantthis'
    }

    void 'Test bindData Overriding Included With Excluded'() {
        when:
        def model = controller.bindWithIncludeOverriddenByExclude()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
        target.email == null
    }

    void 'Test bindData With Prefix Filter'() {
        when:
        def model = controller.bindWithPrefixFilter()
        def target = model.target

        then:
        target.name == 'Lee Butts'
        target.email == 'lee@mail.com'
    }

    void 'Test bindData With Disallowed And GrailsParameterMap'() {
        when:
        params.name = 'Marc Palmer'
        params.email = 'dontwantthis'
        params.'address.country' = 'gbr'
        def model = controller.bindWithParamsAndDisallowed()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
        target.address.country == 'gbr'
        target.email == null
    }

    void 'Test bindData With Prefix Filter And Disallowed'() {
        when:
        def model = controller.bindWithPrefixFilterAndDisallowed()
        def target = model.target

        then:
        target.name == 'Lee Butts'
        target.email == null
    }

    void 'Test bindData Converts Single String In Map To List'() {
        when:
        def model = controller.bindWithStringConvertedToList()
        def target = model.target

        then:
        target.name == 'Lee Butts'
        target.email == null
    }

    void 'Test secureBindData binds only allowed params'() {
        when:
        def model = controller.secureBindWithAllowedParams()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
        target.email == null
    }

    void 'Test secureBindData with empty allowed params binds no params'() {
        when:
        def model = controller.secureBindWithEmptyAllowedParams()
        def target = model.target

        then:
        target.name == 'Existing'
        target.email == 'existing@example.com'
    }

    void 'Test secureBindData nulls missing allowed params when requested'() {
        when:
        def model = controller.secureBindWithNullMissing()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
        target.email == null
    }

    void 'Test secureBindData supports prefix filter'() {
        when:
        def model = controller.secureBindWithPrefixFilter()
        def target = model.target

        then:
        target.name == 'Lee Butts'
        target.email == null
    }

    void 'Test secureBindData only binds allowed nested map properties'() {
        when:
        def model = controller.secureBindWithNestedMap()
        def target = model.target

        then:
        target.address.country == 'gbr'
        target.address.city == null
    }

    void 'Test secureBindData nullMissing preserves supplied nested map properties'() {
        when:
        def model = controller.secureBindWithNestedMapAndNullMissing()
        def target = model.target

        then:
        target.address.country == 'gbr'
        target.address.city == null
    }

    void 'Test secureBindData returns binding errors for invalid JSON'() {
        given:
        request.method = 'POST'
        request.json = '''
            {
    "name": [foo.[} this is unparseable JSON{[
'''

        when:
        def model = controller.secureBindWithMalformedJson()
        def bindingResult = model.bindingResult

        then:
        bindingResult.hasErrors()
        bindingResult.errorCount == 1
        bindingResult.allErrors[0].defaultMessage == 'An error occurred parsing the body of the request'
        bindingResult.allErrors[0].code == 'invalidRequestBody'
        'invalidRequestBody' in bindingResult.allErrors[0].codes
        'org.grails.web.servlet.CommandObject.invalidRequestBody' in bindingResult.allErrors[0].codes
    }

    void 'Test secureBindData nullMissing supports JSON request bodies'() {
        given:
        request.method = 'POST'
        request.json = '{"name":"Marc Palmer"}'

        when:
        def model = controller.secureBindWithJsonAndNullMissing()
        def target = model.target

        then:
        target.name == 'Marc Palmer'
        target.email == null
    }

    void 'Test secureBindData supports unchecked checkbox marker parameters'() {
        when:
        params._active = 'on'
        def model = controller.secureBindWithUncheckedCheckboxMarker()
        def target = model.target

        then:
        target.active == false
    }

    void 'Test secureBindData nullMissing preserves unchecked checkbox marker parameters'() {
        when:
        params._active = 'on'
        def model = controller.secureBindWithUncheckedCheckboxMarkerAndNullMissing()
        def target = model.target

        then:
        target.active == false
        target.email == null
    }
}

@Artefact('Controller')
class BindingController {

    def bindWithMap() {
        def target = new CommandObject()
        bindData target, [ name : 'Marc Palmer' ]
        [target: target]
    }

    def bindWithExcludes() {
        def target = new CommandObject()
        bindData target, [name: 'Marc Palmer', email: 'dontwantthis'], [exclude: ['email']]
        [target: target]
    }

    def bindWithIncludes() {
        def target = new CommandObject()
        bindData target, [ name : 'Marc Palmer', email : 'dontwantthis' ], [include:['name']]
        [target: target]
    }

    def bindWithEmptyIncludesExcludesMap() {
        def target = new CommandObject()
        bindData target, [ name : 'Marc Palmer', email : 'dowantthis' ], [:]
        [target: target]
    }

    def bindWithIncludeOverriddenByExclude() {
        def target = new CommandObject()
        bindData target, [ name : 'Marc Palmer', email : 'dontwantthis' ], [include: ['name', 'email'], exclude: ['email']]
        [target: target]
    }

    def bindWithPrefixFilter() {
        def target = new CommandObject()
        def filter = "lee"
        bindData target, [ 'mark.name' : 'Marc Palmer', 'mark.email' : 'dontwantthis', 'lee.name': 'Lee Butts', 'lee.email': 'lee@mail.com'], filter
        [target: target]
    }

    def bindWithParamsAndDisallowed() {
        def target = new CommandObject()
        bindData target, params, [exclude:['email']]
        [target: target]
    }

    def bindWithPrefixFilterAndDisallowed() {
        def target = new CommandObject()
        def filter = "lee"
        def disallowed = [exclude:["email"]]
        bindData target, [ 'mark.name' : 'Marc Palmer', 'mark.email' : 'dontwantthis', 'lee.name': 'Lee Butts', 'lee.email': 'lee@mail.com'], disallowed, filter
        [target: target]
    }

    def bindWithStringConvertedToList() {
        def target = new CommandObject()
        def filter = "lee"
        def disallowed = [exclude:"email"]
        bindData target, [ 'mark.name' : 'Marc Palmer', 'mark.email' : 'dontwantthis', 'lee.name': 'Lee Butts', 'lee.email': 'lee@mail.com'], disallowed, filter
        [target: target]
    }

    def secureBindWithAllowedParams() {
        def target = new CommandObject()
        secureBindData target, [name: 'Marc Palmer', email: 'dontwantthis'], ['name']
        [target: target]
    }

    def secureBindWithEmptyAllowedParams() {
        def target = new CommandObject(name: 'Existing', email: 'existing@example.com')
        secureBindData target, [name: 'Marc Palmer', email: 'dontwantthis'], []
        [target: target]
    }

    def secureBindWithNullMissing() {
        def target = new CommandObject(name: 'Existing', email: 'existing@example.com')
        secureBindData(target, [name: 'Marc Palmer'], ['name', 'email'], nullMissing: true)
        [target: target]
    }

    def secureBindWithPrefixFilter() {
        def target = new CommandObject()
        secureBindData target, ['mark.name': 'Marc Palmer', 'mark.email': 'dontwantthis', 'lee.name': 'Lee Butts', 'lee.email': 'lee@mail.com'], ['name'], 'lee'
        [target: target]
    }

    def secureBindWithNestedMap() {
        def target = new CommandObject()
        secureBindData target, [address: [country: 'gbr', city: 'dontwantthis']], ['address.country']
        [target: target]
    }

    def secureBindWithNestedMapAndNullMissing() {
        def target = new CommandObject(address: new Address(country: 'existing', city: 'existing'))
        secureBindData(target, [address: [country: 'gbr']], ['address.country', 'address.city'], nullMissing: true)
        [target: target]
    }

    def secureBindWithMalformedJson() {
        def target = new CommandObject()
        def bindingResult = secureBindData target, request, ['name']
        [target: target, bindingResult: bindingResult]
    }

    def secureBindWithJsonAndNullMissing() {
        def target = new CommandObject(name: 'Existing', email: 'existing@example.com')
        secureBindData(target, request, ['name', 'email'], nullMissing: true)
        [target: target]
    }

    def secureBindWithUncheckedCheckboxMarker() {
        def target = new CommandObject(active: true)
        secureBindData target, params, ['active']
        [target: target]
    }

    def secureBindWithUncheckedCheckboxMarkerAndNullMissing() {
        def target = new CommandObject(active: true, email: 'existing@example.com')
        secureBindData(target, params, ['active', 'email'], nullMissing: true)
        [target: target]
    }
}

class CommandObject {
    String name
    String email
    Boolean active
    Address address = new Address()
}

class Address {
    String country
    String city
}
