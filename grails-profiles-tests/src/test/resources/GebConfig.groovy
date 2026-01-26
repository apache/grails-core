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

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.Dimension

// Geb configuration for profile tests
waiting {
    timeout = 10
    retryInterval = 0.5
}

// Chrome Headless configuration
environments {
    chromeHeadless {
        driver = {
            ChromeOptions options = new ChromeOptions()
            options.addArguments('--headless')
            options.addArguments('--no-sandbox')
            options.addArguments('--disable-dev-shm-usage')
            options.addArguments('--window-size=1920,1080')
            new ChromeDriver(options)
        }
    }
    
    // Chrome with GUI (for debugging)
    chrome {
        driver = {
            ChromeOptions options = new ChromeOptions()
            options.addArguments('--window-size=1920,1080')
            new ChromeDriver(options)
        }
    }
    
    // Firefox Headless configuration
    firefoxHeadless {
        driver = {
            FirefoxOptions options = new FirefoxOptions()
            options.addArguments('--headless')
            options.addArguments('--width=1920')
            options.addArguments('--height=1080')
            new FirefoxDriver(options)
        }
    }
    
    // Firefox with GUI (for debugging)
    firefox {
        driver = {
            FirefoxOptions options = new FirefoxOptions()
            options.addArguments('--width=1920')
            options.addArguments('--height=1080')
            new FirefoxDriver(options)
        }
    }
}