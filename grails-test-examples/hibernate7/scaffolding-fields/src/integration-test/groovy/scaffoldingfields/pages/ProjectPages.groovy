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
 * Page object for Project list view.
 */
class ProjectListPage extends ScaffoldListPage {

    static url = '/project/index'

    static at = {
        title == 'Project List'
    }
}

/**
 * Page object for Project create view.
 */
class ProjectCreatePage extends ScaffoldCreatePage {

    static url = '/project/create'

    static at = {
        title == 'Create Project'
    }

    static content = {
        nameField { $('input[name="name"]') }
        codeField { $('input[name="code"]') }
        startDateField { $('input[name="startDate"]') }
        endDateField { $('input[name="endDate"]') }
        activeField { $('input[name="active"]') }
        employeesField { $('select[name="employees"]') }
    }

    void fillProject(String name, String code, boolean active = true) {
        nameField.value(name)
        codeField.value(code)
        if (activeField.displayed) {
            if (active && !activeField.value()) {
                activeField.click()
            } else if (!active && activeField.value()) {
                activeField.click()
            }
        }
    }
}

/**
 * Page object for Project show view.
 */
class ProjectShowPage extends ScaffoldShowPage {

    static at = {
        title.contains('Project') && title.contains('Show')
    }

    static content = {
        displayedName { getPropertyValue('Name') }
        displayedCode { getPropertyValue('Code') }
        displayedActive { getPropertyValue('Active') }
        employeesList(required: false) { $('ul.employees-list, .employees li') }
    }

    List<String> getEmployeeNames() {
        employeesList*.text()
    }
}

/**
 * Page object for Project edit view.
 */
class ProjectEditPage extends ScaffoldEditPage {

    static at = {
        title.contains('Project') && title.contains('Edit')
    }

    static content = {
        nameField { $('input[name="name"]') }
        codeField { $('input[name="code"]') }
        startDateField { $('input[name="startDate"]') }
        endDateField { $('input[name="endDate"]') }
        activeField { $('input[name="active"]') }
        employeesField { $('select[name="employees"]') }
    }
}
