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
 * Tests that {@link GrailsPluginGradlePlugin} gives {@code grails-publish} the {@code grails-plugin.xml}
 * sidecar only for a project that declares a plugin. {@code grails-publish} adds the sidecar artifact to the
 * publication whenever the descriptor is present, and the descriptor task writes no XML for a project
 * without a plugin, so publishing such a project failed on a missing artifact file.
 */
class GrailsPluginGradlePluginPublishingSpec extends GradleSpecification {

    def "a project without a plugin class or starter publishes no grails-plugin.xml"() {
        given:
        setupTestResourceProject('plugin-xml-sidecar')

        expect:
        descriptorPresent() == 'false'
    }

    def "a project with a GrailsPlugin source file publishes the grails-plugin.xml"() {
        given:
        def runner = setupTestResourceProject('plugin-xml-sidecar')
        def source = new File(runner.projectDir, 'src/main/groovy/demo/SidecarGrailsPlugin.groovy')
        source.parentFile.mkdirs()
        source.text = "package demo\nclass SidecarGrailsPlugin { def version = '1.0' }\n"

        expect:
        descriptorPresent() == 'true'
    }

    def "a classless starter publishes the grails-plugin.xml"() {
        given:
        setupTestResourceProject('plugin-xml-sidecar')

        expect:
        descriptorPresent(['-Pstarter=true']) == 'true'
    }

    private String descriptorPresent(List<String> args = []) {
        def result = executeTask('inspectPluginDescriptor', args)
        (result.output =~ /PLUGIN_DESCRIPTOR_PRESENT=(\w+)/)[0][1]
    }
}
