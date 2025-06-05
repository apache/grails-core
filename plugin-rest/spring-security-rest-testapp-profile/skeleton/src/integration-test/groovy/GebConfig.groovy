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
import org.openqa.selenium.Dimension
import org.openqa.selenium.phantomjs.PhantomJSDriver
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxProfile

environments {
    phantomJs {

    }

    firefox {
        driver = {
            //set the firefox locale to 'en-us' since the tests expect english
            //see http://stackoverflow.com/questions/9822717 for more details
            FirefoxProfile profile = new FirefoxProfile()
            profile.setPreference("intl.accept_languages", "en-uk")
            def driverInstance = new FirefoxDriver(profile)
            driverInstance.manage().window().maximize()
            driverInstance
        }

        baseNavigatorWaiting = true
        atCheckWaiting = true
    }
}

driver = {
    def capabilities = new DesiredCapabilities()
    capabilities.setCapability("phantomjs.page.customHeaders.Accept-Language", "en-UK")
    def d = new PhantomJSDriver(capabilities)
    d.manage().window().setSize(new Dimension(1028, 768))
    return d
}

atCheckWaiting = true
baseNavigatorWaiting = true
waiting {
    timeout = 10
    retryInterval = 0.5
}
