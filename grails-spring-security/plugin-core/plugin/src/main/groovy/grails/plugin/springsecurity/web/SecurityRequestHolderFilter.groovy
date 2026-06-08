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
import org.springframework.security.web.PortMapper
import org.springframework.security.web.PortResolver
import org.springframework.web.filter.GenericFilterBean

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse

/**
 * Stores the request and response in the {@link SecurityRequestHolder}. Also wraps the request in a
 * wrapper that is aware of the X-Forwarded-Proto header and returns the correct value from isSecure(),
 * getScheme(), and getServerPort() if the header is present.
 *
 * @author Burt Beckwith
 */
@CompileStatic
@Slf4j
class SecurityRequestHolderFilter extends GenericFilterBean {

	// dependency injections
	boolean useHeaderCheckChannelSecurity
	String secureHeaderName
	String secureHeaderValue
	String insecureHeaderName
	String insecureHeaderValue
	PortMapper portMapper
	PortResolver portResolver

	void doFilter(ServletRequest req, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		HttpServletRequest request = wrapRequest(req as HttpServletRequest)

		SecurityRequestHolder.set request, response as HttpServletResponse

		try {
			chain.doFilter request, response
		}
		finally {
			SecurityRequestHolder.reset()
		}
	}

	/**
	 * If using header check channel security, look for the specified header (typically 'X-Forwarded-Proto')
	 * and if found, return a request wrapper that returns the correct values for isSecure(), getScheme(),
	 * and getServerPort(). Note that the values are switched intentionally since they're configured for
	 * channel security.
	 *
	 * @param request the original request
	 * @return the original request or a wrapper for it
	 */
	protected HttpServletRequest wrapRequest(HttpServletRequest request) {
		if (!useHeaderCheckChannelSecurity) {
			return request
		}

		if (request.getHeader(secureHeaderName) == insecureHeaderValue && request.scheme == 'http') {
			return new HttpServletRequestWrapper(request) {
				boolean isSecure() { true }
				String getScheme() { 'https' }
				int getServerPort() {
					int serverPort = portResolver.getServerPort(request)
					Integer httpsPort = portMapper.lookupHttpsPort(serverPort)
					if (httpsPort == null) {
						log.warn 'No port mapping found for HTTP port {}', serverPort
						httpsPort = serverPort
					}
					httpsPort
				}
			}
		}

		if (request.getHeader(insecureHeaderName) == secureHeaderValue && request.scheme == 'https') {
			return new HttpServletRequestWrapper(request) {
				boolean isSecure() { false }
				String getScheme() { 'http' }
				int getServerPort() {
					int serverPort = portResolver.getServerPort(request)
					Integer httpPort = portMapper.lookupHttpPort(serverPort)
					if (httpPort == null) {
						log.warn 'No port mapping found for HTTPS port {}', serverPort
						httpPort = serverPort
					}
					httpPort
				}
			}
		}

		request
	}
}
