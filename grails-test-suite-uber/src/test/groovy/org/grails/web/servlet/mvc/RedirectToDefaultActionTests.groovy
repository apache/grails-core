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
package org.grails.web.servlet.mvc

import grails.testing.web.GrailsWebUnitTest
import spock.lang.Specification
import grails.artefact.Artefact

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class RedirectToDefaultActionTests extends Specification implements GrailsWebUnitTest {

    void setup() {
        mockController(RepositoryController)
        mockController(PortalController)
    }

    void testRedirect() {
        when:
        def c = new PortalController()
        c.content()

        then:
        "/repository/index" == response.redirectedUrl
    }

    void testRedirectToExplicitDefaultAction() {
        when:
        def c = new RepositoryController()
        c.toPortal()

        then:
        "/portal/content" == response.redirectedUrl
    }
}

@Artefact('Controller')
class PortalController {

    static defaultAction = 'content'

    def content = { redirect(controller:'repository') }
}

@Artefact('Controller')
class RepositoryController {
    def index = { render "hello world" }

    def toPortal = { redirect(controller: "portal") }
}
