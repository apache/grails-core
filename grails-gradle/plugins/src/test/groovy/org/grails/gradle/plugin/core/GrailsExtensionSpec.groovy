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
 * <p>Focuses on the {@link GrailsExtension#getBom} property - which BOM (if any) the
 * Grails Gradle plugin applies automatically - and the backward-compatible bridge from
 * the deprecated {@code springDependencyManagement} flag onto it.</p>
 *
 * @since 8.0
 */
class GrailsExtensionSpec extends Specification {

    def "bom defaults to grails-bom"() {
        given:
        Project project = ProjectBuilder.builder().build()

        when:
        GrailsExtension extension = new GrailsExtension(project)

        then:
        extension.bom.get() == GrailsExtension.DEFAULT_BOM
        extension.bom.get() == 'grails-bom'
    }

    def "bom can be set to a different curated variant"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        when:
        extension.bom = 'grails-micronaut-bom'

        then:
        extension.bom.get() == 'grails-micronaut-bom'
    }

    def "bom = null clears the property so no BOM is applied"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        expect: 'the default is present'
        extension.bom.getOrNull() == 'grails-bom'

        when:
        extension.bom = null

        then:
        extension.bom.getOrNull() == null
    }

    def "bom = blank clears the property so no BOM is applied"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        when:
        extension.bom = '   '

        then:
        extension.bom.getOrNull() == null
    }

    def "deprecated springDependencyManagement = false clears bom for backward compatibility"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        expect: 'bom starts at the default'
        extension.bom.getOrNull() == 'grails-bom'

        when: 'a project opts out using the legacy Grails 7 flag'
        extension.springDependencyManagement = false

        then: 'the BOM is no longer auto-applied'
        !extension.springDependencyManagement
        extension.bom.getOrNull() == null
    }

    def "deprecated springDependencyManagement = true leaves bom at its default"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        when:
        extension.springDependencyManagement = true

        then:
        extension.springDependencyManagement
        extension.bom.getOrNull() == 'grails-bom'
    }

    def "an explicit bom selection is honored independently of the deprecated flag"() {
        given:
        Project project = ProjectBuilder.builder().build()
        GrailsExtension extension = new GrailsExtension(project)

        when:
        extension.bom = 'grails-hibernate5-bom'

        then:
        extension.bom.getOrNull() == 'grails-hibernate5-bom'
        extension.springDependencyManagement
    }
}
