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

import grails.config.Config
import grails.config.Settings
import grails.plugins.Plugin
import grails.util.Environment
import grails.util.Metadata

class WebLayoutGrailsPlugin extends Plugin {

    public static final String GSP_VIEW_LAYOUT_RESOLVER_ENABLED = 'grails.gsp.view.layoutViewResolver'
    public static final String DEFAULT_LAYOUT = 'grails.views.layout.default'
    public static final String GRAILS_LAYOUT_ENABLE_NONGSP = 'grails.views.layout.enable.nongsp'

    def grailsVersion = '7.0.0-SNAPSHOT > *'
    def title = "Web Layout"

    Closure doWithSpring() {
        { ->
            def application = grailsApplication
            Config config = application.config

            boolean developmentMode = Metadata.getCurrent().isDevelopmentEnvironmentAvailable()
            Environment env = Environment.current

            boolean enableReload = env.isReloadEnabled() ||
                    config.getProperty(Settings.GSP_ENABLE_RELOAD, Boolean, false) ||
                    (developmentMode && env == Environment.DEVELOPMENT)

            // "grails.gsp.view.layoutViewResolver=false" can be used to disable EmbeddedGrailsLayoutViewResolver
            // containsKey check must be made to check existence of boolean false values in ConfigObject
            boolean enableLayoutViewResolver = config.getProperty(GSP_VIEW_LAYOUT_RESOLVER_ENABLED, Boolean, true)
            if (enableLayoutViewResolver) {
                String defaultDecoratorNameSetting = config.getProperty(DEFAULT_LAYOUT, '')
                Boolean grailsLayoutEnableNonGspViews = config.getProperty(GRAILS_LAYOUT_ENABLE_NONGSP, Boolean, false)
                groovyPageLayoutFinder(GroovyPageLayoutFinder) {
                    gspReloadEnabled = enableReload
                    defaultDecoratorName = defaultDecoratorNameSetting ?: null
                    enableNonGspViews = grailsLayoutEnableNonGspViews
                }
                grailsRenderViewMutator(GrailsLayoutRenderViewMutator)
                grailsLayoutSelector(LayoutSelector)
                grailsLayoutViewResolverPostProcessor(GrailsLayoutViewResolverPostProcessor)
            }
        }
    }
}
