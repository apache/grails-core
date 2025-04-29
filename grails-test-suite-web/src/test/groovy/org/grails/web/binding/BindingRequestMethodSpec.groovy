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

class BindingRequestMethodSpec extends Specification implements ControllerUnitTest<BindingController>, DomainUnitTest<Employee> {

    void 'Test binding to a domain class command object'() {
        when:
        request.method = 'POST'
        params.firstName = 'Zack'
        params.lastName = 'Brown'
        def model = controller.createEmployee()
        def employee = model.employee
        def originalId = employee.id

        then:
        originalId != null

        when: 'Submitting a GET request with an id that matches an existing record'
        request.method = 'GET'
        params.clear()
        params.id = originalId
        params.firstName = 'Zachary'
        params.remove 'lastName'
        model = controller.someActionWhichAcceptsAnEmployee()
        employee = model.employee

        then: 'Data binding should not have happened'
        employee.firstName == 'Zack'
        employee.lastName == 'Brown'

        when: 'Submitting a PUT request with an id that matches an existing record'
        request.method = 'PUT'
        params.clear()
        params.id = originalId
        params.firstName = 'Zachary'
        model = controller.someActionWhichAcceptsAnEmployee()
        employee = model.employee

        then: 'Data binding should have happened'
        employee.firstName == 'Zachary'
        employee.lastName == 'Brown'

        when: 'Submitting a POST request with an id that matches an existing record'
        request.method = 'POST'
        params.clear()
        params.id = originalId
        params.firstName = 'Jake'
        model = controller.someActionWhichAcceptsAnEmployee()
        employee = model.employee

        then: 'Data binding should have happened'
        employee.firstName == 'Jake'
        employee.lastName == 'Brown'

        when: 'Submitting a GET request with no id'
        request.method = 'GET'
        params.clear()
        params.firstName = 'Zachary'
        params.lastName = 'Brown'
        model = controller.someActionWhichAcceptsAnEmployee()
        employee = model.employee

        then: 'no instance should have been created'
        employee == null

        when: 'Submitting a PUT request with no id'
        request.method = 'PUT'
        params.clear()
        params.firstName = 'Zack'
        params.lastName = 'Browning'
        model = controller.someActionWhichAcceptsAnEmployee()
        employee = model.employee

        then: 'no instance should have been created'
        employee == null

        when: 'Submitting a POST request with no id'
        request.method = 'POST'
        params.clear()
        params.firstName = 'Jake'
        params.lastName = 'Brown'
        model = controller.someActionWhichAcceptsAnEmployee()
        employee = model.employee

        then: 'Data binding should have happened'
        employee.firstName == 'Jake'
        employee.lastName == 'Brown'
        employee.id == null
    }

    void 'Test binding to a non-domain class command object'() {
        when: 'Submitting a GET request'
        request.method = 'GET'
        params.firstName = 'Zack'
        params.lastName = 'Brown'
        def model = controller.someActionWhichAcceptsMyCommandObject()
        def command = model.command

        then: 'Data binding should have happened'
        command.firstName == 'Zack'
        command.lastName == 'Brown'

        when: 'Submitting a PUT request'
        request.method = 'PUT'
        params.clear()
        params.firstName = 'Zachary'
        params.lastName = 'Browning'
        model = controller.someActionWhichAcceptsMyCommandObject()
        command = model.command

        then: 'Data binding should have happened'
        command.firstName == 'Zachary'
        command.lastName == 'Browning'

        when: 'Submitting a POST request'
        request.method = 'POST'
        params.clear()
        params.firstName = 'Jake'
        params.lastName = 'Brown'
        model = controller.someActionWhichAcceptsMyCommandObject()
        command = model.command

        then: 'Data binding should have happened'
        command.firstName == 'Jake'
        command.lastName == 'Brown'
    }
}

@Artefact('Controller')
class BindingController {
    def createEmployee(Employee employee) {
        employee.save()
        [employee: employee]
    }

    def someActionWhichAcceptsAnEmployee(Employee employee) {
        [employee: employee]
    }

    def someActionWhichAcceptsMyCommandObject(MyCommandObject command) {
        [command: command]
    }
}

@Entity
class Employee {
    String firstName
    String lastName
}

class MyCommandObject {
    String firstName
    String lastName
}
