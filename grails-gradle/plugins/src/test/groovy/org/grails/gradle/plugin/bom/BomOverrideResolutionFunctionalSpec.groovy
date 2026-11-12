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

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * End-to-end resolution tests for the
 * {@code org.apache.grails.gradle.bom-property-overrides} plugin.
 *
 * <p>Unlike {@link BomPropertyOverridesPluginFunctionalSpec}, which only
 * verifies platform auto-detection, this spec performs a real dependency
 * resolution against a local Maven repository to assert that property
 * overrides actually change the resolved versions, including the two cases
 * a naive {@code useVersion()} implementation gets wrong:</p>
 *
 * <ul>
 *   <li><strong>Downgrade override</strong> - an override lower than the BOM
 *       default must win over the platform's {@code require} constraint.</li>
 *   <li><strong>Imported-BOM property override</strong> - overriding the
 *       property that selects an imported BOM's version must re-import that
 *       BOM and pull in its updated managed versions.</li>
 *   <li><strong>Coordinates written as a property</strong> - an override must
 *       reach a module, or an imported BOM, whose group the BOM writes as
 *       {@code ${project.groupId}}.</li>
 * </ul>
 *
 * @since 8.0
 * @see BomManagedVersions
 */
class BomOverrideResolutionFunctionalSpec extends GradleSpecification {

    def "property overrides win over platform constraints, including downgrades, imported-BOM version switches and property-written coordinates"() {
        given:
        setupTestResourceProject('bom-override-resolution')

        when:
        def result = executeTask('printVersions')

        then: 'a downgrade override (mylib 2.0.0 -> 1.0.0) wins over the platform require(2.0.0) constraint'
        result.output.contains('RESOLVED=org.example:mylib:1.0.0')
        !result.output.contains('RESOLVED=org.example:mylib:2.0.0')

        and: 'overriding the imported-BOM selector property re-imports child-bom:2.0.0 and bumps childlib 1.0.0 -> 2.0.0'
        result.output.contains('RESOLVED=org.example:childlib:2.0.0')
        !result.output.contains('RESOLVED=org.example:childlib:1.0.0')

        and: 'an override reaches grouplib, whose group test-bom writes as ${project.groupId}'
        result.output.contains('RESOLVED=org.example:grouplib:2.0.0')
        !result.output.contains('RESOLVED=org.example:grouplib:1.0.0')
    }
}
