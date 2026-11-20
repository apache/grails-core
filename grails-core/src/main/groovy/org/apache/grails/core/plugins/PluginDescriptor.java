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
import java.util.Objects;

import org.springframework.core.io.Resource;

/**
 * Represents a parsed Grails plugin descriptor discovered on the classpath.
 *
 * <p>A descriptor captures the source {@link Resource} together with the plugin implementation classes declared by
 * the descriptor and any additional provided class names contributed by the plugin.</p>
 */
public class PluginDescriptor {

    private final Resource resource;
    private final List<String> providedPlugins;
    private final List<String> providedClasses;

    /**
     * Creates a plugin descriptor.
     *
     * @param resource the source resource from which the descriptor was read
     * @param providedPlugins the fully qualified plugin implementation class names declared by the descriptor
     * @param providedClasses the fully qualified provided class names declared by the descriptor
     */
    public PluginDescriptor(
            Resource resource,
            List<String> providedPlugins,
            List<String> providedClasses) {
        this.resource = resource;
        this.providedPlugins = providedPlugins;
        this.providedClasses = providedClasses;
    }

    /**
     * Returns the source resource for this descriptor.
     *
     * @return the descriptor resource
     */
    public Resource getResource() {
        return resource;
    }

    /**
     * Returns the plugin implementation classes declared by this descriptor.
     *
     * @return the declared plugin implementation class names
     */
    public List<String> getProvidedPlugins() {
        return providedPlugins;
    }

    /**
     * Returns the non-plugin classes contributed by this descriptor.
     *
     * @return the declared provided class names
     */
    public List<String> getProvidedClasses() {
        return providedClasses;
    }

    /**
     * Compares this descriptor with another descriptor using the resource, provided plugin classes, and provided
     * class names.
     *
     * @param o the other object to compare
     * @return {@code true} if the descriptors describe the same resource and declared classes
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PluginDescriptor that)) return false;
        return Objects.equals(resource, that.resource) &&
                Objects.equals(providedPlugins, that.providedPlugins) &&
                Objects.equals(providedClasses, that.providedClasses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, providedPlugins, providedClasses);
    }

    @Override
    public String toString() {
        return "PluginDescriptor[" +
                "resource=" + resource +
                ", providedPlugins=" + providedPlugins +
                ", providedClasses=" + providedClasses +
                ']';
    }
}
