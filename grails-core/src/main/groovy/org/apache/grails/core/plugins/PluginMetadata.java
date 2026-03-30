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

import java.util.Map;
import java.util.Set;

import grails.plugins.exceptions.PluginException;
import grails.util.Environment;

/**
 * Immutable metadata extracted from a Grails plugin class during discovery.
 *
 * <p>This value object captures the fields needed for plugin filtering, ordering, dependency resolution,
 * environment checks, and later bootstrap processing.</p>
 */
public final class PluginMetadata {

    private final String name;
    private final String pluginVersion;
    private final String grailsVersionRange;
    private final Class<?> pluginClass;
    private final String[] loadAfterNames;
    private final String[] loadBeforeNames;
    private final Map<String, Object> dependencies;
    private final String[] dependsOnNames;
    private final String[] evictions;
    private final String[] observedPluginNames;
    private final Map<String, Set<Object>> environments;
    private final boolean enabled;

    /**
     * Creates plugin metadata extracted from a plugin class.
     *
     * @param name the logical plugin name (for example {@code core} or {@code myPlugin})
     * @param pluginVersion the declared plugin version
     * @param grailsVersionRange the Grails version range supported by this plugin
     * @param pluginClass the plugin implementation class
     * @param loadAfterNames plugin names this plugin prefers to load after
     * @param loadBeforeNames plugin names this plugin prefers to load before
     * @param dependencies the raw declared dependency map keyed by plugin name
     * @param dependsOnNames plugin names this plugin depends on, used for dependency resolution
     * @param evictions plugin names this plugin evicts
     * @param observedPluginNames plugin names observed by this plugin
     * @param environments the environments this plugin is enabled for, or an empty map if enabled for all environments
     * @param enabled whether the plugin is enabled at all
     */
    public PluginMetadata(
            String name,
            String pluginVersion,
            String grailsVersionRange,
            Class<?> pluginClass,
            String[] loadAfterNames,
            String[] loadBeforeNames,
            Map<String, Object> dependencies,
            String[] dependsOnNames,
            String[] evictions,
            String[] observedPluginNames,
            Map<String, Set<Object>> environments,
            boolean enabled) {
        this.name = name;
        this.pluginVersion = pluginVersion;
        this.grailsVersionRange = grailsVersionRange;
        this.pluginClass = pluginClass;
        this.loadAfterNames = loadAfterNames;
        this.loadBeforeNames = loadBeforeNames;
        this.dependencies = dependencies;
        this.dependsOnNames = dependsOnNames;
        this.evictions = evictions;
        this.observedPluginNames = observedPluginNames;
        this.environments = environments;
        this.enabled = enabled;
    }

    /**
     * Returns the logical plugin name (for example {@code core} or {@code myPlugin}).
     *
     * @return the plugin name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the declared plugin version.
     *
     * @return the plugin version
     */
    public String getPluginVersion() {
        return pluginVersion;
    }

    /**
     * Returns the Grails version range supported by this plugin.
     *
     * @return the supported Grails version
     */
    public String getGrailsVersionRange() {
        return grailsVersionRange;
    }

    /**
     * Returns the plugin implementation class.
     *
     * @return the plugin class
     */
    public Class<?> getPluginClass() {
        return pluginClass;
    }

    /**
     * Returns the plugin names this plugin prefers to load after.
     *
     * @return the declared load-after plugin names
     */
    public String[] getLoadAfterNames() {
        return loadAfterNames;
    }

    /**
     * Returns the plugin names this plugin prefers to load before.
     *
     * @return the declared load-before plugin names
     */
    public String[] getLoadBeforeNames() {
        return loadBeforeNames;
    }

    /**
     * Returns the raw declared dependency map.
     *
     * @return the dependency map keyed by plugin name
     */
    public Map<String, Object> getDependencies() {
        return dependencies;
    }

    /**
     * Returns the plugin names this plugin depends on.
     *
     * @return the declared dependency plugin names
     */
    public String[] getDependsOnNames() {
        return dependsOnNames;
    }

    /**
     * Returns the plugin names this plugin evicts.
     *
     * @return the declared evicted plugin names
     */
    public String[] getEvictions() {
        return evictions;
    }

    /**
     * Returns the plugin names observed by this plugin.
     *
     * @return the declared observed plugin names
     */
    public String[] getObservedPluginNames() {
        return observedPluginNames;
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
        return enabled;
    }

    /**
     * Determines whether this plugin can be registered in the current Grails environment.
     *
     * @return {@code true} if the plugin is enabled and supports the current environment
     */
    boolean canRegisterPlugin() {
        Environment environment = Environment.getCurrent();
        return enabled && supportsEnvironment(environment);
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
        var dependentVersion = dependencies.get(name);
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
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "GrailsPluginClassMetadata[" + name + "]";
    }
}
