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

import org.openqa.selenium.remote.RemoteWebDriver
import org.demo.spock.pages.HomePage

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Test spec to verify that our custom GebConfig.groovy driver configuration
 * is being used instead of the default WebDriverContainerHolder configuration.
 */
@Integration
class GebConfigSpec extends ContainerGebSpec {

    void 'should use custom RemoteWebDriver from GebConfig.groovy'() {
        expect: 'the driver to be a RemoteWebDriver'
        driver instanceof RemoteWebDriver

        when: 'getting the capabilities of the driver'
        def capabilities = ((RemoteWebDriver) driver).capabilities

        then: 'our custom capability set in GebConfig is available'
        capabilities.getCapability('grails:gebConfigUsed') == true

        and: 'the driver should have Chrome-specific capabilities'
        capabilities.browserName == 'firefox'

        when: 'navigating to a page'
        to(HomePage)

        then: 'the session should be active'
        driver.sessionId != null
    }
}
