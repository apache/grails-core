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
package namespaces

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
 * Pins where links and redirects built from a controller name or a domain instance land in a running
 * application, across namespaces, controllers not named after their domain class, and controllers
 * sharing a name.
 */
@Integration(applicationClass = Application)
@Tag('http-client')
class LinkResolutionSpec extends Specification implements HttpClientSupport {

    @Shared
    Map<String, Long> ids = [:]

    void setup() {
        if (!ids) {
            Assessment.withNewTransaction {
                ids.assessment = new Assessment(title: 'Quarterly').save(flush: true, failOnError: true).id
                Film film = new Film(title: 'Jaws').save(flush: true, failOnError: true)
                ids.film = film.id
                ids.screening = new Screening(film: film).save(flush: true, failOnError: true).id
                ids.gadget = new Gadget(name: 'Widget').save(flush: true, failOnError: true).id
                ids.catalogItem = new namespaces.catalog.Item(name: 'Catalog').save(flush: true, failOnError: true).id
                ids.archiveItem = new namespaces.archive.Item(name: 'Archive').save(flush: true, failOnError: true).id
            }
        }
    }

    @Unroll
    void "#element rendered by #page targets #expected"(String page, String element, String expected, String lands) {
        when: 'the page is rendered'
        TestHttpResponse response = http("${page}?${fixtureQuery()}")

        then:
        response.statusCode() == 200

        when:
        String target = linkTarget(response.body() as String, element)

        then: 'the link targets the expected controller'
        target == contextPath + fill(expected)

        and: 'the target serves the linked resource'
        lands == null || bodyAt(target).contains(lands)

        where:
        page                             | element                  || expected                                       | lands
        '/home/links'                    | 'assessmentShowLink'     || '/assessment/show/{assessment}'                | 'Quarterly'
        '/home/links'                    | 'assessmentEditLink'     || '/assessment/edit/{assessment}'                | null
        '/home/links'                    | 'assessmentNoActionLink' || '/assessment/index/{assessment}'               | null
        '/home/links'                    | 'assessmentGetLink'      || '/assessment/show/{assessment}'                | 'Quarterly'
        '/home/links'                    | 'assessmentClassLink'    || '/assessment/show/{assessment}'                | 'Quarterly'
        '/home/links'                    | 'assessmentIndexLink'    || '/assessment/index'                            | null
        '/home/links'                    | 'assessmentTagLink'      || '/assessment/show/{assessment}'                | 'Quarterly'
        '/home/links'                    | 'assessmentForm'         || '/assessment/update/{assessment}'              | null
        '/home/links'                    | 'filmShowLink'           || '/movies/show/{film}'                          | 'Jaws'
        '/home/links'                    | 'gadgetShowLink'         || '/gadget/show/{gadget}'                        | 'Widget'
        '/home/links'                    | 'catalogItemShowLink'    || '/item/show/{catalogItem}'                     | 'Catalog'
        '/home/links'                    | 'archiveItemShowLink'    || '/archive/item/show/{archiveItem}'             | 'Archive'
        '/home/links'                    | 'screeningFilmLink'      || '/movies/show/{film}'                          | 'Jaws'
        '/home/links'                    | 'authorLink'             || '/author/index'                                | 'Root Author'
        '/home/links'                    | 'unknownLink'            || '/nowhere/list'                                | null
        '/assessment/links'              | 'assessmentShowLink'     || '/assessment/show/{assessment}'                | 'Quarterly'
        '/assessment/links'              | 'assessmentNoActionLink' || '/assessment/index/{assessment}'                 | null
        '/assessment/links'              | 'assessmentIndexLink'    || '/assessment/index'                            | null
        '/manage/manageAssessment/links' | 'assessmentShowLink'     || '/manage/manageAssessment/show/{assessment}'   | 'Quarterly'
        '/manage/manageAssessment/links' | 'assessmentEditLink'     || '/manage/manageAssessment/edit/{assessment}'   | null
        '/manage/manageAssessment/links' | 'assessmentNoActionLink' || '/manage/manageAssessment/index/{assessment}'  | null
        '/manage/manageAssessment/links' | 'assessmentGetLink'      || '/manage/manageAssessment/show/{assessment}'   | 'Quarterly'
        '/manage/manageAssessment/links' | 'assessmentClassLink'    || '/manage/manageAssessment/show/{assessment}'   | 'Quarterly'
        '/manage/manageAssessment/links' | 'assessmentIndexLink'    || '/manage/manageAssessment/index'               | null
        '/manage/manageAssessment/links' | 'assessmentTagLink'      || '/manage/manageAssessment/show/{assessment}'   | 'Quarterly'
        '/manage/manageAssessment/links' | 'assessmentForm'         || '/manage/manageAssessment/update/{assessment}' | null
        '/manage/manageAssessment/links' | 'assessmentExplicitLink' || '/assessment/show/{assessment}'                | 'Quarterly'
        '/manage/dashboard/links'        | 'assessmentShowLink'     || '/manage/manageAssessment/show/{assessment}'   | 'Quarterly'
        '/manage/dashboard/links'        | 'filmShowLink'           || '/movies/show/{film}'                          | 'Jaws'
        '/manage/dashboard/links'        | 'authorLink'             || '/author/index'                                | 'Root Author'
        '/manage/dashboard/links'        | 'unknownLink'            || '/manage/nowhere/list'                         | null
        '/admin/page/resourceLinks'      | 'assessmentShowLink'     || '/assessment/show/{assessment}'                | 'Quarterly'
        '/admin/page/resourceLinks'      | 'filmShowLink'           || '/movies/show/{film}'                          | 'Jaws'
        '/admin/page/resourceLinks'      | 'gadgetShowLink'         || '/gadget/show/{gadget}'                        | 'Widget'
        '/admin/page/resourceLinks'      | 'authorLink'             || '/admin/author/index'                          | 'Admin Author'
        '/admin/page/resourceLinks'      | 'homeLink'               || '/home/index'                                  | 'Home'
        '/admin/page/resourceLinks'      | 'unknownLink'            || '/admin/nowhere/list'                          | null
        '/admin/gadgetReport/links'      | 'gadgetShowLink'         || '/gadget/show/{gadget}'                        | 'Widget'
        '/archive/item/links'            | 'archiveItemShowLink'    || '/archive/item/show/{archiveItem}'             | 'Archive'
        '/archive/item/links'            | 'catalogItemShowLink'    || '/item/show/{catalogItem}'                     | 'Catalog'
        '/item/links'                    | 'catalogItemShowLink'    || '/item/show/{catalogItem}'                     | 'Catalog'
        '/item/links'                    | 'archiveItemShowLink'    || '/archive/item/show/{archiveItem}'             | 'Archive'
    }

