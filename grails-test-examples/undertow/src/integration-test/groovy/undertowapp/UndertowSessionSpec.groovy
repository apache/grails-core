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

package undertowapp

import undertowapp.pages.SessionFormPage
import undertowapp.pages.SessionShowPage

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.server.context.WebServerApplicationContext

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Functional test for the Grails Undertow plugin (grails-undertow).
 *
 * Verifies that a Grails application running on Undertow (instead of Tomcat) can process
 * request parameters and store values in the HTTP session without serialization errors.
 * This confirms the Grails Undertow plugin can serve a full request/redirect/session cycle.
 */
@Integration
class UndertowSessionSpec extends ContainerGebSpec {

    @Autowired
    WebServerApplicationContext webServerApplicationContext

    void 'the application runs on the Undertow embedded servlet container'() {
        expect: 'the web server comes from the vendored Undertow support, not Tomcat or Jetty'
        webServerApplicationContext.webServer.class.name.startsWith('org.apache.grails.undertow.core')
    }

    void 'request params can be processed and stored in session on an Undertow-backed Grails app'() {
        given: 'a user visits the session form'
        to(SessionFormPage)

        when: 'they submit a message value'
        messageInput.value('hello undertow')
        submitButton.click()

        then: 'the value is stored in session and displayed after the redirect'
        at(SessionShowPage)
        messageParagraph.text() == 'hello undertow'
    }
}
