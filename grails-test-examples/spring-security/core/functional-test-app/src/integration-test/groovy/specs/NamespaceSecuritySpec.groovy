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

import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import pages.IndexPage
import pages.LoginPage
import spock.lang.IgnoreIf
import spock.lang.Unroll

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'annotation' })
class NamespaceSecuritySpec extends AbstractSecuritySpec {

	protected void resetDatabase() {
		super.resetDatabase()
		go 'testData/addTestUsers'
	}

    @Unroll
	void '#path should redirect to login page for anonymous'(String uri, String path) {
		when:
		go path

		then:
		at LoginPage

		where:
		uri << ['books', 'books.json', 'movies', 'movies.json']
        path = 'api/v1/' + uri
	}

	void 'api not allowed for testuser'() {
		when:
		login 'testuser', 'password'

		then:
		at IndexPage

		when:
		go 'api/v1/books' + format

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'api/v1/movies' + format

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		where:
		format << ['', '.json']
	}

	void 'verify security for testuser_books'() {
		when:
		login 'testuser_books', 'password'

		then:
		at IndexPage

		when:
		go 'api/v1/books' + format

		then:
		jsonResultTitle == 'TestBook'

		when:
		go 'api/v1/movies' + format

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		where:
		format << ['', '.json']
	}

	void 'verify security for testuser_movies'() {
		when:
		login 'testuser_movies', 'password'

		then:
		at IndexPage

		when:
		go 'api/v1/books' + format

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'api/v1/movies' + format

		then:
		jsonResultTitle == 'TestMovie'

		where:
		format << ['', '.json']
	}

	void 'verify security for testuser_books_and_movies'() {
		when:
		login 'testuser_books_and_movies', 'password'

		then:
		at IndexPage

		when:
		go 'api/v1/books' + format

		then:
		jsonResultTitle == 'TestBook'

		when:
		go 'api/v1/movies' + format

		then:
		jsonResultTitle == 'TestMovie'

		where:
		format << ['', '.json']
	}

	void 'namespaced controller with same name can have different secured annotations - open'() {
		when:
		go 'openNamespaced'

		then:
		pageSource.contains 'open'
	}

	void 'namespaced controller with same name can have different secured annotations - secured'() {
		when:
		go 'secureNamespaced'

		then:
		at LoginPage
	}

	private String getJsonResultTitle() {

		def matcher = pageSource =~ /.*(\[\{.+\}\]).*/
		assert matcher.hasGroup()
		assert matcher.count == 1

		def results = new JsonSlurper().parseText(matcher[0][1])
		assert results instanceof List
		assert results.size() == 1
		assert results[0].id instanceof Number

		results[0].title
	}
}
