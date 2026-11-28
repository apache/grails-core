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
 * Tests that {@link GrailsPluginGradlePlugin} publishes no {@code grails-plugin.xml} sidecar: a Grails 9 plugin is
 * described by the descriptors inside its jar.
 */
class GrailsPluginGradlePluginPublishingSpec extends GradleSpecification {

    def "a project without a plugin publishes no grails-plugin.xml"() {
        given:
        setupTestResourceProject('plugin-xml-sidecar')

        expect:
        descriptorPresent() == 'false'
    }

    def "a project with a plugin class publishes no grails-plugin.xml"() {
        given:
        def runner = setupTestResourceProject('plugin-xml-sidecar')
        def source = new File(runner.projectDir, 'src/main/groovy/demo/SidecarGrailsPlugin.groovy')
        source.parentFile.mkdirs()
        source.text = "package demo\nclass SidecarGrailsPlugin {}\n"

        expect:
        descriptorPresent() == 'false'
    }

    private String descriptorPresent() {
        def result = executeTask('inspectPluginDescriptor')
        (result.output =~ /PLUGIN_DESCRIPTOR_PRESENT=(\w+)/)[0][1]
    }
}
