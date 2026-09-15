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
package grails.gsp.boot

import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader

import org.grails.gsp.GroovyPage
import org.grails.gsp.io.GroovyPageCompiledScriptSource
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator

import spock.lang.Specification

/**
 * Covers which of the two ways a view can be found the auto-configuration hands the page locator:
 * the views compiled into the application, or the templates it was built from. Rendering from the
 * compiled views end to end is covered by the gsp-spring-boot example.
 */
class GspAutoConfigurationSpec extends Specification {

    private final GspAutoConfiguration.GspTemplateEngineAutoConfiguration configuration =
            new GspAutoConfiguration.GspTemplateEngineAutoConfiguration()

    private final ResourceLoader resourceLoader = new DefaultResourceLoader()

    void 'the compiled views are read for an application served from the class path'() {
        when: 'the templates can only come from the class path, as they do once packaged'
        Map<String, String> views = configuration.resolvePrecompiledViews(resourceLoader, ['classpath:/templates'])

        then: 'every view the registry names is available to the locator, layouts included'
        views == ['/probe.gsp': CompiledProbePage.name, '/layouts/probe.gsp': CompiledProbeLayout.name]
    }

    void 'the locator the auto-configuration contributes resolves a view from its compiled class'() {
        given: 'the templateRoots of a packaged application, which hold no templates'
        configuration.templateRoots = ['classpath:/templates'] as String[]

        when: 'a view is asked for under the path the build registry names it by'
        def source = locator().findPage('/probe.gsp')

        then: 'the registry reached the locator, and the bare path it registers under is searched'
        source instanceof GroovyPageCompiledScriptSource
        ((GroovyPageCompiledScriptSource) source).compiledClass == CompiledProbePage
    }

    void 'a layout is resolved from its compiled class as any other view is'() {
        given:
        configuration.templateRoots = ['classpath:/templates'] as String[]

        expect: 'a layout is a view in its own right, so the same path resolves it'
        locator().findPage('/layouts/probe.gsp') instanceof GroovyPageCompiledScriptSource
    }

    void 'an application rendering from its templates resolves no compiled view'() {
        given: 'a template root on the file system, which holds the templates themselves'
        configuration.templateRoots = ['file:./src/main/resources/templates'] as String[]

        expect: 'the registry is left unread, so an edit to a template takes effect'
        !(locator().findPage('/probe.gsp') instanceof GroovyPageCompiledScriptSource)
    }

    private GrailsConventionGroovyPageLocator locator() {
        configuration.groovyPageLocator(resourceLoader)
    }

    void 'the compiled views are left unread for an application served from #root'() {
        when: 'a template root on the file system, which holds the templates themselves'
        Map<String, String> views = configuration.resolvePrecompiledViews(resourceLoader, roots)

        then: 'the locator is left to render the templates, so an edit to one takes effect'
        views == null

        where:
        root                | roots
        'a directory'       | ['file:./src/main/resources/templates']
        'a directory first' | ['file:./src/main/resources/templates', 'classpath:/templates']
        'a directory last'  | ['classpath:/templates', 'file:./templates']
    }

    void 'an application with no compiled views renders from its templates'() {
        given: 'a class path without a view registry on it'
        ResourceLoader empty = new DefaultResourceLoader(new ClassLoader(null) {})

        expect:
        configuration.resolvePrecompiledViews(empty, ['classpath:/templates']) == null
    }

    /**
     * Stands in for a page the build compiled. The constants are the ones the compiler emits and a
     * page's metadata is read from, so the locator treats these as it does any compiled page.
     */
    static class CompiledProbePage extends GroovyPage {

        public static final String CONTENT_TYPE = 'text/html;charset=UTF-8'

        public static final Map JSP_TAGS = [:]

        public static final String EXPRESSION_CODEC = 'html'

        public static final String STATIC_CODEC = 'none'

        public static final String OUT_CODEC = 'none'

        public static final String TAGLIB_CODEC = 'none'

        @Override
        Object run() { null }

        @Override
        String getGroovyPageFileName() { 'probe.gsp' }

    }

    static class CompiledProbeLayout extends CompiledProbePage {
    }

}
