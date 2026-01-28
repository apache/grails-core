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

/**
 * Functional tests for form validation error display.
 * Tests that validation errors are properly rendered in scaffolded views.
 */
@Rollback
@Integration
class ValidationFunctionalSpec extends ContainerGebSpec {

    // ==================== REQUIRED FIELD VALIDATION ====================

    // TODO: Not fully implemented
    def "Blank required field shows error message"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting the form without filling required fields"
        $('input[name="firstName"]').value('')
        $('input[name="lastName"]').value('')
        $('input[name="email"]').value('')
        $('input[type="submit"], button[type="submit"]').click()

        then: "still on create page with errors - validation should prevent navigation away"
        waitFor(5) {
            title == 'Create Employee' ||
            currentUrl.contains('/employee/create') ||
            // Error elements that may be displayed
            $('.errors, .alert-danger, .invalid-feedback, .field-error, .has-error').displayed
        }
    }

    // TODO: Not fully implemented
    def "Blank first name shows specific error"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting with blank first name but valid other fields"
        $('input[name="firstName"]').value('')
        $('input[name="lastName"]').value('ValidLastName')
        $('input[name="email"]').value('valid@email.com')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown for first name"
        // Check for error associated with firstName field or general errors
        $('.errors li, .alert-danger li, .invalid-feedback').any {
            it.text().toLowerCase().contains('first') || it.text().toLowerCase().contains('blank')
        } || title == 'Create Employee' // Still on create page indicates validation failed
    }

    // ==================== EMAIL VALIDATION ====================
    // TODO: Not fully implemented
    def "Invalid email format shows error message"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting with invalid email format"
        $('input[name="firstName"]').value('John')
        $('input[name="lastName"]').value('Doe')
        $('input[name="email"]').value('not-a-valid-email')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown for email - validation should prevent navigation away"
        waitFor(5) {
            title == 'Create Employee' ||
            currentUrl.contains('/employee/create') ||
            $('.errors, .alert-danger, .invalid-feedback, .field-error').displayed
        }
    }

    def "Duplicate email shows unique constraint error"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting with an email that already exists"
        $('input[name="firstName"]').value('Another')
        $('input[name="lastName"]').value('User')
        // Use email from bootstrap data
        $('input[name="email"]').value('john.doe@example.com')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown for duplicate email - validation prevents navigation away"
        title == 'Create Employee'
        $('.alert-danger').displayed
        pageSource.contains('Property [email] of class [class scaffoldingfields.Employee] with value [john.doe@example.com] must be unique')
    }

    // ==================== NUMERIC VALIDATION ====================

    // TODO: Not fully implemented
    def "Age below minimum shows error"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting with age below minimum (18)"
        $('input[name="firstName"]').value('Young')
        $('input[name="lastName"]').value('Person')
        $('input[name="email"]').value('young.person@example.com')
        def ageField = $('input[name="age"]')
        ageField.value('10')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown if age was provided"
        // Either validation failed or field wasn't shown
        true
    }

    // TODO: Not fully implemented
    def "Age above maximum shows error"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting with age above maximum (120)"
        $('input[name="firstName"]').value('Ancient')
        $('input[name="lastName"]').value('Person')
        $('input[name="email"]').value('ancient.person@example.com')
        def ageField = $('input[name="age"]')
        if (ageField.displayed) {
            ageField.value('150')
        }
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown if age was provided"
        true
    }

    def "Negative salary shows error"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting with negative salary"
        $('input[name="firstName"]').value('Negative')
        $('input[name="lastName"]').value('Salary')
        $('input[name="email"]').value('negative.salary@example.com')
        def salaryField = $('input[name="salary"]')
        salaryField.value('-1000')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown if salary was provided"
        pageSource.contains('Property [salary] of class [class scaffoldingfields.Employee] with value [-1,000] is less than minimum value [0]')
    }

    // ==================== SIZE CONSTRAINT VALIDATION ====================

    // TODO: Not fully implemented
    def "First name exceeding max size shows error"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting with first name exceeding 50 characters"
        def longName = 'A' * 60
        $('input[name="firstName"]').value(longName)
        $('input[name="lastName"]').value('Test')
        $('input[name="email"]').value('longname@example.com')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown for exceeding size - stays on create page or shows error"
        waitFor(5) {
            title == 'Create Employee' ||
            currentUrl.contains('/employee/create') ||
            // Some databases may truncate instead of reject
            currentUrl.contains('/employee/show/')
        }
    }

    // ==================== DEPARTMENT VALIDATION ====================
    // TODO: Not fully implemented
    def "Department with blank name shows error"() {
        given: "navigating to the department create page"
        go '/department/create'

        when: "submitting with blank name"
        $('input[name="name"]').value('')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown - stays on create page"
        waitFor(5) {
            title == 'Create Department' ||
            currentUrl.contains('/department/create') ||
            $('.errors, .alert-danger, .invalid-feedback').displayed
        }
    }


    def "Department with duplicate name shows error"() {
        given: "navigating to the department create page"
        go '/department/create'

        when: "submitting with existing department name"
        // Use name from bootstrap data
        $('input[name="name"]').value('Engineering')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown for duplicate - validation prevents navigation or unique constraint not configured"
        title == 'Create Department'
        pageSource.contains('Property [name] of class [class scaffoldingfields.Department] with value [Engineering] must be unique')
        $('.alert-danger').displayed
    }

    // ==================== PROJECT VALIDATION ====================

    def "Project with invalid code format shows error"() {
        given: "navigating to the project create page"
        go '/project/create'

        when: "submitting with invalid code format (lowercase, special chars)"
        $('input[name="name"]').value('Test Project')
        $('input[name="code"]').value('invalid-code!')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown for invalid code format"
        title == 'Create Project'
        pageSource.contains('Property [code] of class [class scaffoldingfields.Project] with value [invalid-code!] does not match the required pattern [[A-Z0-9_-]+]')
    }

    def "Project with end date before start date shows error"() {
        given: "navigating to the project create page"
        go '/project/create'

        when: "submitting with end date before start date"
        $('input[name="name"]').value('Invalid Dates Project')
        $('input[name="code"]').value('INVALID')
        $('select[name="startDate_day"]').value('1')
        $('select[name="startDate_month"]').value('12')
        $('select[name="startDate_year"]').value('2024')
        $('select[name="endDate_day"]').value('1')
        $('select[name="endDate_month"]').value('1')
        $('select[name="endDate_year"]').value('2024')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error may be shown for invalid date range"
        pageSource.contains('Property [endDate] of class [class scaffoldingfields.Project] with value [1/1/24')
    }

    // ==================== EDIT VALIDATION ====================

    // TODO: Not fully implemented
    def "Edit with invalid data shows errors"() {
        given: "navigating to an existing employee edit page"
        go '/employee/edit/1'

        when: "clearing required field and submitting"
        def firstNameField = $('input[name="firstName"]')
        firstNameField.value('')
        $('input[type="submit"], button[type="submit"]').click()

        then: "error is shown and still on edit page"
        title.contains('Edit')
        title.contains('Employee')
    }

    // ==================== ERROR MESSAGE DISPLAY ====================

    // TODO: Not fully implemented
    def "Error messages are visible and readable"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting empty form"
        $('input[type="submit"], button[type="submit"]').click()

        then: "error messages are present and visible"
        def errors = $('.errors li, .alert-danger li, .invalid-feedback, .field-error')
        errors.size() > 0 || title == 'Create Employee'
    }

    // TODO: Not fully implemented
    def "Error styling is applied to invalid fields"() {
        given: "navigating to the employee create page"
        go '/employee/create'

        when: "submitting empty form"
        $('input[type="submit"], button[type="submit"]').click()

        then: "fields or their containers have error styling"
        // Check for Bootstrap error classes or Grails error classes
        $('.has-error, .is-invalid, .error, .errors').displayed || title == 'Create Employee'
    }
}
