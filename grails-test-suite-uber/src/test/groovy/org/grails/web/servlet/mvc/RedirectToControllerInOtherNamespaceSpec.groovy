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
import org.grails.web.util.GrailsApplicationAttributes

import spock.lang.Specification

/**
 * A redirect naming no namespace resolves its target the way a link does, starting from the redirecting
 * controller's namespace, rather than being forced into that namespace. Controllers registered here stay
 * registered for the rest of the specification, so its steps run in one feature, in order.
 */
class RedirectToControllerInOtherNamespaceSpec extends Specification implements UrlMappingsUnitTest<RedirectMethodTests.UrlMappings> {

    void "a redirect reaches a controller defined only in another namespace, and the nearest one once both define it"() {
        given: 'anotherNamespaced is registered only in the secondary namespace'
        registerController(org.grails.web.servlet.mvc.beta.AnotherNamespacedController)
        webRequest.controllerName = 'namespaced'

        when: 'the non-namespaced controller redirects to it without naming a namespace'
        new org.grails.web.servlet.mvc.alpha.NamespacedController().redirectToAnotherNamespaced()

        then: 'the redirect is not forced into the default namespace'
        response.redirectedUrl == '/anotherSecondaryNamespace/demo'

        when: 'anotherNamespaced is registered in the default namespace as well'
        registerController(org.grails.web.servlet.mvc.alpha.AnotherNamespacedController)
        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        new org.grails.web.servlet.mvc.alpha.NamespacedController().redirectToAnotherNamespaced()

        then: 'the non-namespaced controller reaches the one in its own namespace'
        response.redirectedUrl == '/anotherNoNamespace/demo'

        when: 'the secondary controller redirects to it'
        request.removeAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED)
        new org.grails.web.servlet.mvc.beta.NamespacedController().redirectToAnotherNamespaced()

        then: 'so does the secondary one'
        response.redirectedUrl == '/anotherSecondaryNamespace/demo'
    }

    private void registerController(Class controllerClass) {
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, controllerClass)
        def linkGenerator = applicationContext.getBean('grailsLinkGenerator')
        linkGenerator.grailsApplication = grailsApplication
        linkGenerator.resetControllerNamespaceCache()
    }
}
