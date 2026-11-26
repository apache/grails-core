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

import java.util.LinkedHashSet;
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
 * <p>A plugin's configuration sits at {@code META-INF/grails/plugins/<name>/plugin.yml} (or
 * {@code plugin.groovy}), beside its descriptor, so a resource hint for that path is all a native image
 * needs.</p>
 *
 * @since 9.0
 */
public class PluginConfigurationBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Set<String> paths = collectConfigurationPaths(beanFactory);
        if (paths.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) ->
                paths.forEach(path -> generationContext.getRuntimeHints().resources().registerPattern(path));
    }

    private static Set<String> collectConfigurationPaths(ConfigurableListableBeanFactory beanFactory) {
        Set<String> paths = new LinkedHashSet<>();
        if (!(beanFactory.getSingleton(GrailsPluginManager.BEAN_NAME) instanceof GrailsPluginManager pluginManager)) {
            return paths;
        }
        ClassLoader beanClassLoader = beanFactory.getBeanClassLoader();
        for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
            Class<?> pluginClass = plugin.getPluginClass();
            ClassLoader loader = pluginClass != null && pluginClass.getClassLoader() != null ?
                    pluginClass.getClassLoader() : beanClassLoader;
            Resource configuration = PluginUtils.readPluginConfiguration(plugin.getName(), loader);
            if (configuration != null) {
                paths.add(PluginUtils.getPluginConfigPath(plugin.getName(), configuration.getFilename()));
            }
        }
        return paths;
    }
}
