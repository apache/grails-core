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
package grails.databinding

import grails.databinding.DataBindingSource;
import grails.databinding.SimpleDataBinder;
import grails.databinding.SimpleMapDataBindingSource;
import grails.databinding.StructuredBindingEditor;
import spock.lang.Specification

class CustomTypeConverterSpec extends Specification {

    void 'Test custom type converter'() {
        given:
        def binder = new SimpleDataBinder()
        binder.registerStructuredEditor Address, new StructuredAddressBindingEditor()
        def resident = new Resident()
        def bindingSource = [:]
        bindingSource.name = 'Scott'
        bindingSource.homeAddress_someCity = "Scott's Home City"
        bindingSource.homeAddress_someState = "Scott's Home State"
        bindingSource.workAddress_someState = "Scott's Work State"
        bindingSource.workAddress = 'struct'
        bindingSource.homeAddress = 'struct'

        when:
        binder.bind resident, new SimpleMapDataBindingSource(bindingSource)

        then:
        resident.name == 'Scott'
        resident.homeAddress
        resident.homeAddress.city == "Scott's Home City"
        resident.homeAddress.state == "Scott's Home State"
        resident.workAddress
        resident.workAddress.state == "Scott's Work State"
        resident.workAddress.city == null

        // make sure the custome editor does not get in the way when the value being bound does not need to be converted
        when:
        resident = new Resident()
        binder.bind resident, new SimpleMapDataBindingSource([homeAddress: new Address(state: 'Some State', city: 'Some City')])

        then:
        resident.homeAddress.state == 'Some State'
        resident.homeAddress.city == 'Some City'
    }
}

class Resident {
    String name
    Address homeAddress
    Address workAddress
}

class Address {
    String state
    String city
}

class StructuredAddressBindingEditor implements StructuredBindingEditor<Address> {

    @Override
    Address getPropertyValue(Object obj, String propertyName, DataBindingSource source) {
        def address = new Address()

        address.state = source[propertyName + '_someState']
        address.city = source[propertyName + '_someCity']

        address
    }
}
