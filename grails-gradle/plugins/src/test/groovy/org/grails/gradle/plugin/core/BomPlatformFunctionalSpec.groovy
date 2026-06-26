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
 * Functional tests for the Gradle platform-based BOM integration.
 *
 * <p>Uses Gradle TestKit to verify that the Grails Gradle plugin correctly
 * applies {@code grails-bom} as a Gradle {@code platform()} dependency,
 * applies the {@code org.apache.grails.gradle.bom-property-overrides}
 * plugin, and no longer depends on the Spring Dependency Management
 * plugin.</p>
 *
 * @since 8.0
 * @see GrailsGradlePlugin#applyGrailsBom
 */
class BomPlatformFunctionalSpec extends GradleSpecification {

    def "plugin applies grails-bom as Gradle platform, applies bom-property-overrides plugin, and does not apply Spring DM plugin"() {
        given:
        setupTestResourceProject('bom-platform-basic')

        when:
        def result = executeTask('inspectBomSetup')

        then:
        result.output.contains('HAS_PLATFORM_BOM=true')
        result.output.contains('HAS_BOM_PROPERTY_OVERRIDES=true')
        result.output.contains('HAS_SPRING_DM=false')
    }

    def "plugin does not inject grails-bom when the build already declares a Grails BOM by hand"() {
        given: 'a project that declares the Micronaut BOM variant itself'
        setupTestResourceProject('bom-platform-manual')

        when:
        def result = executeTask('inspectBomSetup')

        then: 'the plugin does NOT add a second (grails-bom) platform - exactly one Grails BOM is applied'
        result.output.contains('HAS_GRAILS_BOM=false')

        and: 'the hand-declared Micronaut BOM remains'
        result.output.contains('HAS_MICRONAUT_BOM=true')

        and: 'a sibling configuration that did not declare a BOM still receives the Micronaut BOM (and not grails-bom)'
        result.output.contains('SIBLING_HAS_MICRONAUT_BOM=true')
        result.output.contains('SIBLING_HAS_GRAILS_BOM=false')

        and: 'property-based version overrides are still enabled for the declared BOM'
        result.output.contains('HAS_BOM_PROPERTY_OVERRIDES=true')
    }

    def "plugin applies configured Hibernate 7 Micronaut BOM as enforced platform"() {
        given:
        setupTestResourceProject('bom-platform-hibernate7-micronaut-auto')

        when:
        def result = executeTask('inspectBomSetup')

        then:
        result.output.contains('HAS_MICRONAUT_LIBRARY=true')
        result.output.contains('HAS_H7_MICRONAUT_BOM=true')
        result.output.contains('H7_MICRONAUT_BOM_ENFORCED=true')
        result.output.contains('HAS_GRAILS_BOM=false')
        result.output.contains('HAS_BOM_PROPERTY_OVERRIDES=true')
    }

    def "plugin recognizes manually declared Hibernate 7 Micronaut BOM as the single Grails BOM"() {
        given:
        setupTestResourceProject('bom-platform-hibernate7-micronaut-manual')

        when:
        def result = executeTask('inspectBomSetup')

        then:
        result.output.contains('HAS_GRAILS_BOM=false')
        result.output.contains('HAS_MICRONAUT_LIBRARY=true')
        result.output.contains('HAS_H7_MICRONAUT_BOM=true')
        result.output.contains('H7_MICRONAUT_BOM_ENFORCED=true')
        result.output.contains('SIBLING_HAS_H7_MICRONAUT_BOM=true')
        result.output.contains('SIBLING_H7_MICRONAUT_BOM_ENFORCED=true')
        result.output.contains('SIBLING_HAS_GRAILS_BOM=false')
        result.output.contains('HAS_BOM_PROPERTY_OVERRIDES=true')
    }
}
