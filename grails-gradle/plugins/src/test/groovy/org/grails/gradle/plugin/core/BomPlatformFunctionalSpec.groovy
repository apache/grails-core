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
 * applies {@code grails-bom} as a Gradle {@code platform()} dependency
 * and no longer depends on the Spring Dependency Management plugin.</p>
 *
 * @since 8.0
 * @see BomManagedVersions
 * @see GrailsGradlePlugin#applyGrailsBom
 */
class BomPlatformFunctionalSpec extends GradleSpecification {

    def "plugin applies grails-bom as Gradle platform and does not apply Spring DM plugin"() {
        given:
        setupTestResourceProject('bom-platform-basic')

        when:
        def result = executeTask('inspectBomSetup')

        then:
        result.output.contains('HAS_PLATFORM_BOM=true')
        result.output.contains('HAS_SPRING_DM=false')
    }
}
