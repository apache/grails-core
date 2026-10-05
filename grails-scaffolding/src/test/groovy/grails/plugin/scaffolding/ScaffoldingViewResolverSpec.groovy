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

import java.nio.charset.StandardCharsets

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent

import grails.core.GrailsControllerClass
import grails.util.BuildSettings
import grails.plugin.scaffolding.annotation.Scaffold
import org.apache.grails.core.testing.support.LogCapture
import org.apache.grails.scaffolding.ScaffoldedPages
import org.grails.gsp.GroovyPageTemplate
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.io.GroovyPageScriptSource
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.view.GroovyPageView
import org.springframework.aot.AotDetector
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.StaticWebApplicationContext
import spock.lang.Specification

class ScaffoldingViewResolverSpec extends Specification {

    static final String TEST_NAMESPACE = "admin"
    static final String TEST_VIEW_NAME = "/event/index"
    /** A view whose template exists nowhere on disk, so the tests decide what the resolver finds. */
    static final String LIST_VIEW_NAME = "/event/list"
    static final String LIST_TEMPLATE = 'list ${className}'

    ScaffoldingViewResolver resolver
    GrailsConventionGroovyPageLocator mockPageLocator
    GroovyPagesTemplateEngine mockTemplateEngine
    GrailsWebRequest mockWebRequest
    GrailsControllerClass mockControllerClass
    StaticWebApplicationContext context

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
        // a view is created against the servlet context the resolver runs in
        context = new StaticWebApplicationContext()
        context.servletContext = Stub(jakarta.servlet.ServletContext) {
            getInitParameterNames() >> Collections.emptyEnumeration()
            getAttributeNames() >> Collections.emptyEnumeration()
        }
        context.refresh()
        resolver.applicationContext = context

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

