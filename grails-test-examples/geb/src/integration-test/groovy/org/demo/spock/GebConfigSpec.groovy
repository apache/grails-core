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

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import org.openqa.selenium.remote.RemoteWebDriver

/**
 * Test spec to verify that our custom GebConfig.groovy driver configuration
 * is being used instead of the default WebDriverContainerHolder configuration.
 */
@Integration
class GebConfigSpec extends ContainerGebSpec {

    void 'should use custom RemoteWebDriver from GebConfig.groovy'() {
        when: 'accessing the driver'
        def driver = browser.driver

        then: 'the driver should be a RemoteWebDriver'
        driver instanceof RemoteWebDriver

        and: 'the driver should have our custom capability indicating GebConfig was used'
        def capabilities = ((RemoteWebDriver) driver).capabilities
        capabilities.getCapability("grails:gebConfigUsed") == true

        and: 'the driver should have Chrome-specific capabilities'
        capabilities.getBrowserName() == "chrome"

        and: 'the driver should be using our custom configuration'
        // The custom capability we set proves our GebConfig.groovy was used
        capabilities.getCapability("grails:gebConfigUsed") == true
    }

    void 'should verify our GebConfig driver configuration is active'() {
        when: 'navigating to a page'
        go('/')

        then: 'the page loads successfully'
        title == 'Welcome to Grails'

        and: 'the driver capabilities should reflect our configuration'
        def driver = browser.driver as RemoteWebDriver
        def capabilities = driver.capabilities

        // Verify our custom marker capability that proves GebConfig.groovy was used
        capabilities.getCapability("grails:gebConfigUsed") == true

        and: 'the driver should be Chrome with our configuration'
        capabilities.getBrowserName() == "chrome"

        and: 'the driver should be a RemoteWebDriver as configured in GebConfig'
        driver instanceof RemoteWebDriver

        and: 'the session should be active'
        driver.sessionId != null
    }
}
