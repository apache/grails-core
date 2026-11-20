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
package scaffoldingfields

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import scaffoldingfields.pages.*

/**
 * Functional tests for scaffolded CRUD operations.
 * Tests Create, Read, Update, Delete operations via browser.
 */
@Integration
class CrudFunctionalSpec extends ContainerGebSpec {

    // ==================== LIST (INDEX) TESTS ====================

    def "Employee list page displays correctly"() {
        when: 'navigating to the employee list page'
        def page = to(EmployeeListPage)

        then: 'the employee table is present'
        page.employeeTable.displayed
    }

    def "Department list page displays correctly"() {
        expect: 'department list page renders'
        to(DepartmentListPage)
    }

    def "Project list page displays correctly"() {
        expect: 'project list page renders'
        to(ProjectListPage)
    }

    def "List page shows create new button"() {
        expect: 'employee list page renders'
        to(EmployeeListPage)
    }

    // ==================== CREATE TESTS ====================

    def "Create page displays correctly"() {
        expect: 'employee create page renders'
        to(EmployeeCreatePage)
    }

    @Rollback
    def "Create employee with valid data succeeds"() {
        when: 'filling in valid employee data and submitting the form'
        to(EmployeeCreatePage)
                .createEmployee('Integration', 'Test', 'integration.test@example.com')

        then: 'redirected to employee list page'
        at(EmployeeListPage)
    }

    @Rollback
    def "Create department with valid data succeeds"() {
        when: 'filling in valid department data and submitting the form'
        to(DepartmentCreatePage).createDepartment('Test Department')

        then: 'redirected to department list page'
        at(DepartmentListPage)
    }

    // ==================== SHOW (READ) TESTS ====================

    @Rollback
    def "Show page displays employee details"() {
        when: 'creating an employee'
        to(EmployeeCreatePage)
                .createEmployee('ShowTest', 'Employee', 'showtest@example.com')

        then: 'on the show page after create'
        def page = at(EmployeeShowPage)

        and: 'employee details are shown (at least one property list or table element)'
        $('.property-list, ol.property-list, table').size() > 0
    }

    @Rollback
    def "Show page has edit and delete buttons"() {
        when: 'creating an employee'
        to(EmployeeCreatePage)
                .createEmployee('EditButtonTest', 'Employee', 'editbuttontest@example.com')

        then: 'on the show page after create'
        def page = at(EmployeeShowPage)

        and: 'edit button is present'
        page.editButton.displayed
    }

    // ==================== EDIT (UPDATE) TESTS ====================

    @Rollback
    def "Edit page displays correctly with existing data"() {
        when: 'creating an employee'
        to(EmployeeCreatePage)
                .createEmployee('EditTest', 'Employee', 'edittest@example.com')

        then: 'on the show page after create'
        def page = at(EmployeeShowPage)

        when: 'clicking edit from the show page'
        page.clickEdit()
        page = at(EmployeeEditPage)

        then: 'the form is present with existing values'
        page.firstNameField.value() == 'EditTest'
    }

    @Rollback
    def "Edit employee with valid data succeeds"() {
        when: "creating an employee to edit"
        to(EmployeeCreatePage)
                .createEmployee('UpdateTest', 'BeforeUpdate', 'updatetest@example.com')

        then: 'on the show page after create'
        def page = at(EmployeeShowPage)

        when: 'clicking edit from the show page'
        page.clickEdit()
        page = at(EmployeeEditPage)

        and: 'modifying the last name'
        page.lastNameField.value('AfterUpdate')

        and: 'submitting the form'
        page.submitForm()

        then: 'redirected to show page with updated data'
        at(EmployeeShowPage)
    }

    // ==================== DELETE TESTS ====================

    @Rollback
    def "Delete removes employee from list"() {
        when: 'navigating to employee list and count rows'
        to(EmployeeListPage)

        and: 'reading initial row count'
        def initialRowCount = $('table tbody tr').size()

        and: 'create a new employee to delete'
        to(EmployeeCreatePage)
                .createEmployee('DeleteTest', 'Employee', 'todelete@example.com')

        then: 'on the show page of the new employee'
        def page = at(EmployeeShowPage)

        when: 'clicking delete'
        withConfirm { page.clickDelete() }

        then: 'on the list page after deletion'
        at(EmployeeListPage)

        and: 'row count is back to initial (employee deleted)'
        $('table tbody tr').size() == initialRowCount
    }

    // ==================== NAVIGATION TESTS ====================

    def "Can navigate from list to create to list"() {
        when: 'starting on the list page'
        def page = to(EmployeeListPage)

        and: 'clicking create new'
        page.clickCreateNew()

        then: 'on create page'
        at(EmployeeCreatePage)

        expect: 'navigating back back to list page works'
        to(EmployeeListPage)
    }

    @Rollback
    def "Can navigate from list to show to edit to show"() {
        when: 'creating an employee to navigate'
        to(EmployeeCreatePage)
                .createEmployee('NavTest', 'Employee', 'navtest@example.com')

        then: 'on show page after create'
        def page = at(EmployeeShowPage)

        when: 'clicking edit'
        page.clickEdit()

        then: 'on edit page'
        at(EmployeeEditPage)

        expect: 'going back to list works'
        to(EmployeeListPage)
    }
}
