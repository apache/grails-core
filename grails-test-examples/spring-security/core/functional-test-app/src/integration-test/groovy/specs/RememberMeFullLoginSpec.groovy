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

package specs

import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import com.testapp.TestDataService
import functional.test.app.Application
import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Specification

/**
 * A user who is remembered only by the remember-me cookie, and has lost the session, asks for a page that needs a full
 * login. The redirects are followed one at a time so that a loop shows as a long chain of hops instead of a browser error.
 */
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'annotation' })
@Issue('https://github.com/apache/grails-core/issues/15877')
@Integration(applicationClass = Application)
class RememberMeFullLoginSpec extends Specification implements HttpClientSupport {

	static final String FORM = 'application/x-www-form-urlencoded'

	@Autowired
	TestDataService testDataService

	private final CookieManager cookies = new CookieManager()

	private final HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.cookieHandler(cookies)
			.build()

	private HttpResponse<String> send(HttpRequest request) {
		client.send(request, HttpResponse.BodyHandlers.ofString())
	}

	/** Follows redirects by hand and returns the path and query of every request made. */
	private List<String> hopsFrom(String path, int maxHops = 12) {
		List<String> hops = []
		String next = path
		while (next && hops.size() < maxHops) {
			URI uri = URI.create(httpBaseUrl).resolve(next)
			hops << (uri.path + (uri.query ? '?' + uri.query : ''))
			HttpResponse<String> response = send(HttpRequest.newBuilder(uri).GET().build())
			next = response.statusCode() in [301, 302, 303, 307, 308] ? response.headers().firstValue('Location').orElse(null) : null
		}
		hops
	}

	void 'a user remembered by cookie only is sent to the full login page for a page that needs a full login'() {
		given: 'a user who logged in with remember-me'
		testDataService.returnToInitialState()
		send(HttpRequest.newBuilder(URI.create("${httpBaseUrl}/testData/addTestUsers")).GET().build())
		HttpResponse<String> login = send(HttpRequest.newBuilder(URI.create("${httpBaseUrl}/login/authenticate"))
				.header('Content-Type', FORM)
				.POST(HttpRequest.BodyPublishers.ofString('username=testuser&password=password&remember-me=on'))
				.build())
		assert login.statusCode() == 302

		and: 'the session is lost, only the remember-me cookie is left'
		cookies.cookieStore.cookies.findAll { it.name == 'JSESSIONID' }.each { cookies.cookieStore.remove(null, it) }
		assert cookies.cookieStore.cookies*.name.contains('grails_remember_me')

		when: 'a page that needs IS_AUTHENTICATED_FULLY is requested'
		List<String> hops = hopsFrom('/secureNamespaced')

		then: 'the chain ends on the full login page, with no bounce between the page and /login/auth'
		hops == ['/secureNamespaced', '/secureNamespaced?continue', '/login/auth', '/login/full']
	}
}
