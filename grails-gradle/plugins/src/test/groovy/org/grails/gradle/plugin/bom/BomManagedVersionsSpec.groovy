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

import spock.lang.Specification

/**
 * Unit tests for {@link BomManagedVersions}.
 *
 * <p>Verifies that the utility correctly parses BOM POM files,
 * extracts {@code <properties>}, builds property-to-artifact mappings
 * from {@code <dependencyManagement>} entries, and skips dependencies
 * with hardcoded versions.</p>
 *
 * @since 8.0
 * @see BomManagedVersions
 */
class BomManagedVersionsSpec extends Specification {

    def "parseBomFile extracts properties from BOM POM"() {
        given:
        def pomFile = new File(getClass().getClassLoader().getResource('test-poms/test-bom.pom').toURI())
        def bomProperties = [:] as Map<String, String>
        def propertyToArtifacts = [:] as Map<String, List<String>>

        when:
        BomManagedVersions.parseBomFile(pomFile, bomProperties, propertyToArtifacts)

        then:
        bomProperties['jackson.version'] == '2.15.0'
        bomProperties['slf4j.version'] == '2.0.9'
        bomProperties['groovy.version'] == '4.0.30'
    }

    def "parseBomFile maps property references to artifact coordinates"() {
        given:
        def pomFile = new File(getClass().getClassLoader().getResource('test-poms/test-bom.pom').toURI())
        def bomProperties = [:] as Map<String, String>
        def propertyToArtifacts = [:] as Map<String, List<String>>

        when:
        BomManagedVersions.parseBomFile(pomFile, bomProperties, propertyToArtifacts)

        then: "jackson.version maps to all three jackson artifacts"
        propertyToArtifacts['jackson.version'].containsAll([
            'com.fasterxml.jackson.core:jackson-databind',
            'com.fasterxml.jackson.core:jackson-core',
            'com.fasterxml.jackson.core:jackson-annotations'
        ])

        and: "slf4j.version maps to slf4j-api"
        propertyToArtifacts['slf4j.version'] == ['org.slf4j:slf4j-api']

        and: "groovy.version maps to groovy"
        propertyToArtifacts['groovy.version'] == ['org.apache.groovy:groovy']
    }

    def "parseBomFile ignores dependencies with hardcoded versions"() {
        given:
        def pomFile = new File(getClass().getClassLoader().getResource('test-poms/test-bom.pom').toURI())
        def bomProperties = [:] as Map<String, String>
        def propertyToArtifacts = [:] as Map<String, List<String>>

        when:
        BomManagedVersions.parseBomFile(pomFile, bomProperties, propertyToArtifacts)

        then: "hardcoded-version artifact is not in any property mapping"
        !propertyToArtifacts.values().flatten().contains('org.example:hardcoded-version')
    }

    def "BomManagedVersions with no overrides reports hasOverrides false"() {
        given:
        def instance = new BomManagedVersions()

        expect:
        !instance.hasOverrides()
        instance.overrides.isEmpty()
    }
}
