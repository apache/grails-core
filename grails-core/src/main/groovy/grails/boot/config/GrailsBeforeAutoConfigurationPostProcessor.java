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
package grails.boot.config;

import grails.core.DefaultGrailsApplication;
import grails.core.GrailsApplication;
import grails.plugins.DefaultGrailsPluginManager;
import grails.plugins.GrailsPluginManager;
import org.apache.grails.core.plugins.PluginDiscovery;
import org.grails.config.PropertySourcesConfig;
import org.grails.spring.DefaultRuntimeSpringConfiguration;
import org.grails.spring.RuntimeSpringConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.PriorityOrdered;

/**
 * Registers plugin beans contributed via
 * {@link grails.core.GrailsApplicationLifeCycle#doWithSpringBeforeAutoConfiguration()} <em>before</em>
 * Spring Boot's auto-configuration is processed.
 *
 * <p>It is added to the context programmatically (see {@code GrailsBeforeAutoConfigurationInitializer}),
 * so its {@code postProcessBeanDefinitionRegistry} runs ahead of Boot's {@code ConfigurationClassPostProcessor}
 * (which expands the {@code @AutoConfiguration} imports). Because the plugin beans are already in the
 * registry, Boot auto-config beans guarded by {@code @ConditionalOnMissingBean} back off in favour of the
 * plugin's bean — no removal or override needed afterwards.
 *
 * <p>This deliberately does the minimum needed to drain the modern phase: it builds a lightweight
 * {@link GrailsApplication} backed by the already-populated {@code Environment} config and loads the plugin
 * classes (cheap). The full plugin lifecycle (artefact discovery, dynamic methods, legacy {@code doWithSpring})
 * still runs later in {@code GrailsApplicationPostProcessor}; {@code loadPlugins()} is idempotent.
 *
 * @since 8.0
 */
public class GrailsBeforeAutoConfigurationPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsBeforeAutoConfigurationPostProcessor.class);

    private final ConfigurableApplicationContext applicationContext;

    public GrailsBeforeAutoConfigurationPostProcessor(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!applicationContext.containsBean(PluginDiscovery.BEAN_NAME)) {
            // No plugin discovery promoted to the context (e.g. unit-test slice) — nothing to do.
            return;
        }
        PluginDiscovery pluginDiscovery = applicationContext.getBean(PluginDiscovery.BEAN_NAME, PluginDiscovery.class);

        DefaultGrailsApplication grailsApplication = new DefaultGrailsApplication();
        grailsApplication.setConfig(new PropertySourcesConfig(applicationContext.getEnvironment().getPropertySources()));
        grailsApplication.setApplicationContext(applicationContext);

        DefaultGrailsPluginManager pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery);
        pluginManager.setApplicationContext(applicationContext);
        pluginManager.loadPlugins();

        RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration();
        pluginManager.doRuntimeConfigurationBeforeAutoConfiguration(springConfig);
        springConfig.registerBeansWithRegistry(registry);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Registered plugin beans from the before-auto-configuration phase");
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }
}
