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

import grails.testing.web.UrlMappingsUnitTest
import org.grails.core.artefact.ControllerArtefactHandler

import spock.lang.Specification

/**
 * A redirect from a namespaced controller, naming no namespace, reaches a controller defined only in the
 * default namespace rather than being forced into the redirecting controller's namespace. This needs an
 * application where only the default-namespace controller is registered, so it has a specification of
 * its own.
 */
class RedirectToControllerInDefaultNamespaceSpec extends Specification implements UrlMappingsUnitTest<RedirectMethodTests.UrlMappings> {

    void "a redirect from a namespace reaches a controller defined only in the default namespace"() {
        given: 'anotherNamespaced is registered only in the default namespace'
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, org.grails.web.servlet.mvc.alpha.AnotherNamespacedController)
        def linkGenerator = applicationContext.getBean('grailsLinkGenerator')
        linkGenerator.grailsApplication = grailsApplication
        linkGenerator.resetControllerNamespaceCache()
        webRequest.controllerName = 'namespaced'

        when: 'the secondary controller redirects to it without naming a namespace'
        new org.grails.web.servlet.mvc.beta.NamespacedController().redirectToAnotherNamespaced()

        then: 'the redirect is not forced into the secondary namespace'
        response.redirectedUrl == '/anotherNoNamespace/demo'
    }
}
