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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit-level tests for {@link GrailsExtension} using {@link ProjectBuilder}.
 *
 * <p>Focuses on the backward-compatible bridge between the deprecated
 * {@code springDependencyManagement} flag and the new {@link GrailsExtension#getAutoApplyBom}
 * property, which controls whether {@code platform(grails-bom)} is applied
 * automatically.</p>
 *
 * @since 8.0
 */
class GrailsExtensionSpec extends Specification {

    def "autoApplyBom defaults to true"() {
        given:
        Project project = ProjectBuilder.builder().build()

        when:
        GrailsExtension extension = new GrailsExtension(project)

        then:
        extension.autoApplyBom.get()
    }

    def "deprecated springDependencyManagement = false disables autoApplyBom for backward compatibility"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        expect: 'autoApplyBom starts enabled'
        extension.autoApplyBom.get()

        when: 'a project opts out using the legacy Grails 7 flag'
        extension.springDependencyManagement = false

        then: 'the BOM is no longer auto-applied'
        !extension.springDependencyManagement
        !extension.autoApplyBom.get()
    }

    def "deprecated springDependencyManagement = true leaves autoApplyBom at its convention"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        when:
        extension.springDependencyManagement = true

        then:
        extension.springDependencyManagement
        extension.autoApplyBom.get()
    }

    def "an explicit autoApplyBom = false is honored independently of the deprecated flag"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        when:
        extension.autoApplyBom.set(false)

        then:
        !extension.autoApplyBom.get()
        extension.springDependencyManagement
    }
}
