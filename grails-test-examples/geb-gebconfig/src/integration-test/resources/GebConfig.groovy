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
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import geb.report.ReportingListener

// Configuration for container-based Geb testing
// This driver configuration will be used by WebDriverContainerHolder
driver = {
    // Chrome preferences to disable password manager and credentials service
    def prefs = [
            'credentials_enable_service': false,
            'profile.password_manager_enabled': false,
            'profile.password_manager_leak_detection': false
    ]

    def chromeOptions = new ChromeOptions()
    // TO DO: guest would be preferred, but this causes issues with downloads
    // see https://issues.chromium.org/issues/42323769
    // chromeOptions.addArguments('--guest')
    chromeOptions.setExperimentalOption('prefs', prefs)

    // Add a custom capability that we can test for to verify our configuration is being used
    chromeOptions.setCapability('grails:gebConfigUsed', true)

    // The remote address will be set by WebDriverContainerHolder via system property
    // webdriver.remote.server before this closure is called
    new RemoteWebDriver(chromeOptions)
}

// Another proof that GebConfig.groovy is being utilized, next to GebConfigSpec
reportingListener = new ReportingListener() {
    void onReport(Reporter reporter, ReportState reportState, List<File> reportFiles) {
        reportFiles.each {
            println "[[ATTACHMENT|$it.absolutePath]]"
        }
    }
}