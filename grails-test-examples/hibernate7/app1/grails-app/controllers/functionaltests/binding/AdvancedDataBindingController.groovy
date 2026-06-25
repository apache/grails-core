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

package functionaltests.binding

import grails.converters.JSON
import grails.databinding.SimpleMapDataBindingSource
import grails.validation.Validateable
import grails.web.RequestParameter

/**
 * Controller for testing advanced data binding features.
 */
class AdvancedDataBindingController {

    def grailsWebDataBinder

    /**
     * Field lists for explicit data binding.
     * Each domain type has its own field list to control which properties can be bound.
     */
    def employeeBindParamsBasic = ['firstName', 'lastName', 'email', 'salary', 'homeAddress']
    def employeeBindParamsFull = ['firstName', 'lastName', 'email', 'salary', 'homeAddress', 'hireDate', 'birthDate']
    def employeeBindParamsMinimal = ['firstName', 'lastName']
    def teamBindParams = ['name', 'members']
    def projectBindParams = ['name', 'description', 'contributors']

    /**
     * Test basic map-based binding with nested objects.
     */
    def bindEmployee() {
        def employee = new Employee(params.subMap(employeeBindParamsBasic))
        render([
            firstName: employee.firstName,
            lastName: employee.lastName,
            email: employee.email,
            salary: employee.salary,
            homeAddress: employee.homeAddress ? [
                street: employee.homeAddress.street,
                city: employee.homeAddress.city,
                state: employee.homeAddress.state
            ] : null
        ] as JSON)
    }
    
    /**
     * Test @BindUsing annotation - email should be lowercased.
     */
    def bindWithBindUsing() {
        def employee = new Employee(params.subMap(employeeBindParamsFull))
        render([
            email: employee.email,
            originalEmail: params.email
        ] as JSON)
    }
    
    /**
     * Test @BindingFormat annotation for dates.
     */
    def bindWithDateFormat() {
        def employee = new Employee(params.subMap(employeeBindParamsFull))
        render([
            hireDate: employee.hireDate?.format('yyyy-MM-dd'),
            birthDate: employee.birthDate?.format('yyyy-MM-dd'),
            hireDateInput: params.hireDate,
            birthDateInput: params.birthDate
        ] as JSON)
    }
    
    /**
     * Test collection binding to List.
     */
    def bindTeamWithMembers() {
        def team = new Team()
        bindData(team, params, [include: teamBindParams])
        render([
            name: team.name,
            members: team.members?.findAll { it != null }?.collect { [name: it.name, role: it.role] } ?: []
        ] as JSON)
    }
    
    /**
     * Test Map-based collection binding.
     */
    def bindProjectWithContributors() {
        def project = new Project()
        bindData(project, params, [include: projectBindParams])
        def contributorsMap = project.contributors?.collectEntries { k, v ->
            [k, [name: v?.name, expertise: v?.expertise]]
        } ?: [:]
        render([
            name: project.name,
            contributors: contributorsMap
        ] as JSON)
    }
    
    /**
     * Test binding with @RequestParameter annotation.
     */
    def bindWithRequestParameter(
        @RequestParameter('firstName') String givenName,
        @RequestParameter('lastName') String familyName,
        Integer age
    ) {
        render([
            givenName: givenName,
            familyName: familyName,
            age: age
        ] as JSON)
    }
    
    /**
     * Test bindData method with include/exclude.
     */
    def bindWithIncludeExclude() {
        def employee = new Employee(params.subMap(employeeBindParamsMinimal))
        render([
            firstName: employee.firstName,
            lastName: employee.lastName,
            email: employee.email,  // Should be null
            salary: employee.salary // Should be null
        ] as JSON)
    }
    
    /**
     * Test selective property binding using subscript operator.
     */
    def bindSelectiveProperties() {
        def employee = new Employee()
        employee.properties['firstName', 'lastName'] = params
        render([
            firstName: employee.firstName,
            lastName: employee.lastName,
            email: employee.email,  // Should be null
            salary: employee.salary // Should be null
        ] as JSON)
    }
    
    /**
     * Test direct data binder usage in service-like scenario.
     */
    def bindUsingDirectBinder() {
        def employee = new Employee()
        grailsWebDataBinder.bind(employee, params as SimpleMapDataBindingSource)
        render([
            firstName: employee.firstName,
            lastName: employee.lastName,
            email: employee.email
        ] as JSON)
    }
    
    /**
     * Test type conversion errors.
     */
    def bindWithTypeConversion(Integer salary, String firstName) {
        def hasErrors = hasErrors()
        render([
            salary: salary,
            firstName: firstName,
            hasErrors: hasErrors,
            errorCount: errors?.errorCount ?: 0
        ] as JSON)
    }
    
    /**
     * Test command object binding.
     */
    def bindCommandObject(EmployeeCommand cmd) {
        render([
            firstName: cmd.firstName,
            lastName: cmd.lastName,
            email: cmd.email,
            valid: cmd.validate(),
            errors: cmd.errors?.allErrors?.collect { it.field } ?: []
        ] as JSON)
    }
    
    /**
     * Test nested command object binding.
     */
    def bindNestedCommandObject(ContactCommand cmd) {
        render([
            name: cmd.name,
            address: cmd.address ? [
                street: cmd.address.street,
                city: cmd.address.city
            ] : null,
            valid: cmd.validate()
        ] as JSON)
    }
    
    /**
     * Test binding JSON request body to command object.
     */
    def bindJsonBody(EmployeeCommand cmd) {
        render([
            firstName: cmd.firstName,
            lastName: cmd.lastName,
            email: cmd.email,
            valid: cmd.validate()
        ] as JSON)
    }
    
    /**
     * Test multiple command objects.
     */
    def bindMultipleCommandObjects(EmployeeCommand employee, AddressCommand address) {
        render([
            employee: [
                firstName: employee.firstName,
                lastName: employee.lastName
            ],
            address: [
                street: address.street,
                city: address.city
            ]
        ] as JSON)
    }
    
    /**
     * Test empty string to null conversion.
     */
    def bindEmptyStrings() {
        def employee = new Employee(params.subMap(employeeBindParamsFull))
        render([
            firstName: employee.firstName,
            firstNameIsNull: employee.firstName == null,
            lastName: employee.lastName,
            lastNameIsNull: employee.lastName == null
        ] as JSON)
    }
    
    /**
     * Test string trimming during binding.
     */
    def bindWithTrimming() {
        def employee = new Employee(params.subMap(employeeBindParamsFull))
        render([
            firstName: employee.firstName,
            firstNameLength: employee.firstName?.length() ?: 0,
            originalFirstName: params.firstName,
            originalLength: params.firstName?.length() ?: 0
        ] as JSON)
    }
}

/**
 * Command object for employee data.
 */
class EmployeeCommand implements Validateable {
    String firstName
    String lastName
    String email
    
    static constraints = {
        firstName blank: false
        lastName blank: false
        email nullable: true, email: true
    }
}

/**
 * Command object for address data.
 */
class AddressCommand implements Validateable {
    String street
    String city
    String state
    String zipCode
    
    static constraints = {
        street nullable: true
        city nullable: true
        state nullable: true
        zipCode nullable: true
    }
}

/**
 * Command object with nested address.
 */
class ContactCommand implements Validateable {
    String name
    AddressCommand address
    
    static constraints = {
        name blank: false
        address nullable: true
    }
}
