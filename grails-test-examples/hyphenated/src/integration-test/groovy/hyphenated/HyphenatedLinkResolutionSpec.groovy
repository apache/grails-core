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
package hyphenated

import java.net.http.HttpClient
import java.util.regex.Matcher
import java.util.regex.Pattern

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * Pins where resource links land when the hyphenated URL converter renames controllers, actions and
 * namespaces in URLs.
 */
@Integration(applicationClass = Application)
@Tag('http-client')
class HyphenatedLinkResolutionSpec extends Specification implements HttpClientSupport {

    @Shared
    Long guideId

    void setup() {
        if (!guideId) {
            TourGuide.withNewTransaction {
                guideId = new TourGuide(name: 'Rome').save(flush: true, failOnError: true).id
            }
        }
    }

    @Unroll
    void "#element rendered by #page targets #expected"(String page, String element, String expected) {
        when:
        String target = linkTarget(http("${page}?guideId=${guideId}").body() as String, element)

        then:
        target == expected.replace('{id}', String.valueOf(guideId))

        where: 'a namespace is generated in its logical form, which routes as the hyphenated one does'
        page                               | element                   || expected
        '/link-page/links'                 | 'guideShowLink'           || '/city-guides/show/{id}'
        '/link-page/links'                 | 'guideDetailsLink'        || '/city-guides/show-details/{id}'
        '/link-page/links'                 | 'guideDetailsByClassLink' || '/city-guides/show-details/{id}'
        '/back-office/tour-desk/links'     | 'guideShowLink'           || '/backOffice/tour-desk/show/{id}'
        '/back-office/tour-desk/links'     | 'guideDetailsLink'        || '/city-guides/show-details/{id}'
        '/back-office/guide-ledger/links'  | 'guideShowLink'           || '/backOffice/guide-ledger/show/{id}'
    }

    void "a form save on a namespaced controller not named after the domain redirects to it"() {
        given:
        HttpClient client = newHttpClientWith { followRedirects(HttpClient.Redirect.NEVER) }

        when:
        TestHttpResponse response = httpPost([:], '/back-office/tour-desk/save', 'name=Paris',
                'application/x-www-form-urlencoded', client)
        String location = new URI(response.headerValue('Location') ?: '').path

        then:
        response.statusCode() == 302
        location ==~ Pattern.quote('/backOffice/tour-desk/show/') + /\d+/
    }

    private static String linkTarget(String html, String element) {
        Matcher tag = html =~ /<a\b[^>]*\bid="${Pattern.quote(element)}"[^>]*>/
        assert tag.find(), "no element with id ${element}"
        Matcher attribute = tag.group() =~ /\bhref="([^"]*)"/
        assert attribute.find()
        attribute.group(1).replace('&amp;', '&')
    }
}
