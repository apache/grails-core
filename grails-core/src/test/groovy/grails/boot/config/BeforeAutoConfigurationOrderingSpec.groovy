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

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/**
 * Proves the architectural linchpin of {@link GrailsBeforeAutoConfigurationPostProcessor}: a
 * {@code BeanDefinitionRegistryPostProcessor} added to the context via
 * {@code addBeanFactoryPostProcessor} registers its beans <em>before</em>
 * {@code ConfigurationClassPostProcessor} evaluates {@code @ConditionalOnMissingBean}. That is why
 * a plugin registering a bean in {@code doWithSpringBeforeAutoConfiguration} makes the matching Boot
 * auto-config bean back off — instead of the plugin having to override/remove it afterwards.
 */
class BeforeAutoConfigurationOrderingSpec extends Specification {

    void "an early BDRPP makes a @ConditionalOnMissingBean auto-config bean defer"() {
        given: "a context whose configuration would, by itself, register a conditional 'myResolver'"
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(AutoConfigLikeConfig)

        and: "a registrar added the same way GrailsBeforeAutoConfigurationPostProcessor is added — ahead of ConfigurationClassPostProcessor"
            ctx.addBeanFactoryPostProcessor(new PluginPhaseRegistrar())

        when: "the context refreshes"
            ctx.refresh()

        then: "the early (plugin) bean is the one named 'myResolver'..."
            ctx.getBean('myResolver').class == PluginResolver

        and: "...and Boot's conditional default was never created"
            ctx.getBeansOfType(BootDefaultResolver).isEmpty()

        cleanup:
            ctx.close()
    }

    void "without the early registrar, the conditional auto-config bean IS created (control)"() {
        given:
            def ctx = new AnnotationConfigApplicationContext()
            ctx.register(AutoConfigLikeConfig)

        when:
            ctx.refresh()

        then: "the conditional default wins when nothing registered the bean first"
            ctx.getBean('myResolver').class == BootDefaultResolver

        cleanup:
            ctx.close()
    }

    /** Stands in for a Spring Boot auto-configuration: a name-guarded conditional bean. */
    @Configuration
    static class AutoConfigLikeConfig {
        @Bean
        @ConditionalOnMissingBean(name = 'myResolver')
        BootDefaultResolver myResolver() { new BootDefaultResolver() }
    }

    /** Stands in for what a plugin's doWithSpringBeforeAutoConfiguration closure registers. */
    static class PluginPhaseRegistrar implements BeanDefinitionRegistryPostProcessor {
        @Override
        void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
            registry.registerBeanDefinition('myResolver', new RootBeanDefinition(PluginResolver))
        }
        @Override
        void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) {}
    }

    static class BootDefaultResolver {}
    static class PluginResolver {}
}
