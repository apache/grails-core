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
package org.grails.web.mapping

import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingInfo
import grails.web.servlet.mvc.GrailsParameterMap
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.IncludedContent
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.ModelAndView
import spock.lang.Specification
import spock.lang.Unroll

class UrlMappingUtilsSpec extends Specification {

    private MockHttpServletRequest request
    private MockHttpServletResponse response
    private GrailsApplicationAttributes attr
    private GrailsWebRequest webRequest
    private LinkGenerator linkGenerator

    void setup() {
        request = new MockHttpServletRequest()
        response = new MockHttpServletResponse()
        attr = Mock(GrailsApplicationAttributes.class)
        linkGenerator = Mock(LinkGenerator.class)
        webRequest = new GrailsWebRequest(request, response, attr)
        webRequest.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView(), 0)
        request.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, webRequest)
    }

    @Unroll
    void "test buildDispatchUrlForMapping"() {
        expect:
        expected == UrlMappingUtils.findAllParamsNotInUrlMappingKeywords(params)

        where:
        params                      | expected
        [id: 1, controller: 'home'] | [id: 1]
        [id: 1, format: 'json']     | [id: 1, format: 'json']
    }

    void "test includeForUrlMappingInfo when linkGenerator is passed in"() {
        given:
            final String retUrl = '/testAction'
            final UrlMappingInfo info = new ForwardUrlMappingInfo(controllerName: 'testController', actionName: 'testAction')
            final Map model = [:]

        when:
            final IncludedContent includedContent = UrlMappingUtils.includeForUrlMappingInfo(request, response, info, model, linkGenerator)
        then:
            1 * linkGenerator.link(_ as Map) >> { Map m -> return retUrl }
        and:
            includedContent
    }

    void "test includeForUrlMappingInfo keeps explicit null namespace when linkGenerator is passed in"() {
        given:
            final String retUrl = '/testAction'
            final UrlMappingInfo info = new ForwardUrlMappingInfo(controllerName: 'testController', actionName: 'testAction', namespaceSpecified: true)
            final Map model = [:]

        when:
            final IncludedContent includedContent = UrlMappingUtils.includeForUrlMappingInfo(request, response, info, model, linkGenerator)

        then:
            1 * linkGenerator.link(_ as Map) >> { Map m ->
                assert m.namespace == null
                assert m.containsKey(LinkGenerator.ATTRIBUTE_NAMESPACE)
                retUrl
            }

        and:
            includedContent
    }

    void "test forwardRequestForUrlMappingInfo clears TEMPLATE_MODEL before forwarding"() {
        given: "a TEMPLATE_MODEL set on the request before the forward"
            final Map<String, Object> templateModel = [key: 'value']
            webRequest.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, templateModel, 0)
            final UrlMappingInfo info = new ForwardUrlMappingInfo(controllerName: 'testController', actionName: 'testAction')
            Map<String, Object> capturedDuringForward = [:]
            MockHttpServletRequest capturingRequest = new MockHttpServletRequest() {
                @Override
                jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
                    return new jakarta.servlet.RequestDispatcher() {
                        @Override
                        void forward(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                            capturedDuringForward.templateModel = req.getAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL)
                        }
                        @Override
                        void include(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {}
                    }
                }
            }
            GrailsWebRequest capturingWebRequest = new GrailsWebRequest(capturingRequest, response, attr)
            capturingWebRequest.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView(), 0)
            capturingWebRequest.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, templateModel, 0)
            capturingRequest.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, capturingWebRequest)

        when: "the request is forwarded"
            UrlMappingUtils.forwardRequestForUrlMappingInfo(capturingRequest, response, info)

        then: "TEMPLATE_MODEL was null during the forward (not leaked from the failed action to the error action)"
            capturedDuringForward.templateModel == null
    }

    void "test includeForUrlMappingInfo clears TEMPLATE_MODEL during include and restores it after"() {
        given: "a TEMPLATE_MODEL set on the outer request before the include"
            final Map<String, Object> outerTemplateModel = [outerKey: 'outerValue']
            webRequest.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, outerTemplateModel, 0)
            final String retUrl = '/testAction'
            final UrlMappingInfo info = new ForwardUrlMappingInfo(controllerName: 'testController', actionName: 'testAction')
            final Map model = [:]
            Map<String, Object> capturedDuringInclude = [:]
            MockHttpServletRequest capturingRequest = new MockHttpServletRequest() {
                @Override
                jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
                    return new jakarta.servlet.RequestDispatcher() {
                        @Override
                        void forward(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {}
                        @Override
                        void include(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                            capturedDuringInclude.templateModel = req.getAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL)
                        }
                    }
                }
            }
            GrailsWebRequest capturingWebRequest = new GrailsWebRequest(capturingRequest, response, attr)
            capturingWebRequest.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView(), 0)
            capturingWebRequest.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, outerTemplateModel, 0)
            capturingRequest.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, capturingWebRequest)

        when: "the include is dispatched"
            linkGenerator.link(_ as Map) >> retUrl
            UrlMappingUtils.includeForUrlMappingInfo(capturingRequest, response, info, model, linkGenerator)

        then: "TEMPLATE_MODEL was null during the include (not leaked from the outer action)"
            capturedDuringInclude.templateModel == null

        and: "TEMPLATE_MODEL is restored to the outer value after the include"
            capturingWebRequest.getAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, 0) == outerTemplateModel
    }

    void "test includeForUrlMappingInfo restores outer TEMPLATE_MODEL even when the include throws"() {
        given: "an outer TEMPLATE_MODEL and a dispatcher that sets a distinct inner model then throws"
            final Map<String, Object> outerTemplateModel = [outerKey: 'outerValue']
            final String retUrl = '/testAction'
            final UrlMappingInfo info = new ForwardUrlMappingInfo(controllerName: 'testController', actionName: 'testAction')
            final Map model = [:]
            MockHttpServletRequest capturingRequest = new MockHttpServletRequest() {
                @Override
                jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
                    return new jakarta.servlet.RequestDispatcher() {
                        @Override
                        void forward(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {}
                        @Override
                        void include(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                            req.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, [fromIncluded: true])
                            throw new RuntimeException('include failed')
                        }
                    }
                }
            }
            GrailsWebRequest capturingWebRequest = new GrailsWebRequest(capturingRequest, response, attr)
            capturingWebRequest.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView(), 0)
            capturingWebRequest.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, outerTemplateModel, 0)
            capturingRequest.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, capturingWebRequest)

        when: "the include is dispatched and the included action throws"
            linkGenerator.link(_ as Map) >> retUrl
            UrlMappingUtils.includeForUrlMappingInfo(capturingRequest, response, info, model, linkGenerator)

        then: "the exception propagates"
            thrown(Exception)

        and: "the outer TEMPLATE_MODEL is restored by identity (not just equality)"
            capturingWebRequest.getAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, 0).is(outerTemplateModel)
    }

    void "test includeForUrlMappingInfo removes TEMPLATE_MODEL after include when it was not set before"() {
        given: "no TEMPLATE_MODEL on the outer request before the include"
            final String retUrl = '/testAction'
            final UrlMappingInfo info = new ForwardUrlMappingInfo(controllerName: 'testController', actionName: 'testAction')
            final Map model = [:]
            MockHttpServletRequest capturingRequest = new MockHttpServletRequest() {
                @Override
                jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
                    return new jakarta.servlet.RequestDispatcher() {
                        @Override
                        void forward(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {}
                        @Override
                        void include(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                            req.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, [fromIncluded: true])
                        }
                    }
                }
            }
            GrailsWebRequest capturingWebRequest = new GrailsWebRequest(capturingRequest, response, attr)
            capturingWebRequest.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, new ModelAndView(), 0)
            capturingRequest.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, capturingWebRequest)

        when: "the include is dispatched and the included action sets TEMPLATE_MODEL"
            linkGenerator.link(_ as Map) >> retUrl
            UrlMappingUtils.includeForUrlMappingInfo(capturingRequest, response, info, model, linkGenerator)

        then: "TEMPLATE_MODEL is removed after the include (not leaked back to the outer action)"
            capturingWebRequest.getAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, 0) == null
    }

    void "includeRequestForUrlMappingInfo #outcome gives the page back its namespace, controller, action, id, parameters, model and request attributes"() {
        given: 'a page with its own namespace, controller, action, id, parameter, model, request attribute and matched mapping'
            final ModelAndView pageModel = new ModelAndView('/outer/page', [greeting: 'hello'])
            final Map<String, Object> pageTemplateModel = [templateKey: 'templateValue']
            final Object pageMatch = new ForwardUrlMappingInfo(controllerName: 'outer', actionName: 'page')
            final Object pageControllerClass = new Object()
            final UrlMappingInfo fragment = new ForwardUrlMappingInfo(controllerName: 'inner', actionName: 'fragment', id: '9', params: [marker: 'fragment'])
            Map<String, Object> duringInclude = [fails: fails]
            MockHttpServletRequest pageRequest = new MockHttpServletRequest() {
                @Override
                jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
                    return new jakarta.servlet.RequestDispatcher() {
                        @Override
                        void forward(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {}
                        @Override
                        void include(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                            GrailsWebRequest included = GrailsWebRequest.lookup((jakarta.servlet.http.HttpServletRequest) req)
                            duringInclude.path = path
                            duringInclude.controller = included.controllerName
                            duringInclude.action = included.actionName
                            duringInclude.mapping = req.getAttribute(UrlMappingsHandlerMapping.INCLUDED_MAPPING_INFO)
                            duringInclude.model = req.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
                            duringInclude.greeting = req.getAttribute('greeting')
                            duringInclude.only = req.getAttribute('only')
                            // as the included action does, with a controller of no namespace
                            included.controllerNamespace = null
                            // as the handler mapping does for the included action
                            req.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, fragment)
                            req.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, 'inner controller')
                            if (duringInclude.fails) {
                                throw new IllegalStateException('the included action failed')
                            }
                        }
                    }
                }
            }
            GrailsWebRequest page = new GrailsWebRequest(pageRequest, response, attr)
            pageRequest.setAttribute(GrailsApplicationAttributes.WEB_REQUEST, page)
            page.controllerNamespace = 'admin'
            page.controllerName = 'outer'
            page.actionName = 'page'
            page.id = '7'
            page.params.marker = 'page'
            page.setAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, pageModel, 0)
            page.setAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, pageTemplateModel, 0)
            pageRequest.setAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, pageMatch)
            pageRequest.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, pageControllerClass)
            pageRequest.setAttribute('greeting', 'outer')

        when: 'the page includes another action, with a model of its own'
            Throwable failure = null
            try {
                UrlMappingUtils.includeRequestForUrlMappingInfo(pageRequest, response, fragment, [greeting: 'inner', only: 'fragment'], false)
            }
            catch (IllegalStateException e) {
                failure = e
            }

        then: 'the include ran for the action it was made for, without the model of the page'
            duringInclude.path == '/inner/fragment'
            duringInclude.controller == 'inner'
            duringInclude.action == 'fragment'
            duringInclude.mapping.is(fragment)
            duringInclude.model == null
            duringInclude.greeting == 'inner'
            duringInclude.only == 'fragment'
            (failure != null) == fails

        and: 'the page is as it was'
            page.controllerNamespace == 'admin'
            page.controllerName == 'outer'
            page.actionName == 'page'
            page.id == '7'
            page.params.marker == 'page'
            page.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, 0).is(pageModel)
            page.getAttribute(GrailsApplicationAttributes.TEMPLATE_MODEL, 0).is(pageTemplateModel)
            pageRequest.getAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST).is(pageMatch)
            pageRequest.getAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS).is(pageControllerClass)
            pageRequest.getAttribute(UrlMappingsHandlerMapping.INCLUDED_MAPPING_INFO) == null
            pageRequest.getAttribute('greeting') == 'outer'
            pageRequest.getAttribute('only') == null

        where:
            outcome                         | fails
            'that returns'                  | false
            'whose included action throws'  | true
    }
}
