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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.jspecify.annotations.Nullable;

import org.springframework.core.io.Resource;

import grails.plugins.metadata.PluginDefinition;

/**
 * Represents a parsed Grails plugin descriptor discovered on the classpath from
 * {@code META-INF/grails/plugins/<name>/plugin.properties}.
 *
 * <p>A descriptor captures the source {@link Resource}, the logical plugin name, an optional plugin
 * implementation class name (omitted for descriptor-less starter plugins), any statically declared
 * metadata, and the artefact classes contributed by the plugin grouped by artefact type.</p>
 */
public class PluginDescriptor {

    private final @Nullable Resource resource;
    private final @Nullable String pluginName;
    private final @Nullable String pluginVersion;
    private final @Nullable String grailsVersionRange;
    private final List<String> providedPlugins;
    private final List<String> providedClasses;
    private final Map<String, List<String>> providedArtefactsByType;
    private final List<String> loadAfter;
    private final List<String> loadBefore;
    private final Map<String, String> dependsOn;
    private final List<String> evict;
    private final List<String> observe;
    private final List<String> environmentsIncludes;
    private final List<String> environmentsExcludes;
    private final boolean enabled;
    private final boolean metadataComplete;

    /**
     * Creates a minimal plugin descriptor with the given resource, declared plugin classes, and provided
     * artefact classes.
     *
     * @param resource the source resource from which the descriptor was read
     * @param providedPlugins the fully qualified plugin implementation class names declared by the descriptor
     * @param providedClasses the fully qualified provided class names declared by the descriptor
     */
    public PluginDescriptor(
            @Nullable Resource resource,
            List<String> providedPlugins,
            List<String> providedClasses) {
        this(
                resource,
                null,
                null,
                null,
                providedPlugins,
                providedClasses,
                Map.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                false
        );
    }

    /**
     * Creates a fully populated plugin descriptor.
     */
    public PluginDescriptor(
            @Nullable Resource resource,
            @Nullable String pluginName,
            @Nullable String pluginVersion,
            @Nullable String grailsVersionRange,
            List<String> providedPlugins,
            List<String> providedClasses,
            Map<String, List<String>> providedArtefactsByType,
            List<String> loadAfter,
            List<String> loadBefore,
            Map<String, String> dependsOn,
            List<String> evict,
            List<String> observe,
            List<String> environmentsIncludes,
            List<String> environmentsExcludes,
            boolean enabled,
            boolean metadataComplete) {
        this.resource = resource;
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
        this.grailsVersionRange = grailsVersionRange;
        this.providedPlugins = providedPlugins != null ? List.copyOf(providedPlugins) : List.of();
        this.providedClasses = providedClasses != null ? List.copyOf(providedClasses) : List.of();
        this.providedArtefactsByType = copyArtefactsByType(providedArtefactsByType);
        this.loadAfter = loadAfter != null ? List.copyOf(loadAfter) : List.of();
        this.loadBefore = loadBefore != null ? List.copyOf(loadBefore) : List.of();
        this.dependsOn = dependsOn != null ? Collections.unmodifiableMap(new LinkedHashMap<>(dependsOn)) : Map.of();
        this.evict = evict != null ? List.copyOf(evict) : List.of();
        this.observe = observe != null ? List.copyOf(observe) : List.of();
        this.environmentsIncludes = environmentsIncludes != null ? List.copyOf(environmentsIncludes) : List.of();
        this.environmentsExcludes = environmentsExcludes != null ? List.copyOf(environmentsExcludes) : List.of();
        this.enabled = enabled;
        this.metadataComplete = metadataComplete;
    }

    /**
     * Parses a {@code META-INF/grails/plugins/<name>/plugin.properties} payload into a {@link PluginDescriptor}.
     *
     * @param defaultPluginName fallback logical plugin name when {@code name} is omitted from the properties
     * @param resource the descriptor resource
     * @param properties the loaded properties
     * @return the parsed plugin descriptor
     * @throws IllegalArgumentException if the descriptor specifies an unsupported {@code format.version}
     */
    public static PluginDescriptor fromProperties(
            @Nullable String defaultPluginName,
            @Nullable Resource resource,
            Properties properties) {
        PluginDefinition definition = PluginDefinition.fromProperties(defaultPluginName, properties);
        return new PluginDescriptor(resource, definition.getPluginName(), definition.getPluginVersion(),
                definition.getGrailsVersionRange(), definition.getProvidedPlugins(), definition.getProvidedClasses(),
                definition.getProvidedArtefactsByType(), definition.getLoadAfter(), definition.getLoadBefore(),
                definition.getDependsOn(), definition.getEvict(), definition.getObserve(), definition.getEnvironmentsIncludes(),
                definition.getEnvironmentsExcludes(), definition.isEnabled(), definition.isMetadataComplete());
    }

