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
 * Tests what {@link GrailsPluginGradlePlugin} gives the compiler that writes a plugin's descriptors, and that the
 * build reads the plugin's name from {@code plugins.imports}, where its code declares it.
 */
class GrailsPluginGradlePluginCompilationSpec extends GradleSpecification {

    def "the main Groovy compiler, and only it, is given the project version and grails-app/conf"() {
        given:
        setupTestResourceProject('plugin-descriptor-compilation')

        when:
        def result = executeTask('printDescriptorArguments', ['-Pversion=2.3.0'])

        then:
        String main = argumentsOf(result.output, 'MAIN_ARGS=')
        String test = argumentsOf(result.output, 'TEST_ARGS=')
        main.contains('-Dgrails.plugin.version=2.3.0')
        main.contains('-Dgrails.plugin.config.dir=') && main.contains('/grails-app/conf')
        !test.contains('grails.plugin.version')
        !test.contains('grails.plugin.config.dir')
    }

    def "the i18n descriptor names the plugin as plugins.imports does, not as the project is named"() {
        given: 'a project named descriptor-compilation whose plugins.imports lists acmeAudit'
        def runner = setupTestResourceProject('plugin-descriptor-compilation')

        when:
        executeTask('generateI18nDescriptor')

        then: 'the acme-audit bundle is inside the plugin namespace, and the descriptor names the plugin'
        File descriptor = new File(runner.projectDir, 'build/generated-resources/grails-i18n/META-INF/grails/i18n.properties')
        descriptor.readLines('UTF-8').contains('artifact.name=acme-audit')
    }

    private static String argumentsOf(String output, String prefix) {
        output.readLines().find { String line -> line.startsWith(prefix) }.replace('\\', '/')
    }
}
