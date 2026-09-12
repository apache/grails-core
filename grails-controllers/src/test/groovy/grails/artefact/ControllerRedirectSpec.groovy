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
package grails.artefact

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.GrailsControllerClass
import grails.util.GrailsWebMockUtil
import grails.web.mapping.LinkGenerator
import grails.web.mapping.mvc.RedirectEventListener
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.web.servlet.mvc.ParameterCreationListener
import org.grails.web.util.GrailsApplicationAttributes

class ControllerRedirectSpec extends Specification {

    MockServletContext servletContext = new MockServletContext()

    List<Map> linkArguments = []

    LinkGenerator linkGenerator = Stub(LinkGenerator) {
        getServerBaseURL() >> 'http://localhost:8080'
        link(_) >> { Map arguments ->
            linkArguments << arguments
            "/${arguments.action}".toString()
        }
    }

    WebApplicationContext applicationContext = Mock(WebApplicationContext)

    DefaultGrailsApplication grailsApplication = new DefaultGrailsApplication(PlainRedirectController, NamespacedRedirectController)

    void setup() {
        grailsApplication.initialise()
        applicationContext.getBean(LinkGenerator) >> linkGenerator
        applicationContext.getBeansOfType(ParameterCreationListener) >> [:]
        applicationContext.containsBean(GrailsApplication.APPLICATION_ID) >> true
        applicationContext.getBean(GrailsApplication.APPLICATION_ID) >> grailsApplication
        servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, applicationContext)
    }

    void cleanup() {
        RequestContextHolder.setRequestAttributes(null)
    }

    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext)
        GrailsWebMockUtil.bindMockWebRequest(applicationContext, request, new MockHttpServletResponse())
        request
    }

    private MockHttpServletResponse currentResponse() {
        RequestContextHolder.currentRequestAttributes().currentResponse as MockHttpServletResponse
    }

    void 'redirect resolves the namespace declared by the controller class issuing it'() {
        given: 'a controller without a namespace and one declaring a namespace'
        def plain = new PlainRedirectController()
        def namespaced = new NamespacedRedirectController()

        when: 'each controller redirects'
        bindRequest()
        plain.redirectToIndex()
        bindRequest()
        namespaced.redirectToIndex()

        then: 'each redirect carries the namespace of its own class'
        linkArguments[0].namespace == null
        linkArguments[1].namespace == 'admin'

        when: 'the same two controller classes redirect again'
        bindRequest()
        namespaced.redirectToIndex()
        bindRequest()
        plain.redirectToIndex()
        bindRequest()
        new NamespacedRedirectController().redirectToIndex()

        then: 'the value is still the one declared by each class, never shared between them'
        linkArguments[2].namespace == 'admin'
        linkArguments[3].namespace == null
        linkArguments[4].namespace == 'admin'
    }

    void 'the namespace is taken from the artefact registered for the controller issuing the redirect'() {
        given: 'the namespace the artefact reports for each controller class'
        GrailsControllerClass plainArtefact = grailsApplication.getArtefact(ControllerArtefactHandler.TYPE,
                PlainRedirectController.name) as GrailsControllerClass
        GrailsControllerClass namespacedArtefact = grailsApplication.getArtefact(ControllerArtefactHandler.TYPE,
                NamespacedRedirectController.name) as GrailsControllerClass

        expect: 'the registry is what declares them'
        plainArtefact.namespace == null
        namespacedArtefact.namespace == 'admin'

        when: 'a namespaced controller redirects while a different controller is the one executing'
        MockHttpServletRequest request = bindRequest()
        request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, plainArtefact)
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'plainRedirect')
        new NamespacedRedirectController().redirectToIndex()

        then: 'the namespace is the one declared by the redirecting controller, not the executing one'
        linkArguments[0].namespace == 'admin'
    }

    void 'a controller that is not a registered artefact still resolves its declared namespace'() {
        given: 'a controller class the application knows nothing about'
        expect:
        grailsApplication.getArtefact(ControllerArtefactHandler.TYPE, UnregisteredRedirectController.name) == null

        when:
        bindRequest()
        new UnregisteredRedirectController().redirectToIndex()

        then: 'the namespace declared on the class is used'
        linkArguments[0].namespace == 'reporting'
    }

    void 'an explicit namespace argument is never overwritten by the declared one'() {
        given:
        def namespaced = new NamespacedRedirectController()

        when:
        bindRequest()
        namespaced.redirectToIndexInNamespace('reporting')

        then:
        linkArguments[0].namespace == 'reporting'
        currentResponse().redirectedUrl == 'http://localhost:8080/index'
    }

    void 'a link generator set after the first redirect replaces the one already in use'() {
        given:
        def controller = new PlainRedirectController()
        List<Map> replacementArguments = []
        LinkGenerator replacement = Stub(LinkGenerator) {
            getServerBaseURL() >> 'http://replacement:9090'
            link(_) >> { Map arguments ->
                replacementArguments << arguments
                '/replaced'
            }
        }

        when: 'the controller redirects once, then is given a different link generator'
        bindRequest()
        controller.redirectToIndex()
        controller.setGrailsLinkGenerator(replacement)
        bindRequest()
        controller.redirectToIndex()

        then: 'the second redirect is generated by the replacement'
        linkArguments.size() == 1
        replacementArguments.size() == 1
        currentResponse().redirectedUrl == 'http://replacement:9090/replaced'
    }

    void 'redirect listeners registered after the first redirect are notified'() {
        given:
        def controller = new PlainRedirectController()
        List<String> notified = []
        RedirectEventListener listener = { String url -> notified << url } as RedirectEventListener

        when: 'the controller redirects once before any listener is registered'
        bindRequest()
        controller.redirectToIndex()

        then:
        notified.isEmpty()

        when: 'a listener is registered and a further redirect is issued'
        controller.setRedirectListeners([listener])
        bindRequest()
        controller.redirectToIndex()

        then:
        notified == ['http://localhost:8080/index']
    }
}

class PlainRedirectController implements Controller {

    void redirectToIndex() {
        redirect(action: 'index')
    }
}

class NamespacedRedirectController implements Controller {

    static namespace = 'admin'

    void redirectToIndex() {
        redirect(action: 'index')
    }

    void redirectToIndexInNamespace(String explicitNamespace) {
        redirect(action: 'index', namespace: explicitNamespace)
    }
}

class UnregisteredRedirectController implements Controller {

    static namespace = 'reporting'

    void redirectToIndex() {
        redirect(action: 'index')
    }
}
