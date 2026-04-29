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

/**
 * End-to-end functional test for the standalone
 * {@code org.apache.grails.gradle.bom-property-overrides} plugin.
 *
 * <p>Uses Gradle TestKit to apply the plugin in isolation (without any
 * Grails plugins) to verify that the plugin can be used generically with
 * any BOM. Confirms the plugin registers its extension, auto-detects
 * declared {@code platform()} / {@code enforcedPlatform()} dependencies,
 * and skips non-platform dependencies.</p>
 *
 * @since 8.0
 */
class BomPropertyOverridesPluginFunctionalSpec extends GradleSpecification {

    def "plugin registers extension, autoDetect defaults to true, and identifies declared platforms"() {
        given:
        setupTestResourceProject('bom-property-overrides-basic')

        when:
        def result = executeTask('inspectBomSetup')

        then: 'extension is registered with default autoDetect=true'
        result.output.contains('HAS_EXTENSION=true')
        result.output.contains('AUTO_DETECT_DEFAULT=true')

        and: 'auto-detect identifies both regular and enforced platforms but skips non-platform dependencies'
        result.output.contains('DETECTED_BOMS=org.example:enforced-bom:2.0.0,org.example:test-bom:1.0.0')
    }
}
