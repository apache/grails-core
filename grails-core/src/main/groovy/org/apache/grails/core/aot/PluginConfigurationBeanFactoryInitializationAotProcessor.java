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
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.Resource;

import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import org.apache.grails.core.plugins.PluginUtils;

/**
 * Copies each plugin's configuration to a location of its own, where an application running on generated
 * code finds it.
 *
 * <p>A plugin's {@code plugin.yml} is found relative to the root its class was loaded from: the plugin's
 * JAR, or its build directory. A native image has no such roots. Every class in it is at the same root, so
 * every plugin's configuration is looked for at the same {@code /plugin.yml}, which is either missing or
 * one plugin's file answering for all of them. An image built without these lost every plugin's
 * configuration.</p>
 *
 * <p>The configurations are read from the plugins the application loaded, while the code is generated and
 * the roots still exist, and each is written under the name of its plugin class at
 * {@link PluginUtils#getGeneratedConfigurationPath(String, String)}, which the lookup prefers, and
 * registered as a resource.</p>
 *
 * @since 9.0
 */
public class PluginConfigurationBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Map<String, Resource> configurations = configurationsOf(beanFactory);
        if (configurations.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) -> configurations.forEach((path, configuration) -> {
            generationContext.getGeneratedFiles().addResourceFile(path, configuration);
            generationContext.getRuntimeHints().resources().registerPattern(path);
        });
    }

    /** Where each loaded plugin's configuration is copied to, and the configuration itself. */
    private static Map<String, Resource> configurationsOf(ConfigurableListableBeanFactory beanFactory) {
        Map<String, Resource> configurations = new LinkedHashMap<>();
        if (!(beanFactory.getSingleton(GrailsPluginManager.BEAN_NAME) instanceof GrailsPluginManager pluginManager)) {
            return configurations;
        }
        for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
            Class<?> pluginClass = plugin.getPluginClass();
            Resource configuration = PluginUtils.readPluginConfiguration(pluginClass);
            if (configuration != null) {
                configurations.put(PluginUtils.getGeneratedConfigurationPath(pluginClass.getName(),
                        configuration.getFilename()), configuration);
            }
        }
        return configurations;
    }

}
