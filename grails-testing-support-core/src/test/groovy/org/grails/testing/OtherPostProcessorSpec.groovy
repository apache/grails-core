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
package org.grails.testing

import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.ConfigurableApplicationContext

import grails.boot.config.GrailsApplicationPostProcessor
import grails.core.GrailsApplicationLifeCycle
import grails.util.Holders
import org.apache.grails.core.plugins.PluginDiscovery
import spock.lang.Specification

/**
 * {@code registerGrailsAppPostProcessorBean} is an extension point: a builder that registers a
 * post-processor of another type keeps the plugin registration that post-processor does.
 */
class OtherPostProcessorSpec extends Specification {

    static class OtherPostProcessor extends GrailsApplicationPostProcessor {

        OtherPostProcessor(PluginDiscovery pluginDiscovery) {
            super([doWithSpring: { -> null }] as GrailsApplicationLifeCycle, null, pluginDiscovery)
        }
    }

    GrailsApplicationBuilder builder

    void cleanup() {
        ((ConfigurableApplicationContext) builder?.grailsApplication?.mainContext)?.close()
        Holders.clear()
    }

    void "a builder registering a post-processor of another type builds, with the plugins' beans that post-processor registers"() {
        given:
        builder = new GrailsApplicationBuilder() {
            @Override
            protected void registerGrailsAppPostProcessorBean(ConfigurableBeanFactory beanFactory, PluginDiscovery pluginDiscovery) {
                def arguments = new ConstructorArgumentValues()
                arguments.addIndexedArgumentValue(0, pluginDiscovery)
                ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(POST_PROCESSOR_BEAN_NAME,
                        new RootBeanDefinition(OtherPostProcessor, arguments, null))
            }
        }

        when:
        builder.build()

        then:
        def context = builder.grailsApplication.mainContext
        context.getBean(GrailsApplicationBuilder.POST_PROCESSOR_BEAN_NAME) instanceof OtherPostProcessor
        context.containsBean('proxyHandler')
    }
}
