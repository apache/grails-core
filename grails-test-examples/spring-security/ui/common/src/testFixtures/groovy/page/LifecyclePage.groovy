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
package page

import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.WebElement

import geb.Page
import geb.navigator.Navigator

class LifecyclePage extends Page {

    boolean loaded = false
    boolean unloaded = false

    @Override
    void onUnload(Page nextPage) {
        unloaded = true
    }

    @Override
    void onLoad(Page previousPage) {
        loaded = true
    }

    <T extends LifecyclePage> T waitForPage(Class<T> expectedPageType) {
        T page = browser.at(expectedPageType)
        waitFor { page.loaded }
        page
    }

    /**
     * Clicks a button that triggers navigation and waits for the new document to
     * replace the current one. Required when navigating to a page with the same
     * at-check (e.g. a validation failure re-rendering the same page), where
     * {@code browser.at()} would otherwise pass against the outgoing document.
     */
    protected void clickAndWaitForNavigation(Navigator button) {
        WebElement oldElement = button.firstElement()
        button.click()
        waitForStale(oldElement)
    }

    protected void waitForStale(WebElement oldElement) {
        waitFor {
            try {
                oldElement.enabled
                false
            }
            catch (StaleElementReferenceException ignored) {
                true
            }
        }
    }

    protected <T extends LifecyclePage> T clickAndWaitForPage(Navigator button, Class<T> expectedPageType) {
        clickAndWaitForNavigation(button)
        waitForPage(expectedPageType)
    }
}
