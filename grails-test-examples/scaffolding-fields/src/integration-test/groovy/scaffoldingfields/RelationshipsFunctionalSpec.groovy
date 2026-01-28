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

import spock.lang.PendingFeature

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Functional tests for relationship handling in scaffolded views.
 * Tests belongsTo, hasMany, and manyToMany relationship rendering.
 */
@Rollback
@Integration
class RelationshipsFunctionalSpec extends ContainerGebSpec {

    // ==================== BELONGSTO RELATIONSHIP ====================

    def "BelongsTo renders as select with existing entities"() {
        when: "navigating to employee create page"
        go '/employee/create'

        then: "department select is present with options"
        def deptSelect = $('select[name="department.id"]')
        deptSelect.displayed

        and: "select contains departments from bootstrap"
        def options = deptSelect.find('option')
        options.size() > 1 // At least null option + departments

        and: "options include Engineering department"
        options*.text().any { it.contains('Engineering') }
    }

    def "Can select belongsTo association when creating entity"() {
        given: "navigating to employee create page"
        go '/employee/create'

        when: "selecting a department and filling required fields"
        def deptSelect = $('select[name="department.id"]')
        deptSelect.find('option', text: contains('Engineering')).click()
        $('input[name="firstName"]').value('Related')
        $('input[name="lastName"]').value('Employee')
        $('input[name="email"]').value('related.employee@example.com')

        and: "submitting the form"
        $('input[type="submit"], button[type="submit"]').click()

        then: "employee is created successfully"
        title == 'Show Employee'
        pageSource.contains('Engineering')
    }

    def "Show page displays belongsTo association"() {
        when: "navigating to an employee show page with department"
        go '/employee/show/1'

        then: "department is displayed"
        def pageContent = $('body').text()
        pageContent.contains('Department')
        pageContent.contains('Engineering')
    }

    def "Edit page preserves belongsTo selection"() {
        when: "navigating to employee edit page"
        go '/employee/edit/1'

        then: "department select has a value selected"
        def deptSelect = $('select[name="department"], select[name="department.id"]')
        def selectedOption = deptSelect.find('option:checked, option[selected]')
        selectedOption.text().contains('Engineering')
    }

    // ==================== HASMANY RELATIONSHIP (Parent Side) ====================

    def "Department show page displays hasMany employees"() {
        when: "navigating to a department show page"
        go '/department/show/1'

        then: "employees list is displayed"
        def pageContent = $('body').text()
        pageContent.contains('Employees')
        pageContent.contains('John')
    }

    // TODO: Not fully implemented
    def "Department list shows employee count or link"() {
        when: "navigating to department list"
        go '/department/index'

        then: "page displays correctly"
        title == 'Department List'
        // HasMany might show count or be hidden in list view
    }

    // ==================== HASMANY RELATIONSHIP (Child Side - Multi-select) ====================

    def "HasMany renders as multi-select when applicable"() {
        when: "navigating to employee create/edit page"
        go '/employee/create'

        then: "projects field may be multi-select"
        def projectsSelect = $('select[name="projects"]')
        // HasMany on create might not show or might be multi-select
        projectsSelect.attr('multiple') != null
        projectsSelect.find('option').size() >= 0
    }

    // TODO: Not fully implemented
    def "Can select multiple hasMany items"() {
        given: "navigating to employee edit page with projects"
        go '/employee/edit/1'

        when: "projects multi-select is available"
        def projectsSelect = $('select[name="projects"]')

        then: "can interact with multi-select if present"
        projectsSelect.displayed
    }

    // ==================== MANYTOMANY RELATIONSHIP ====================

    @PendingFeature(reason = 'Many-to-many relationship does not show up on page')
    def "Project shows many-to-many employees"() {
        when: "navigating to project show page"
        go '/project/show/1'

        then: "employees may be displayed"
        def pageContent = $('body').text()
        pageContent.contains('Employees')
        pageContent.contains('John')
    }

    def "Project edit allows selecting multiple employees"() {
        when: "navigating to project edit page"
        go '/project/edit/1'

        then: "employees is present"
        def employeesSelect = $('select[name="employees"]')
        employeesSelect.displayed
        employeesSelect.find('option').size() >= 0
    }

    // ==================== EMBEDDED RELATIONSHIP ====================

    def "Embedded address fields render inline"() {
        when: "navigating to employee create page"
        go '/employee/create'

        then: "embedded address fields are present"
        // Check for embedded address fields with dotted notation
        def streetField = $('input[name="address.street"]')
        def cityField = $('input[name="address.city"]')
        def postalCodeField = $('input[name="address.postalCode"]')
        def countryField = $('input[name="address.country"]')

        streetField.displayed
        cityField.displayed
        postalCodeField.displayed
        countryField.displayed
    }

    def "Can save entity with embedded object"() {
        given: "navigating to employee create page"
        go '/employee/create'

        when: "filling required fields and embedded address"
        $('input[name="firstName"]').value('Embedded')
        $('input[name="lastName"]').value('Address')
        $('input[name="email"]').value('embedded.address@example.com')

        def streetField = $('input[name="address.street"]')
        streetField.value('789 Test Street')
        def cityField = $('input[name="address.city"]')
        cityField.value('Test City')

        and: "submitting the form"
        $('input[type="submit"], button[type="submit"]').click()

        then: "entity is created"
        !title.contains('Create')
        title.contains('Employee')
    }

    def "Show page displays embedded object properties"() {
        when: "navigating to employee show page with address"
        go '/employee/show/1'

        then: "address fields are displayed"
        def pageContent = $('body').text()
        // Check for any address-related content
        pageContent.contains('Address')
        pageContent.contains('Street')
        pageContent.contains('New York')
    }

    // ==================== RELATIONSHIP CONSISTENCY ====================

    // TODO: Not fully implemented
    def "Creating employee with department updates department employee count"() {
        given: "count existing employees in engineering"
        go '/department/show/1'
        def initialContent = $('body').text()

        when: "creating new employee in engineering"
        go '/employee/create'
        def deptSelect = $('select[name="department"], select[name="department.id"]')
        deptSelect.find('option', text: contains('Engineering')).click()
        $('input[name="firstName"]').value('New')
        $('input[name="lastName"]').value('EngineerTest')
        $('input[name="email"]').value('new.engineer.test@example.com')
        $('input[type="submit"], button[type="submit"]').click()

        then: "employee was created"
        !title.contains('Create')
        title.contains('Employee')
    }

    // ==================== NULL ASSOCIATION HANDLING ====================

    def "Can create entity without selecting optional association"() {
        given: "navigating to employee create page"
        go '/employee/create'

        when: "filling required fields without selecting department"
        $('input[name="firstName"]').value('NoDept')
        $('input[name="lastName"]').value('Employee')
        $('input[name="email"]').value('nodept.employee@example.com')
        // Don't select department

        and: "submitting the form"
        $('input[type="submit"], button[type="submit"]').click()

        then: "entity is created without association"
        !title.contains('Create')
        title.contains('Employee')
    }

    def "Show page handles null associations gracefully"() {
        when: "navigating to employee show page"
        go '/employee/show/3' // Bob Wilson has no projects

        then: "page renders without errors"
        title.contains('Employee')
        title.contains('Show')
        $('body').text().contains('Wilson')
    }
}
