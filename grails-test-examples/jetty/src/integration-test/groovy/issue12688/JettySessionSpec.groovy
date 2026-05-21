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

package issue12688

import issue12688.pages.SessionFormPage
import issue12688.pages.SessionShowPage

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Regression test for https://github.com/apache/grails-core/issues/12688.
 *
 * Verifies that a Grails application running on Jetty (instead of Tomcat) can process
 * request parameters and store values in the HTTP session without serialization errors.
 * Jetty's session management checks serializability of session attributes, so this test
 * confirms there are no NotSerializableException warnings from GrailsParameterMap.
 */
@Integration
class JettySessionSpec extends ContainerGebSpec {

    void 'request params can be processed and stored in session on a Jetty-backed Grails app'() {
        given: 'a user visits the session form'
        to(SessionFormPage)

        when: 'they submit a message value'
        messageInput.value('hello jetty')
        submitButton.click()

        then: 'the value is stored in session and displayed after the redirect'
        at(SessionShowPage)
        messageParagraph.text() == 'hello jetty'
    }
}
