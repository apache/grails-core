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
 * Base page for scaffolded show views.
 * Provides common elements for show/detail pages.
 */
class ScaffoldShowPage extends Page {

    static at = {
        title.endsWith('Show') || title.contains('Show')
    }

    static content = {
        // Page title
        pageTitle { $('h1').text() }

        // Property display (ol/li or table structure)
        propertyList { $('ol.property-list, .property-list, table.property-list') }
        propertyItems { $('.property-list li, .property-list tr, .fieldcontain') }

        // Action buttons
        editButton { $('a', text: contains('Edit')) }
        deleteButton { $('input[type=submit][value*="Delete"], button[type=submit]:contains("Delete"), a:contains("Delete")') }
        listButton(required: false) { $('a', text: contains('List')) }

        // Flash messages
        flashMessage(required: false) { $('.message, .alert-success, .flash-message') }
    }

    /**
     * Get the value of a displayed property by label
     */
    String getPropertyValue(String label) {
        def item = propertyItems.find {
            it.find('.property-label, th, label').text()?.contains(label)
        }
        item?.find('.property-value, td:last-child, .value')?.text()?.trim()
    }

    /**
     * Click the edit button
     */
    void clickEdit() {
        editButton.click()
    }

    /**
     * Click the delete button (note: may need confirmation)
     */
    void clickDelete() {
        deleteButton.click()
    }

    /**
     * Click back to list
     */
    void clickList() {
        listButton.click()
    }

    /**
     * Check if a property is displayed
     */
    boolean hasProperty(String label) {
        propertyItems.any {
            it.find('.property-label, th, label').text()?.contains(label)
        }
    }

    /**
     * Get flash message text if present
     */
    String getFlashMessageText() {
        flashMessage.displayed ? flashMessage.text() : null
    }

    /**
     * Get all displayed property labels
     */
    List<String> getPropertyLabels() {
        propertyItems.collect {
            it.find('.property-label, th, label').text()?.trim()
        }.findAll { it }
    }
}
