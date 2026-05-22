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
 * Functional test verifying that {@code grails.autoApplyBom = false} suppresses
 * the automatic application of {@code platform(grails-bom)} and the
 * {@code org.apache.grails.gradle.bom-property-overrides} plugin.
 *
 * @since 8.0
 * @see GrailsExtension#getAutoApplyBom
 * @see GrailsGradlePlugin#applyGrailsBom
 */
class AutoApplyBomSpec extends GradleSpecification {

    def "autoApplyBom = false suppresses platform(grails-bom) and bom-property-overrides plugin"() {
        given:
        setupTestResourceProject('auto-apply-bom-disabled')

        when:
        def result = executeTask('inspectBomSetup')

        then: 'no platform(grails-bom) is added to implementation'
        result.output.contains('HAS_PLATFORM_BOM=false')

        and: 'the bom-property-overrides plugin is NOT applied'
        result.output.contains('HAS_BOM_PROPERTY_OVERRIDES=false')

        and: 'Spring DM is also not applied (regardless of autoApplyBom)'
        result.output.contains('HAS_SPRING_DM=false')
    }
}
