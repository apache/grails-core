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
 * Page object for Department list view.
 */
class DepartmentListPage extends ScaffoldListPage {

    static url = '/department/index'

    static at = {
        title == 'Department List' || title?.contains('Department')
    }

    static content = {
        // Alias for clarity in tests
        departmentTable { dataTable }
    }
}

/**
 * Page object for Department create view.
 */
class DepartmentCreatePage extends ScaffoldCreatePage {

    static url = '/department/create'

    static at = {
        title == 'Create Department'
    }

    static content = {
        nameField { $('input[name="name"]') }
        descriptionField { $('textarea[name="description"], input[name="description"]') }
    }

    void fillDepartment(String name, String description = null) {
        nameField.value(name)
        if (description && descriptionField.displayed) {
            descriptionField.value(description)
        }
    }
}

/**
 * Page object for Department show view.
 */
class DepartmentShowPage extends ScaffoldShowPage {

    static at = {
        title.contains('Department') && title.contains('Show')
    }

    static content = {
        displayedName { getPropertyValue('Name') }
        displayedDescription { getPropertyValue('Description') }
        employeesList(required: false) { $('ul.employees-list, .employees li') }
    }

    List<String> getEmployeeNames() {
        employeesList*.text()
    }
}

/**
 * Page object for Department edit view.
 */
class DepartmentEditPage extends ScaffoldEditPage {

    static at = {
        title.contains('Department') && title.contains('Edit')
    }

    static content = {
        nameField { $('input[name="name"]') }
        descriptionField { $('textarea[name="description"], input[name="description"]') }
    }
}
