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

import org.openqa.selenium.JavascriptExecutor
import org.demo.spock.pages.HomePage

import grails.plugin.geb.PlaywrightGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class PlaywrightStressSpec extends PlaywrightGebSpec {

    void 'should wait for delayed content and handle repeated type and click interactions'() {
        given:
        to(HomePage)
        JavascriptExecutor javascript = driver as JavascriptExecutor
        javascript.executeScript('''
            document.body.insertAdjacentHTML('beforeend',
                '<input id="playwright-input"><button id="playwright-button">Submit</button><p id="playwright-result"></p>');
            document.querySelector('#playwright-button').addEventListener('click', () => {
                document.querySelector('#playwright-result').textContent = document.querySelector('#playwright-input').value;
            });
            setTimeout(() => document.querySelector('#playwright-result').dataset.ready = 'true', 150);
        ''')

        when:
        $('#playwright-input') << 'grails-playwright'
        $('#playwright-button').click()

        then:
        waitFor { $('#playwright-result').text() == 'grails-playwright' }
        waitFor { $('#playwright-result').attr('data-ready') == 'true' }
    }

}
