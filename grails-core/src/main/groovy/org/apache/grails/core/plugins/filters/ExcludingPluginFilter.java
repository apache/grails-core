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
 * A {@link PluginFilter} implementation that removes explicitly named plugins and any plugins that
 * transitively depend on them.
 */
public class ExcludingPluginFilter extends BasePluginFilter {

    /**
     * Creates a filter that excludes the supplied plugin names.
     *
     * @param excluded the plugin names to exclude
     */
    public ExcludingPluginFilter(Set<String> excluded) {
        super(excluded);
    }

    /**
     * Creates a filter that excludes the supplied plugin names.
     *
     * @param excluded the plugin names to exclude; each value is trimmed before use
     */
    public ExcludingPluginFilter(String... excluded) {
        super(excluded);
    }

    /**
     * Returns the original plugin list with the explicitly excluded plugins and their derived dependents removed.
     *
     * @param original the original plugin list in load order
     * @param pluginList the plugins to remove from the original list
     * @return a filtered list that preserves the original order of the remaining plugins
     */
    @Override
    protected List<PluginMetadata> getPluginList(List<PluginMetadata> original, List<PluginMetadata> pluginList) {
        var newList = new ArrayList<>(original);
        newList.removeIf(pluginList::contains);
        return newList;
    }

    /**
     * Adds plugins that depend on the supplied plugin, so they are excluded as well.
     *
     * <p>The plugin itself is ignored during the scan, and each matching dependent is registered through
     * {@link #registerDependency(List, PluginMetadata)} so recursive traversal and deduplication are handled
     * consistently.</p>
     *
     * @param additionalList the list collecting plugins that should also be excluded
     * @param plugin the plugin whose dependents should be excluded
     */
    @Override
    protected void addPluginDependencies(List<PluginMetadata> additionalList, PluginMetadata plugin) {
        var pluginName = plugin.getName();
        getAllPlugins().stream()
                .filter(p -> !pluginName.equals(p.getName())) // looking for dependents, so don't include self
                .filter(p -> isDependentOn(p, pluginName))
                .forEach(p -> registerDependency(additionalList, p));
    }
}
