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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;

import grails.plugins.exceptions.PluginException;
import grails.plugins.metadata.PluginDefinition;

/**
 * Finds the Grails plugins on a class path: every name listed in a {@code META-INF/grails/plugins.imports},
 * read from its {@code META-INF/grails/plugins/<name>/plugin.properties}.
 *
 * <p>A descriptor that cannot be read, that names a class which is not on the class path, or a class that
 * is not a {@code grails.plugins.Plugin}, fails discovery: the jar it came from is broken. The same plugin
 * found twice, as on a test class path that holds both a project's classes and its jar, is found once; two
 * different plugins with one name fail discovery.</p>
 */
public class ClasspathPluginFinder {

    private static final Logger LOG = LoggerFactory.getLogger(ClasspathPluginFinder.class);

    /**
     * @param classLoader the class loader whose class path is searched
     * @param targetGrailsVersion the Grails version the plugins are checked against
     * @return the plugins found, one per name
     */
    public List<PluginInfo> findClasspathPlugins(ClassLoader classLoader, String targetGrailsVersion) {
        Map<String, PluginInfo> byName = new LinkedHashMap<>();
        for (Map.Entry<Resource, PluginDefinition> descriptor : PluginUtils.scanPluginDescriptorResources(classLoader).entrySet()) {
            Resource resource = descriptor.getKey();
            PluginDefinition definition = descriptor.getValue();
            Class<?> pluginClass = loadPluginClass(definition, resource, classLoader);
            PluginInfo candidate = PluginUtils.createPluginInfo(pluginClass, definition, resource);
            candidate.isGrailsVersionCompatible(targetGrailsVersion);
            PluginInfo previous = byName.putIfAbsent(candidate.getName(), candidate);
            if (previous != null && previous.getPluginClass() != candidate.getPluginClass()) {
                throw new PluginException("Conflicting definitions for Grails plugin [" + candidate.getName() + "]: " +
                        previous.getDescriptorResource() + " and " + candidate.getDescriptorResource());
            }
        }
        LOG.debug("Found [{}] plugins on the class path", byName.size());
        return List.copyOf(byName.values());
    }

    private static Class<?> loadPluginClass(PluginDefinition definition, Resource resource, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(definition.getPluginClass());
        }
        catch (ClassNotFoundException | LinkageError e) {
            throw new PluginException("Grails plugin [" + definition.getPluginName() + "] names plugin class [" +
                    definition.getPluginClass() + "], which cannot be loaded: " + resource, e);
        }
    }
}
