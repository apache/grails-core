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

import java.util.jar.JarFile

import org.gradle.testkit.runner.GradleRunner

/**
 * A plugin that keeps a Forge feature pack under {@code src/main/forge} publishes it in its jar, where
 * Forge looks for it.
 */
class GrailsPluginGradlePluginForgePackSpec extends GradleSpecification {

    def "the feature pack of a plugin is in its jar, and the manifest names it"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        def result = executeTask('jar')

        then:
        assertTaskSuccess('jar', result)

        when:
        JarFile jar = new JarFile(new File(runner.projectDir, 'build/libs').listFiles().find { it.name.endsWith('.jar') })
        List<String> entries = jar.entries().toList()*.name
        String descriptor = jar.getInputStream(jar.getEntry('META-INF/grails-forge/feature.yml')).getText('UTF-8')
        String template = jar.getInputStream(jar.getEntry('META-INF/grails-forge/templates/Audit.groovy.gsp')).getText('UTF-8')
        String name = jar.manifest.mainAttributes.getValue('Grails-Forge-Pack')
        jar.close()

        then: 'the descriptor and the templates, GSP sources among them, are there as written'
        descriptor.contains('name: acme-audit')
        template == new File(runner.projectDir, 'src/main/forge/templates/Audit.groovy.gsp').getText('UTF-8')
        entries.contains('META-INF/grails-forge/templates/conf/audit.txt')

        and: 'and nothing else of the directory'
        !entries.any { it.contains('notes.md') }

        and:
        name == 'acme-audit'
    }

    def "a file of the pack that is removed is no longer in the jar, and a renamed pack is named anew"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        executeTask('jar')

        when:
        new File(runner.projectDir, 'src/main/forge/templates/conf/audit.txt').delete()
        File descriptor = new File(runner.projectDir, 'src/main/forge/feature.yml')
        descriptor.setText(descriptor.getText('UTF-8').replace('name: acme-audit', "name: 'acme-trail' # renamed"), 'UTF-8')
        def result = executeTask('jar')
        JarFile jar = new JarFile(new File(runner.projectDir, 'build/libs').listFiles().find { it.name.endsWith('.jar') })
        List<String> entries = jar.entries().toList()*.name
        String name = jar.manifest.mainAttributes.getValue('Grails-Forge-Pack')
        jar.close()

        then:
        assertTaskSuccess('jar', result)
        !entries.contains('META-INF/grails-forge/templates/conf/audit.txt')
        entries.contains('META-INF/grails-forge/templates/Audit.groovy.gsp')
        name == 'acme-trail'
    }

    def "a plugin without a feature pack has none in its jar"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge').deleteDir()

        when:
        def result = executeTask('jar')
        JarFile jar = new JarFile(new File(runner.projectDir, 'build/libs').listFiles().find { it.name.endsWith('.jar') })
        List<String> entries = jar.entries().toList()*.name
        String name = jar.manifest.mainAttributes.getValue('Grails-Forge-Pack')
        jar.close()

        then:
        assertTaskSuccess('jar', result)
        !entries.any { it.startsWith('META-INF/grails-forge') }
        name == null
    }

    def "a descriptor that does not name the pack fails the build"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge/feature.yml').setText('format: 1\nname: Not A Name\n', 'UTF-8')

        when:
        def result = runner.withArguments('jar').buildAndFail()

        then:
        result.output.contains('src/main/forge/feature.yml does not name the feature pack')
    }
}
