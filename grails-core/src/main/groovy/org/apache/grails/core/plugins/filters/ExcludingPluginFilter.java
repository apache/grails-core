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

import org.apache.grails.core.plugins.PluginInfo;

/**
 * A {@link PluginFilter} implementation that removes the explicitly named plugins.
 *
 * <p>Only the named plugins are removed. A plugin that depends on one of them is kept, and discovery then
 * stops with an exception that names it, so excluding a plugin never silently takes the plugins that need it
 * along: they are excluded by naming them too.</p>
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
     * Returns the original plugin list with the explicitly excluded plugins removed.
     *
     * @param original the original plugin list in load order
     * @param pluginList the explicitly excluded plugins
     * @return a filtered list that preserves the original order of the remaining plugins
     */
    @Override
    protected List<PluginInfo> getPluginList(List<PluginInfo> original, List<PluginInfo> pluginList) {
        var newList = new ArrayList<>(original);
        newList.removeIf(pluginList::contains);
        return newList;
    }

    /**
     * Adds nothing: excluding a plugin does not exclude the plugins that depend on it.
     *
     * @param additionalList the list collecting plugins that should also be excluded
     * @param plugin the excluded plugin
     */
    @Override
    protected void addPluginDependencies(List<PluginInfo> additionalList, PluginInfo plugin) {
        // a dependent is kept, so discovery reports it as depending on an excluded plugin
    }
}
