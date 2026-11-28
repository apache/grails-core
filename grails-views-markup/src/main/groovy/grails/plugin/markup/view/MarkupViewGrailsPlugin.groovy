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

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.plugin.markup.view.mvc.MarkupViewResolver
import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor
import grails.views.mvc.GenericGroovyTemplateViewResolver
import grails.views.mvc.SmartViewResolver
import grails.views.resolve.PluginAwareTemplateResolver

/**
 * Plugin class for markup views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@GrailsPluginDescriptor
class MarkupViewGrailsPlugin extends Plugin {

    // Extra (optional) plugin metadata

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('markupViewConfiguration', MarkupViewConfiguration)
            registry.registerBean('markupTemplateEngine', MarkupViewTemplateEngine) {
                it.supplier {
                    new MarkupViewTemplateEngine(
                            it.bean('markupViewConfiguration', MarkupViewConfiguration),
                            applicationContext.classLoader)
                }
            }
            registry.registerBean('smartMarkupViewResolver', MarkupViewResolver) {
                it.supplier {
                    MarkupViewResolver viewResolver = new MarkupViewResolver(it.bean('markupTemplateEngine', MarkupViewTemplateEngine))
                    PluginAwareTemplateResolver templateResolver = new PluginAwareTemplateResolver(
                            it.bean('markupViewConfiguration', MarkupViewConfiguration))
                    templateResolver.pluginManager = pluginManager
                    viewResolver.templateResolver = templateResolver
                    return viewResolver
                }
            }
            registry.registerBean('markupViewResolver', GenericGroovyTemplateViewResolver) {
                it.supplier {
                    new GenericGroovyTemplateViewResolver(it.bean('smartMarkupViewResolver', SmartViewResolver))
                }
            }
        }
    }
}
