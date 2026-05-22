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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit-level tests for {@link BomPropertyOverridesPlugin} using
 * {@link ProjectBuilder}.
 *
 * <p>Verifies plugin application creates the {@code bomPropertyOverrides}
 * extension with sensible defaults, that the extension's DSL methods
 * register explicit BOM coordinates, and that auto-detect identifies
 * declared {@code platform()} / {@code enforcedPlatform()} dependencies.</p>
 *
 * @since 8.0
 */
class BomPropertyOverridesPluginSpec extends Specification {

    def "applying the plugin registers the bomPropertyOverrides extension"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.plugins.apply(BomPropertyOverridesPlugin)

        then:
        def extension = project.extensions.findByType(BomPropertyOverridesExtension)
        extension != null
        extension.autoDetect.get() == true
        extension.boms.get().isEmpty()
    }

    def "extension bom() method registers explicit BOM coordinates"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply(BomPropertyOverridesPlugin)
        def extension = project.extensions.getByType(BomPropertyOverridesExtension)

        when:
        extension.bom('org.example:my-bom:1.0.0')
        extension.bom('org.example:other-bom:2.0.0')

        then:
        extension.boms.get() == ['org.example:my-bom:1.0.0', 'org.example:other-bom:2.0.0']
    }

    def "extension boms() vararg method registers multiple BOMs"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply(BomPropertyOverridesPlugin)
        def extension = project.extensions.getByType(BomPropertyOverridesExtension)

        when:
        extension.boms('org.example:a:1.0.0', 'org.example:b:2.0.0', 'org.example:c:3.0.0')

        then:
        extension.boms.get() == ['org.example:a:1.0.0', 'org.example:b:2.0.0', 'org.example:c:3.0.0']
    }

    def "detectDeclaredBoms finds regular platform() dependencies"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply('java')
        project.dependencies.add(
                'implementation',
                project.dependencies.platform('org.example:test-bom:1.0.0')
        )

        when:
        def coordinates = BomPropertyOverridesPlugin.detectDeclaredBoms(project.configurations)

        then:
        'org.example:test-bom:1.0.0' in coordinates
    }

    def "detectDeclaredBoms finds enforcedPlatform() dependencies"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply('java')
        project.dependencies.add(
                'implementation',
                project.dependencies.enforcedPlatform('org.example:enforced-bom:2.0.0')
        )

        when:
        def coordinates = BomPropertyOverridesPlugin.detectDeclaredBoms(project.configurations)

        then:
        'org.example:enforced-bom:2.0.0' in coordinates
    }

    def "detectDeclaredBoms ignores non-platform dependencies"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply('java')
        project.dependencies.add('implementation', 'org.example:regular-lib:1.0.0')

        when:
        def coordinates = BomPropertyOverridesPlugin.detectDeclaredBoms(project.configurations)

        then:
        coordinates.isEmpty()
    }

    def "detectDeclaredBoms deduplicates the same BOM declared on multiple configurations"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply('java')
        project.dependencies.add(
                'implementation',
                project.dependencies.platform('org.example:shared-bom:1.0.0')
        )
        project.dependencies.add(
                'testImplementation',
                project.dependencies.platform('org.example:shared-bom:1.0.0')
        )

        when:
        def coordinates = BomPropertyOverridesPlugin.detectDeclaredBoms(project.configurations)

        then:
        coordinates == ['org.example:shared-bom:1.0.0'] as Set
    }
}
