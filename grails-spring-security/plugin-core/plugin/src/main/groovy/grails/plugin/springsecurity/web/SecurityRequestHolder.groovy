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

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import groovy.transform.CompileStatic

/**
 * Uses a {@link ThreadLocal} to store the current request and response.
 *
 * @author Burt Beckwith
 */
@CompileStatic
final class SecurityRequestHolder {

	private static final ThreadLocal<HttpServletRequest> REQUEST_HOLDER = new ThreadLocal<HttpServletRequest>()
	private static final ThreadLocal<HttpServletResponse> RESPONSE_HOLDER = new ThreadLocal<HttpServletResponse>()

	private SecurityRequestHolder() {
		// static only
	}

	/**
	 * Clear the saved request.
	 */
	static void reset() {
		REQUEST_HOLDER.remove()
		RESPONSE_HOLDER.remove()
	}

	/**
	 * Set the current request and response.
	 * @param request the request
	 * @param response the response
	 */
	static void set(HttpServletRequest request, HttpServletResponse response) {
		REQUEST_HOLDER.set request
		RESPONSE_HOLDER.set response
	}

	/**
	 * Get the current request.
	 * @return the request
	 */
	static HttpServletRequest getRequest() {
		REQUEST_HOLDER.get()
	}

	/**
	 * Get the current response.
	 * @return the response
	 */
	static HttpServletResponse getResponse() {
		RESPONSE_HOLDER.get()
	}
}
