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
package grails.artefact.controller.support

import grails.web.UrlConverter
import grails.web.api.WebAttributes
import grails.web.mapping.LinkGenerator
import groovy.transform.CompileStatic
import groovy.transform.Generated
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.grails.web.mapping.UrlMappingUtils
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.WebRequest
import org.springframework.web.filter.OncePerRequestFilter

/**
 * A Trait for classes that forward the request
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
trait RequestForwarder implements WebAttributes {

    private UrlConverter urlConverter
    private LinkGenerator linkGenerator

    @Generated
    @Autowired(required = false)
    void setUrlConverter(UrlConverter urlConverter) {
        this.urlConverter = urlConverter
    }

    private LinkGenerator lookupLinkGenerator() {
        if (this.linkGenerator == null) {
            this.linkGenerator = webRequest.getApplicationContext().getBean(LinkGenerator)
        }
        return this.linkGenerator
    }

    /**
     * Forwards a request for the given parameters using the RequestDispatchers forward method
     *
     * @param params The parameters
     * @return The forwarded URL
     */
    @Generated
    String forward(Map params) {

        GrailsWebRequest webRequest = getWebRequest()

        if (webRequest) {
            def controllerName
            if (params.controller) {
                controllerName = params.controller
            } else {
                controllerName = webRequest.controllerName
            }

            if (controllerName) {
                def convertedControllerName = convert(controllerName.toString())
                webRequest.controllerName = convertedControllerName
            }
            params.controller = webRequest.controllerName

            if (params.action) {
                params.action = convert(params.action.toString())
            }

            if (params.namespace) {
                params.namespace = params.namespace
            }

            if (params.plugin) {
                params.plugin = params.plugin
            }

            if (!params.params) {
                params.params = UrlMappingUtils.findAllParamsNotInKeys(
                        UrlMappingUtils.findAllParamsNotInUrlMappingKeywords(webRequest.params),
                        webRequest.originalParams.keySet()
                )
            }
        }

        Map model = params.model instanceof Map ? (Map) params.model : Collections.EMPTY_MAP

        HttpServletRequest request = webRequest.currentRequest
        HttpServletResponse response = webRequest.currentResponse

        for (Map.Entry<String, Object> entry : model.entrySet()) {
            request.setAttribute(entry.getKey(), entry.getValue())
        }

        request.setAttribute(GrailsApplicationAttributes.FORWARD_IN_PROGRESS, true)
        params.includeContext = false
        String fowardURI = lookupLinkGenerator().link(params)

        RequestDispatcher dispatcher = request.getRequestDispatcher(fowardURI)

        int requestScope = WebRequest.SCOPE_REQUEST
        webRequest.removeAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, requestScope)
        webRequest.removeAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS_AVAILABLE, requestScope)
        webRequest.removeAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, requestScope)
        webRequest.removeAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, requestScope)
        webRequest.removeAttribute("grailsWebRequestFilter" + OncePerRequestFilter.ALREADY_FILTERED_SUFFIX, requestScope)
        try {
            dispatcher.forward(request, response)
            request.setAttribute(GrailsApplicationAttributes.FORWARD_ISSUED, true)
        } finally {
            // cleanup after forward
            webRequest.removeAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW, requestScope)
            webRequest.removeAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS_AVAILABLE, requestScope)
            webRequest.removeAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST, requestScope)
            webRequest.removeAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE, requestScope)
            webRequest.removeAttribute("grailsWebRequestFilter" + OncePerRequestFilter.ALREADY_FILTERED_SUFFIX, requestScope)
        }
        return fowardURI
    }

    private String convert(String value) {
        (urlConverter) ? urlConverter.toUrlElement(value) : value
    }
}