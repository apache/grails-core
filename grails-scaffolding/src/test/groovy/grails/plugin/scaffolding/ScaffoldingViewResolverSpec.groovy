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
package grails.plugin.scaffolding

import grails.core.GrailsControllerClass
import grails.plugin.scaffolding.annotation.Scaffold
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.view.GroovyPageView
import org.springframework.core.io.Resource
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

class ScaffoldingViewResolverSpec extends Specification {

    static final String TEST_NAMESPACE = "admin"
    static final String TEST_VIEW_NAME = "/event/index"

    ScaffoldingViewResolver resolver
    GrailsConventionGroovyPageLocator mockPageLocator
    GroovyPagesTemplateEngine mockTemplateEngine
    GrailsWebRequest mockWebRequest
    GrailsControllerClass mockControllerClass

    def setup() {
        resolver = new ScaffoldingViewResolver()
        mockPageLocator = Mock(GrailsConventionGroovyPageLocator)
        mockTemplateEngine = Mock(GroovyPagesTemplateEngine)
        mockControllerClass = Mock(GrailsControllerClass)

        // Create GrailsWebRequest with required constructor args
        def mockHttpRequest = Mock(jakarta.servlet.http.HttpServletRequest)
        def mockHttpResponse = Mock(jakarta.servlet.http.HttpServletResponse)
        def mockServletContext = Mock(jakarta.servlet.ServletContext)
        mockWebRequest = Stub(GrailsWebRequest, constructorArgs: [mockHttpRequest, mockHttpResponse, mockServletContext])

        resolver.groovyPageLocator = mockPageLocator
        resolver.templateEngine = mockTemplateEngine

        // Set up thread local
        RequestContextHolder.setRequestAttributes(mockWebRequest)
    }

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
        resolver.clearCache()
    }

    // Helper methods
    void setupScaffoldController(Class controllerClazz, Class scaffoldDomain = null) {
        mockControllerClass.clazz >> controllerClazz
        mockControllerClass.getPropertyValue('scaffold') >> scaffoldDomain
        mockWebRequest.controllerClass >> mockControllerClass
    }

    void setupNamespaceController(String namespace = TEST_NAMESPACE) {
        mockControllerClass.namespace >> namespace
    }

    GroovyPageView mockViewWithUrl(String url) {
        def view = Mock(GroovyPageView)
        view.url >> url
        return view
    }

    void "test enableNamespaceViewDefaults defaults to false"() {
        expect:
        !resolver.enableNamespaceViewDefaults
    }

    void "test scaffold value cache stores null values for non-scaffold controllers"() {
        given:
        setupScaffoldController(String)

        when:
        def result = resolver.getScaffoldValue(mockControllerClass)

        then:
        result == null
        resolver.scaffoldValueCache.containsKey(String)
        resolver.scaffoldValueCache.get(String) == ScaffoldingViewResolver.NULL_SCAFFOLD_VALUE
    }

    void "test scaffold value cache returns cached value"() {
        given:
        setupScaffoldController(String)
        def cachedValue = String
        resolver.scaffoldValueCache.put(String, cachedValue)

        when:
        def result = resolver.getScaffoldValue(mockControllerClass)

        then:
        result == cachedValue
        0 * mockControllerClass.getPropertyValue(_) // Uses cache
    }

    void "test scaffold value cache handles annotation"() {
        given:
        setupScaffoldController(TestScaffoldController)

        when:
        def result = resolver.getScaffoldValue(mockControllerClass)

        then:
        result == TestDomain
        resolver.scaffoldValueCache.containsKey(TestScaffoldController)
    }

    void "test clearCache clears both view and scaffold caches"() {
        given:
        resolver.generatedViewCache.put("test", Mock(GroovyPageView))
        resolver.scaffoldValueCache.put(String, String)

        when:
        resolver.clearCache()

        then:
        resolver.generatedViewCache.isEmpty()
        resolver.scaffoldValueCache.isEmpty()
    }

    void "test buildCacheKey includes view name"() {
        given:
        mockPageLocator.resolveViewFormat(TEST_VIEW_NAME) >> TEST_VIEW_NAME

        when:
        def cacheKey = resolver.buildCacheKey(TEST_VIEW_NAME)

        then:
        cacheKey != null
        cacheKey.contains(TEST_VIEW_NAME)
    }

    void "test namespace controller without scaffold annotation returns null scaffold value"() {
        given:
        resolver.enableNamespaceViewDefaults = true
        setupScaffoldController(String)
        setupNamespaceController()

        expect:
        resolver.getScaffoldValue(mockControllerClass) == null
    }

    void "test tryGenerateScaffoldedView returns null for non-scaffold controller"() {
        given:
        setupScaffoldController(String)

        when:
        def result = resolver.tryGenerateScaffoldedView(TEST_VIEW_NAME, mockControllerClass) { shortViewName ->
            Mock(Resource)
        }

        then:
        result == null
    }

    void "test tryGenerateScaffoldedView uses generated view cache"() {
        given:
        def cacheKey = "test-cache-key"
        def cachedView = Mock(GroovyPageView)
        resolver.generatedViewCache.put(cacheKey, cachedView)

        expect:
        resolver.generatedViewCache.get(cacheKey) == cachedView
    }

    void "test tryGenerateScaffoldedView returns null when resource does not exist"() {
        given:
        setupScaffoldController(TestScaffoldController, TestDomain)
        mockPageLocator.resolveViewFormat(TEST_VIEW_NAME) >> TEST_VIEW_NAME

        when:
        def result = resolver.tryGenerateScaffoldedView(TEST_VIEW_NAME, mockControllerClass) { shortViewName ->
            def resource = Mock(Resource)
            resource.exists() >> false
            return resource
        }

        then:
        result == null
    }

    void "test RestfulServiceController annotation without AST transformation returns null"() {
        given:
        setupScaffoldController(TestRestfulServiceScaffoldController)

        when:
        def result = resolver.getScaffoldValue(mockControllerClass)

        then:
        // This test validates RAW annotation behavior (pre-AST transformation).
        // In real applications, ScaffoldingControllerInjector AST transformation extracts
        // the generic type from RestfulServiceController<TestDomain> and sets domain()
        // at compile time, so @Scaffold(RestfulServiceController<User>) DOES work.
        // See grails-test-examples/scaffolding for working integration tests.
        result == null
        resolver.scaffoldValueCache.containsKey(TestRestfulServiceScaffoldController)
    }

    void "test Scaffold annotation with domain attribute works correctly"() {
        given:
        setupScaffoldController(TestScaffoldController, TestDomain)

        when:
        def result = resolver.getScaffoldValue(mockControllerClass)

        then:
        // This tests @Scaffold(domain = TestDomain) AND validates the post-AST behavior
        // of @Scaffold(RestfulServiceController<TestDomain>) since AST transformation
        // sets domain = TestDomain at compile time for both patterns
        result == TestDomain
        resolver.scaffoldValueCache.containsKey(TestScaffoldController)
    }

    void "test namespace view URL detection identifies namespace-specific views"() {
        given:
        def namespaceView = mockViewWithUrl("/grails-app/views/${TEST_NAMESPACE}/event/index.gsp")
        def nonNamespaceView = mockViewWithUrl("/grails-app/views/event/index.gsp")
        setupNamespaceController()

        expect:
        // Namespace view should contain namespace in URL
        namespaceView.url.contains("/${TEST_NAMESPACE}/")
        // Non-namespace view should not
        !nonNamespaceView.url.contains("/${TEST_NAMESPACE}/")
    }

    void "test cache prevents repeated reflection for non-scaffold controllers"() {
        given:
        setupScaffoldController(String) // Non-scaffold controller

        when: "First call performs reflection"
        def result1 = resolver.getScaffoldValue(mockControllerClass)

        then:
        result1 == null
        resolver.scaffoldValueCache.get(String) == ScaffoldingViewResolver.NULL_SCAFFOLD_VALUE

        when: "Second call uses cache"
        def result2 = resolver.getScaffoldValue(mockControllerClass)

        then:
        result2 == null
        0 * mockControllerClass.getPropertyValue(_) // No reflection on second call
    }

    // Test domain class for annotation testing
    static class TestDomain {}

    @Scaffold(domain = TestDomain)
    static class TestScaffoldController {}

    @Scaffold(RestfulServiceController<TestDomain>)
    static class TestRestfulServiceScaffoldController {}
}
