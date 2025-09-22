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

import geb.report.ReportState
import geb.report.Reporter
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.remote.RemoteWebDriver
import geb.report.ReportingListener

// Configuration for container-based Geb testing.
// This driver configuration will be used by WebDriverContainerHolder.
driver = {

    // The remote address will be set by WebDriverContainerHolder via
    // system property `webdriver.remote.server` before this closure is called.
    new RemoteWebDriver(new FirefoxOptions().tap {
        // Add a custom capability that we can test for
        // to verify our configuration is being used.
        setCapability('grails:gebConfigUsed', true)
    })
}

// The `containerBrowser` property must match the configured
// driver in order to start up a matching Selenium browser container.
containerBrowser = 'firefox'

// Another proof that GebConfig.groovy is being utilized, next to GebConfigSpec.
reportingListener = new ReportingListener() {
    void onReport(Reporter reporter, ReportState reportState, List<File> reportFiles) {
        reportFiles.each {
            println "[[ATTACHMENT|$it.absolutePath]]"
        }
    }
}
