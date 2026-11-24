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
package org.apache.grails.core.aot;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.Resource;

import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import org.apache.grails.core.plugins.PluginUtils;

/**
 * Ensures each loaded plugin's configuration ({@code plugin.yml} or {@code plugin.groovy}) is reachable
 * in an ahead-of-time / native image.
 *
 * <p>Plugins packaged under Grails 9 store their configuration at the collision-free path
 * {@code META-INF/grails/plugins/<name>/plugin.yml}, which only requires registering a resource hint.
 * For dynamic or test plugins whose configuration still sits at the class root ({@code /plugin.yml}),
 * this processor copies the resource under {@link PluginUtils#getGeneratedConfigurationPath(String, String)}
 * so each plugin retains its own file when JAR roots are flattened in a native image.</p>
 *
 * @since 9.0
 */
public class PluginConfigurationBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Map<String, Resource> copiedConfigurations = new LinkedHashMap<>();
        Set<String> namespacedPaths = new LinkedHashSet<>();
        collectConfigurations(beanFactory, copiedConfigurations, namespacedPaths);
        if (copiedConfigurations.isEmpty() && namespacedPaths.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) -> {
            namespacedPaths.forEach(path -> generationContext.getRuntimeHints().resources().registerPattern(path));
            copiedConfigurations.forEach((path, configuration) -> {
                generationContext.getGeneratedFiles().addResourceFile(path, configuration);
                generationContext.getRuntimeHints().resources().registerPattern(path);
            });
        };
    }

    private static void collectConfigurations(
            ConfigurableListableBeanFactory beanFactory,
            Map<String, Resource> copiedConfigurations,
            Set<String> namespacedPaths) {
        if (!(beanFactory.getSingleton(GrailsPluginManager.BEAN_NAME) instanceof GrailsPluginManager pluginManager)) {
            return;
        }
        ClassLoader beanClassLoader = beanFactory.getBeanClassLoader();
        for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
            Class<?> pluginClass = plugin.getPluginClass();
            String pluginName = plugin.getName();
            ClassLoader pluginLoader = pluginClass != null ? pluginClass.getClassLoader() : null;
            ClassLoader loader = pluginLoader != null ? pluginLoader : beanClassLoader;
            if (loader != null && PluginUtils.isValidPluginName(pluginName)) {
                String ymlPath = PluginUtils.getPluginConfigPath(pluginName, PluginUtils.PLUGIN_YML_CONFIG);
                String groovyPath = PluginUtils.getPluginConfigPath(pluginName, PluginUtils.PLUGIN_GROOVY_CONFIG);
                boolean hasYml = loader.getResource(ymlPath) != null;
                boolean hasGroovy = loader.getResource(groovyPath) != null;
                if (hasYml && hasGroovy) {
                    throw new RuntimeException("A plugin [" + pluginName +
                            "] may define a plugin.yml or a plugin.groovy, but not both");
                }
                if (hasYml) {
                    namespacedPaths.add(ymlPath);
                    continue;
                }
                if (hasGroovy) {
                    namespacedPaths.add(groovyPath);
                    continue;
                }
            }
            if (pluginClass != null) {
                Resource configuration = PluginUtils.readPluginConfiguration(pluginName, pluginClass, loader);
                if (configuration != null) {
                    copiedConfigurations.put(PluginUtils.getGeneratedConfigurationPath(pluginClass.getName(),
                            configuration.getFilename()), configuration);
                }
            }
        }
    }

}
