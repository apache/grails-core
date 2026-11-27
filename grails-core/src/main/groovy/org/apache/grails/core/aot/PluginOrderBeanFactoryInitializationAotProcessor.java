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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.AotDetector;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.plugins.exceptions.PluginException;

/**
 * Records the order of the plugins an ahead-of-time build put the beans in, so that an application running on
 * the generated code checks it runs with the plugins in that same order.
 *
 * <p>The build runs the plugins' bean registrars in plugin order and freezes the bean definitions they make.
 * The hooks, views, messages and configuration still follow the order discovery finds when the application
 * runs. The two agree as long as the application runs with the plugins it was built with, since the order
 * depends on nothing else; a different set of plugins is reported instead of running with beans of another
 * order.</p>
 *
 * @since 9.0
 */
public class PluginOrderBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    /** The resource the build writes the plugin names to, one per line, in plugin order. */
    public static final String PLUGIN_ORDER_PATH = "META-INF/grails/aot/plugin-order";

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory.getSingleton(GrailsPluginManager.BEAN_NAME) instanceof GrailsPluginManager pluginManager)) {
            return null;
        }
        StringBuilder order = new StringBuilder();
        for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
            order.append(plugin.getName()).append('\n');
        }
        return (generationContext, beanFactoryInitializationCode) -> {
            generationContext.getGeneratedFiles().addResourceFile(PLUGIN_ORDER_PATH, order);
            generationContext.getRuntimeHints().resources().registerPattern(PLUGIN_ORDER_PATH);
        };
    }

    /**
     * Checks, when the application runs on generated code, that its plugins are in the order the build recorded.
     * An application not running on generated code, or one whose build recorded no order, passes.
     *
     * @param pluginNames the names of the plugins the application runs with, in plugin order
     * @param classLoader the class loader of the application
     * @throws PluginException if the plugins differ from the ones the build ordered
     */
    public static void verifyOrder(List<String> pluginNames, @Nullable ClassLoader classLoader) {
        if (!AotDetector.useGeneratedArtifacts()) {
            return;
        }
        List<String> recorded = recordedOrder(classLoader != null ? classLoader : PluginOrderBeanFactoryInitializationAotProcessor.class.getClassLoader());
        if (recorded != null && !recorded.equals(pluginNames)) {
            throw new PluginException("The Grails plugins are in the order " + pluginNames + ", but the ahead-of-time " +
                    "build made the beans with the plugins in the order " + recorded + ": build the application again " +
                    "with the plugins it runs with");
        }
    }

    private static @Nullable List<String> recordedOrder(ClassLoader classLoader) {
        try (InputStream input = classLoader.getResourceAsStream(PLUGIN_ORDER_PATH)) {
            if (input == null) {
                return null;
            }
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(text.split("\n")).map(String::trim).filter(name -> !name.isEmpty()).toList();
        }
        catch (IOException e) {
            throw new PluginException("Could not read " + PLUGIN_ORDER_PATH, e);
        }
    }
}
