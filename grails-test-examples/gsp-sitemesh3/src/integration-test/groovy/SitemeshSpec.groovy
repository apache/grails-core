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

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class SitemeshSpec extends ContainerGebSpec {

    void "forced layout"() {
        when:
        browser.go 'demo/index'

        then:
        browser.driver.pageSource.contains('Do you like BootStrap?')
    }

    void "decorator chaining"() {
        when:
        browser.go 'demo/chaining'

        then:
        browser.driver.pageSource.contains('This is so cool.')
    }

    void "jsp demo"() {
        when:
        browser.go 'demo/jsp'

        then:
        def container = browser.$('div.container')
        container

        browser.driver.pageSource.contains('Hello World, I am a JSP page!')
    }

    void "text"() {
        when:
        browser.go 'demo/renderText'

        then:
        downloadText() == '''<p>Hello World</p>'''
    }

    void "Controller 500 Example"() {
        when:
        browser.go 'demo/exception'

        then:
        browser.driver.pageSource.contains('Whoops, why would you ever want to see an exception??')
    }

    void "View 500 Example"() {
        when:
        browser.go 'demo/viewException'

        then:
        browser.driver.pageSource.contains('Oh Man, this view sucks!')
    }

    void "404 Error"() {
        when:
        browser.go 'demo/404'

        then:
        browser.driver.pageSource.contains('Error: Page Not Found (404)')
    }
}
