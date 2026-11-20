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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.grails.forge.feature.FeaturePhase

/**
 * What only the descriptor of a pack Forge has in itself says: how the feature takes part in the
 * selection of the features of an application, as the features of the framework that are written as
 * classes do, and the contributions that depend on the other features. The pack of a plugin has
 * {@link #NONE}.
 *
 * @since 9.0
 */
@CompileStatic
@ToString(includeNames = true, includePackage = false)
final class PackOfForge {

    /** What a descriptor that says none of it has */
    public static final PackOfForge NONE = new PackOfForge(null, [], FeaturePhase.DEFAULT.getOrder(), true, [], null, null, false, [],
            [:], [])

    /** When the feature is selected without being named, or {@code null} for a feature that is never a default */
    final PackCondition byDefault
    /** The features that are added with this one once the defaults are chosen, each when its condition holds and it is not there */
    final List<Addition> adds
    /** The place of the feature among the features, see {@link FeaturePhase} */
    final int order
    /** Whether the feature can be selected by its name */
    final boolean visible
    /** The features that what this one generates makes use of, each for the types of application of its condition */
    final List<Addition> reliesOn
    /** The first JDK the feature can be used with, or {@code null} */
    final Integer requiresJdk
    /**
     * The settings a database driver gives the Grails Data implementation that connects through it, in
     * the form of the configuration of a descriptor, or {@code null}
     */
    final Map<String, Object> dataSource
    /** Whether the feature takes the {@link #dataSource} settings of the database driver of the application, before its own */
    final boolean dataSourceOfDriver
    /** The comments the feature puts in front of its build properties */
    final List<String> propertyComments
    /** The build extensions that are given in parts, each part there when its condition holds */
    final Map<String, List<Part>> buildExtensionParts
    /** The settings that are given with a condition or for the configuration file of one environment, in their order */
    final List<Settings> settings

    @SuppressWarnings('ParameterCount')
    PackOfForge(PackCondition byDefault, List<Addition> adds, int order, boolean visible, List<Addition> reliesOn, Integer requiresJdk,
                Map<String, Object> dataSource, boolean dataSourceOfDriver, List<String> propertyComments,
                Map<String, List<Part>> buildExtensionParts, List<Settings> settings) {
        this.byDefault = byDefault
        this.adds = List.copyOf(adds)
        this.order = order
        this.visible = visible
        this.reliesOn = List.copyOf(reliesOn)
        this.requiresJdk = requiresJdk
        this.dataSource = dataSource == null ? null : (Map<String, Object>) PackDescriptor.frozen(dataSource)
        this.dataSourceOfDriver = dataSourceOfDriver
        this.propertyComments = List.copyOf(propertyComments)
        Map<String, List<Part>> parts = new LinkedHashMap<>()
        buildExtensionParts.each { String name, List<Part> each -> parts.put(name, List.copyOf(each)) }
        this.buildExtensionParts = Collections.unmodifiableMap(parts)
        this.settings = List.copyOf(settings)
    }

    private List<Object> state() {
        return [byDefault, adds, order, visible, reliesOn, requiresJdk, dataSource, dataSourceOfDriver, propertyComments,
                buildExtensionParts, settings] as List<Object>
    }

    @Override
    boolean equals(Object other) {
        // compared by Java, as the descriptor is: 1 and 1.0 are different settings
        return other instanceof PackOfForge && Objects.equals(state(), ((PackOfForge) other).state())
    }

    @Override
    int hashCode() {
        return Objects.hashCode(state())
    }

    /**
     * A feature that is named with a condition: one that is added, or one that is relied on.
     */
    @CompileStatic
    @EqualsAndHashCode
    @ToString(includeNames = true, includePackage = false)
    static final class Addition {

        final String feature
        final PackCondition when

        Addition(String feature, PackCondition when) {
            this.feature = feature
            this.when = when
        }
    }

    /**
     * A part of the body of a build extension.
     */
    @CompileStatic
    @EqualsAndHashCode
    @ToString(includeNames = true, includePackage = false)
    static final class Part {

        final String text
        final PackCondition when

        Part(String text, PackCondition when) {
            this.text = text
            this.when = when
        }
    }

    /**
     * Settings that are there when a condition holds, in the configuration of the application or in the
     * configuration file of one environment.
     */
    @CompileStatic
    @ToString(includeNames = true, includePackage = false)
    static final class Settings {

        final PackCondition when
        /** The environment whose configuration file the settings go to, or {@code null} for the configuration of the application */
        final String environmentFile
        final Map<String, Object> values

        Settings(PackCondition when, String environmentFile, Map<String, Object> values) {
            this.when = when
            this.environmentFile = environmentFile
            this.values = (Map<String, Object>) PackDescriptor.frozen(values)
        }

        @Override
        boolean equals(Object other) {
            return other instanceof Settings && Objects.equals([when, environmentFile, values], [((Settings) other).when,
                    ((Settings) other).environmentFile, ((Settings) other).values])
        }

        @Override
        int hashCode() {
            return Objects.hash(when, environmentFile, values)
        }
    }
}