    /**
     * Returns the source resource for this descriptor.
     *
     * @return the descriptor resource
     */
    public @Nullable Resource getResource() {
        return resource;
    }

    /**
     * Returns the logical plugin name declared in this descriptor, if known.
     *
     * @return the logical plugin name, or {@code null}
     */
    public @Nullable String getPluginName() {
        return pluginName;
    }

    /**
     * Returns the plugin version declared in this descriptor, if known.
     *
     * @return the plugin version, or {@code null}
     */
    public @Nullable String getPluginVersion() {
        return pluginVersion;
    }

    /**
     * Returns the supported Grails version range declared in this descriptor, if any.
     *
     * @return the Grails version range, or {@code null}
     */
    public @Nullable String getGrailsVersionRange() {
        return grailsVersionRange;
    }

    /**
     * Returns the primary plugin implementation class name declared by this descriptor,
     * or {@code null} for a descriptor-less starter plugin.
     *
     * @return the plugin class name, or {@code null}
     */
    public @Nullable String getPluginClassName() {
        return providedPlugins.isEmpty() ? null : providedPlugins.get(0);
    }

    /**
     * Returns the plugin implementation classes declared by this descriptor.
     * Empty for descriptor-less starter plugins.
     *
     * @return the declared plugin implementation class names
     */
    public List<String> getProvidedPlugins() {
        return providedPlugins;
    }

    /**
     * Returns the non-plugin artefact classes contributed by this descriptor.
     *
     * @return the declared provided class names
     */
    public List<String> getProvidedClasses() {
        return providedClasses;
    }

    /**
     * Returns the contributed artefact classes grouped by their {@code @Artefact} type name.
     *
     * @return an unmodifiable map of artefact type to class names
     */
    public Map<String, List<String>> getProvidedArtefactsByType() {
        return providedArtefactsByType;
    }

    public List<String> getLoadAfter() {
        return loadAfter;
    }

    public List<String> getLoadBefore() {
        return loadBefore;
    }

    public Map<String, String> getDependsOn() {
        return dependsOn;
    }

    public List<String> getEvict() {
        return evict;
    }

    public List<String> getObserve() {
        return observe;
    }

    public List<String> getEnvironmentsIncludes() {
        return environmentsIncludes;
    }

    public List<String> getEnvironmentsExcludes() {
        return environmentsExcludes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns {@code true} when this descriptor carries the complete metadata for the plugin
     * (true for descriptor-less starter plugins and {@code @GrailsPluginDescriptor}-annotated classes),
     * meaning runtime discovery does not need to instantiate a Groovy plugin class to inspect its properties.
     *
     * @return {@code true} if the descriptor metadata is authoritative
     */
    public boolean isMetadataComplete() {
        return metadataComplete;
    }

    /**
     * Returns {@code true} when this descriptor represents a descriptor-less starter plugin with no
     * {@code *GrailsPlugin} or {@code @GrailsPluginDescriptor} class.
     *
     * @return {@code true} if no plugin class is declared
     */
    public boolean isDescriptorLess() {
        return providedPlugins.isEmpty();
    }

    private static Map<String, List<String>> copyArtefactsByType(@Nullable Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() != null ? List.copyOf(entry.getValue()) : List.of());
        }
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PluginDescriptor that)) {
            return false;
        }
        return enabled == that.enabled &&
                metadataComplete == that.metadataComplete &&
                Objects.equals(resource, that.resource) &&
                Objects.equals(pluginName, that.pluginName) &&
                Objects.equals(pluginVersion, that.pluginVersion) &&
                Objects.equals(grailsVersionRange, that.grailsVersionRange) &&
                Objects.equals(providedPlugins, that.providedPlugins) &&
                Objects.equals(providedClasses, that.providedClasses) &&
                Objects.equals(providedArtefactsByType, that.providedArtefactsByType) &&
                Objects.equals(loadAfter, that.loadAfter) &&
                Objects.equals(loadBefore, that.loadBefore) &&
                Objects.equals(dependsOn, that.dependsOn) &&
                Objects.equals(evict, that.evict) &&
                Objects.equals(observe, that.observe) &&
                Objects.equals(environmentsIncludes, that.environmentsIncludes) &&
                Objects.equals(environmentsExcludes, that.environmentsExcludes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, pluginName, pluginVersion, grailsVersionRange,
                providedPlugins, providedClasses, providedArtefactsByType,
                loadAfter, loadBefore, dependsOn, evict, observe,
                environmentsIncludes, environmentsExcludes, enabled, metadataComplete);
    }

    @Override
    public String toString() {
        return "PluginDescriptor[" +
                "name=" + pluginName +
                ", resource=" + resource +
                ", providedPlugins=" + providedPlugins +
                ", providedClasses=" + providedClasses +
                ']';
    }
}
