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

        then: 'the platform is contributed lazily: a configuration nothing has observed yet declares no BOM'
        result.output.contains('DECLARED_EAGERLY=false')

        and: 'it is declared on the declarable configurations once their dependencies are observed'
        result.output.contains('HAS_PLATFORM_BOM=true')
        result.output.contains('IMPLEMENTATION_HAS_PLATFORM_BOM=true')
        result.output.contains('TEST_IMPLEMENTATION_HAS_PLATFORM_BOM=true')

        and:
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

    def "the lazily contributed platform does not pre-empt the defaultDependencies of other configurations"() {
        given: 'a project whose configurations are populated on demand, the way the jacoco plugin populates jacocoAgent'
        setupTestResourceProject('bom-platform-default-dependencies')

        when:
        def result = executeTask('inspectDefaultDependencies')

        then: "the jacoco plugin's defaults resolve without the application platform on its tool classpaths (#16335)"
        result.output.contains('JACOCO_AGENT_RESOLVED=[org.jacoco:org.jacoco.agent:0.0.1-test]')
        result.output.contains('JACOCO_ANT_RESOLVED=[org.jacoco:org.jacoco.ant:0.0.1-test]')
        result.output.contains('JACOCO_AGENT_HAS_BOM=false')
        result.output.contains('JACOCO_ANT_HAS_BOM=false')

        and: 'so do the defaults of any other configuration populated through defaultDependencies'
        result.output.contains('LAZY_TOOL_RESOLVED=[org.apache.grails:lazy-bom:1.0-lazy, org.example:lazy-tool:1.0.0]')
        result.output.contains('LAZY_TOOL_HAS_BOM=true')

        and: "the plugin's own profile configuration keeps its default profile"
        result.output.contains('PROFILE_DEFAULT_DECLARED=true')
        result.output.contains('PROFILE_HAS_BOM=true')

        and: 'the platform still manages versions on configurations declared by the build'
        result.output.contains('MANAGED_RESOLVED=[org.apache.grails:lazy-bom:1.0-lazy, org.example:managed-lib:1.0.0]')
    }

    def "late-created configurations receive the platform only if their final role allows dependencies"() {
        given:
        setupTestResourceProject('bom-platform-default-dependencies')

        when:
        def result = executeTask('inspectLateConfigurations', ['--warning-mode=fail'])

        then: 'a configuration made non-declarable in its create closure resolves without an injected dependency'
        result.output.contains('LATE_RESOLVABLE_RESOLVED=[]')
        result.output.contains('LATE_RESOLVABLE_HAS_BOM=false')

        and: 'a late-created declarable configuration still gets its defaults and the platform'
        result.output.contains('LATE_DECLARABLE_RESOLVED=[org.apache.grails:lazy-bom:1.0-lazy, org.example:lazy-tool:1.0.0]')
    }

    def "auto-applied BOM property overrides respect autoDetect=#autoDetect and explicit registration=#explicitBom"() {
        given:
        setupTestResourceProject('bom-platform-default-dependencies')

        when:
        def result = executeTask('inspectDefaultDependencies', [
                "-PautoDetect=$autoDetect" as String, "-PexplicitBom=$explicitBom" as String, '-Pmanaged-lib.version=2.0.0'
        ])

        then: 'the platform always applies, but property overrides require automatic detection or explicit registration'
        result.output.contains("MANAGED_RESOLVED=[org.apache.grails:lazy-bom:1.0-lazy, org.example:managed-lib:$expectedVersion]")

        where:
        autoDetect | explicitBom | expectedVersion
        true       | false       | '2.0.0'
        false      | false       | '1.0.0'
        false      | true        | '2.0.0'
    }

    def "an auto-applied Micronaut BOM variant satisfies the enforcedPlatform validation"() {
        given: 'a Micronaut project that selects the Micronaut BOM variant via grails.bom'
        setupTestResourceProject('bom-platform-micronaut')

        when:
        def result = executeTask('inspectBomSetup')

        then: 'the validation accepts the BOM the plugin contributes lazily'
        result.output.contains('BUILD SUCCESSFUL')

        and: 'that BOM is contributed as an enforcedPlatform'
        result.output.contains('HAS_MICRONAUT_BOM=true')
        result.output.contains('MICRONAUT_BOM_CATEGORY=enforced-platform')
    }

    def "opting out of the automatic BOM without declaring a Micronaut BOM by hand still fails the validation"() {
        given: 'a Micronaut project that opts out of automatic BOM application'
        def runner = setupTestResourceProject('bom-platform-micronaut')

        when:
        def result = runner.withArguments('help', '--stacktrace', '-PbomOptOut').buildAndFail()

        then:
        result.output.contains('uses Micronaut but does not apply a Micronaut BOM as an enforcedPlatform')
    }

}
