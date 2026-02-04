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

package scaffoldingfields.pages

/**
 * Page object for Employee list view.
 */
class EmployeeListPage extends ScaffoldListPage {

    static url = '/employee/index'

    static at = {
        title == 'Employee List' || title?.contains('Employee')
    }

    static content = {
        // Alias for clarity in tests
        employeeTable { dataTable }
        // Employee-specific content
        firstNameColumn { tableHeaders.find { it.text().contains('First Name') } }
        lastNameColumn { tableHeaders.find { it.text().contains('Last Name') } }
        emailColumn { tableHeaders.find { it.text().contains('Email') } }
        sortLink { String columnName ->
            employeeTable.find('th', text: columnName).find('a')
        }
    }
}

/**
 * Page object for Employee create view.
 */
class EmployeeCreatePage extends ScaffoldCreatePage {

    static url = '/employee/create'

    static at = {
        title == 'Create Employee' || title?.contains('Employee')
    }

    static content = {
        // Employee-specific fields
        firstNameField { $('input[name="firstName"]') }
        lastNameField { $('input[name="lastName"]') }
        emailField { $('input[name="email"]') }
        biographyField { $('textarea[name="biography"]') }
        ageField { $('input[name="age"]') }
        salaryField { $('input[name="salary"]') }
        hireDateField { $('input[name="hireDate"]') }
        activeField { $('input[name="active"]') }
        statusField { $('select[name="status"]') }
        priorityField { $('select[name="priority"]') }
        departmentField { $('select[name="department"], select[name="department.id"]') }
        projectsField { $('select[name="projects"]') }

        // Embedded address fields
        streetField { $('input[name="address.street"]') }
        cityField { $('input[name="address.city"]') }
        postalCodeField { $('input[name="address.postalCode"]') }
        countryField { $('input[name="address.country"]') }
    }

    /**
     * Fill employee basic info
     */
    void fillBasicInfo(String firstName, String lastName, String email) {
        firstNameField.value(firstName)
        lastNameField.value(lastName)
        emailField.value(email)
    }

    /**
     * Fill employee address
     */
    void fillAddress(String street, String city, String postalCode, String country) {
        if (streetField.displayed) streetField.value(street)
        if (cityField.displayed) cityField.value(city)
        if (postalCodeField.displayed) postalCodeField.value(postalCode)
        if (countryField.displayed) countryField.value(country)
    }
}

/**
 * Page object for Employee show view.
 */
class EmployeeShowPage extends ScaffoldShowPage {

    static at = {
        title.contains('Employee') && title.contains('Show')
    }

    static content = {
        // Employee-specific displayed values
        displayedFirstName { getPropertyValue('First Name') }
        displayedLastName { getPropertyValue('Last Name') }
        displayedEmail { getPropertyValue('Email') }
        displayedDepartment { getPropertyValue('Department') }
    }
}

/**
 * Page object for Employee edit view.
 */
class EmployeeEditPage extends ScaffoldEditPage {

    static at = {
        title.contains('Employee') && title.contains('Edit')
    }

    static content = {
        // Employee-specific fields
        firstNameField { $('input[name="firstName"]') }
        lastNameField { $('input[name="lastName"]') }
        emailField { $('input[name="email"]') }
        biographyField { $('textarea[name="biography"]') }
        ageField { $('input[name="age"]') }
        salaryField { $('input[name="salary"]') }
        activeField { $('input[name="active"]') }
        statusField { $('select[name="status"]') }
        priorityField { $('select[name="priority"]') }
        departmentField { $('select[name="department"], select[name="department.id"]') }
    }
}
