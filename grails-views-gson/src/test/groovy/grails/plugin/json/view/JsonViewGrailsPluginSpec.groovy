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
package grails.plugin.json.view

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment

import grails.plugin.json.view.mvc.JsonViewResolver
import grails.plugins.GrailsPluginManager
import grails.views.mvc.GenericGroovyTemplateViewResolver
import grails.views.resolve.PluginAwareTemplateResolver

import spock.lang.Specification

class JsonViewGrailsPluginSpec extends Specification {

    def beanFactory = new DefaultListableBeanFactory()
    def pluginManager = Mock(GrailsPluginManager)

    void setup() {
        def plugin = new JsonViewGrailsPlugin()
        plugin.applicationContext = new GenericApplicationContext()
        plugin.pluginManager = pluginManager
        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }

    void "beanRegistrar registers the json view beans"() {
        expect:
        beanFactory.containsBeanDefinition('jsonApiIdRenderStrategy')
        beanFactory.getBeanDefinition('jsonViewConfiguration').beanClassName == JsonViewConfiguration.name
        beanFactory.containsBeanDefinition('jsonTemplateEngine')
        beanFactory.containsBeanDefinition('jsonSmartViewResolver')
        beanFactory.containsBeanDefinition('jsonViewResolver')
    }

    void "the smart view resolver is wired with a plugin-aware template resolver"() {
        when:
        def viewResolver = beanFactory.getBean('jsonSmartViewResolver', JsonViewResolver)

        then:
        viewResolver.templateEngine.is(beanFactory.getBean('jsonTemplateEngine', JsonViewTemplateEngine))
        viewResolver.templateEngine.templateResolver instanceof PluginAwareTemplateResolver
        ((PluginAwareTemplateResolver) viewResolver.templateEngine.templateResolver).pluginManager.is(pluginManager)
    }

    void "the mvc view resolver delegates to the smart view resolver"() {
        expect:
        beanFactory.getBean('jsonViewResolver', GenericGroovyTemplateViewResolver) != null
    }
}
