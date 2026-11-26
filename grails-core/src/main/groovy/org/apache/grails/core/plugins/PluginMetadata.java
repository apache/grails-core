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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import grails.plugins.exceptions.PluginException;
import grails.plugins.metadata.PluginDefinition;
import grails.util.Environment;

/**
 * Immutable metadata extracted from a Grails plugin class or descriptor during discovery.
 *
 * <p>This value object captures the fields needed for plugin filtering, ordering, dependency resolution,
 * environment checks, and later bootstrap processing.</p>
 */
public final class PluginMetadata {

    private final PluginDefinition definition;
    private final @Nullable Class<?> pluginClass;
    private final Map<String, Set<Object>> environments;

    public PluginMetadata(PluginDefinition definition, @Nullable Class<?> pluginClass) {
        if (definition.getPluginVersion() == null || definition.getPluginVersion().isBlank()) {
            throw new PluginException("Plugin [" + definition.getPluginName() + "] must specify a version!");
        }
        this.definition = definition;
        this.pluginClass = pluginClass;
        this.environments = PluginUtils.buildEnvironmentsMap(definition.getEnvironmentsIncludes(), definition.getEnvironmentsExcludes());
    }

    public PluginDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns the logical plugin name (for example {@code core} or {@code myPlugin}).
     *
     * @return the plugin name
     */
    public String getName() {
        return definition.getPluginName();
    }

    /**
     * Returns the declared plugin version.
     *
     * @return the plugin version
     */
    public String getPluginVersion() {
        return definition.getPluginVersion();
    }

    /**
     * Returns the Grails version range supported by this plugin.
     *
     * @return the supported Grails version
     */
    public @Nullable String getGrailsVersionRange() {
        return definition.getGrailsVersionRange();
    }

    /**
     * Returns the plugin implementation class.
     *
     * @return the plugin class, or {@code null} for descriptor-less starter plugins
     */
    public @Nullable Class<?> getPluginClass() {
        return pluginClass;
    }

    /**
     * Returns the plugin names this plugin prefers to load after.
     *
     * @return the declared load-after plugin names
     */
    public String[] getLoadAfterNames() {
        return definition.getLoadAfter().toArray(String[]::new);
    }

    /**
     * Returns the plugin names this plugin prefers to load before.
     *
     * @return the declared load-before plugin names
     */
    public String[] getLoadBeforeNames() {
        return definition.getLoadBefore().toArray(String[]::new);
    }

    /**
     * Returns the raw declared dependency map.
     *
     * @return the dependency map keyed by plugin name
     */
    public Map<String, Object> getDependencies() {
        return Collections.unmodifiableMap(definition.getDependsOn());
    }

    /**
     * Returns the plugin names this plugin depends on.
     *
     * @return the declared dependency plugin names
     */
    public String[] getDependsOnNames() {
        return definition.getDependsOn().keySet().toArray(String[]::new);
    }

    /**
     * Returns the plugin names this plugin evicts.
     *
     * @return the declared evicted plugin names
     */
    public String[] getEvictions() {
        return definition.getEvict().toArray(String[]::new);
    }

    /**
     * Returns the plugin names observed by this plugin.
     *
     * @return the declared observed plugin names
     */
    public String[] getObservedPluginNames() {
        return definition.getObserve().toArray(String[]::new);
    }

    /**
     * Returns the environments in which this plugin is enabled.
     *
     * @return the configured environment include/exclude map
     */
    public Map<String, Set<Object>> getEnvironments() {
        return environments;
    }

    /**
     * Returns whether the plugin is enabled or disabled.
     *
     * @return {@code true} if the plugin is enabled
     */
    public boolean getEnabled() {
        return definition.isEnabled();
    }

    /**
     * Determines whether this plugin can be registered in the current Grails environment.
     *
     * @return {@code true} if the plugin is enabled and supports the current environment
     */
    boolean canRegisterPlugin() {
        Environment environment = Environment.getCurrent();
        return getEnabled() && supportsEnvironment(environment);
    }

    /**
     * Determines whether this plugin supports the supplied environment.
     *
     * @param environment the environment to test
     * @return {@code true} if the plugin is enabled for the supplied environment
     */
    boolean supportsEnvironment(Environment environment) {
        return PluginUtils.supportsValueInIncludeExcludeMap(environments, environment.getName());
    }

    /**
     * Returns the declared version for the named plugin dependency.
     *
     * @param name the dependency plugin name
     * @return the declared dependency version
     * @throws PluginException if the dependency is referenced without a version
     */
    public String getDependentVersion(String name) {
        var dependentVersion = definition.getDependsOn().get(name);
        if (dependentVersion == null) {
            throw new PluginException("Plugin [" + getName() + "] referenced dependency [" + name + "] with no version!");
        }
        return dependentVersion.toString();
    }

    /**
     * Compares this metadata with another metadata instance using the logical plugin name.
     *
     * @param o the other object to compare
     * @return {@code true} if both metadata instances have the same plugin name
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PluginMetadata other)) return false;
        return getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return "GrailsPluginClassMetadata[" + getName() + "]";
    }
}
