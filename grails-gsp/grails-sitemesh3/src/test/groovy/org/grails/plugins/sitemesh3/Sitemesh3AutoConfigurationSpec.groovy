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
package org.grails.plugins.sitemesh3

import org.sitemesh.SiteMeshContext
import org.sitemesh.content.Content

import org.springframework.beans.factory.ObjectProvider

import grails.config.Config
import grails.core.GrailsApplication

import org.grails.gsp.io.GroovyPageScriptSource
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator

import spock.lang.Specification

class Sitemesh3AutoConfigurationSpec extends Specification {

    Sitemesh3AutoConfiguration autoConfiguration = new Sitemesh3AutoConfiguration()

    void "the contentProcessor bean is a CaptureAwareContentProcessor"() {
        expect:
        autoConfiguration.contentProcessor() instanceof CaptureAwareContentProcessor
    }

    void "the post processor bean is the Grails view-resolver subclass"() {
        expect:
        Sitemesh3AutoConfiguration.siteMeshViewResolverBeanPostProcessor() instanceof GrailsSiteMeshViewResolverBeanPostProcessor
    }

    void "the decoratorSelector bean is created even when no groovyPageLocator is available"() {
        given: "an empty locator provider, as in a context without GSP support"
        ObjectProvider<GrailsConventionGroovyPageLocator> provider = Mock(ObjectProvider) {
            getIfAvailable() >> null
        }
        Config config = Mock(Config) {
            getProperty('grails.gsp.enable.reload', _, _) >> false
            getProperty('grails.sitemesh.layout.cache.interval', _, _) >> 5000L
        }
        GrailsApplication grailsApplication = Stub(GrailsApplication) {
            getConfig() >> config
        }

        expect:
        autoConfiguration.decoratorSelector(provider, grailsApplication) instanceof Sitemesh3LayoutFinder
    }

    void "the decoratorSelector bean resolves the configured default layout"() {
        given: "a locator that resolves the configured default layout path"
        GrailsConventionGroovyPageLocator locator = Mock(GrailsConventionGroovyPageLocator) {
            findViewByPath('/layouts/mylayout') >> Mock(GroovyPageScriptSource)
        }
        ObjectProvider<GrailsConventionGroovyPageLocator> provider = Mock(ObjectProvider) {
            getIfAvailable() >> locator
        }
        Config config = Mock(Config) {
            getProperty('grails.sitemesh.default.layout') >> 'mylayout'
            getProperty('grails.gsp.enable.reload', _, _) >> false
            getProperty('grails.sitemesh.layout.cache.interval', _, _) >> 5000L
        }
        GrailsApplication grailsApplication = Stub(GrailsApplication) {
            getConfig() >> config
        }

        when: "selecting a decorator outside a web context, which falls back to the default"
        Sitemesh3LayoutFinder finder = autoConfiguration.decoratorSelector(provider, grailsApplication)
        String[] paths = finder.selectDecoratorPaths(Mock(Content), Mock(SiteMeshContext))

        then:
        paths == ['/layouts/mylayout'] as String[]
    }

    void "the decoratorSelector bean falls back to grails.views.layout.default"() {
        given: "no SiteMesh 3 default, but a SiteMesh 2 style default layout"
        GrailsConventionGroovyPageLocator locator = Mock(GrailsConventionGroovyPageLocator) {
            findViewByPath('/layouts/legacy') >> Mock(GroovyPageScriptSource)
        }
        ObjectProvider<GrailsConventionGroovyPageLocator> provider = Mock(ObjectProvider) {
            getIfAvailable() >> locator
        }
        Config config = Mock(Config) {
            getProperty('grails.sitemesh.default.layout') >> null
            getProperty('grails.views.layout.default') >> 'legacy'
            getProperty('grails.gsp.enable.reload', _, _) >> false
            getProperty('grails.sitemesh.layout.cache.interval', _, _) >> 5000L
        }
        GrailsApplication grailsApplication = Stub(GrailsApplication) {
            getConfig() >> config
        }

        when:
        Sitemesh3LayoutFinder finder = autoConfiguration.decoratorSelector(provider, grailsApplication)
        String[] paths = finder.selectDecoratorPaths(Mock(Content), Mock(SiteMeshContext))

        then:
        paths == ['/layouts/legacy'] as String[]
    }
}
