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
package scaffoldingfields

import java.net.http.HttpClient
import java.util.regex.Matcher
import java.util.regex.Pattern

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * Pins the association links the fields plugin renders, and the redirect after a save, for a domain class
 * served by a scaffolded controller not named after it.
 */
@Integration
@Tag('http-client')
class RenamedScaffoldLinksSpec extends Specification implements HttpClientSupport {

    @Shared
    Map<String, Long> ids = [:]

    void setup() {
        if (!ids) {
            Crew.withNewTransaction {
                Crew crew = new Crew(name: 'Pequod')
                crew.addToSailors(new Sailor(name: 'Ishmael'))
                crew.save(flush: true, failOnError: true)
                ids.crew = crew.id
                ids.ishmael = crew.sailors.first().id
            }
        }
    }

    void "a one-to-many association links each element to the controller serving it"() {
        when:
        String href = anchorHref(page("/crew/show/${ids.crew}"), 'Ishmael')

        then:
        href == "${contextPath}/deckhands/show/${ids.ishmael}"
        bodyAt(href).contains('Ishmael')
    }

    void "the add link of a one-to-many input targets the controller serving the element"() {
        when:
        String href = anchorHref(page("/crew/edit/${ids.crew}"), 'Add Sailor')

        then:
        href == "${contextPath}/deckhands/create?crew.id=${ids.crew}"
        bodyAt(href).contains('Create Sailor')
    }

    void "a to-one association rendered by the renamed controller links to its owner"() {
        when:
        String href = anchorHref(page("/deckhands/show/${ids.ishmael}"), 'Pequod')

        then:
        href == "${contextPath}/crew/show/${ids.crew}"
    }

    void "a form save on the renamed controller redirects to it"() {
        given:
        HttpClient client = newHttpClientWith { followRedirects(HttpClient.Redirect.NEVER) }

        when:
        TestHttpResponse response = httpPost([:], '/deckhands/save', "name=Starbuck&crew.id=${ids.crew}",
                'application/x-www-form-urlencoded', client)
        String location = new URI(response.headerValue('Location') ?: '').path

        then:
        response.statusCode() == 302
        location ==~ Pattern.quote("${contextPath}/deckhands/show/") + /\d+/
        bodyAt(location).contains('Starbuck')
    }

    private String page(String path) {
        TestHttpResponse response = http(path)
        assert response.statusCode() == 200
        response.body() as String
    }

    private String getContextPath() {
        new URI(httpBaseUrl).path
    }

    private String bodyAt(String path) {
        URI base = new URI(httpBaseUrl)
        TestHttpResponse response = http("${base.scheme}://${base.authority}${path}")
        response.statusCode() == 200 ? response.body() as String : "HTTP ${response.statusCode()}"
    }

    private static String anchorHref(String html, String text) {
        Matcher anchor = html =~ /<a\b[^>]*\bhref="([^"]*)"[^>]*>\s*${Pattern.quote(text)}\s*<\/a>/
        assert anchor.find(), "no link reading ${text}"
        anchor.group(1).replace('&amp;', '&')
    }
}
