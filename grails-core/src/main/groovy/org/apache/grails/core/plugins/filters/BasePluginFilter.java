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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.grails.core.plugins.PluginMetadata;

/**
 * Abstract base implementation for plugin filters that start from a supplied set of plugin names,
 * resolve related plugins through dependency traversal, and then delegate the final list assembly
 * to subclasses.
 *
 * <p>The shared algorithm is:</p>
 * <ol>
 *     <li>Index the original plugin list by name.</li>
 *     <li>Collect plugins whose names were explicitly supplied.</li>
 *     <li>Discover additional plugins through subclass-defined dependency traversal.</li>
 *     <li>Let the subclass decide how the explicit and derived plugins should influence the final result.</li>
 * </ol>
 *
 * @see IncludingPluginFilter
 * @see ExcludingPluginFilter
 */
public abstract class BasePluginFilter implements PluginFilter {

    /**
     * The plugin names supplied to the filter.
     */
    private final Set<String> suppliedNames;

    /**
     * Plugins whose names match {@link #suppliedNames}.
     */
    private final List<PluginMetadata> explicitlyNamedPlugins = new ArrayList<>();

    /**
     * Plugins discovered indirectly through dependency traversal.
     */
    private final List<PluginMetadata> derivedPlugins = new ArrayList<>();

    /**
     * Lookup of plugin name to plugin metadata for the original plugin list.
     */
    protected Map<String, PluginMetadata> nameMap;

    /**
     * Tracks plugin names that have already been added while building the filtered result.
     */
    private Set<String> addedNames;

    private List<PluginMetadata> originalPlugins;

    /**
     * Creates a filter using the supplied plugin names.
     *
     * @param suppliedNames the plugin names that drive the filter result
     */
    public BasePluginFilter(Set<String> suppliedNames) {
        this.suppliedNames = suppliedNames;
    }

    /**
     * Creates a filter using the supplied plugin names.
     *
     * @param included the plugin names that drive the filter result; each value is trimmed before use
     */
    public BasePluginFilter(String[] included) {
        suppliedNames = new HashSet<>();
        for (var s : included) {
            suppliedNames.add(s.trim());
        }
    }

    /**
     * Applies this filter to the supplied plugin metadata.
     *
     * <p>This method performs the shared filtering workflow and then delegates the final list assembly to
     * {@link #getPluginList(List, List)}.</p>
     *
     * @param original the original plugin metadata to filter
     * @return the filtered plugin list
     */
    @Override
    public List<PluginMetadata> filterPluginList(List<PluginMetadata> original) {

        originalPlugins = Collections.unmodifiableList(original);
        addedNames = new HashSet<>();

        buildNameMap();
        buildExplicitlyNamedList();
        buildDerivedPluginList();

        var pluginList = new ArrayList<PluginMetadata>();
        pluginList.addAll(explicitlyNamedPlugins);
        pluginList.addAll(derivedPlugins);

        return getPluginList(originalPlugins, pluginList);
    }

    /**
     * Adds any plugins related to the supplied plugin through the dependency rules defined by the subclass.
     * Implementations are expected to call {@link #registerDependency(List, PluginMetadata)} to avoid duplicates
     * and to continue recursive traversal.
     *
     * @param additionalList the list collecting plugins discovered indirectly
     * @param plugin the plugin whose related plugins should be considered
     */
    protected abstract void addPluginDependencies(List<PluginMetadata> additionalList, PluginMetadata plugin);

    /**
     * Builds the final filtered list from the original plugins and the plugins collected by the shared algorithm.
     *
     * @param original the full original plugin list supplied to {@link #filterPluginList(List)}
     * @param pluginList the explicit and dependency-derived plugins collected so far
     * @return the filtered plugin list to expose to callers
     */
    protected abstract List<PluginMetadata> getPluginList(List<PluginMetadata> original, List<PluginMetadata> pluginList);

    /**
     * Determines whether the supplied plugin declares a dependency on the named plugin.
     *
     * @param plugin the plugin to compare
     * @param pluginName the name to compare against
     * @return {@code true} if {@code plugin} depends on {@code pluginName}
     */
    protected boolean isDependentOn(PluginMetadata plugin, String pluginName) {
        for (var dependencyName : plugin.getDependsOnNames()) {
            if (pluginName.equals(dependencyName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers a derived plugin if it has not already been added and continues dependency traversal for it.
     *
     * @param additionalList the list collecting derived plugins
     * @param plugin the plugin to register
     */
    protected void registerDependency(List<PluginMetadata> additionalList, PluginMetadata plugin) {
        if (!addedNames.contains(plugin.getName())) {
            addedNames.add(plugin.getName());
            additionalList.add(plugin);
            addPluginDependencies(additionalList, plugin);
        }
    }

    /**
     * Returns all plugins from the original input as an unmodifiable collection.
     *
     * @return all original plugins keyed in {@link #nameMap}
     */
    protected Collection<PluginMetadata> getAllPlugins() {
        return Collections.unmodifiableCollection(nameMap.values());
    }

    /**
     * Looks up a plugin from the original input by name.
     *
     * @param name the plugin name
     * @return the matching plugin metadata, or {@code null} if none exists
     */
    protected PluginMetadata getNamedPlugin(String name) {
        return nameMap.get(name);
    }

    /**
     * Returns the plugin names supplied to this filter.
     *
     * @return the supplied plugin names
     */
    protected Set<String> getSuppliedNames() {
        return suppliedNames;
    }

    /**
     * Populates {@link #derivedPlugins} with plugins discovered from the explicitly named plugins through
     * subclass-defined dependency traversal.
     */
    private void buildDerivedPluginList() {
        // find their dependencies
        for (var plugin : explicitlyNamedPlugins) {
            // recursively add in plugin dependencies
            addPluginDependencies(derivedPlugins, plugin);
        }
    }

    /**
     * Populates {@link #explicitlyNamedPlugins} with any original plugins whose names were explicitly supplied.
     */
    private void buildExplicitlyNamedList() {
        originalPlugins.stream()
                .filter(p -> suppliedNames.contains(p.getName()))
                .forEach(p -> {
                    explicitlyNamedPlugins.add(p);
                    addedNames.add(p.getName());
                });
    }

    /**
     * Builds the {@link #nameMap} lookup from the original plugin list.
     */
    private void buildNameMap() {
        nameMap = new HashMap<>();
        for (var plugin : originalPlugins) {
            nameMap.put(plugin.getName(), plugin);
        }
    }
}
