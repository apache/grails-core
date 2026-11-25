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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import grails.plugins.exceptions.PluginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers Grails plugins from {@code META-INF/grails/plugins.imports} and per-plugin
 * {@code META-INF/grails/plugins/<name>/plugin.properties} descriptors available on the classpath.
 *
 * <p>Supports class-backed plugins (traditional {@code *GrailsPlugin} classes and
 * {@link grails.plugins.metadata.GrailsPluginDescriptor}-annotated classes) as well as descriptor-less
 * starter plugins that declare no plugin class at all.</p>
 *
 * <p>Discovery is intentionally tolerant: individual plugin load failures are logged and skipped so the
 * remaining classpath plugins can still be discovered.</p>
 */
public class ClasspathPluginFinder {

    private static final Logger LOG = LoggerFactory.getLogger(ClasspathPluginFinder.class);

    /**
     * Discovers classpath plugins for the supplied Grails version.
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
            if (pluginDescriptor.isDescriptorLess()) {
                try {
                    var metadata = PluginUtils.extractPluginMetadata(pluginDescriptor, null);
                    if (metadata != null) {
                        var configResource = PluginUtils.readPluginConfiguration(metadata.getName(), null, classLoader);
                        var pluginInfo = new PluginInfo(pluginDescriptor, metadata, configResource, false);
                        pluginInfo.isGrailsVersionCompatible(targetGrailsVersion);
                        discoveredPlugins.add(pluginInfo);
                    }
                } catch (Exception e) {
                    LOG.debug("Error loading descriptor-less plugin [{}]: {}",
                            pluginDescriptor.getPluginName(), e.getMessage());
                }
                continue;
            }

            for (var pluginClassName : pluginDescriptor.getProvidedPlugins()) {
                try {
                    var pluginClass = attemptPluginClassLoad(pluginClassName, classLoader);
                    if (PluginUtils.isGrailsPluginLoadable(pluginClass)) {
                        var metadata = PluginUtils.extractPluginMetadata(pluginDescriptor, pluginClass);
                        if (metadata != null) {
                            var configResource = PluginUtils.readPluginConfiguration(
                                    metadata.getName(), pluginClass, classLoader);
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

        Map<String, PluginInfo> byName = new LinkedHashMap<>();
        for (PluginInfo candidate : discoveredPlugins) {
            PluginInfo previous = byName.putIfAbsent(candidate.getName(), candidate);
            if (previous != null && (candidate.getPluginClass() != previous.getPluginClass()
                    || candidate.getPluginClass() == null && !candidate.getPluginDescriptor().equals(previous.getPluginDescriptor()))) {
                throw new PluginException("Conflicting definitions for Grails plugin [" + candidate.getName() + "]: "
                        + previous.getPluginDescriptor() + " and " + candidate.getPluginDescriptor());
            }
        }
        return List.copyOf(byName.values());
    }

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
