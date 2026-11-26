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

/**
 * A plugin's published {@link PluginDefinition} together with the resource it was read from, normally
 * {@code META-INF/grails/plugins/<name>/plugin.properties} in the plugin's jar or class directory.
 *
 * @since 9.0
 */
public final class PluginDescriptor {

    private final @Nullable Resource resource;
    private final PluginDefinition definition;

    /**
     * @param resource the resource the definition was read from, or {@code null} for a plugin class
     *        registered directly, whose definition comes from its annotation
     * @param definition the plugin definition
     */
    public PluginDescriptor(@Nullable Resource resource, PluginDefinition definition) {
        this.resource = resource;
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    /**
     * Reads a descriptor from the properties of a {@code plugin.properties} file.
     *
     * @param importedName the name {@code plugins.imports} lists the plugin under
     * @param resource the file
     * @param properties its contents
     * @return the descriptor
     */
    public static PluginDescriptor fromProperties(@Nullable String importedName, @Nullable Resource resource,
            Properties properties) {
        return new PluginDescriptor(resource, PluginDefinition.fromProperties(importedName, properties));
    }

    public PluginDefinition getDefinition() {
        return definition;
    }

    public @Nullable Resource getResource() {
        return resource;
    }

    public String getPluginName() {
        return definition.getPluginName();
    }

    public @Nullable String getPluginVersion() {
        return definition.getPluginVersion();
    }

    /** @return the name of the plugin's {@code grails.plugins.Plugin} subclass */
    public String getPluginClassName() {
        return definition.getPluginClass();
    }

    /** @return the artefact classes the plugin contributes */
    public List<String> getProvidedClasses() {
        return definition.getProvidedClasses();
    }

    /** @return the artefact classes compiled with the plugin, by artefact type */
    public Map<String, List<String>> getArtefactsByType() {
        return definition.getArtefactsByType();
    }

    public @Nullable String getGrailsVersionRange() {
        return definition.getGrailsVersionRange();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PluginDescriptor that && Objects.equals(resource, that.resource) &&
                definition.equals(that.definition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, definition);
    }

    @Override
    public String toString() {
        return "PluginDescriptor[name=" + getPluginName() + ", class=" + getPluginClassName() + ", resource=" + resource + "]";
    }
}
