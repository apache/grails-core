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

import geb.Page

/**
 * Base page for scaffolded list views.
 * Provides common elements for list/index pages.
 */
class ScaffoldListPage extends Page {

    static at = {
        title.endsWith('List')
    }

    static content = {
        // Page title
        pageTitle { $('h1').text() }

        // Data table
        dataTable { $('table.table') }
        tableHeaders { dataTable.find('thead th') }
        tableRows { dataTable.find('tbody tr') }

        // Pagination
        pagination { $('.pagination') }
        paginationLinks { pagination.find('a') }
        nextPageLink(required: false) { pagination.find('a', text: contains('Next')) }
        prevPageLink(required: false) { pagination.find('a', text: contains('Previous')) }

        // Action buttons
        createNewButton { $('a', text: contains('New')) }

        // Row actions (for first row by default)
        showLinks { tableRows.find('a.show') }
        editLinks { tableRows.find('a.edit') }

        // Record count display
        recordCount(required: false) { $('.pagination-info, .record-count') }
    }

    /**
     * Get the number of rows in the table
     */
    int getRowCount() {
        tableRows.size()
    }

    /**
     * Click the create new button
     */
    void clickCreateNew() {
        createNewButton.click()
    }

    /**
     * Click show link for a specific row (0-indexed)
     */
    void clickShow(int rowIndex) {
        tableRows[rowIndex].find('a.show, a[href*="show"]').click()
    }

    /**
     * Click edit link for a specific row (0-indexed)
     */
    void clickEdit(int rowIndex) {
        tableRows[rowIndex].find('a.edit, a[href*="edit"]').click()
    }

    /**
     * Get cell text for a specific row and column (0-indexed)
     */
    String getCellText(int rowIndex, int colIndex) {
        tableRows[rowIndex].find('td')[colIndex].text()
    }

    /**
     * Check if pagination is present
     */
    boolean hasPagination() {
        pagination.displayed
    }

    /**
     * Go to next page if available
     */
    boolean goToNextPage() {
        if (nextPageLink.displayed) {
            nextPageLink.click()
            return true
        }
        return false
    }
}
