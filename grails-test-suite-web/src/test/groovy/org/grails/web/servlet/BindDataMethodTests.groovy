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

    void 'GAP: bindData does not crash on a non-numeric indexed parameter for a List property'() {
        when: 'a crafted request uses a non-numeric bracket index against a plain bindData call (no secureBindData involved)'
        params.'members[abc].name' = 'Hacked'
        def model = controller.bindWithNonNumericIndex()
        def target = model.target

        then: 'the malformed index should be ignored rather than blowing up the whole binding result'
        model.bindingResult == null || !model.bindingResult.hasErrors()
        target.members.size() == 1
        target.members[0].name == 'Existing'
        target.members[0].role == 'Existing'
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

    void 'Test secureBindData supports indexed parameter roots'() {
        when:
        params.'members[0].name' = 'Alice'
        params.'members[0].role' = 'Lead'
        params.'members[0].email' = 'blocked@example.com'
        params.'members[1].name' = 'Bob'
        params.'members[1].role' = 'Developer'
        def model = controller.secureBindWithIndexedParams()
        def target = model.target

        then:
        target.members.size() == 2
        target.members[0].name == 'Alice'
        target.members[0].role == 'Lead'
        target.members[0].email == null
        target.members[1].name == 'Bob'
        target.members[1].role == 'Developer'
    }

    void 'Test secureBindData supports prefixed indexed parameter roots'() {
        when:
        params.'team.members[0].name' = 'Alice'
        params.'team.members[0].role' = 'Lead'
        params.'team.members[0].email' = 'blocked@example.com'
        params.'team[0].members[0].name' = 'Blocked'
        params.'other.members[0].name' = 'Blocked'
        def model = controller.secureBindWithPrefixedIndexedParams()
        def target = model.target

        then:
        target.members.size() == 1
        target.members[0].name == 'Alice'
        target.members[0].role == 'Lead'
        model.bindingResult == null || !model.bindingResult.hasErrors()
        target.members[0].email == null
    }

    void 'Test secureBindData supports prefixed indexed map roots'() {
        when:
        def model = controller.secureBindWithPrefixedIndexedMapSource()
        def target = model.target

        then:
        target.members.size() == 1
        target.members[0].name == 'Alice'
        target.members[0].role == 'Lead'
        target.members[0].email == null
    }

    void 'Test secureBindData supports indexed map keys containing dots'() {
        when:
        params.'contributors[jane.doe].name' = 'Jane'
        params.'contributors[jane.doe].role' = 'Architect'
        params.'contributors[jane.doe].email' = 'blocked@example.com'
        def model = controller.secureBindWithDottedMapKey()
        def target = model.target

        then:
        target.contributors.size() == 1
        target.contributors['jane.doe'].name == 'Jane'
        target.contributors['jane.doe'].role == 'Architect'
        target.contributors['jane.doe'].email == null
    }

    void 'Test secureBindData supports nested indexed properties inside indexed map roots'() {
        when:
        def model = controller.secureBindWithNestedIndexedMapRoot()
        def target = model.target

        then:
        target.members.size() == 1
        target.members[0].addresses.size() == 1
        target.members[0].addresses[0].city == 'Portland'
        target.members[0].addresses[0].country == null
    }

    void 'Test secureBindData supports map-valued map roots'() {
        when:
        def model = controller.secureBindWithMapValuedMapRoot()
        def target = model.target

        then:
        target.contributors.size() == 2
        target.contributors.name.name == 'Named Entry'
        target.contributors.name.email == null
        target.contributors.lead.name == 'Jane'
        target.contributors.lead.role == 'Architect'
        target.contributors.lead.email == null
    }

    void 'Test secureBindData drops scalar entries from map-valued map roots'() {
        when:
        def model = controller.secureBindWithScalarMapEntryOnMapRoot()
        def target = model.target

        then:
        target.contributors.size() == 1
        !target.contributors.containsKey('name')
        target.contributors.lead.name == 'Jane'
        target.contributors.lead.role == 'Architect'
    }

    void 'Test secureBindData drops scalar-only map-valued map roots'() {
        when:
        def model = controller.secureBindWithScalarOnlyMapRoot()
        def target = model.target

        then:
        target.contributors.isEmpty()
    }

    void 'Test secureBindData drops flat scalar params for typed map roots'() {
        when:
        params.'contributors.name' = 'blocked'
        def model = controller.secureBindWithFlatScalarMapRootParam()
        def target = model.target

        then:
        target.contributors.isEmpty()
    }

    void 'Test secureBindData preserves direct map-valued allowed properties'() {
        when:
        def model = controller.secureBindWithDirectMapValuedProperty()
        def target = model.target

        then:
        target.address.preferences == [theme: 'dark', locale: 'en']
        target.address.country == null
    }

    void 'Test secureBindData preserves direct keys on map-valued properties'() {
        when:
        def model = controller.secureBindWithDirectMapKeyProperty()
        def target = model.target

        then:
        target.preferences == [theme: 'dark']
    }

    void 'Test secureBindData preserves direct keys on typed scalar map-valued properties'() {
        when:
        def model = controller.secureBindWithTypedScalarMapKeyProperty()
        def target = model.target

        then:
        target.dates == [start: new Date(0)]
    }

    void 'Test secureBindData supports typed map roots nested inside collection elements'() {
        when:
        def model = controller.secureBindWithTypedMapRootInsideCollection()
        def target = model.target

        then:
        target.departments.size() == 1
        target.departments[0].contributors.size() == 1
        target.departments[0].contributors.lead.name == 'Jane'
        target.departments[0].contributors.lead.role == 'Architect'
        target.departments[0].contributors.lead.email == null
    }

    void 'Test secureBindData nullMissing preserves indexed parameter roots'() {
        when:
        params.'members[0].name' = 'Alice'
        def model = controller.secureBindWithIndexedParamsAndNullMissing()
        def target = model.target

        then:
        target.members.size() == 1
        target.members[0].name == 'Alice'
        target.members[0].role == null
        model.bindingResult == null || !model.bindingResult.hasErrors()
    }

    void 'Test secureBindData nullMissing preserves nested indexed parameter roots'() {
        when:
        params.'departments[0].members[0].name' = 'Alice'
        def model = controller.secureBindWithNestedIndexedParamsAndNullMissing()
        def target = model.target

        then:
        target.departments.size() == 1
        target.departments[0].members.size() == 1
        target.departments[0].members[0].name == 'Alice'
        target.departments[0].members[0].role == null
        model.bindingResult == null || !model.bindingResult.hasErrors()
    }

    void 'GAP: secureBindData nullMissing does not crash on a non-numeric indexed parameter root'() {
        when: 'a crafted request uses a non-numeric bracket index against a List-typed allowed property'
        params.'members[abc].role' = 'Lead'
        def model = controller.secureBindWithIndexedParamsAndNullMissing()
        def target = model.target

        then: 'the malformed index should be ignored rather than blowing up the whole binding result'
        model.bindingResult == null || !model.bindingResult.hasErrors()
        target.members.size() == 1
        target.members[0].name == 'Existing'
        target.members[0].role == 'Existing'
    }

    void 'GAP: secureBindData nullMissing does not crash on an out-of-range indexed parameter root'() {
        when: 'a crafted request uses an indexed root beyond the auto-grow limit, which the binder silently ignores but nullMissing still tries to null out'
        params.'members[300].name' = 'Alice'
        def model = controller.secureBindWithIndexedParamsAndNullMissing()
        def target = model.target

        then: 'the out-of-range index should be ignored rather than blowing up the whole binding result'
        model.bindingResult == null || !model.bindingResult.hasErrors()
        target.members.size() == 1
        target.members[0].name == 'Existing'
        target.members[0].role == 'Existing'
    }

    void 'Test secureBindData nullMissing supports JSON collection roots'() {
        given:
        request.method = 'POST'
        request.json = '{"members":[{"name":"Alice"}]}'

        when:
        def model = controller.secureBindWithJsonCollectionAndNullMissing()
        def target = model.target

        then:
        target.members.size() == 1
        target.members[0].name == 'Alice'
        target.members[0].role == null
        model.bindingResult == null || !model.bindingResult.hasErrors()
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

    def bindWithNonNumericIndex() {
        def target = new CommandObject(members: [new Member(name: 'Existing', role: 'Existing')])
        def bindingResult = bindData(target, params)
        [target: target, bindingResult: bindingResult]
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
        secureBindData target, [address: [country: 'gbr', city: [country: 'blocked']]], ['address.country']
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

    def secureBindWithIndexedParams() {
        def target = new CommandObject()
        secureBindData target, params, ['members.name', 'members.role']
        [target: target]
    }

    def secureBindWithPrefixedIndexedParams() {
        def target = new CommandObject()
        secureBindData target, params, ['members.name', 'members.role'], 'team'
        [target: target]
    }

    def secureBindWithPrefixedIndexedMapSource() {
        def target = new CommandObject()
        secureBindData target, [
            'team.members[0]': [name: 'Alice', role: 'Lead', email: 'blocked@example.com'],
            'team[0].members[0]': [name: 'Blocked']
        ], ['members.name', 'members.role'], 'team'
        [target: target]
    }

    def secureBindWithDottedMapKey() {
        def target = new CommandObject()
        secureBindData target, params, ['contributors.name', 'contributors.role']
        [target: target]
    }

    def secureBindWithNestedIndexedMapRoot() {
        def target = new CommandObject()
        secureBindData target, [
            'members[0]': ['addresses[0].city': 'Portland', 'addresses[0].country': 'blocked']
        ], ['members.addresses.city']
        [target: target]
    }

    def secureBindWithMapValuedMapRoot() {
        def target = new CommandObject()
        secureBindData target, [
            contributors: [
                name: [name: 'Named Entry', email: 'blocked@example.com'],
                lead: [name: 'Jane', role: 'Architect', email: 'blocked@example.com']
            ]
        ], ['contributors.name', 'contributors.role']
        [target: target]
    }

    def secureBindWithScalarMapEntryOnMapRoot() {
        def target = new CommandObject()
        secureBindData target, [
            contributors: [
                name: 'blocked',
                lead: [name: 'Jane', role: 'Architect', email: 'blocked@example.com']
            ]
        ], ['contributors.name', 'contributors.role']
        [target: target]
    }

    def secureBindWithScalarOnlyMapRoot() {
        def target = new CommandObject()
        secureBindData target, [contributors: [name: 'blocked']], ['contributors.name']
        [target: target]
    }

    def secureBindWithFlatScalarMapRootParam() {
        def target = new CommandObject()
        secureBindData target, params, ['contributors.name']
        [target: target]
    }

    def secureBindWithDirectMapValuedProperty() {
        def target = new CommandObject()
        secureBindData target, [
            address: [preferences: [theme: 'dark', locale: 'en'], country: 'blocked']
        ], ['address.preferences']
        [target: target]
    }

    def secureBindWithDirectMapKeyProperty() {
        def target = new CommandObject()
        secureBindData target, [preferences: [theme: 'dark', locale: 'blocked']], ['preferences.theme']
        [target: target]
    }

    def secureBindWithTypedScalarMapKeyProperty() {
        def target = new CommandObject()
        secureBindData target, [dates: [start: new Date(0), end: new Date(1)]], ['dates.start']
        [target: target]
    }

    def secureBindWithTypedMapRootInsideCollection() {
        def target = new CommandObject()
        secureBindData target, [
            departments: [[
                contributors: [
                    lead: [name: 'Jane', role: 'Architect', email: 'blocked@example.com']
                ]
            ]]
        ], ['departments.contributors.name', 'departments.contributors.role']
        [target: target]
    }

    def secureBindWithIndexedParamsAndNullMissing() {
        def target = new CommandObject(members: [new Member(name: 'Existing', role: 'Existing')])
        def bindingResult = secureBindData(target, params, ['members.name', 'members.role'], nullMissing: true)
        [target: target, bindingResult: bindingResult]
    }

    def secureBindWithNestedIndexedParamsAndNullMissing() {
        def target = new CommandObject(departments: [new Department(members: [new Member(name: 'Existing', role: 'Existing')])])
        def bindingResult = secureBindData(target, params, ['departments.members.name', 'departments.members.role'], nullMissing: true)
        [target: target, bindingResult: bindingResult]
    }

    def secureBindWithJsonCollectionAndNullMissing() {
        def target = new CommandObject(members: [new Member(name: 'Existing', role: 'Existing')])
        def bindingResult = secureBindData(target, request, ['members.name', 'members.role'], nullMissing: true)
        [target: target, bindingResult: bindingResult]
    }
}

class CommandObject {
    String name
    String email
    Boolean active
    Address address = new Address()
    List<Member> members = []
    List<Department> departments = []
    Map<String, Member> contributors = [:]
    Map preferences = [:]
    Map<String, Date> dates = [:]
}

class Address {
    String country
    String city
    Map preferences = [:]
}

class Member {
    String name
    String role
    String email
    List<Address> addresses = []
}

class Department {
    List<Member> members = []
    Map<String, Member> contributors = [:]
}
