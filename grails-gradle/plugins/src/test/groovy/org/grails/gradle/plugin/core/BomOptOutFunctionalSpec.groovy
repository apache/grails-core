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
package org.grails.gradle.plugin.core

/**
 * Functional test verifying that {@code grails.bom = null} suppresses the automatic
 * application of the Grails platform BOM and, in effect, all bom-property-overrides behavior.
 *
 * <p>{@code org.apache.grails.gradle.bom-property-overrides} is applied unconditionally by
 * {@link GrailsGradlePlugin#applyGrailsBom} (see that method for why - applying it eagerly,
 * before {@code GrailsCliGradlePlugin}, avoids an {@code afterEvaluate} ordering race in
 * multi-project builds), so {@code project.plugins.findPlugin(...)} alone can no longer be used
 * to verify the opt-out. What matters is the functional outcome: with no declared Grails BOM (and
 * none declared by hand), bom-property-overrides' own auto-detection finds nothing to override,
 * so it adds zero constraints - opting out is still fully effective.</p>
 *
 * @since 8.0
 * @see GrailsExtension#getBom
 * @see GrailsGradlePlugin#applyGrailsBom
 */
class BomOptOutFunctionalSpec extends GradleSpecification {

    def "grails.bom = null suppresses the platform BOM and any property-based version overrides"() {
        given:
        setupTestResourceProject('auto-apply-bom-disabled')

        when:
        def result = executeTask('inspectBomSetup')

        then: 'no Grails platform BOM is added to implementation'
        result.output.contains('HAS_PLATFORM_BOM=false')

        and: 'bom-property-overrides finds no declared platform, so it adds no version-override constraints'
        result.output.contains('HAS_BOM_OVERRIDE_CONSTRAINTS=false')

        and: 'Spring DM is also not applied (regardless of the bom setting)'
        result.output.contains('HAS_SPRING_DM=false')
    }
}
