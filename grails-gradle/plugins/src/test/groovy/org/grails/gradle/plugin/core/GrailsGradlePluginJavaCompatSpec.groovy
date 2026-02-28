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
 * Tests that {@link GrailsGradlePlugin} applies Java compatibility JVM arguments
 * conditionally based on the target Java version.
 *
 * <p>The plugin adds {@code --enable-native-access=ALL-UNNAMED} for Java 24+
 * (JEP 472) and {@code --sun-misc-unsafe-memory-access=allow} for Java 23+
 * (JEP 471/498) to both {@code Test} and {@code JavaExec} tasks.</p>
 *
 * @since 7.0.8
 * @see GrailsGradlePlugin#configureJavaCompatibilityArgs
 */
class GrailsGradlePluginJavaCompatSpec extends GradleSpecification {

    // ----------------------------------------------------------------
    // Current JDK without toolchain - args depend on JDK version
    // ----------------------------------------------------------------

    def "compat args match current JDK version when no toolchain configured"() {
        given:
        setupTestResourceProject('java-compat-no-toolchain')
        boolean expectNativeAccess = CURRENT_JDK >= 24
        boolean expectUnsafeAccess = CURRENT_JDK >= 23

        when:
        def result = executeTask('inspectCompatArgs')

        then:
        result.output.contains("HAS_NATIVE_ACCESS=${expectNativeAccess}")
        result.output.contains("HAS_UNSAFE_ACCESS=${expectUnsafeAccess}")
    }

    // ----------------------------------------------------------------
    // Current JDK with toolchain - args depend on JDK version
    // ----------------------------------------------------------------

    def "compat args match current JDK version when toolchain is set to current JDK"() {
        given:
        setupTestResourceProject('java-compat-toolchain-current')
        boolean expectNativeAccess = CURRENT_JDK >= 24
        boolean expectUnsafeAccess = CURRENT_JDK >= 23

        when:
        def result = executeTask('inspectCompatArgs')

        then:
        result.output.contains("HAS_NATIVE_ACCESS=${expectNativeAccess}")
        result.output.contains("HAS_UNSAFE_ACCESS=${expectUnsafeAccess}")
    }

    // ----------------------------------------------------------------
    // Java 23 toolchain: only --sun-misc-unsafe-memory-access=allow
    // ----------------------------------------------------------------

    def "Java 23 toolchain adds sun-misc-unsafe-memory-access arg to JavaExec and Test tasks"() {
        given:
        setupTestResourceProject('java-compat-toolchain-23')

        when:
        def result = executeTask('inspectCompatArgs')

        then: "sun.misc.Unsafe memory access flag is present"
        result.output.contains('HAS_UNSAFE_ACCESS=true')
        result.output.contains('TEST_HAS_UNSAFE_ACCESS=true')

        and: "native access flag is NOT present (only for 24+)"
        result.output.contains('HAS_NATIVE_ACCESS=false')
        result.output.contains('TEST_HAS_NATIVE_ACCESS=false')
    }

    // ----------------------------------------------------------------
    // Java 24 toolchain: both flags
    // ----------------------------------------------------------------

    def "Java 24 toolchain adds both native-access and sun-misc-unsafe-memory-access args"() {
        given:
        setupTestResourceProject('java-compat-toolchain-24')

        when:
        def result = executeTask('inspectCompatArgs')

        then: "both flags are present on JavaExec tasks"
        result.output.contains('HAS_NATIVE_ACCESS=true')
        result.output.contains('HAS_UNSAFE_ACCESS=true')

        and: "both flags are present on Test tasks"
        result.output.contains('TEST_HAS_NATIVE_ACCESS=true')
        result.output.contains('TEST_HAS_UNSAFE_ACCESS=true')
    }
}
