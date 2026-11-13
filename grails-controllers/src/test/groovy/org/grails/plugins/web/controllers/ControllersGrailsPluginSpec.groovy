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
package org.grails.plugins.web.controllers

import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import org.grails.plugins.web.servlet.context.BootStrapClassRunner
import org.grails.web.servlet.mvc.TokenResponseActionResultTransformer
import org.grails.web.servlet.view.CompositeViewResolver

import spock.lang.Specification

class ControllersGrailsPluginSpec extends Specification {

    def beanFactory = new DefaultListableBeanFactory()

    void setup() {
        def plugin = new ControllersGrailsPlugin(grailsApplication: new DefaultGrailsApplication())
        def registrar = plugin.beanRegistrar()
        new BeanRegistryAdapter(beanFactory, new StandardEnvironment(), registrar.getClass()).register(registrar)
    }

    void "beanRegistrar registers the controllers infrastructure beans"() {
        expect:
        beanFactory.getBeanDefinition('bootStrapClassRunner').beanClassName == BootStrapClassRunner.name
        beanFactory.getBeanDefinition('tokenResponseActionResultTransformer').beanClassName == TokenResponseActionResultTransformer.name
        beanFactory.getBeanDefinition(CompositeViewResolver.BEAN_NAME).beanClassName == CompositeViewResolver.name
        beanFactory.containsBeanDefinition('controllerBeanDefinitionsPostProcessor')
    }

    void "the exception handler default is left to ControllersAutoConfiguration"() {
        // Auto-configured with @ConditionalOnMissingBean so an application- or plugin-defined
        // exceptionHandler backs the default off instead of triggering a definition override
        expect:
        !beanFactory.containsBeanDefinition('exceptionHandler')
    }

    void "the controller definitions post-processor is created for the plugin's application"() {
        expect:
        beanFactory.getBean('controllerBeanDefinitionsPostProcessor', ControllerBeanDefinitionsPostProcessor) != null
    }
}
