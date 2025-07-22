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

import grails.plugins.Plugin
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.taglib.RenderSitemeshTagLib
import org.grails.plugins.web.taglib.SitemeshTagLib
import org.grails.web.config.http.GrailsFilters
import org.grails.web.util.WebUtils
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource

class Sitemesh3GrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0-SNAPSHOT > *'

    def title = "SiteMesh 3"
    def author = "Scott Murphy"
    def authorEmail = ""
    def description = "Configures Grails to use SiteMesh 3 instead of SiteMesh 2"
    def profiles = ['web']

    def license = "APACHE"

    def developers = [[name: "Scott Murphy"]]

    def loadBefore = ['groovyPages']

    def providedArtefacts = [
        RenderSitemeshTagLib,
        SitemeshTagLib,
    ]

    static PropertySource getDefaultPropertySource(ConfigurableEnvironment configurableEnvironment, String defaultLayout) {

        Map props = [
                'grails.gsp.view.layoutViewResolver': 'false',
                'sitemesh.decorator.metaTag': 'layout',
                'sitemesh.decorator.attribute': WebUtils.LAYOUT_ATTRIBUTE,
                'sitemesh.decorator.prefix': '/layouts/',
                'sitemesh.filter.order': GrailsFilters.SITEMESH_FILTER.order,
                'sitemesh.decorator.tagRuleBundles': ['org.sitemesh.content.tagrules.html.Sm2TagRuleBundle']
        ]
        if (defaultLayout) {
            props['sitemesh.decorator.default'] = defaultLayout
        }
        // if property already exists, don't override
        props.clone().each {
            if (configurableEnvironment.getProperty(it.key)) {
                props.remove(it.key)
            }
        }
        return new MapPropertySource("sitemesh3Properties", props)
    }


    Closure doWithSpring() {
        { ->
            ConfigurableEnvironment configurableEnvironment = application.mainContext.environment
            def propertySources = configurableEnvironment.getPropertySources()
            // https://gsp.grails.org/latest/guide/layouts.html
            // Default view should be application, but it is inefficient to add a rule for a page that may not exist.
            String defaultLayout = grailsApplication.getConfig().getProperty("grails.sitemesh.default.layout")
            propertySources.addFirst(getDefaultPropertySource(configurableEnvironment, defaultLayout))
            application.config = new PropertySourcesConfig(propertySources)
            grailsLayoutHandlerMapping(GrailsLayoutHandlerMapping)
        }
    }

    void doWithApplicationContext() {}

    void doWithDynamicMethods() {}

    void onChange(Map<String, Object> event) {}

    void onConfigChange(Map<String, Object> event) {}

    void onShutdown(Map<String, Object> event) {}
}