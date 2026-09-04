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
 * Regression test for a multi-project {@code afterEvaluate} ordering race between
 * {@link GrailsGradlePlugin#applyGrailsBom} and {@code GrailsCliGradlePlugin}'s CLI companion
 * probe (introduced by the {@code grailsCliDetect} configuration).
 *
 * <p>The fixture is two Grails-plugin projects, {@code consumer} depending on {@code producer}
 * via {@code project(':producer')}, with {@code producer} carrying an active property-based BOM
 * version override. Resolving {@code consumer}'s own {@code grailsCliDetect} probe needs to
 * select a variant of the {@code producer} project dependency, which forces {@code producer} to
 * fully configure mid-resolution - landing inside the window where {@code producer}'s own
 * BOM-override mutation could previously race its own CLI probe resolving (and locking)
 * {@code api}/{@code implementation}/{@code runtimeOnly} first, causing:
 *
 * <pre>
 * Cannot mutate the dependencies of configuration ':producer:implementation' after the
 * configuration's child configuration ':producer:grailsCliDetect' was resolved. After a
 * configuration has been observed, it should not be modified.
 * </pre>
 *
 * <p>None of the other functional specs in this module exercise this: they're all single-project
 * fixtures, so they never force one project's configuration to complete as a side effect of
 * resolving another's.</p>
 *
 * @since 8.0
 * @see GrailsGradlePlugin#applyGrailsBom
 */
class BomCliMultiprojectRaceFunctionalSpec extends GradleSpecification {

    def "a property-based BOM override on a project depended on by another project does not race the CLI companion probe"() {
        given:
        setupTestResourceProject('bom-cli-multiproject-race')

        when:
        def result = executeTask('help', ['-PgrailsVersion=1.0-race'])

        then: 'the whole build configures successfully, including cross-project resolution into :producer'
        result.output.contains('BUILD SUCCESSFUL')

        when: 'the override is inspected directly'
        result = executeTask(':producer:printOverrideApplied', ['-PgrailsVersion=1.0-race'])

        then: 'the property-based override was genuinely computed and applied, not silently skipped'
        result.output.contains('OVERRIDE_APPLIED=true')
    }
}
