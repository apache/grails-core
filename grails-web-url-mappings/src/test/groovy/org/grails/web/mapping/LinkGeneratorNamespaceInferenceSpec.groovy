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
package org.grails.web.mapping

import ch.qos.logback.classic.Level

import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMappingsHolder
import org.apache.grails.core.testing.support.LogCapture
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.web.util.WebUtils
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exhaustive tests for namespace inference performed by
 * {@link org.grails.web.mapping.DefaultLinkGenerator#getDefaultNamespace(String, String)} and the way
 * it feeds into {@link org.grails.web.mapping.DefaultLinkGenerator#link(java.util.Map, String)}.
 *
 * <p>The registered controllers establish these logical-name to namespace mappings:</p>
 * <ul>
 *   <li>{@code home}   -> root only</li>
 *   <li>{@code book}   -> {@code admin} only (unambiguous single namespace)</li>
 *   <li>{@code author} -> root and {@code admin}</li>
 *   <li>{@code report} -> {@code admin} and {@code sales}, with no root controller</li>
 * </ul>
 */
class LinkGeneratorNamespaceInferenceSpec extends Specification {

    static final String BASE_URL = 'https://myserver.com/foo'
    static final String CONTEXT = '/bar'

    DefaultGrailsApplication grailsApplication

    def setup() {
        WebUtils.clearGrailsWebRequest()
        grailsApplication = new DefaultGrailsApplication(
                org.grails.web.mapping.nsinference.root.AuthorController,
                org.grails.web.mapping.nsinference.root.HomeController,
                org.grails.web.mapping.nsinference.admin.AuthorController,
                org.grails.web.mapping.nsinference.admin.BookController,
                org.grails.web.mapping.nsinference.admin.ReportController,
                org.grails.web.mapping.nsinference.sales.ReportController
        ).tap {
            initialise()
        }
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
        WebUtils.clearGrailsWebRequest()
    }

    @Unroll
    def "getDefaultNamespace(#controller) with request controller=#reqController namespace=#reqNamespace resolves to #expected"() {
        given:
        bindRequest(reqController, reqNamespace)
        def generator = createGenerator()

        expect:
        generator.getDefaultNamespace(controller, null) == expected

        where:
        reqController | reqNamespace || controller || expected
        // Same controller as the current request: reuse the current namespace (historical behaviour)
        'book'        | 'admin'      || 'book'      || 'admin'
        'home'        | null         || 'home'      || null
        // No current namespace: only infer for an unambiguous single-namespaced target
        null          | null         || 'book'      || 'admin'   // unique namespaced
        null          | null         || 'home'      || null      // root only
        null          | null         || 'author'    || null      // root + admin: the default namespace
        null          | null         || 'report'    || null      // admin + sales: ambiguous
        null          | null         || 'unknown'   || null      // not registered
        // Defined in the request namespace: it wins over the default namespace, nearest scope first
        'page'        | 'admin'      || 'author'    || 'admin'
        'page'        | 'admin'      || 'report'    || 'admin'
        'page'        | 'sales'      || 'report'    || 'sales'
        // Single controller with the name -> just works regardless of the current namespace
        'page'        | 'admin'      || 'book'      || 'admin'
        'page'        | 'sales'      || 'book'      || 'admin'
        'page'        | 'sales'      || 'author'    || null      // not in sales: the default namespace
        'page'        | 'admin'      || 'home'      || null      // single root controller -> root
    }

    @Unroll
    def "link infers namespace for #attrs -> #expectedUrl"() {
        given:
        bindRequest(reqController, reqNamespace)
        def generator = createGenerator()

        expect:
        generator.link(attrs) == expectedUrl

        where:
        reqController | reqNamespace || attrs                                          || expectedUrl
        null          | null         || [controller: 'book', action: 'index']          || '/bar/admin/book/index'
        null          | null         || [controller: 'home', action: 'index']          || '/bar/home/index'
        null          | null         || [controller: 'author', action: 'index']        || '/bar/author/index'
        'page'        | 'admin'      || [controller: 'author', action: 'list']         || '/bar/admin/author/list'
        'page'        | 'admin'      || [controller: 'report', action: 'index']        || '/bar/admin/report/index'
        'page'        | 'sales'      || [controller: 'report', action: 'index']        || '/bar/sales/report/index'
    }

    def "an explicit namespace attribute always wins over inference"() {
        given:
        bindRequest('page', 'admin')
        def generator = createGenerator()

        expect: 'explicit namespace beats the inferred admin namespace'
        generator.link(controller: 'book', action: 'index', namespace: 'frontend') == '/bar/frontend/book/index'
    }

    @Unroll
    def "resolveNamespace returns #expected for attrs #attrs"() {
        given:
        bindRequest('page', 'admin')
        def generator = createGenerator()

        expect:
        generator.resolveNamespace('book', null, attrs) == expected

        where:
        attrs                   || expected
        [:]                     || 'admin'
        [namespace: 'frontend'] || 'frontend'
        [namespace: '']         || null
        [namespace: '   ']      || null
        [namespace: null]       || null
    }

    def "an explicit null namespace opts out of inference and targets the root controller"() {
        given:
        bindRequest('page', 'admin')
        def generator = createGenerator()

        expect: 'namespace: null suppresses the inferred admin namespace'
        generator.link(controller: 'book', action: 'index', namespace: null) == '/bar/book/index'
    }

    @Unroll
    def "a blank explicit namespace (#explicitNamespace) reaches reverse mapping as null like namespace: null"() {
        given: 'a generator that records the namespace passed to the reverse mapping'
        bindRequest('page', 'admin')
        String captured = 'UNSET'
        def generator = new DefaultLinkGenerator(BASE_URL, CONTEXT)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.grailsApplication = grailsApplication
        def callable = { String controller, String action, String namespace, String pluginName, String httpMethod, Map params ->
            captured = namespace
            [createRelativeURL: { String c, String a, String n, String p, Map pv, String enc, String frag ->
                "/$controller/$action".toString()
            }] as UrlCreator
        }
        generator.urlMappingsHolder = [getReverseMapping: callable, getReverseMappingNoDefault: callable] as UrlMappingsHolder

        when: 'a blank namespace is supplied explicitly (book exists only in admin, so inference would yield admin)'
        generator.link(controller: 'book', action: 'index', namespace: explicitNamespace)

        then: 'the blank namespace is normalised to null so non-namespaced reverse mappings match'
        captured == null

        where:
        explicitNamespace << ['', '   ', null]
    }

    def "inference is skipped gracefully when no GrailsApplication is available"() {
        given:
        bindRequest(null, null)
        def generator = createGenerator(false)

        expect: 'with no application wired, links are never namespaced by inference'
        generator.getDefaultNamespace('book', null) == null
        generator.link(controller: 'book', action: 'index') == '/bar/book/index'
    }

    def "a plugin-targeted link does not infer a namespace from the application controllers"() {
        given:
        bindRequest('page', 'admin')
        def generator = createGenerator()

        expect: 'a plugin target is resolved within the plugin, so no app namespace is inferred'
        generator.getDefaultNamespace('book', 'someplugin') == null
        generator.link(controller: 'book', action: 'index', plugin: 'someplugin') == '/bar/book/index'
    }

    def "a controller name no scope resolves is reported once until the cache is reset"() {
        given: 'report is defined only in admin and sales, and the request is in the default namespace'
        bindRequest('page', null)
        def generator = createGenerator()
        def logCapture = new LogCapture(DefaultLinkGenerator)

        when: 'its namespace is inferred twice'
        def first = generator.getDefaultNamespace('report', null)
        def second = generator.getDefaultNamespace('report', null)

        then: 'no namespace is inferred'
        first == null
        second == null

        and: 'the ambiguity is reported once, as a warning naming the namespaces'
        def reports = warningsAbout(logCapture, 'controller [report]')
        reports.size() == 1
        reports[0].contains('[admin, sales]')

        when: 'the cache is reset, as a reload does, and the name is inferred again'
        generator.resetControllerNamespaceCache()
        generator.getDefaultNamespace('report', null)

        then: 'it is reported again'
        warningsAbout(logCapture, 'controller [report]').size() == 2

        cleanup:
        logCapture.close()
    }

    def "a controller name no scope resolves is reported again once the controllers change"() {
        given: 'report is defined only in admin and sales, and the request is in the default namespace'
        bindRequest('page', null)
        def generator = createGenerator()
        def logCapture = new LogCapture(DefaultLinkGenerator)

        when: 'the ambiguity is reported, and a controller is then reloaded'
        generator.getDefaultNamespace('report', null)
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, org.grails.web.mapping.nsinference.admin.ReportController)
        generator.getDefaultNamespace('report', null)

        then: 'it is reported against the reloaded controllers too, without resetting the cache'
        warningsAbout(logCapture, 'controller [report]').size() == 2

        cleanup:
        logCapture.close()
    }

    def "caching link generator does not collide across request namespaces for the same attrs"() {
        given: 'report exists in both admin and sales namespaces'
        def generator = createCachingGenerator()

        when: 'the same url-map link is generated from an admin request'
        bindRequest('page', 'admin')
        generator.resetControllerNamespaceCache()
        def adminUrl = generator.link(url: [controller: 'report', action: 'index'])

        and: 'and then from a sales request'
        bindRequest('page', 'sales')
        def salesUrl = generator.link(url: [controller: 'report', action: 'index'])

        then: 'each request namespace gets its own correctly namespaced URL (no cache collision)'
        adminUrl == '/bar/admin/report/index'
        salesUrl == '/bar/sales/report/index'
    }

    def "caching link generator does not collide across request namespaces for resource links"() {
        given: 'report exists in both admin and sales namespaces'
        def generator = createCachingGenerator()

        when: 'the same resource link is generated from an admin request'
        bindRequest('page', 'admin')
        generator.resetControllerNamespaceCache()
        def adminUrl = generator.link(resource: 'report', action: 'index')

        and: 'and then from a sales request'
        bindRequest('page', 'sales')
        def salesUrl = generator.link(resource: 'report', action: 'index')

        then: 'the resource link is namespaced per request (no cache collision)'
        adminUrl == '/bar/admin/report/index'
        salesUrl == '/bar/sales/report/index'
    }

    private static List<String> warningsAbout(LogCapture logCapture, String subject) {
        logCapture.events.findAll { it.level == Level.WARN && it.formattedMessage.contains(subject) }*.formattedMessage
    }

    private void bindRequest(String controllerName, String namespace) {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        if (controllerName != null) {
            webRequest.setControllerName(controllerName)
        }
        if (namespace != null) {
            webRequest.setControllerNamespace(namespace)
        }
    }

    private DefaultLinkGenerator createGenerator(boolean withApplication = true) {
        def generator = new DefaultLinkGenerator(BASE_URL, CONTEXT)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        if (withApplication) {
            generator.grailsApplication = grailsApplication
        }
        final callable = { String controller, String action, String namespace, String pluginName, String httpMethod, Map params ->
            [createRelativeURL: { String c, String a, String n, String p, Map parameterValues, String encoding, String fragment ->
                "${namespace ? '/' + namespace : ''}/$controller/$action${parameterValues.id ? '/' + parameterValues.id : ''}".toString()
            }] as UrlCreator
        }
        generator.urlMappingsHolder = [getReverseMapping: callable, getReverseMappingNoDefault: callable] as UrlMappingsHolder
        generator
    }

    private CachingLinkGenerator createCachingGenerator() {
        def generator = new CachingLinkGenerator(BASE_URL, CONTEXT)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.grailsApplication = grailsApplication
        final callable = { String controller, String action, String namespace, String pluginName, String httpMethod, Map params ->
            [createRelativeURL: { String c, String a, String n, String p, Map parameterValues, String encoding, String fragment ->
                "${namespace ? '/' + namespace : ''}/$controller/$action${parameterValues.id ? '/' + parameterValues.id : ''}".toString()
            }] as UrlCreator
        }
        generator.urlMappingsHolder = [getReverseMapping: callable, getReverseMappingNoDefault: callable] as UrlMappingsHolder
        generator
    }
}
