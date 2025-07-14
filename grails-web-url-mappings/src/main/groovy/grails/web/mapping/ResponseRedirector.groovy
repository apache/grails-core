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
package grails.web.mapping

import grails.web.http.HttpHeaders
import grails.web.mapping.mvc.RedirectEventListener
import grails.web.mapping.mvc.exceptions.CannotRedirectException
import groovy.transform.CompileStatic
import groovy.util.logging.Commons
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.http.HttpStatus
import org.springframework.util.Assert
import org.springframework.web.servlet.support.RequestDataValueProcessor

/**
 * Encapsulates the logic for issuing a redirect based on a Map of arguments
 *
 * @since 2.4
 * @author Graeme Rocher
 */
@CompileStatic
@Commons
class ResponseRedirector {

    public static final String ARGUMENT_PERMANENT = "permanent"
    public static final String ARGUMENT_ABSOLUTE = "absolute"
    public static final String ARGUMENT_MOVED = "moved"
    public static final String GRAILS_REDIRECT_ISSUED = GrailsApplicationAttributes.REDIRECT_ISSUED
    private static final String BLANK = ""
    private static final String KEEP_PARAMS_WHEN_REDIRECT = 'keepParamsWhenRedirect'

    LinkGenerator linkGenerator
    Collection<RedirectEventListener> redirectListeners = []
    RequestDataValueProcessor requestDataValueProcessor
    boolean useJessionId = false

    ResponseRedirector(LinkGenerator linkGenerator) {
        Assert.notNull linkGenerator, "Argument [linkGenerator] cannot be null"
        this.linkGenerator = linkGenerator
    }

    void redirect(Map arguments = Collections.emptyMap()) {
        def webRequest = GrailsWebRequest.lookup()
        HttpServletRequest request = webRequest.currentRequest
        HttpServletResponse response = webRequest.getCurrentResponse()

        redirect(request, response, arguments)
    }

    private static boolean getBooleanArgument(String argumentName, Map arguments, Boolean defaultValue = null) {
        def argument = arguments.get(argumentName)
        if (argument instanceof String) {
            return Boolean.valueOf(argument)
        } else if (argument == null && defaultValue != null) {
            return defaultValue
        } else {
            return Boolean.TRUE == argument
        }
    }

    void redirect(HttpServletRequest request, HttpServletResponse response, Map arguments) {
        if (request.getAttribute(GRAILS_REDIRECT_ISSUED)) {
            throw new CannotRedirectException("Cannot issue a redirect(..) here. A previous call to redirect(..) has already redirected the response.")
        }

        if (response.committed) {
            throw new CannotRedirectException("Cannot issue a redirect(..) here. The response has already been committed either by another redirect or by directly writing to the response.")
        }

        boolean permanent = getBooleanArgument(ARGUMENT_PERMANENT, arguments)
        boolean moved = getBooleanArgument(ARGUMENT_MOVED, arguments, true)

        final Map namedParameters = new LinkedHashMap<>(arguments)
        // we generate a relative link with no context path so that the absolute can be calculated by combining the serverBaseURL
        // which includes the contextPath
        namedParameters.put LinkGenerator.ATTRIBUTE_CONTEXT_PATH, BLANK

        boolean absolute = getBooleanArgument(ARGUMENT_ABSOLUTE, arguments, true)

        // If the request parameters contain "keepParamsWhenRedirect = true", then we add the original params. The
        // new attribute can be used from UrlMappings to redirect from old URLs to new ones while keeping the params
        // See https://github.com/apache/grails-core/issues/10622 & https://github.com/apache/grails-core/issues/10965
        if (Boolean.valueOf(arguments.get(KEEP_PARAMS_WHEN_REDIRECT).toString())) {
            // When redirecting from UrlMappings the original request params are on webRequest.originalParams
            // instead of arguments.params so we merge them.
            def webRequest = GrailsWebRequest.lookup(request)
            if (webRequest.originalParams) {
                final Map configuredParams = (Map) arguments.get(LinkGenerator.ATTRIBUTE_PARAMS) ?: [:]
                namedParameters.put(LinkGenerator.ATTRIBUTE_PARAMS, configuredParams + webRequest.originalParams)
            }
        }
        redirectResponse(linkGenerator.getServerBaseURL(), linkGenerator.link(namedParameters), request, response, permanent, moved, absolute)
    }

    /*
     * Redirects the response the the given URI
     */

    private void redirectResponse(String serverBaseURL, String actualUri, HttpServletRequest request, HttpServletResponse response, boolean permanent, boolean moved, boolean absolute) {
        if (log.isDebugEnabled()) {
            log.debug "Method [redirect] forwarding request to [$actualUri]"
            log.debug "Executing redirect with response [$response]"
        }

        String processedActualUri = processedUrl(actualUri, request)

        String redirectURI
        if (absolute) {
            redirectURI = processedActualUri.contains("://") ? processedActualUri : serverBaseURL + processedActualUri
        } else {
            redirectURI = linkGenerator.contextPath + processedActualUri
        }

        String redirectUrl = useJessionId ? response.encodeRedirectURL(redirectURI) : redirectURI

        int status
        if (permanent) {
            status = moved ? HttpStatus.MOVED_PERMANENTLY.value() : HttpStatus.PERMANENT_REDIRECT.value()
        } else {
            status = moved ? HttpStatus.MOVED_TEMPORARILY.value() : HttpStatus.TEMPORARY_REDIRECT.value()
        }

        response.status = status
        response.setHeader HttpHeaders.LOCATION, redirectUrl

        if (redirectListeners) {
            for (redirectEventListener in redirectListeners) {
                redirectEventListener.responseRedirected redirectUrl
            }
        }

        request.setAttribute GRAILS_REDIRECT_ISSUED, processedActualUri
    }

    private String processedUrl(String link, HttpServletRequest request) {
        if (requestDataValueProcessor) {
            link = requestDataValueProcessor.processUrl(request, link)
        }
        return link
    }
}
