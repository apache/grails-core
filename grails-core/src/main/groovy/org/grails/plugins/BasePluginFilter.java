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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grails.plugins.GrailsPlugin;
import grails.plugins.PluginFilter;

/**
 * Base functionality shared by <code>IncludingPluginFilter</code> and
 * <code>ExcludingPluginFilter</code>.
 *
 * @author Phil Zoio
 */
public abstract class BasePluginFilter implements PluginFilter {

    /**
     * The supplied included plugin names (a String).
     */
    private final Set<String> suppliedNames;

    /**
     * Plugins corresponding with the supplied names.
     */
    private final List<GrailsPlugin> explicitlyNamedPlugins = new ArrayList<GrailsPlugin>();

    /**
     * Plugins derivied through a dependency relationship.
     */
    private final List<GrailsPlugin> derivedPlugins = new ArrayList<GrailsPlugin>();

    /**
     * Holds a name to GrailsPlugin map (String, Plugin).
     */
    protected Map<String, GrailsPlugin> nameMap;

    /**
     * Temporary field holding list of plugin names added to the filtered List
     * to return (String).
     */
    private Set<String> addedNames;

    private List<GrailsPlugin> originalPlugins;

    public BasePluginFilter(Set<String> suppliedNames) {
        this.suppliedNames = suppliedNames;
    }

    public BasePluginFilter(String[] included) {
        suppliedNames = new HashSet<String>();
        for (int i = 0; i < included.length; i++) {
            suppliedNames.add(included[i].trim());
        }
    }

    /**
     * Defines operation for adding dependencies for a plugin to the list
     */
    @SuppressWarnings("rawtypes")
    protected abstract void addPluginDependencies(List additionalList, GrailsPlugin plugin);

    /**
     * Defines an operation getting the final list to return from the original
     * and derived lists
     */
    @SuppressWarnings("rawtypes")
    protected abstract List<GrailsPlugin> getPluginList(List original, List pluginList);

    /**
     * Template method shared by subclasses of <code>BasePluginFilter</code>.
     */
    public List<GrailsPlugin>  filterPluginList(List<GrailsPlugin> original) {

        originalPlugins = Collections.unmodifiableList(original);
        addedNames = new HashSet<String>();

        buildNameMap();
        buildExplicitlyNamedList();
        buildDerivedPluginList();

        List<GrailsPlugin> pluginList = new ArrayList<GrailsPlugin>();
        pluginList.addAll(explicitlyNamedPlugins);
        pluginList.addAll(derivedPlugins);

        return getPluginList(originalPlugins, pluginList);
    }

    /**
     * Builds list of <code>GrailsPlugins</code> which are derived from the
     * <code>explicitlyNamedPlugins</code> through a dependency relationship
     */
    private void buildDerivedPluginList() {

        // find their dependencies
        for (int i = 0; i < explicitlyNamedPlugins.size(); i++) {
            GrailsPlugin plugin = explicitlyNamedPlugins.get(i);

            // recursively add in plugin dependencies
            addPluginDependencies(derivedPlugins, plugin);
        }
    }

    /**
     * Checks whether a plugin is dependent on another plugin with the specified
     * name
     *
     * @param plugin
     *            the plugin to compare
     * @param pluginName
     *            the name to compare against
     * @return true if <code>plugin</code> depends on <code>pluginName</code>
     */
    protected boolean isDependentOn(GrailsPlugin plugin, String pluginName) {

        // check if toCompare depends on the current plugin
        String[] dependencyNames = plugin.getDependencyNames();
        for (int i = 0; i < dependencyNames.length; i++) {

            final String dependencyName = dependencyNames[i];
            if (pluginName.equals(dependencyName)) {

                return true;

                // we've establish that p does depend on plugin, so we can
                // break from this loop
            }
        }
        return false;
    }

    /**
     * Returns the sublist of the supplied set who are explicitly named, either
     * as included or excluded plugins
     *
     * @return a sublist containing the elements of the original list
     *         corresponding with the explicitlyNamed items as passed into the
     *         constructor
     */
    private void buildExplicitlyNamedList() {

        // each plugin must either be in included set or must be a dependent of
        // included set

        for (GrailsPlugin plugin : originalPlugins) {
        // find explicitly included plugins
            String name = plugin.getName();
            if (suppliedNames.contains(name)) {
                explicitlyNamedPlugins.add(plugin);
                addedNames.add(name);
            }
        }
    }

    /**
     * Builds a name to plugin map from the original list of plugins supplied
     *
     */
    private void buildNameMap() {
        nameMap = new HashMap<String, GrailsPlugin>();
        for (GrailsPlugin plugin : originalPlugins) {
            nameMap.put(plugin.getName(), plugin);
        }
    }

    /**
     * Adds a plugin to the additional if this hasn't happened already
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void registerDependency(List additionalList, GrailsPlugin plugin) {
        if (!addedNames.contains(plugin.getName())) {
            addedNames.add(plugin.getName());
            additionalList.add(plugin);
            addPluginDependencies(additionalList, plugin);
        }
    }

    @SuppressWarnings("rawtypes")
    protected Collection getAllPlugins() {
        return Collections.unmodifiableCollection(nameMap.values());
    }

    protected GrailsPlugin getNamedPlugin(String name) {
        return nameMap.get(name);
    }

    protected Set<String> getSuppliedNames() {
        return suppliedNames;
    }
}