    static Resource template(String text) {
        new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8))
    }

    /** Serves each classpath template location from {@code templates}. */
    ResourceLoader templates(Map<String, String> templates) {
        Stub(ResourceLoader) {
            getResource(_ as String) >> { String location ->
                String text = templates[location - 'classpath:META-INF/templates/scaffolding/' - '.gsp']
                text != null ? template(text) : Stub(Resource) { exists() >> false }
            }
        }
    }

    String className() {
        resolver.model(TestDomain).className
    }

    String pageFor(String templatePath, String text, Class domain = TestDomain) {
        ScaffoldedPages.uri(templatePath, resolver.model(domain).asMap(), text.getBytes(StandardCharsets.UTF_8))
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
        def result = resolver.tryGenerateScaffoldedView(TEST_VIEW_NAME, mockControllerClass) { String shortViewName ->
            [shortViewName]
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
        mockPageLocator.resolveViewFormat(LIST_VIEW_NAME) >> LIST_VIEW_NAME
        resolver.resourceLoader = templates([:])

        when:
        def result = resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass) { String shortViewName ->
            [shortViewName]
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

    void "a scaffolded view is served by the page compiled from its template and model"() {
        given:
        setupScaffoldController(TestScaffoldController, TestDomain)
        mockPageLocator.resolveViewFormat(LIST_VIEW_NAME) >> LIST_VIEW_NAME
        resolver.resourceLoader = templates(list: LIST_TEMPLATE)
        String expected = pageFor('list', LIST_TEMPLATE)
        def page = Stub(GroovyPageScriptSource)

        when:
        def view = resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass) { String name -> [name] }

        then: 'the page is found under its template and domain class'
        expected.startsWith("/grails-scaffolded/${TestDomain.name}/list-")
        1 * mockPageLocator.findPage(expected) >> page

        and: 'rendered from the compiled page, with nothing expanded'
        1 * mockTemplateEngine.createTemplate(page)
        0 * mockTemplateEngine.createTemplate(_ as Resource, _)
        view instanceof GroovyPageView
        (view as GroovyPageView).url == expected
    }

    void "a template with no compiled page is expanded as before"() {
        given:
        setupScaffoldController(TestScaffoldController, TestDomain)
        mockPageLocator.resolveViewFormat(LIST_VIEW_NAME) >> LIST_VIEW_NAME
        mockTemplateEngine.gspEncoding >> 'UTF-8'
        resolver.resourceLoader = templates(list: LIST_TEMPLATE)

        when:
        def view = resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass) { String name -> [name] }

        then:
        1 * mockPageLocator.findPage(pageFor('list', LIST_TEMPLATE)) >> null
        1 * mockTemplateEngine.createTemplate({ Resource expanded -> expanded.inputStream.text == "list ${className()}".toString() }, true) >> Stub(GroovyPageTemplate)
        view instanceof GroovyPageView
    }

    void "a namespaced controller's own template is looked for under its own path"() {
        given:
        setupScaffoldController(TestScaffoldController, TestDomain)
        setupNamespaceController()
        mockPageLocator.resolveViewFormat(LIST_VIEW_NAME) >> LIST_VIEW_NAME
        resolver.resourceLoader = templates(list: LIST_TEMPLATE, 'admin/list': 'admin list ${className}')
        String expected = pageFor('admin/list', 'admin list ${className}')

        when:
        resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass)

        then:
        expected.startsWith("/grails-scaffolded/${TestDomain.name}/admin/list-")
        1 * mockPageLocator.findPage(expected) >> Stub(GroovyPageScriptSource)
        0 * mockPageLocator.findPage(pageFor('list', LIST_TEMPLATE))
    }

    void "a namespaced controller with no template of its own uses the general one"() {
        given:
        setupScaffoldController(TestScaffoldController, TestDomain)
        setupNamespaceController()
        mockPageLocator.resolveViewFormat(LIST_VIEW_NAME) >> LIST_VIEW_NAME
        resolver.resourceLoader = templates(list: LIST_TEMPLATE)

        when:
        resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass)

        then:
        1 * mockPageLocator.findPage(pageFor('list', LIST_TEMPLATE)) >> Stub(GroovyPageScriptSource)
    }

    void "a customised template is looked for under its own page, never the stock template's"() {
        given:
        setupScaffoldController(TestScaffoldController, TestDomain)
        mockPageLocator.resolveViewFormat(LIST_VIEW_NAME) >> LIST_VIEW_NAME
        mockTemplateEngine.gspEncoding >> 'UTF-8'
        String customised = 'customised list ${className}'
        resolver.resourceLoader = templates(list: customised)

        when:
        resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass) { String name -> [name] }

        then:
        pageFor('list', customised) != pageFor('list', LIST_TEMPLATE)
        1 * mockPageLocator.findPage(pageFor('list', customised)) >> null
        0 * mockPageLocator.findPage(pageFor('list', LIST_TEMPLATE))
        1 * mockTemplateEngine.createTemplate(_ as Resource, true) >> Stub(GroovyPageTemplate)
    }

    void "with reloading enabled the template is always expanded, so an edit to it shows"() {
        given:
        resolver.enableReload = true
        setupScaffoldController(TestScaffoldController, TestDomain)
        mockPageLocator.resolveViewFormat(LIST_VIEW_NAME) >> LIST_VIEW_NAME
        mockTemplateEngine.gspEncoding >> 'UTF-8'
        resolver.resourceLoader = templates(list: LIST_TEMPLATE)

        when:
        resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass) { String name -> [name] }

        then:
        0 * mockPageLocator.findPage(_)
        1 * mockTemplateEngine.createTemplate(_ as Resource, false) >> Stub(GroovyPageTemplate)
    }

    void "a compiled page is looked for once and the view kept"() {
        given:
        setupScaffoldController(TestScaffoldController, TestDomain)
        mockPageLocator.resolveViewFormat(LIST_VIEW_NAME) >> LIST_VIEW_NAME
        resolver.resourceLoader = templates(list: LIST_TEMPLATE)

        when:
        def first = resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass) { String name -> [name] }
        def second = resolver.tryGenerateScaffoldedView(LIST_VIEW_NAME, mockControllerClass) { String name -> [name] }

        then:
        1 * mockPageLocator.findPage(pageFor('list', LIST_TEMPLATE)) >> Stub(GroovyPageScriptSource)
        second.is(first)
    }

    void "during development a template is read from the project, unless the application runs from ahead-of-time artifacts"() {
        given: 'this module keeps the stock templates in src/main/templates, as a project keeps its own'
        File project = new File(BuildSettings.BASE_DIR, 'src/main/templates/scaffolding/show.gsp')
        resolver = new ScaffoldingViewResolver() {
            @Override
            protected boolean isDevelopmentMode() {
                true
            }
        }
        resolver.groovyPageLocator = mockPageLocator
        resolver.templateEngine = mockTemplateEngine
        resolver.applicationContext = context
        setupScaffoldController(TestScaffoldController, TestDomain)
        mockPageLocator.resolveViewFormat(_ as String) >> { String name -> name }
        resolver.resourceLoader = templates(show: 'packaged show ${className}')
        String previous = System.setProperty(AotDetector.AOT_ENABLED, String.valueOf(aot))

        when:
        resolver.tryGenerateScaffoldedView('/event/show', mockControllerClass)

        then:
        project.isFile()
        1 * mockPageLocator.findPage(pageFor('show', aot ? 'packaged show ${className}' : project.getText('UTF-8'))) >> Stub(GroovyPageScriptSource)

        cleanup:
        previous == null ? System.clearProperty(AotDetector.AOT_ENABLED) : System.setProperty(AotDetector.AOT_ENABLED, previous)

        where:
        aot << [false, true]
    }

    /** Expands the list template for two views of the same domain class, with no compiled page for it. */
    List<ILoggingEvent> reportsWhenExpanding(boolean precompiled) {
        setupScaffoldController(TestScaffoldController, TestDomain)
        mockPageLocator.precompiledAvailable >> precompiled
        mockPageLocator.resolveViewFormat(_ as String) >> { String name -> name }
        mockPageLocator.findPage(_) >> null
        mockTemplateEngine.gspEncoding >> 'UTF-8'
        mockTemplateEngine.createTemplate(_ as Resource, _) >> Stub(GroovyPageTemplate)
        resolver.resourceLoader = templates(list: LIST_TEMPLATE)
        new LogCapture(ScaffoldingViewResolver, Level.INFO).withCloseable { LogCapture log ->
            resolver.tryGenerateScaffoldedView('/event/list', mockControllerClass) { String name -> [name] }
            resolver.tryGenerateScaffoldedView('/other/list', mockControllerClass) { String name -> [name] }
            new ArrayList<ILoggingEvent>(log.events)
        }
    }

    void "a template expanded on the JVM where compiled pages are used is noted, once"() {
        when:
        def reports = reportsWhenExpanding(true)

        then: 'it is expanded as it always was, so it is not a warning'
        reports*.level == [Level.INFO]
        reports[0].formattedMessage.contains("list for ${TestDomain.name} was not compiled by the build")
    }

    void "a template expanded in a native image is warned about, once, with what to do"() {
        given: 'a native image, where the expansion that follows fails'
        resolver = new ScaffoldingViewResolver() {
            @Override
            protected boolean inNativeImage() {
                true
            }
        }
        resolver.groovyPageLocator = mockPageLocator
        resolver.templateEngine = mockTemplateEngine
        resolver.applicationContext = context

        when:
        def reports = reportsWhenExpanding(true)

        then:
        reports*.level == [Level.WARN]
        reports[0].formattedMessage.contains('a native image cannot expand it at runtime')
    }

    void "where no compiled pages are used, as in development or an application's tests, an expanded template is not reported"() {
        expect:
        reportsWhenExpanding(false).isEmpty()
    }

    // Test domain class for annotation testing
    static class TestDomain {}

    @Scaffold(domain = TestDomain)
    static class TestScaffoldController {}

    @Scaffold(RestfulServiceController<TestDomain>)
    static class TestRestfulServiceScaffoldController {}
}
