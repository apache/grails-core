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
package grails.plugins;

import java.util.Collection;
import java.util.List;

import org.apache.grails.core.plugins.PluginMetadata;
import org.apache.grails.core.plugins.filters.PluginFilter;

/**
 * @deprecated Compatibility stub to allow {@link grails.plugins.PluginFilter} to be used as {@link org.apache.grails.core.plugins.filters.PluginFilter}.
 * This compatibility stub will be removed in Grails 8.0.0.
 */
@Deprecated(forRemoval = true, since = "7.1")
public class CompatibilityPluginFilter implements PluginFilter {

    private grails.plugins.PluginFilter filter;
    private List<String> pluginNames;

    public CompatibilityPluginFilter(grails.plugins.PluginFilter filter, Collection<GrailsPlugin> plugins) {
        this.filter = filter;
        this.pluginNames = plugins.stream().map(GrailsPluginInfo::getName).toList();
    }

    @Override
    public List<PluginMetadata> filterPluginList(List<PluginMetadata> original) {
        return original.stream().filter(toFilter -> pluginNames.contains(toFilter.getName())).toList();
    }
}
