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
package com.example

import jakarta.servlet.ServletContext

import org.springframework.core.io.DefaultResourceLoader
import org.springframework.web.context.support.StaticWebApplicationContext
import spock.lang.Specification

import grails.core.GrailsControllerClass
import grails.plugin.scaffolding.ScaffoldingViewResolver
import org.grails.gsp.GroovyPageTemplate
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.io.GroovyPageCompiledScriptSource
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.servlet.view.GroovyPageView

/**
 * The resolver finds, for every scaffolded view of this application, the page the build compiled
 * for it - the real resolver, choosing among the real templates, the application's own included,
 * against the pages this build compiled, as the packaged application does. The build and the
 * resolver decide on the pages independently of each other, so this is where the two are held
 * together.
 */
class PrecompiledScaffoldPagesSpec extends Specification {

    ScaffoldingViewResolver resolver
    GrailsConventionGroovyPageLocator locator
    ClassLoader original

    void setup() {
        File compiled = new File(System.getProperty('scaffolding.compiledPages'))
        Properties views = new Properties()
        new File(compiled, 'gsp/views.properties').withInputStream { views.load(it) }
        original = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = new URLClassLoader([compiled.toURI().toURL()] as URL[], original)

        locator = new GrailsConventionGroovyPageLocator() {
            @Override
            protected boolean isDevelopmentMode() { false }
        }
        locator.precompiledGspMap = views as Map<String, String>

        def context = new StaticWebApplicationContext()
        context.servletContext = Stub(ServletContext) {
            getInitParameterNames() >> Collections.emptyEnumeration()
            getAttributeNames() >> Collections.emptyEnumeration()
        }
        context.refresh()

        resolver = new ScaffoldingViewResolver()
        resolver.groovyPageLocator = locator
        resolver.templateEngine = Stub(GroovyPagesTemplateEngine) { createTemplate(_) >> Stub(GroovyPageTemplate) }
        resolver.resourceLoader = new DefaultResourceLoader()
        resolver.applicationContext = context
    }

    void cleanup() {
        Thread.currentThread().contextClassLoader = original
    }

    void '#controller.name serves its #view view from the page the build compiled from #template'() {
        given:
        GrailsControllerClass controllerClass = Stub(GrailsControllerClass) {
            getClazz() >> controller
            getNamespace() >> namespace
            // scaffolded by the annotation, not the legacy static property
            getPropertyValue('scaffold') >> null
        }

        when: 'the resolver chooses the template as it does for a request'
        GroovyPageView page = resolver.tryGenerateScaffoldedView("/${view}", controllerClass) as GroovyPageView

        then:
        page.url.startsWith("/grails-scaffolded/${domain.name}/${template}-")
        locator.findPage(page.url) instanceof GroovyPageCompiledScriptSource
        resolver.reportedPages.isEmpty()

        where: "summary is the application's own template, and the community namespace has one of its own"
        [controller, namespace, domain, view] << [
                [[BookController, null, Book], [UserController, null, User],
                 [com.example.community.UserController, 'community', com.example.community.User]],
                ['index', 'create', 'edit', 'show', 'summary']
        ].combinations().collect { List pair -> pair[0] + [pair[1]] }
        template = namespace && view == 'summary' ? "${namespace}/${view}" : view
    }
}
