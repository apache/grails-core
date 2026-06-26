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

import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource

import grails.core.DefaultGrailsApplication
import grails.plugins.Plugin
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.taglib.RenderSitemeshTagLib
import org.grails.web.util.WebUtils

class Sitemesh3GrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0-SNAPSHOT > *'

    def title = 'SiteMesh 3'
    def author = 'Scott Murphy'
    def authorEmail = ''
    def description = 'Configures Grails to use SiteMesh 3 instead of SiteMesh 2'
    def profiles = ['web']

    def license = 'APACHE'

    def developers = [[name: 'Scott Murphy']]

    def loadBefore = ['groovyPages']

    def providedArtefacts = [
            RenderSitemeshTagLib,
            Sitemesh3LayoutTagLib,
    ]

    static PropertySource getDefaultPropertySource(ConfigurableEnvironment configurableEnvironment, String defaultLayout) {
        Map props = [
                'sitemesh.decorator.metaTag': 'layout',
                'sitemesh.decorator.attribute': WebUtils.LAYOUT_ATTRIBUTE,
                'sitemesh.decorator.prefix': '/layouts/',
        ]
        if (defaultLayout) {
            props['sitemesh.decorator.default'] = defaultLayout
        }
        props.clone().each {
            if (configurableEnvironment.getProperty(it.key)) {
                props.remove(it.key)
            }
        }
        new MapPropertySource('defaultSitemesh3Properties', props)
    }

    Closure doWithSpring() {
        { ->
            ConfigurableEnvironment configurableEnvironment = grailsApplication.mainContext.environment as ConfigurableEnvironment
            def propertySources = configurableEnvironment.getPropertySources()
            // The SiteMesh 3 specific key wins; fall back to the SiteMesh 2
            // plugin's grails.views.layout.default so existing apps keep
            // their configured default layout when switching.
            String defaultLayout = grailsApplication.getConfig().getProperty('grails.sitemesh.default.layout') ?:
                    grailsApplication.getConfig().getProperty('grails.views.layout.default')
            propertySources.addFirst(getDefaultPropertySource(configurableEnvironment, defaultLayout))
            (grailsApplication as DefaultGrailsApplication).config = new PropertySourcesConfig(propertySources)

            // Unwraps the SiteMesh view for "render template:" partials so
            // they are never decorated with a layout (the SiteMesh 2 plugin
            // does the same with its GrailsLayoutRenderViewMutator).
            grailsRenderViewMutator(Sitemesh3RenderViewMutator)
        }
    }
}