    @Unroll
    void "#description redirects to #expected"(String description, String path, String form, String expected, String lands) {
        given: 'a client that reports redirects rather than following them'
        HttpClient client = newHttpClientWith { followRedirects(HttpClient.Redirect.NEVER) }

        when:
        TestHttpResponse response = form != null ?
                httpPost([:], fill(path), form, 'application/x-www-form-urlencoded', client) :
                http(fill(path), client)

        then:
        response.statusCode() == 302

        when:
        String target = new URI(response.headerValue('Location')).path

        then: 'the redirect targets the expected controller'
        target ==~ Pattern.quote(contextPath) + fill(expected).split(/\{new\}/, -1).collect { Pattern.quote(it) }.join(/\d+/)

        and: 'the target serves the resource'
        lands == null || bodyAt(target).contains(lands)

        where:
        description                                               | path                                      | form           || expected                                   | lands
        'a form save on a renamed RestfulController'              | '/movies/save'                            | 'title=Alien'  || '/movies/show/{new}'                        | 'Alien'
        'a form save on a namespaced RestfulController'           | '/manage/manageAssessment/save'           | 'title=Annual' || '/manage/manageAssessment/show/{new}'       | 'Annual'
        'a form save on the controller named after the domain'    | '/assessment/save'                        | 'title=Weekly' || '/assessment/show/{new}'                    | 'Weekly'
        'a form save on a namespaced same-named controller'       | '/archive/item/save'                      | 'name=Old'     || '/archive/item/show/{new}'                  | 'Old'
        'a form save on the default-namespace same-named one'     | '/item/save'                              | 'name=New'     || '/item/show/{new}'                          | 'New'
        'an admin redirect to a controller only in the default'   | '/admin/page/redirectToHome'              | null           || '/home/index'                               | 'Home'
        'an admin redirect to a name in the default and admin'    | '/admin/page/redirectToAuthor'            | null           || '/admin/author/index'                       | 'Admin Author'
        'an admin redirect to a controller only in admin'         | '/admin/page/redirectToBook'              | null           || '/admin/book/index'                         | null
        'a manage redirect to a film'                             | '/manage/dashboard/redirectToFilm/{film}' | null           || '/movies/show/{film}'                       | 'Jaws'
        'an admin redirect to a gadget'                           | '/admin/page/redirectToGadget/{gadget}'   | null           || '/gadget/show/{gadget}'                     | 'Widget'
    }

