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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.core.io.Resource;

/**
 * Represents a parsed Grails plugin descriptor discovered on the classpath from
 * {@code META-INF/grails/plugins/<name>/plugin.properties}.
 *
 * <p>A descriptor captures the source {@link Resource}, the logical plugin name, an optional plugin
 * implementation class name (omitted for descriptor-less starter plugins), any statically declared
 * metadata, and the artefact classes contributed by the plugin grouped by artefact type.</p>
 */
public class PluginDescriptor {

    public static final String FORMAT_VERSION_KEY = "format.version";
    public static final String SUPPORTED_FORMAT_VERSION = "1";
    public static final String NAME_KEY = "name";
    public static final String VERSION_KEY = "version";
    public static final String CLASS_KEY = "class";
    public static final String GRAILS_VERSION_KEY = "grailsVersion";
    public static final String LOAD_AFTER_KEY = "loadAfter";
    public static final String LOAD_BEFORE_KEY = "loadBefore";
    public static final String DEPENDS_ON_PREFIX = "dependsOn.";
    public static final String EVICT_KEY = "evict";
    public static final String OBSERVE_KEY = "observe";
    public static final String ENABLED_KEY = "enabled";
    public static final String ENVIRONMENTS_INCLUDES_KEY = "environments.includes";
    public static final String ENVIRONMENTS_EXCLUDES_KEY = "environments.excludes";
    public static final String ARTEFACTS_KEY = "artefacts";
    public static final String ARTEFACTS_PREFIX = "artefacts.";
    public static final String METADATA_COMPLETE_KEY = "metadata.complete";

    private final Resource resource;
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
        String formatVersion = trimToNull(properties.getProperty(FORMAT_VERSION_KEY));
        if (formatVersion != null && !SUPPORTED_FORMAT_VERSION.equals(formatVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported Grails plugin descriptor format.version [" + formatVersion +
                            "] in " + (resource != null ? resource.getDescription() : defaultPluginName));
        }

        String rawName = trimToNull(properties.getProperty(NAME_KEY));
        String pluginName = rawName != null ? PluginUtils.normalizePluginName(rawName) :
                (defaultPluginName != null ? PluginUtils.normalizePluginName(defaultPluginName) : null);
        String pluginVersion = trimToNull(properties.getProperty(VERSION_KEY));
        String grailsVersion = trimToNull(properties.getProperty(GRAILS_VERSION_KEY));

        List<String> pluginClasses = splitList(properties.getProperty(CLASS_KEY));

        Map<String, List<String>> artefactsByType = new LinkedHashMap<>();
        Set<String> allArtefacts = new LinkedHashSet<>();

        List<String> propertyNames = new ArrayList<>(properties.stringPropertyNames());
        Collections.sort(propertyNames);

        Map<String, String> dependsOn = new LinkedHashMap<>();
        for (String key : propertyNames) {
            if (key.startsWith(DEPENDS_ON_PREFIX)) {
                String depName = key.substring(DEPENDS_ON_PREFIX.length()).trim();
                String depVersion = properties.getProperty(key);
                if (!depName.isEmpty() && depVersion != null) {
                    dependsOn.put(PluginUtils.normalizePluginName(depName), depVersion.trim());
                }
            } else if (key.startsWith(ARTEFACTS_PREFIX)) {
                String artefactType = key.substring(ARTEFACTS_PREFIX.length()).trim();
                List<String> typeClasses = splitList(properties.getProperty(key));
                if (!artefactType.isEmpty() && !typeClasses.isEmpty()) {
                    artefactsByType.put(artefactType, typeClasses);
                    allArtefacts.addAll(typeClasses);
                }
            }
        }
        allArtefacts.addAll(splitList(properties.getProperty(ARTEFACTS_KEY)));

        List<String> loadAfter = splitList(properties.getProperty(LOAD_AFTER_KEY));
        List<String> loadBefore = splitList(properties.getProperty(LOAD_BEFORE_KEY));
        List<String> evict = splitList(properties.getProperty(EVICT_KEY));
        List<String> observe = splitList(properties.getProperty(OBSERVE_KEY));
        List<String> envIncludes = splitList(properties.getProperty(ENVIRONMENTS_INCLUDES_KEY));
        List<String> envExcludes = splitList(properties.getProperty(ENVIRONMENTS_EXCLUDES_KEY));

        String enabledStr = trimToNull(properties.getProperty(ENABLED_KEY));
        boolean enabled = enabledStr == null || Boolean.parseBoolean(enabledStr);

        String completeStr = trimToNull(properties.getProperty(METADATA_COMPLETE_KEY));
        boolean metadataComplete = completeStr != null ? Boolean.parseBoolean(completeStr) : pluginClasses.isEmpty();

        return new PluginDescriptor(
                resource,
                pluginName,
                pluginVersion,
                grailsVersion,
                pluginClasses,
                new ArrayList<>(allArtefacts),
                artefactsByType,
                loadAfter,
                loadBefore,
                dependsOn,
                evict,
                observe,
                envIncludes,
                envExcludes,
                enabled,
                metadataComplete
        );
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

    private static List<String> splitList(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split(",");
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PluginDescriptor that)) {
            return false;
        }
        return Objects.equals(resource, that.resource) &&
                Objects.equals(pluginName, that.pluginName) &&
                Objects.equals(providedPlugins, that.providedPlugins) &&
                Objects.equals(providedClasses, that.providedClasses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, pluginName, providedPlugins, providedClasses);
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
