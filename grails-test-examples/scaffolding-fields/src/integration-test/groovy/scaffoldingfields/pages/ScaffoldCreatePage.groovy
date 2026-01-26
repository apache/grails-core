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
 * Base page for scaffolded create views.
 * Provides common form elements for create pages.
 */
class ScaffoldCreatePage extends Page {

    static at = {
        title.endsWith('Create') || title.contains('Create')
    }

    static content = {
        // Page title
        pageTitle { $('h1').text() }

        // Form
        createForm { $('form') }

        // Submit and cancel buttons
        createButton { $('input[type=submit], button[type=submit]', value: contains('Create')) }
        cancelButton(required: false) { $('a', text: contains('Cancel')) }

        // Error messages
        errorMessages { $('.errors li, .alert-danger li, .field-error, .invalid-feedback') }
        hasErrors { errorMessages.size() > 0 }

        // Field wrappers (grails-fields structure)
        fieldWrappers { $('.fieldcontain, .form-group, .mb-3') }
    }

    /**
     * Fill a text input field by name
     */
    void fillField(String fieldName, String value) {
        def field = createForm.find("input[name='${fieldName}'], textarea[name='${fieldName}']")
        if (field.displayed) {
            field.value(value)
        }
    }

    /**
     * Select an option from a dropdown by name
     */
    void selectOption(String fieldName, String optionText) {
        def select = createForm.find("select[name='${fieldName}']")
        if (select.displayed) {
            select.find('option', text: contains(optionText)).click()
        }
    }

    /**
     * Select an option from a dropdown by value
     */
    void selectOptionByValue(String fieldName, String value) {
        def select = createForm.find("select[name='${fieldName}']")
        if (select.displayed) {
            select.value(value)
        }
    }

    /**
     * Check/uncheck a checkbox by name
     */
    void setCheckbox(String fieldName, boolean checked) {
        def checkbox = createForm.find("input[type='checkbox'][name='${fieldName}']")
        if (checkbox.displayed) {
            if (checked && !checkbox.value()) {
                checkbox.click()
            } else if (!checked && checkbox.value()) {
                checkbox.click()
            }
        }
    }

    /**
     * Submit the form
     */
    void submitForm() {
        createButton.click()
    }

    /**
     * Get all error messages as a list
     */
    List<String> getErrors() {
        errorMessages*.text()
    }

    /**
     * Check if a specific field has an error
     */
    boolean hasFieldError(String fieldName) {
        def fieldWrapper = createForm.find("[name='${fieldName}']").closest('.fieldcontain, .form-group, .mb-3')
        fieldWrapper.find('.errors, .invalid-feedback, .field-error').displayed
    }

    /**
     * Get the input element for a field
     */
    def getField(String fieldName) {
        createForm.find("[name='${fieldName}']")
    }

    /**
     * Check if field exists on page
     */
    boolean hasField(String fieldName) {
        getField(fieldName).displayed
    }

    /**
     * Get the type of input for a field
     */
    String getFieldType(String fieldName) {
        def field = getField(fieldName)
        if (field.tag() == 'select') return 'select'
        if (field.tag() == 'textarea') return 'textarea'
        return field.attr('type') ?: 'text'
    }
}
