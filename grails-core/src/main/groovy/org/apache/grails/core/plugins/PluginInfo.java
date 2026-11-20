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

import org.springframework.core.io.Resource;

/**
 * Represents a discovered Grails plugin together with the metadata and resources needed during bootstrap.
 *
 * <p>A {@code PluginInfo} combines the parsed {@link PluginDescriptor}, the extracted {@link PluginMetadata}, an
 * optional plugin configuration resource, and whether the plugin originated from dynamic plugin configuration rather
 * than classpath descriptor discovery.</p>
 */
public final class PluginInfo {

    private final PluginMetadata metadata;
    private final PluginDescriptor pluginDescriptor;
    private final Resource configResource;
    private final boolean dynamic;

    /**
     * Creates a new {@code PluginInfo}.
     *
     * @param pluginDescriptor the descriptor resource and declared classes that produced this plugin
     * @param metadata the plugin metadata extracted for this plugin
     * @param configResource the plugin's configuration resource ({@code plugin.yml} or
     *        {@code plugin.groovy}), or {@code null} if no config file exists
     * @param dynamic {@code true} if this plugin came from dynamically supplied classes or resources
     */
    PluginInfo(PluginDescriptor pluginDescriptor, PluginMetadata metadata, Resource configResource, boolean dynamic) {
        this.metadata = metadata;
        this.pluginDescriptor = pluginDescriptor;
        this.configResource = configResource;
        this.dynamic = dynamic;
    }

    /**
     * Returns the logical plugin name (for example {@code core} or {@code myPlugin}).
     *
     * @return the plugin name
     */
    public String getName() {
        return metadata.getName();
    }

    /**
     * Returns the declared plugin version.
     *
     * @return the plugin version
     */
    public String getPluginVersion() {
        return metadata.getPluginVersion();
    }

    /**
     * Returns the plugin implementation class.
     *
     * @return the plugin class
     */
    public Class<?> getPluginClass() {
        return metadata.getPluginClass();
    }

    /**
     * Returns the plugin configuration resource associated with this plugin.
     *
     * @return the plugin configuration resource, or {@code null} if the plugin has no external config resource
     */
    public Resource getConfigResource() {
        return configResource;
    }

    /**
     * Returns the extracted plugin metadata.
     *
     * @return the plugin metadata backing this plugin info
     */
    public PluginMetadata getMetadata() {
        return metadata;
    }

    /**
     * Returns the plugin names that this plugin prefers to load after.
     *
     * @return the declared load-after plugin names
     */
    public String[] getLoadAfterNames() {
        return metadata.getLoadAfterNames();
    }

    /**
     * Returns the plugin names that this plugin prefers to load before.
     *
     * @return the declared load-before plugin names
     */
    public String[] getLoadBeforeNames() {
        return metadata.getLoadBeforeNames();
    }

    /**
     * Returns the plugin names that this plugin depends on.
     *
     * @return the declared dependency plugin names
     */
    public String[] getDependsOnNames() {
        return metadata.getDependsOnNames();
    }

    /**
     * Returns the plugin names observed by this plugin.
     *
     * @return the declared observed plugin names
     */
    public String[] getObservedPluginNames() {
        return metadata.getObservedPluginNames();
    }

    /**
     * Returns whether this plugin was discovered from a dynamic plugin configuration.
     *
     * @return {@code true} if this plugin came from configured classes or Groovy resources instead of classpath
     * descriptor discovery
     */
    public boolean isDynamic() {
        return dynamic;
    }

    /**
     * Returns the Grails version declared by this plugin.
     *
     * @return the declared Grails version
     */
    public String getGrailsVersionRange() {
        return getMetadata().getGrailsVersionRange();
    }

    /**
     * Returns the plugin names evicted by this plugin.
     *
     * @return the declared evicted plugin names
     */
    public String[] getEvictions() {
        return metadata.getEvictions();
    }

    /**
     * Returns the descriptor that produced this plugin info.
     *
     * @return the plugin descriptor associated with this plugin
     */
    public PluginDescriptor getPluginDescriptor() {
        return pluginDescriptor;
    }

    /**
     * Determines whether this plugin declares compatibility with the supplied Grails version.
     *
     * @param grailsVersion the Grails version to test against
     * @return {@code true} if the plugin is compatible with the supplied Grails version
     */
    public boolean isGrailsVersionCompatible(String grailsVersion) {
        return PluginUtils.isPluginVersionCompatible(getPluginVersion(), getGrailsVersionRange(), grailsVersion, getName());
    }

    /**
     * Compares this plugin info with another plugin info using the underlying metadata equality semantics.
     *
     * @param o the other object to compare
     * @return {@code true} if both plugin infos are considered equal by their metadata
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PluginInfo other)) return false;
        return metadata.equals(other.metadata);
    }

    @Override
    public int hashCode() {
        return metadata.hashCode();
    }

    @Override
    public String toString() {
        return "GrailsPluginInfo[" + metadata.getName() + "]";
    }
}
