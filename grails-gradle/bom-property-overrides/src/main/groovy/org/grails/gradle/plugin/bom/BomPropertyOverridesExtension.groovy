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
package org.grails.gradle.plugin.bom

import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Configuration for the
 * {@code org.apache.grails.gradle.bom-property-overrides} plugin.
 *
 * <p>Exposed on the project as {@code bomPropertyOverrides}:</p>
 *
 * <pre>
 * apply plugin: 'org.apache.grails.gradle.bom-property-overrides'
 *
 * bomPropertyOverrides {
 *     // Disable scanning declared platform() dependencies (default: true)
 *     autoDetect = false
 *
 *     // Add explicit BOM coordinates - useful when a BOM is referenced
 *     // indirectly (e.g., through a parent plugin) and not declared
 *     // directly via platform() in the consumer's build.gradle
 *     bom 'org.example:my-bom:1.0.0'
 *     bom 'org.example:other-bom:2.0.0'
 * }
 *
 * // Override versions via gradle.properties or ext[]
 * ext['slf4j.version'] = '2.0.13'
 * </pre>
 *
 * <p>By default ({@code autoDetect = true}) the plugin scans every project
 * configuration for declared {@code platform()} / {@code enforcedPlatform()}
 * dependencies and registers each unique BOM for property-override
 * processing. Explicit entries added via {@link #bom(String)} are always
 * processed in addition to auto-detected ones, regardless of the
 * {@code autoDetect} flag.</p>
 *
 * @since 8.0
 */
@CompileStatic
class BomPropertyOverridesExtension {

    /**
     * The name of the project extension exposed on every project.
     */
    static final String EXTENSION_NAME = 'bomPropertyOverrides'

    /**
     * Whether to auto-detect BOMs from declared {@code platform()} /
     * {@code enforcedPlatform()} dependencies on the project's
     * configurations. Defaults to {@code true}.
     */
    final Property<Boolean> autoDetect

    /**
     * Explicit list of BOM coordinates ({@code group:artifact:version})
     * that should be processed for property overrides regardless of
     * whether they are declared as platforms on the project.
     */
    final ListProperty<String> boms

    BomPropertyOverridesExtension(ObjectFactory objects) {
        this.autoDetect = objects.property(Boolean).convention(true)
        this.boms = objects.listProperty(String).convention([])
    }

    /**
     * Adds a BOM coordinate to the explicit override list.
     *
     * @param coordinates the BOM coordinates in {@code group:artifact:version} format
     */
    void bom(String coordinates) {
        boms.add(coordinates)
    }

    /**
     * Adds multiple BOM coordinates to the explicit override list.
     *
     * @param coordinates the BOM coordinates in {@code group:artifact:version} format
     */
    void boms(String... coordinates) {
        for (String coord : coordinates) {
            boms.add(coord)
        }
    }
}
