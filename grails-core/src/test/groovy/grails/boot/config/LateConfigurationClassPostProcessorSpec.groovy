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
package grails.boot.config

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import grails.core.GrailsApplicationLifeCycleAdapter
import grails.util.Environment
import grails.util.Holders
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import spock.lang.Specification

/**
 * Reproduces apache/grails-core#16029: an {@code @Configuration} class contributed to the registry
 * by {@code GrailsApplicationPostProcessor.postProcessBeanDefinitionRegistry} -- standing in for one
 * declared in an application's {@code resources.groovy}, which drains through that same method --
 * must still be parsed, even though it arrives strictly after Spring's own
 * {@code ConfigurationClassPostProcessor} has already completed its one and only pass.
 *
 * <p>{@code GrailsApplicationPostProcessor} is wired here exactly as {@code GrailsAutoConfiguration}
 * wires it in production (see {@code GrailsAutoConfiguration#grailsApplicationPostProcessor}): as an
 * ordinary, non-{@code PriorityOrdered} {@code @Bean}, so Spring only discovers and invokes it after
 * expanding the primary source's own configuration -- the same round in which Spring's processor
 * finishes running.</p>
 */
class LateConfigurationClassPostProcessorSpec extends Specification {

    void 'a @Configuration class registered after Boot auto-configuration is still parsed'() {
        given: 'a context wiring GrailsApplicationPostProcessor the same way GrailsAutoConfiguration does'
            def ctx = new AnnotationConfigApplicationContext()
            def discovery = new DefaultPluginDiscovery([] as Class<?>[])
            discovery.loadPluginsFromClasspath = false
            discovery.init(ctx.environment)
            ctx.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
            ctx.register(LateConfigPostProcessorWiring)

        when: 'the context refreshes'
            ctx.refresh()

        then: 'the @Bean method on the late @Configuration class was expanded into a real bean'
            ctx.getBean('lateBean') instanceof LateConfigBean

        cleanup:
            ctx.close()
            Holders.clear()
            Environment.setInitializing(false)
    }
}

@Configuration
class LateConfigPostProcessorWiring {

    @Bean
    GrailsApplicationPostProcessor grailsApplicationPostProcessor(
            ApplicationContext applicationContext, PluginDiscovery pluginDiscovery) {
        new GrailsApplicationPostProcessor(new LateConfigLifeCycle(), applicationContext, pluginDiscovery)
    }
}

class LateConfigLifeCycle extends GrailsApplicationLifeCycleAdapter {

    @Override
    Closure doWithSpring() {
        { -> delegate.lateConfig(LateConfig) }
    }
}

/** Stands in for an {@code @Configuration} class declared in an application's resources.groovy. */
@Configuration
class LateConfig {

    @Bean
    LateConfigBean lateBean() {
        new LateConfigBean()
    }
}

class LateConfigBean {

}
