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

package org.demo.spock

import geb.playwright.PlaywrightWebDriver
import org.demo.spock.pages.HomePage

import grails.plugin.geb.PlaywrightGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Test spec to verify that the Playwright driver configuration is used.
 */
@Integration
class GebConfigSpec extends PlaywrightGebSpec {

    void 'should use PlaywrightWebDriver from GebConfig.groovy'() {
        expect: 'the driver is the local Playwright adapter'
        driver instanceof PlaywrightWebDriver

        when: 'navigating to a page'
        to(HomePage)

        then: 'the browser has an active page'
        ((PlaywrightWebDriver) driver).page != null
    }
}
