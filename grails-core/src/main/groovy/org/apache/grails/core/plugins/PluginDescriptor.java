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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.jspecify.annotations.Nullable;

import org.springframework.core.io.Resource;

import grails.plugins.metadata.PluginDefinition;

/** A classpath resource and the plugin declarations it contributed to discovery. */
public final class PluginDescriptor {

    private final @Nullable Resource resource;
    private final @Nullable PluginDefinition definition;
    private final @Nullable String pluginName;
    private final @Nullable String pluginVersion;
    private final List<String> providedPlugins;
    private final List<String> providedClasses;

    public PluginDescriptor(@Nullable Resource resource, PluginDefinition definition) {
        this.resource = resource;
        this.definition = definition;
        this.pluginName = definition.getPluginName();
        this.pluginVersion = definition.getPluginVersion();
        this.providedPlugins = definition.getProvidedPlugins();
        this.providedClasses = definition.getProvidedClasses();
    }

    public PluginDescriptor(@Nullable Resource resource, List<String> providedPlugins, List<String> providedClasses) {
        this(resource, null, null, providedPlugins, providedClasses);
    }

    /** An XML or dynamic registration can identify classes before their metadata is available. */
    PluginDescriptor(@Nullable Resource resource, @Nullable String name, @Nullable String version,
            List<String> providedPlugins, List<String> providedClasses) {
        this.resource = resource;
        this.definition = null;
        this.pluginName = name;
        this.pluginVersion = version;
        this.providedPlugins = List.copyOf(providedPlugins);
        this.providedClasses = List.copyOf(providedClasses);
    }

    public static PluginDescriptor fromProperties(@Nullable String defaultName, @Nullable Resource resource,
            Properties properties) {
        return new PluginDescriptor(resource, PluginDefinition.fromProperties(defaultName, properties));
    }

    public @Nullable PluginDefinition getDefinition() {
        return definition;
    }

    public @Nullable Resource getResource() {
        return resource;
    }

    public @Nullable String getPluginName() {
        return pluginName;
    }

    public @Nullable String getPluginVersion() {
        return pluginVersion;
    }

    public List<String> getProvidedPlugins() {
        return providedPlugins;
    }

    public List<String> getProvidedClasses() {
        return providedClasses;
    }

    public @Nullable String getPluginClassName() {
        return providedPlugins.isEmpty() ? null : providedPlugins.get(0);
    }

    public Map<String, List<String>> getProvidedArtefactsByType() {
        return definition == null ? Map.of() : definition.getProvidedArtefactsByType();
    }

    public @Nullable String getGrailsVersionRange() {
        return definition == null ? null : definition.getGrailsVersionRange();
    }

    public boolean isMetadataComplete() {
        return definition != null && definition.isMetadataComplete();
    }

    public boolean isDescriptorLess() {
        return providedPlugins.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PluginDescriptor that && Objects.equals(resource, that.resource)
                && Objects.equals(definition, that.definition) && Objects.equals(pluginName, that.pluginName)
                && Objects.equals(pluginVersion, that.pluginVersion) && providedPlugins.equals(that.providedPlugins)
                && providedClasses.equals(that.providedClasses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, definition, pluginName, pluginVersion, providedPlugins, providedClasses);
    }

    @Override
    public String toString() {
        return "PluginDescriptor[name=" + pluginName + ", resource=" + resource + ", providedPlugins=" + providedPlugins + "]";
    }
}
