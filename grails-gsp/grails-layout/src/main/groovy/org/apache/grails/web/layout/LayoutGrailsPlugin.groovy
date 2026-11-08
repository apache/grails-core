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

package org.apache.grails.web.layout

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry

import grails.config.Settings
import grails.plugins.Plugin
import grails.util.Environment
import grails.util.GrailsUtil
import grails.util.Metadata
import org.grails.plugins.web.taglib.GrailsLayoutTagLib
import org.grails.plugins.web.taglib.RenderGrailsLayoutTagLib

/**
 * Plugin responsible for Grails Layout specific configuration.
 */
@CompileStatic
class LayoutGrailsPlugin extends Plugin {

    public static final String GSP_VIEW_LAYOUT_RESOLVER_ENABLED = 'grails.gsp.view.layoutViewResolver'
    public static final String DEFAULT_LAYOUT = 'grails.views.layout.default'
    public static final String GRAILS_LAYOUT_ENABLE_NONGSP = 'grails.views.layout.enable.nongsp'

    def title = 'Layout'
    def grailsVersion = '8.0.0-SNAPSHOT > *'
    def dependsOn = [core: GrailsUtil.getGrailsVersion(), i18n: GrailsUtil.getGrailsVersion()]
    def observe = ['controllers']
    def loadAfter = ['groovyPages']

    def providedArtefacts = [
            RenderGrailsLayoutTagLib,
            GrailsLayoutTagLib
    ]

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, org.springframework.core.env.Environment environment ->
            boolean developmentMode = Metadata.getCurrent().isDevelopmentEnvironmentAvailable()
            Environment env = Environment.current

            boolean enableReload = env.isReloadEnabled() ||
                    environment.getProperty(Settings.GSP_ENABLE_RELOAD, Boolean, false) ||
                    (developmentMode && env == Environment.DEVELOPMENT)

            // "grails.gsp.view.layoutViewResolver=false" can be used to disable EmbeddedGrailsLayoutViewResolver
            boolean enableLayoutViewResolver = environment.getProperty(GSP_VIEW_LAYOUT_RESOLVER_ENABLED, Boolean, true)
            if (enableLayoutViewResolver) {
                String defaultDecoratorNameSetting = environment.getProperty(DEFAULT_LAYOUT, '')
                Boolean grailsLayoutEnableNonGspViews = environment.getProperty(GRAILS_LAYOUT_ENABLE_NONGSP, Boolean, false)
                registry.registerBean('groovyPageLayoutFinder', GroovyPageLayoutFinder) {
                    it.supplier {
                        GroovyPageLayoutFinder layoutFinder = new GroovyPageLayoutFinder()
                        layoutFinder.gspReloadEnabled = enableReload
                        layoutFinder.defaultDecoratorName = defaultDecoratorNameSetting ?: null
                        layoutFinder.enableNonGspViews = grailsLayoutEnableNonGspViews
                        return layoutFinder
                    }
                }
                registry.registerBean('grailsRenderViewMutator', GrailsLayoutRenderViewMutator)
                registry.registerBean('grailsLayoutSelector', LayoutSelector)
                registry.registerBean('grailsLayoutViewResolverPostProcessor', GrailsLayoutViewResolverPostProcessor)
            }
        }
    }
}
