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
package org.apache.grails.core.plugins.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.grails.core.plugins.PluginMetadata;

/**
 * A {@link PluginFilter} implementation that retains explicitly named plugins and any plugins they
 * transitively depend on.
 */
public class IncludingPluginFilter extends BasePluginFilter {

    /**
     * Creates a filter that includes the supplied plugin names.
     *
     * @param included the plugin names to include
     */
    public IncludingPluginFilter(Set<String> included) {
        super(included);
    }

    /**
     * Creates a filter that includes the supplied plugin names.
     *
     * @param included the plugin names to include; each value is trimmed before use
     */
    public IncludingPluginFilter(String... included) {
        super(included);
    }

    /**
     * Returns only the explicitly included plugins and the dependencies discovered from them.
     *
     * @param original the original plugin list supplied to the filter
     * @param pluginList the explicitly named and dependency-derived plugins collected by the base algorithm
     * @return a new list containing only the included plugins
     */
    @Override
    protected List<PluginMetadata> getPluginList(List<PluginMetadata> original, List<PluginMetadata> pluginList) {
        return new ArrayList<>(pluginList);
    }

    /**
     * Adds the direct dependencies declared by the supplied plugin, so they are included as well.
     *
     * <p>Each dependency is registered through {@link #registerDependency(List, PluginMetadata)} so that
     * recursive traversal and deduplication are handled consistently by the base class.</p>
     *
     * @param additionalList the list collecting plugins that should also be included
     * @param plugin the plugin whose dependencies should be included
     */
    @Override
    protected void addPluginDependencies(List<PluginMetadata> additionalList, PluginMetadata plugin) {
        var dependencyNames = plugin.getDependsOnNames();
        for (var name : dependencyNames) {
            registerDependency(additionalList, getNamedPlugin(name));
        }
    }
}
