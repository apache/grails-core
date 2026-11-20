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
package org.apache.grails.core.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers Grails plugins from {@code META-INF/grails-plugin.xml} descriptors available on the classpath.
 *
 * <p>For each descriptor, this finder attempts to load the declared plugin classes, extract their
 * {@link PluginMetadata}, resolve any plugin configuration resource, and assemble the results into
 * {@link PluginInfo} instances.</p>
 *
 * <p>Discovery is intentionally tolerant: individual plugin load failures are logged and skipped so the
 * remaining classpath plugins can still be discovered.</p>
 */
public class ClasspathPluginFinder {

    private static final Logger LOG = LoggerFactory.getLogger(ClasspathPluginFinder.class);

    /**
     * Discovers classpath plugins for the supplied Grails version.
     *
     * <p>This method scans {@code META-INF/grails-plugin.xml} descriptors via
     * {@link PluginUtils#scanPluginDescriptorResources(ClassLoader)}, iterates over each descriptor's
     * declared plugin classes, and then builds {@link PluginInfo} instances from the loaded plugin class,
     * extracted metadata, and optional plugin configuration resource.</p>
     *
     * <p>{@link PluginInfo#isGrailsVersionCompatible(String)} is invoked for each discovered plugin so the
     * compatibility check is evaluated during discovery. Any failure while loading or inspecting an individual
     * plugin class is logged and does not stop discovery of the remaining plugins.</p>
     *
     * @param classLoader the class loader used to locate descriptors and load plugin classes
     * @param targetGrailsVersion the Grails version the discovered plugins should be checked against
     * @return the discovered classpath plugins, or an empty list when no descriptors are found
     */
    public List<PluginInfo> findClasspathPlugins(ClassLoader classLoader, String targetGrailsVersion) {
        var pluginDescriptors = PluginUtils.scanPluginDescriptorResources(classLoader);
        if (pluginDescriptors.isEmpty()) {
            return Collections.emptyList();
        }

        var discoveredPlugins = new ArrayList<PluginInfo>();

        LOG.debug("Attempting to load [{}] plugin descriptors", pluginDescriptors.size());
        for (var pluginDescriptor : pluginDescriptors) {
            for (var pluginClassName : pluginDescriptor.getProvidedPlugins()) {
                try {
                    var pluginClass = attemptPluginClassLoad(pluginClassName, classLoader);
                    if (PluginUtils.isGrailsPluginLoadable(pluginClass)) {
                        var metadata = PluginUtils.extractPluginMetadata(pluginClass);
                        if (metadata != null) {
                            var configResource = PluginUtils.readPluginConfiguration(pluginClass);
                            var pluginInfo = new PluginInfo(pluginDescriptor, metadata, configResource, false);
                            pluginInfo.isGrailsVersionCompatible(targetGrailsVersion);
                            discoveredPlugins.add(pluginInfo);
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Error loading plugin class [{}]: {}", pluginClassName, e.getMessage());
                }
            }
        }

        return discoveredPlugins;
    }

    /**
     * Attempts to load a plugin class from the supplied class loader.
     *
     * <p>{@link ClassNotFoundException} is handled leniently so discovery can continue with the remaining
     * plugin descriptors.</p>
     *
     * @param pluginClassName the fully qualified plugin class name to load
     * @param classLoader the class loader used to load the class
     * @return the loaded plugin class, or {@code null} if the class cannot be found
     */
    private static Class<?> attemptPluginClassLoad(String pluginClassName, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(pluginClassName);
        } catch (ClassNotFoundException e) {
            LOG.warn("Grails Plugin [{}] not found, resuming load without..", pluginClassName);
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
        }
        return null;
    }
}
