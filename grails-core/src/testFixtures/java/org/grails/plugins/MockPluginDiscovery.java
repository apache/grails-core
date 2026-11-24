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
package org.grails.plugins;

import java.util.List;
import java.util.Map;

import grails.plugins.GrailsPlugin;
import org.apache.grails.core.plugins.DefaultPluginDiscovery;
import org.apache.grails.core.plugins.PluginDescriptor;
import org.apache.grails.core.plugins.PluginInfo;
import org.apache.grails.core.plugins.PluginUtils;

public class MockPluginDiscovery extends DefaultPluginDiscovery {

    public MockPluginDiscovery() {
        super();
        reset();
    }

    public MockPluginDiscovery(Class<?>[] pluginClasses) {
        super(pluginClasses);
    }

    public void registerMockPlugin(GrailsPlugin plugin) {
        if (plugin instanceof BinaryGrailsPlugin binaryPlugin) {
            registerMockPlugin(binaryPlugin);
            return;
        }
        Class<?> pluginClass = plugin.getPluginClass();
        if (pluginClass != null) {
            registerMockPlugin(PluginUtils.createPluginInfo(pluginClass, null, true));
            return;
        }
        PluginDescriptor descriptor = syntheticDescriptor(plugin);
        registerMockPlugin(PluginUtils.createPluginInfoForDescriptor(descriptor, null, true));
    }

    public void registerMockPlugin(BinaryGrailsPlugin plugin) {
        PluginDescriptor descriptor = plugin.getPluginDescriptor();
        Class<?> pluginClass = plugin.getPluginClass();
        if (pluginClass != null) {
            registerMockPlugin(PluginUtils.createPluginInfo(
                    pluginClass, descriptor != null ? descriptor.getResource() : null, false));
            return;
        }
        PluginDescriptor effectiveDescriptor = descriptor != null ? descriptor : syntheticDescriptor(plugin);
        registerMockPlugin(PluginUtils.createPluginInfoForDescriptor(effectiveDescriptor, null, false));
    }

    private static PluginDescriptor syntheticDescriptor(GrailsPlugin plugin) {
        return new PluginDescriptor(
                null,
                plugin.getName(),
                plugin.getVersion(),
                null,
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                plugin.isEnabled(),
                true
        );
    }

    public void registerMockPlugin(PluginInfo pluginInfo) {
        initPluginsIfNotDefined();

        plugins.put(pluginInfo.getName(), pluginInfo);
        loadOrderedPlugins.add(pluginInfo);
        orderedPlugins.add(pluginInfo);
    }

    private void initPluginsIfNotDefined() {
        if (plugins == null) {
            reset();
        }
    }

    @Override
    public void reset() {
        super.reset();
        initialized = true; // do not search on the classpath by default
    }
}
