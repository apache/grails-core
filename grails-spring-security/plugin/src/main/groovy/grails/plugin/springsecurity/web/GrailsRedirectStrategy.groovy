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
package grails.plugin.springsecurity.web

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.security.web.PortResolver
import org.springframework.security.web.RedirectStrategy
import org.springframework.security.web.util.UrlUtils

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Builds absolute urls when using header check channel security to prevent the
 * container from generating urls with an incorrect scheme.
 *
 * @author Burt Beckwith
 */
@CompileStatic
@Slf4j
class GrailsRedirectStrategy implements RedirectStrategy {

	/** Dependency injection for the port resolver. */
	PortResolver portResolver

	/** Dependency injection for useHeaderCheckChannelSecurity. */
	boolean useHeaderCheckChannelSecurity

	void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
		String redirectUrl = calculateRedirectUrl(request, url)
		redirectUrl = response.encodeRedirectURL(redirectUrl)

		log.debug "Redirecting to '{}'", redirectUrl

		response.sendRedirect redirectUrl
	}

	protected String calculateRedirectUrl(HttpServletRequest request, String url) {
		if (UrlUtils.isAbsoluteUrl(url)) {
			return url
		}

		url = request.contextPath + url

		if (!useHeaderCheckChannelSecurity) {
			return url
		}

		UrlUtils.buildFullRequestUrl request.scheme, request.serverName,
				portResolver.getServerPort(request), url, null
	}
}