    void "a JSON save on a renamed RestfulController sets a Location naming it"() {
        when:
        TestHttpResponse response = httpPostJson([Accept: 'application/json'], '/movies/save', [title: 'Heat'])

        then:
        response.statusCode() == 201
        new URI(response.headerValue('Location')).path ==~ Pattern.quote("${contextPath}/movies/show/") + /\d+/
    }

    void "a link naming no HTTP method is resolved for each request's method, even once cached"() {
        when: 'the same page is rendered by a GET, a POST and a GET again'
        String viaGet = linkTarget(http('/home/methodLinks').body() as String, 'targetLink')
        String viaPost = linkTarget(httpPost([:], '/home/methodLinks', '', 'application/x-www-form-urlencoded').body() as String, 'targetLink')
        String viaGetAgain = linkTarget(http('/home/methodLinks').body() as String, 'targetLink')

        then: 'the POST uses the mapping for POST, and neither is served the other from the cache'
        viaGet == "${contextPath}/home/target"
        viaPost == "${contextPath}/home-submit"
        viaGetAgain == viaGet
    }

    void "a link fitting an any-method mapping is not sent to the wildcard by a GET mapping needing other parameters"() {
        when:
        String target = linkTarget(http('/home/methodLinks').body() as String, 'pagedListLink')

        then:
        target == "${contextPath}/home-list/2"
        bodyAt(target).contains('List 2')
    }

    private String fixtureQuery() {
        ids.collect { key, value -> "${key}Id=${value}" }.join('&')
    }

    private String fill(String template) {
        template.replaceAll(/\{(\w+)\}/) { List<String> match ->
            match[1] == 'new' ? match[0] : String.valueOf(ids[match[1]])
        }
    }

    private String getContextPath() {
        new URI(httpBaseUrl).path
    }

    private String bodyAt(String path) {
        URI base = new URI(httpBaseUrl)
        TestHttpResponse response = http([Accept: 'application/json'], "${base.scheme}://${base.authority}${path}")
        response.statusCode() == 200 ? response.body() as String : "HTTP ${response.statusCode()}"
    }

    private static String linkTarget(String html, String element) {
        Matcher tag = html =~ /<(?:a|form)\b[^>]*\bid="${Pattern.quote(element)}"[^>]*>/
        assert tag.find(), "no element with id ${element}"
        Matcher attribute = tag.group() =~ /\b(?:href|action)="([^"]*)"/
        assert attribute.find(), "no target on ${tag.group()}"
        attribute.group(1).replace('&amp;', '&')
    }
}
