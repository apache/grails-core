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
package org.grails.web.gsp.io

import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import grails.plugins.metadata.PluginDefinition
import org.apache.grails.core.plugins.PluginInfo
import org.apache.grails.core.plugins.filters.NoOpPluginFilter
import org.grails.core.io.SimpleMapResourceLoader
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.gsp.io.DefaultGroovyPageLocator
import org.grails.gsp.io.GroovyPageCompiledScriptSource
import org.grails.gsp.io.GroovyPageResourceScriptSource
import org.grails.gsp.io.GroovyPageStaticResourceLocator
import org.grails.plugins.CoreGrailsPlugin
import org.grails.plugins.DefaultGrailsPlugin
import org.grails.plugins.MockGrailsPluginManager
import org.grails.plugins.MockPluginDiscovery
import org.grails.web.pages.GroovyPagesServlet

/**
 * Where two plugins provide the same view or static resource, the later plugin in plugin order wins,
 * and a view of the application wins over both.
 */
class PluginViewPrecedenceSpec extends Specification {

    SimpleMapResourceLoader resourceLoader = new SimpleMapResourceLoader()

    Map<String, MockBinaryPluginResource> descriptors = [:]

    DefaultGrailsPlugin earlier = binaryPlugin('earlierViews')

    DefaultGrailsPlugin later = binaryPlugin('laterViews')

    MockGrailsPluginManager pluginManager = pluginManager(earlier, later)

    void 'the later plugin\'s precompiled view wins over an earlier plugin\'s'() {
        given:
        Class<?> earlierPage = precompile(earlier, '/shared/page.gsp')
        Class<?> laterPage = precompile(later, '/shared/page.gsp')
        precompile(earlier, '/shared/onlyEarlier.gsp')

        expect:
        compiledClass(locator().findPage('/shared/page.gsp')) == laterPage
        compiledClass(locator().findPage('/shared/onlyEarlier.gsp')) != null
        compiledClass(locator().findPage('/shared/onlyEarlier.gsp')) != laterPage
        earlierPage != laterPage
    }

    void 'the later plugin\'s source view wins over an earlier plugin\'s, and the application\'s view over both'() {
        given:
        def earlierView = view(earlier, '/shared/source.gsp')
        def laterView = view(later, '/shared/source.gsp')
        def earlierOnly = view(earlier, '/shared/onlyEarlierSource.gsp')

        expect:
        resourceOf(locator().findPage('/shared/source.gsp')).is(laterView)
        resourceOf(locator().findPage('/shared/onlyEarlierSource.gsp')).is(earlierOnly)
        !earlierView.is(laterView)

        when: 'the application has the view too'
        def applicationView = new ByteArrayResource('application'.bytes)
        resourceLoader.resources['/grails-app/views/shared/source.gsp'] = applicationView

        then:
        resourceOf(locator().findPage('/shared/source.gsp')).is(applicationView)
    }

    void 'the later plugin\'s static resource wins over an earlier plugin\'s'() {
        given:
        def earlierCss = new ByteArrayResource('earlier'.bytes)
        def laterCss = new ByteArrayResource('later'.bytes)
        descriptors.earlierViews.relativesResources['static/css/shared.css'] = earlierCss
        descriptors.laterViews.relativesResources['static/css/shared.css'] = laterCss
        def locator = new GroovyPageStaticResourceLocator()
        locator.pluginManager = pluginManager
        locator.resourceLoader = resourceLoader

        expect:
        locator.findResourceForURI('/css/shared.css').is(laterCss)
    }

    void 'the GroovyPagesServlet serves the later plugin\'s precompiled view'() {
        given:
        precompile(earlier, '/shared/servlet.gsp')
        Class<?> laterPage = precompile(later, '/shared/servlet.gsp')
        def servlet = new GroovyPagesServlet()
        servlet.pluginManager = pluginManager

        expect:
        compiledClass(servlet.findPageInBinaryPlugins('/WEB-INF/grails-app/views/shared/servlet.gsp')) == laterPage
    }

    private DefaultGroovyPageLocator locator() {
        new DefaultGroovyPageLocator().tap {
            it.pluginManager = this.pluginManager
            addResourceLoader(resourceLoader)
        }
    }

    private Resource view(DefaultGrailsPlugin plugin, String uri) {
        def resource = new ByteArrayResource(plugin.name.bytes)
        resourceLoader.resources["${plugin.pluginPath}/grails-app/views${uri}" as String] = resource
        resource
    }

    private static Class<?> compiledClass(Object source) {
        source instanceof GroovyPageCompiledScriptSource ? ((GroovyPageCompiledScriptSource) source).compiledClass : null
    }

    private static Resource resourceOf(Object source) {
        source instanceof GroovyPageResourceScriptSource ? ((GroovyPageResourceScriptSource) source).resource : null
    }

    private static int pages

    private static Class<?> precompile(DefaultGrailsPlugin plugin, String uri) {
        String className = "precedence_page_${pages++}"
        def parser = new GroovyPageParser(className, uri, uri, new ByteArrayInputStream(plugin.name.bytes), 'UTF-8', 'HTML', null)
        parser.packageName = 'precedence'
        parser.className = className
        def source = new StringWriter()
        parser.generateGsp(source)
        Class<?> page = new GroovyClassLoader().parseClass(source.toString())
        plugin.@precompiledViewMap["/WEB-INF/grails-app/views${uri}" as String] = page
        page
    }

    private DefaultGrailsPlugin binaryPlugin(String name) {
        def text = "format.version=1\nname=${name}\nversion=1.0\nclass=org.grails.web.gsp.io.TestBinaryGrailsPlugin\n"
        def properties = new Properties()
        properties.load(new StringReader(text))
        def descriptor = new MockBinaryPluginResource(text.bytes)
        descriptors[name] = descriptor
        def info = new PluginInfo(PluginDefinition.fromProperties(name, properties), TestBinaryGrailsPlugin, descriptor, null)
        new DefaultGrailsPlugin(info, new DefaultGrailsApplication())
    }

    private static MockGrailsPluginManager pluginManager(DefaultGrailsPlugin... plugins) {
        def discovery = new MockPluginDiscovery([CoreGrailsPlugin] as Class[])
        discovery.loadPluginsFromClasspath = false
        discovery.pluginFilter = new NoOpPluginFilter()
        discovery.init(new StandardEnvironment())
        def manager = new MockGrailsPluginManager(new DefaultGrailsApplication(), discovery)
        manager.loadPlugins()
        plugins.each { manager.registerMockPlugin(it) }
        manager
    }
}
