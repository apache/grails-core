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

package grails.plugin.markup.view

import grails.plugin.markup.view.mvc.MarkupViewResolver
import grails.plugins.Plugin
import grails.views.mvc.GenericGroovyTemplateViewResolver
import grails.views.resolve.PluginAwareTemplateResolver

/**
 * Plugin class for markup views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class MarkupViewGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "7.0.0 > *"

    def title = "Markup Views" // Headline display name of the plugin
    def author = "Graeme Rocher"
    def authorEmail = "graeme.rocher@gmail.com"
    def description = "A plugin that allows rendering of markup views"
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "https://grails.github.io/grails-views/latest/views/guide"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
    def organization = [name: 'Grails', url: 'https://grails.org']

    // Any additional developers beyond the author specified above.
    def developers = [[name: "Graeme Rocher", email: "graeme.rocher@gmail.com"]]

    // Location of the plugin's issue tracker.
    def issueManagement = [system: "Github", url: "https://github.com/grails/grails-views/issues"]

    // Online location of the plugin's browseable source code.
    def scm = [url: "https://github.com/grails/grails-views"]

    Closure doWithSpring() {
        { ->
            markupViewConfiguration(MarkupViewConfiguration)
            markupTemplateEngine(MarkupViewTemplateEngine, markupViewConfiguration, applicationContext.classLoader)
            smartMarkupViewResolver(MarkupViewResolver, markupTemplateEngine) {
                templateResolver = bean(PluginAwareTemplateResolver, markupViewConfiguration)
            }
            markupViewResolver(GenericGroovyTemplateViewResolver, smartMarkupViewResolver)
        }
    }
}
