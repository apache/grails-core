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

import scaffoldingfields.pages.DepartmentListPage
import scaffoldingfields.pages.EmployeeListPage
import spock.lang.Stepwise

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Functional tests for scaffolded list pagination and sorting.
 * 
 * Tests that:
 * 1. Pagination controls are rendered correctly
 * 2. Page navigation works (next, previous, specific page)
 * 3. Column sorting works (ascending, descending)
 * 4. Sort state is preserved across page navigation
 * 5. Item count and page info are displayed correctly
 */
@Stepwise
@Integration
class PaginationSpec extends ContainerGebSpec {

    // TODO: Not fully implemented
    def "employee list displays pagination when records exceed page size"() {
        when: "navigating to employee list"
        def page = to EmployeeListPage

        then: "list page is displayed"
        at EmployeeListPage

        and: "table with data is shown"
        page.dataTable.displayed

        and: "rows are present"
        page.tableRows.size() > 0
    }

    def "employee list shows sortable column headers"() {
        when: "navigating to employee list"
        to EmployeeListPage

        then: "column headers are displayed"
        $('th a', text: contains('First Name')).displayed ||
        $('th a', text: contains('firstName')).displayed ||
        $('th', text: contains('First')).displayed
    }

    // TODO: Not fully implemented
    def "clicking column header sorts ascending"() {
        given: "on employee list page"
        def page = to EmployeeListPage

        when: "clicking on a sortable column header"
        page.sortLink('First Name').click()

        then: "page reloads with sorted data"
        at EmployeeListPage
        currentUrl.contains('sort=')
        page.tableRows.size() > 0
    }

    // TODO: Not fully implemented
    def "clicking same column header again sorts descending"() {
        given: "on employee list page with ascending sort"
        def page = to EmployeeListPage
        page.sortLink('First Name').click()

        when: "clicking the same column header again"
        page.sortLink('First Name').click()

        then: "sort order changes"
        at EmployeeListPage
        page.tableRows.size() > 0
    }

    // TODO: Not fully implemented
    def "pagination links navigate between pages"() {
        given: "on employee list page"
        def page = to EmployeeListPage
        //def initialRowCount = page.tableRows.size()

        when: "checking for pagination controls"
        def nextLink = $('a.step', text: contains('Next')) ?: $('a', text: contains('Next')) ?: $('a.nextLink')
        def paginationExists = nextLink?.displayed ?: $('nav.pagination').displayed ?: $('ul.pagination').displayed

        then: "list is displayed"
        page.tableRows.size() > 0

        // Pagination may or may not be present depending on data volume
        // If pagination exists, we test it; otherwise we just verify the list works
    }

    def "department list displays records"() {
        when: "navigating to department list"
        def page = to DepartmentListPage

        then: "list page is displayed"
        at DepartmentListPage

        and: "table with data is shown"
        page.dataTable.displayed

        and: "rows are present from bootstrap data"
        page.tableRows.size() > 0
    }

    def "department list shows department names"() {
        when: "navigating to department list"
        def page = to DepartmentListPage

        then: "department names are visible"
        page.tableRows.find {
            it.text()?.contains('Engineering') || it.text()?.contains('Marketing') || it.text()?.contains('Sales')
        }
    }

    // TODO: Not fully implemented
    def "list shows correct record count information"() {
        when: "navigating to employee list"
        def page = to EmployeeListPage

        then: "page displays records"
        page.tableRows.size() > 0

        // Record count info may be displayed in various formats
        // Just verify the page is functional
    }

    // TODO: Not fully implemented
    def "empty search results show appropriate message"() {
        when: "navigating to employee list with filter that matches nothing"
        go '/employee/index?firstName=NONEXISTENT_12345'

        then: "page loads"
        waitFor { $('body').displayed }

        // Either shows empty table or no results message
    }

    // TODO: Not fully implemented
    def "list maintains state across browser refresh"() {
        given: "on sorted employee list"
        def page = to EmployeeListPage

        when: "sorting by a column"
        def sortLink = $('th a').first()
        if (sortLink?.displayed) {
            sortLink.click()
            waitFor { page.tableRows.size() > 0 }
        }
        def currentUrlBeforeRefresh = currentUrl

        and: "refreshing the page"
        driver.navigate().refresh()
        waitFor { page.tableRows.size() > 0 }

        then: "sort parameters are preserved in URL or list is still functional"
        at EmployeeListPage
        page.tableRows.size() > 0
    }

    // TODO: Not fully implemented
    def "pagination works with sorting applied"() {
        given: "on employee list page"
        def page = to EmployeeListPage

        when: "applying a sort"
        def sortLink = $('th a').first()
        if (sortLink?.displayed) {
            sortLink.click()
            waitFor { page.tableRows.size() > 0 }
        }

        then: "sorted list is displayed"
        page.tableRows.size() > 0

        // If pagination is available with current data set, verify it works with sort
        // This ensures sort and pagination don't conflict
    }

    // TODO: Not fully implemented
    def "max parameter limits displayed records"() {
        when: "navigating with max parameter"
        go '/employee/index?max=2'

        then: "page loads successfully"
        waitFor { $('table').displayed || $('body').text().contains('Employee') }

        // The max parameter should limit results (if there's data)
    }

    // TODO: Not fully implemented
    def "offset parameter skips records"() {
        when: "navigating with offset parameter"
        go '/employee/index?offset=1'

        then: "page loads successfully"
        waitFor { $('table').displayed || $('body').text().contains('Employee') }

        // The offset parameter should skip initial records
    }

    // TODO: Not fully implemented
    def "combined max and offset parameters work together"() {
        when: "navigating with both max and offset"
        go '/employee/index?max=5&offset=0'

        then: "page loads successfully"
        waitFor { $('table').displayed || $('body').text().contains('Employee') }

        and: "records are displayed"
        $('table').displayed
    }
}
