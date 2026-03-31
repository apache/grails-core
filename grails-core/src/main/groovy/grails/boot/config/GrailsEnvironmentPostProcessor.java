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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import org.apache.grails.core.plugins.PluginDiscovery;
import org.apache.grails.core.plugins.PluginInfo;
import org.apache.grails.core.plugins.PluginUtils;
import org.grails.config.PrefixedMapPropertySource;
import org.grails.config.yaml.YamlPropertySourceLoader;
import org.grails.core.cfg.GroovyConfigPropertySourceLoader;
import org.grails.core.exceptions.GrailsConfigurationException;

/**
 * A Spring Boot {@link EnvironmentPostProcessor} that loads {@code plugin.yml} or {@code plugin.groovy} configuration
 * files from Grails plugins early in the application lifecycle, before autoconfiguration conditions
 * (such as {@code @ConditionalOnProperty}) are evaluated.
 *
 * <p>This allows plugin configuration to be available to Spring Boot's {@code @ConditionalOnProperty} evaluation on
 * {@code @Configuration} and {@code @AutoConfiguration} classes.</p>
 *
 * @since 7.1
 * @see GrailsApplicationPostProcessor
 * @see PluginDiscovery
 */
public class GrailsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsEnvironmentPostProcessor.class);

    private final ConfigurableBootstrapContext bootstrapContext;

    GrailsEnvironmentPostProcessor(ConfigurableBootstrapContext bootstrapContext) {
        this.bootstrapContext = bootstrapContext;
    }

    @Override
    public int getOrder() {
        // Run after Spring Boot property source loading but before autoconfiguration evaluation.
        // We use a value that ensures we run after standard property sources are loaded but before
        // autoconfiguration conditions are evaluated.
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            PluginDiscovery pluginDiscovery = bootstrapContext.get(PluginDiscovery.class);
            if (pluginDiscovery == null) {
                throw new IllegalStateException("GrailsPluginDiscovery bean not found in bootstrap context");
            }

            pluginDiscovery.init(environment);

            Collection<PluginInfo> plugins = pluginDiscovery.getPluginsInLoadOrder();
            loadPluginConfigurations(plugins, environment);
        } catch (Exception e) {
            LOG.error("Error loading Grails plugin configurations early: ", e);
            throw new GrailsConfigurationException("Unable to load Grails Plugins", e);
        }
    }

    /**
     * Loads plugin configuration files ({@code plugin.yml} or {@code plugin.groovy})
     * in the topologically sorted order and adds them to the environment's property sources.
     *
     * <p>The reverse iteration with {@code addLast} replicates the ordering behavior
     * in the original {@code GrailsApplicationPostProcessor.loadApplicationConfig()},
     * where plugins are iterated in reverse sorted order and added with {@code addLast}.
     * This means earlier plugins (in sort order) get higher property precedence.</p>
     *
     * <p>Property sources are added with the same names and types that
     * {@link org.grails.plugins.AbstractGrailsPlugin} would produce:
     * a {@link org.grails.config.NavigableMapPropertySource} with name
     * {@code "<pluginName>-plugin.yml"} or {@code "<pluginName>-plugin.groovy"},
     * and a {@link PrefixedMapPropertySource} for the {@code grails.plugins.<name>} prefix.</p>
     */
    private void loadPluginConfigurations(Collection<PluginInfo> sortedPlugins, ConfigurableEnvironment environment) {
        MutablePropertySources propertySources = environment.getPropertySources();
        YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();
        GroovyConfigPropertySourceLoader groovyLoader = new GroovyConfigPropertySourceLoader();
        int loadedCount = 0;

        // Iterate in reverse order and use addLast, matching
        // GrailsApplicationPostProcessor.loadApplicationConfig() behavior
        List<PluginInfo> reversed = new ArrayList<>(sortedPlugins);
        Collections.reverse(reversed);

        for (PluginInfo plugin : reversed) {
            Resource resource = plugin.getConfigResource();
            if (resource == null || !resource.exists()) {
                continue;
            }

            try {
                String filename = resource.getFilename();
                String sourceName = plugin.getName() + "-" + filename;

                List<PropertySource<?>> loaded;
                if (PluginUtils.PLUGIN_YML_CONFIG.equals(filename)) {
                    loaded = yamlLoader.load(sourceName, resource, PluginUtils.DEFAULT_CONFIG_IGNORE_LIST);
                } else if (PluginUtils.PLUGIN_GROOVY_CONFIG.equals(filename)) {
                    loaded = groovyLoader.load(sourceName, resource, PluginUtils.DEFAULT_CONFIG_IGNORE_LIST);
                } else {
                    LOG.debug("Unknown config file format [{}] for plugin [{}], skipping",
                            filename, plugin.getName());
                    continue;
                }

                for (PropertySource<?> ps : loaded) {
                    if (ps != null) {
                        // Add the prefixed property source (grails.plugins.<name>.<prop>),
                        // matching what AbstractGrailsPlugin / loadApplicationConfig() does
                        if (ps instanceof EnumerablePropertySource<?> eps) {
                            propertySources.addLast(new PrefixedMapPropertySource(
                                    "grails.plugins." + plugin.getName(), eps));
                        }

                        // Add the property source directly (NavigableMapPropertySource)
                        // with the same name that AbstractGrailsPlugin would produce
                        propertySources.addLast(ps);
                        loadedCount++;
                    }
                }

                LOG.debug("Loaded {} for plugin [{}] early in lifecycle", filename, plugin.getName());
            } catch (Exception e) {
                LOG.debug("Error loading configuration for plugin [{}]: {}", plugin.getName(), e.getMessage());
            }
        }

        if (loadedCount > 0) {
            LOG.info("Loaded {} plugin configuration(s) early via GrailsEnvironmentPostProcessor", loadedCount);
        }
    }

}
