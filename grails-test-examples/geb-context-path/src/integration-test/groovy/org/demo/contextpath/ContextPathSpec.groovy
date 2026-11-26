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

package org.demo.contextpath

import org.demo.contextpath.pages.GreetingPage
import org.demo.contextpath.pages.HomePage

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Verifies that ContainerGebSpec correctly includes the server's
 * context path in the browser's base URL. Without the context path
 * in the base URL, page navigation would result in 404 errors.
 */
@Integration
class ContextPathSpec extends ContainerGebSpec {

    void 'should navigate to the home page under the configured context path'() {
        expect: 'visiting the home page'
        to(HomePage)
    }

    void 'should navigate to a controller under the configured context path'() {
        when: 'visiting a controller page'
        to(GreetingPage)

        then: 'the controller action is reached and renders correctly'
        messageText == 'Hello from Grails'
    }
}
