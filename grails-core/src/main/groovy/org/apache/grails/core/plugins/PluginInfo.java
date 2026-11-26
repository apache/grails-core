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
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.core.io.Resource;

import grails.plugins.Plugin;
import grails.plugins.exceptions.PluginException;
import grails.plugins.metadata.PluginDefinition;
import grails.util.Environment;

/**
 * A discovered Grails plugin: its {@link PluginDefinition}, its {@link Plugin} class, and the resources it was
 * found with. Discovery filters and orders plugins by these records, without creating them.
 *
 * <p>A plugin found on the class path has the {@code META-INF/grails/plugins/<name>/plugin.properties} it was
 * read from as its descriptor resource; a plugin class registered directly has none, and its definition comes
 * from its {@link grails.plugins.metadata.GrailsPluginDescriptor}.</p>
 *
 * @since 9.0
 */
public final class PluginInfo {

    private final PluginDefinition definition;
    private final Class<? extends Plugin> pluginClass;
    private final @Nullable Resource descriptorResource;
    private final @Nullable Resource configResource;
    private final Map<String, Set<Object>> environments;

    /**
     * @param definition the plugin's definition
     * @param pluginClass the class the definition names
     * @param descriptorResource the {@code plugin.properties} the definition was read from, or {@code null} for a
     *        plugin class registered directly
     * @param configResource the plugin's {@code plugin.yml} or {@code plugin.groovy}, or {@code null}
     * @throws PluginException if the class is not the {@code grails.plugins.Plugin} the definition names
     */
    public PluginInfo(PluginDefinition definition, Class<?> pluginClass, @Nullable Resource descriptorResource,
            @Nullable Resource configResource) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(pluginClass, "pluginClass");
        if (!pluginClass.getName().equals(definition.getPluginClass()) || !Plugin.class.isAssignableFrom(pluginClass)) {
            throw new PluginException("Grails plugin [" + definition.getPluginName() + "] names plugin class [" +
                    definition.getPluginClass() + "], but [" + pluginClass.getName() + "] is not that grails.plugins.Plugin");
        }
        this.definition = definition;
        this.pluginClass = pluginClass.asSubclass(Plugin.class);
        this.descriptorResource = descriptorResource;
        this.configResource = configResource;
        this.environments = PluginUtils.buildEnvironmentsMap(definition.getEnvironmentsIncludes(), definition.getEnvironmentsExcludes());
    }

    /** @return the plugin's definition */
    public PluginDefinition getDefinition() {
        return definition;
    }

    /** @return the plugin's {@link Plugin} subclass */
    public Class<? extends Plugin> getPluginClass() {
        return pluginClass;
    }

    /**
     * @return the {@code plugin.properties} the definition was read from, or {@code null} for a plugin class
     *         registered directly
     */
    public @Nullable Resource getDescriptorResource() {
        return descriptorResource;
    }

    /** @return the plugin's {@code plugin.yml} or {@code plugin.groovy}, or {@code null} if it has none */
    public @Nullable Resource getConfigResource() {
        return configResource;
    }

    /** @return the logical plugin name, for example {@code core} or {@code springSecurityCore} */
    public String getName() {
        return definition.getPluginName();
    }

    /** @return the plugin version, or {@code null} for a plugin compiled outside a build that sets one */
    public @Nullable String getPluginVersion() {
        return definition.getPluginVersion();
    }

    /** @return the Grails versions the plugin supports, or {@code null} for every version */
    public @Nullable String getGrailsVersionRange() {
        return definition.getGrailsVersionRange();
    }

    /** @return the names of the plugins this plugin loads after, when they are present */
    public String[] getLoadAfterNames() {
        return definition.getLoadAfter().toArray(String[]::new);
    }

    /** @return the names of the plugins this plugin loads before, when they are present */
    public String[] getLoadBeforeNames() {
        return definition.getLoadBefore().toArray(String[]::new);
    }

    /** @return the names of the plugins this plugin requires */
    public String[] getDependsOnNames() {
        return definition.getDependsOn().keySet().toArray(String[]::new);
    }

    /** @return the names of the plugins this plugin evicts */
    public String[] getEvictions() {
        return definition.getEvict().toArray(String[]::new);
    }

    /** @return the names of the plugins whose changes this plugin observes, {@code *} for every plugin */
    public String[] getObservedPluginNames() {
        return definition.getObserve().toArray(String[]::new);
    }

    /**
     * @param name the name of a plugin this plugin requires
     * @return the version range it requires, {@code *} for any version
     * @throws PluginException if this plugin does not require the named plugin
     */
    public String getDependentVersion(String name) {
        String dependentVersion = definition.getDependsOn().get(name);
        if (dependentVersion == null) {
            throw new PluginException("Plugin [" + getName() + "] referenced dependency [" + name + "] with no version!");
        }
        return dependentVersion;
    }

    /** @return the environments the plugin is limited to or excluded from, by {@code includes} and {@code excludes} */
    public Map<String, Set<Object>> getEnvironments() {
        return environments;
    }

    /**
     * @param environment an environment
     * @return whether the plugin runs in that environment
     */
    public boolean supportsEnvironment(Environment environment) {
        return PluginUtils.supportsValueInIncludeExcludeMap(environments, environment.getName());
    }

    /**
     * Reports, as a warning, when the plugin does not declare support for a Grails version; the caller decides what
     * to do with an incompatible plugin.
     *
     * @param grailsVersion the application's Grails version
     * @return whether the plugin supports it
     */
    public boolean isGrailsVersionCompatible(@Nullable String grailsVersion) {
        return PluginUtils.isPluginVersionCompatible(getPluginVersion(), getGrailsVersionRange(), grailsVersion, getName());
    }

    /** Plugins are identified by name: discovery keeps one plugin of each name. */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PluginInfo that && getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return "PluginInfo[name=" + getName() + ", class=" + pluginClass.getName() +
                (descriptorResource != null ? ", descriptor=" + descriptorResource : "") + "]";
    }
}
