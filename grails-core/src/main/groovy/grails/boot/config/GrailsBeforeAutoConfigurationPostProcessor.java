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
import org.grails.config.NavigableMap;
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
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;

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
 * runs later in {@code GrailsApplicationPostProcessor} against a separate plugin manager, so each plugin is
 * instantiated twice. That is safe for the bean-definition-only contract of the phase, with one caveat: a
 * plugin that also implements {@link org.springframework.context.ApplicationListener} would, when given the
 * real context, be registered as a listener in both passes — so this phase does <strong>not</strong> propagate
 * the application context to its throwaway plugin instances (the manager context is left unset; bean
 * definitions are flushed straight into the registry).
 *
 * <p>Ordering is guaranteed by being added via {@code addBeanFactoryPostProcessor} (manually-registered
 * {@code BeanDefinitionRegistryPostProcessor}s run before registry-discovered ones such as
 * {@code ConfigurationClassPostProcessor}). It is NOT an ordered/priority bean — Spring does not sort
 * manually-added post-processors by {@code getOrder()} — so this class deliberately does not implement
 * {@code PriorityOrdered}.
 *
 * @since 8.0
 */
public class GrailsBeforeAutoConfigurationPostProcessor implements BeanDefinitionRegistryPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsBeforeAutoConfigurationPostProcessor.class);

    private final ConfigurableApplicationContext applicationContext;

    public GrailsBeforeAutoConfigurationPostProcessor(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // Check the LOCAL singleton only — a parent context's discovery must not cause us to re-run
        // the early drain into a child context (containsBean/getBean would delegate to the parent).
        Object discovery = applicationContext.getBeanFactory().getSingleton(PluginDiscovery.BEAN_NAME);
        if (!(discovery instanceof PluginDiscovery)) {
            // No plugin discovery promoted to this context (e.g. unit-test slice) — nothing to do.
            return;
        }
        PluginDiscovery pluginDiscovery = (PluginDiscovery) discovery;

        DefaultGrailsApplication grailsApplication = new DefaultGrailsApplication();
        grailsApplication.setConfig(buildConfig());
        grailsApplication.setApplicationContext(applicationContext);
        grailsApplication.setMainContext(applicationContext);

        // The manager's application context is deliberately NOT set: loadPlugins() must not propagate
        // the real context to these throwaway plugin instances, or a plugin implementing
        // ApplicationListener would be registered a second time (see class javadoc). The drain needs
        // only the GrailsApplication (bound above); profiles are read from its main context.
        DefaultGrailsPluginManager pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery);
        pluginManager.loadPlugins();

        RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration();
        pluginManager.doRuntimeConfigurationBeforeAutoConfiguration(springConfig);
        springConfig.registerBeansWithRegistry(registry);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Registered plugin beans from the before-auto-configuration phase");
        }
    }

    /**
     * Builds the {@link PropertySourcesConfig} that backs {@code grailsApplication.config} in this
     * phase, registering the same conversion-service converters that
     * {@code GrailsApplicationPostProcessor.loadApplicationConfig} registers for the main lifecycle.
     * This gives before-auto-configuration closures parity when reading config — null-safe
     * navigation of missing paths and {@code String -> Resource} coercion — not just scalar
     * {@code getProperty(...)} access.
     */
    private PropertySourcesConfig buildConfig() {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        ConfigurableConversionService conversionService = null;
        if (environment instanceof AbstractEnvironment) {
            conversionService = ((AbstractEnvironment) environment).getConversionService();
            conversionService.addConverter(String.class, Resource.class, applicationContext::getResource);
            conversionService.addConverter(NavigableMap.NullSafeNavigator.class, String.class, source -> null);
            conversionService.addConverter(NavigableMap.NullSafeNavigator.class, Object.class, source -> null);
        }
        PropertySourcesConfig config = new PropertySourcesConfig(environment.getPropertySources());
        if (conversionService != null) {
            config.setConversionService(conversionService);
        }
        return config;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}
