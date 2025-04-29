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
package grails.web.databinding

import grails.databinding.DataBindingSource;
import grails.databinding.SimpleMapDataBindingSource;
import grails.web.databinding.GrailsWebDataBinder;

import org.grails.web.databinding.converters.AbstractStructuredBindingEditor

import spock.lang.Specification

class GrailsWebDataBindingStructuredEditorSpec extends Specification {
    
    void 'Test structured editor'() {
        given: 'A binder with a structured editor registered'
        def binder = new GrailsWebDataBinder()

        // in a Grails app you wouldn't do this... if the editor is a bean
        // in the Spring application context it will be auto-discovered
        binder.setStructuredBindingEditors(new StucturedEmployeeEditor())

        def company = new Company()
        
        when: 'binding happens'
        binder.bind company, [president: 'struct', president_firstName: 'Tim', president_lastName: 'Cook',
                              vicePresident: 'struct', vicePresident_firstName: 'Eddy', vicePresident_lastName: 'Cue'] as SimpleMapDataBindingSource
                          
        then: 'the expected values were bound'
        'Tim' == company.president.firstName
        'Cook' == company.president.lastName
        'Eddy' == company.vicePresident.firstName
        'Cue' == company.vicePresident.lastName                    
    }
}

class Company {
    Employee president
    Employee vicePresident
}

class Employee {
    String firstName
    String lastName
}

class StucturedEmployeeEditor extends AbstractStructuredBindingEditor<Employee> {

    @Override
    public Employee getPropertyValue(Map values) {
        def employee = new Employee()
        employee.firstName = values.firstName
        employee.lastName = values.lastName
        employee
    }
}

