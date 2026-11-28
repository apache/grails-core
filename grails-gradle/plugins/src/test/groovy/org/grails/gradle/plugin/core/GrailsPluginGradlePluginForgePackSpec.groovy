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

    def "the second build of an unchanged pack does nothing again"() {
        given:
        setupTestResourceProject('plugin-forge-pack')
        executeTask('jar')

        when:
        def result = executeTask('jar')

        then:
        result.output.contains('Reusing configuration cache')
        result.tasks.find { it.path == ':jar' }.outcome == org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
    }

    def "a pack that is spoiled after it was built once fails the next build: #problem"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        executeTask('jar')
        change.call(new File(runner.projectDir, 'src/main/forge'))

        when: 'the configuration of the first build is used again'
        def result = runner.withArguments('jar').buildAndFail()

        then:
        result.output.contains('Reusing configuration cache')
        result.output.contains(message)

        where:
        problem                              | change                                                              || message
        'a file Gradle leaves out of a copy' | { File pack -> new File(pack, 'templates/.gitignore').text = 'x' }  || 'has files Gradle leaves out of a jar, so it cannot name them: templates/.gitignore'
        'a file replaced by a link to it'    | { File pack -> GrailsPluginGradlePluginForgePackSpec.replaceByLink(new File(pack, 'templates/conf/audit.txt')) } || 'has a link, which a pack cannot have: templates/conf/audit.txt'
    }

    static void replaceByLink(File file) {
        File elsewhere = new File(file.parentFile.parentFile.parentFile.parentFile, 'audit-elsewhere.txt')
        elsewhere.bytes = file.bytes
        file.delete()
        java.nio.file.Files.createSymbolicLink(file.toPath(), elsewhere.toPath())
    }

    def "the name of the pack is the one the descriptor gives as YAML, however it is written"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge/feature.yml').setText(
                'description: "first\nname: evil\n rest"\n"name" : >\n  on\nfiles: []\n', 'UTF-8')
        new File(runner.projectDir, 'src/main/forge/templates').deleteDir()

        when:
        executeTask('jar')
        JarFile jar = new JarFile(new File(runner.projectDir, 'build/libs').listFiles().find { it.name.endsWith('.jar') })
        String name = jar.manifest.mainAttributes.getValue('Grails-Forge-Pack')
        jar.close()

        then:
        name == 'on'
    }

    def "a pack with #problem fails the build, since Forge could not read it from the jar"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        change.call(new File(runner.projectDir, 'src/main/forge'))

        when:
        def result = runner.withArguments('jar').buildAndFail()

        then:
        result.output.contains(message)

        where:
        problem                              | change                                                              || message
        'a file Gradle leaves out of a copy' | { File pack -> new File(pack, 'templates/.gitignore').text = 'x' }  || 'has files Gradle leaves out of a jar, so it cannot name them: templates/.gitignore'
        'templates and no descriptor'        | { File pack -> new File(pack, 'feature.yml').delete() }             || 'src/main/forge has files of a feature pack and no feature.yml that is a file'
        'a link'                             | { File pack -> java.nio.file.Files.createSymbolicLink(new File(pack, 'templates/link.txt').toPath(), new File(pack, 'notes.md').toPath()) } || 'has a link, which a pack cannot have: templates/link.txt'
        'a name that is not text'            | { File pack -> new File(pack, 'feature.yml').text = 'name: true\n' } || 'src/main/forge/feature.yml does not name the feature pack'
        'a descriptor that is too large'     | { File pack -> new File(pack, 'feature.yml').text = 'name: acme\n#' + ('x' * 300_000) } || 'has more than the 262144 characters a descriptor can have'
        'a descriptor that is not YAML'      | { File pack -> new File(pack, 'feature.yml').text = 'name: [\n' }   || 'src/main/forge/feature.yml cannot be read as YAML'
    }

    def "a directory of the pack that is a link fails #task, and nothing from where it leads is staged or in a jar"() {
        given: 'the pack somewhere else, and a link to it where the pack of the plugin is looked for'
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File pack = new File(runner.projectDir, 'src/main/forge')
        File elsewhere = new File(runner.projectDir, 'pack-elsewhere')
        assert pack.renameTo(elsewhere)
        java.nio.file.Files.createSymbolicLink(pack.toPath(), elsewhere.toPath())

        when:
        def result = runner.withArguments([task] + arguments).buildAndFail()

        then:
        result.output.contains('src/main/forge is a link, which the directory of a feature pack cannot be')
        !new File(runner.projectDir, 'build/libs').exists()
        !new File(runner.projectDir, 'build/published').exists()
        !new File(runner.projectDir, 'build').exists() || !allFiles(new File(runner.projectDir, 'build')).any { String path -> path.contains('/META-INF/grails-forge/') }

        and: 'the directory the link leads to is as it was'
        new File(elsewhere, 'feature.yml').getText('UTF-8').contains('name: acme-audit')

        where:
        task      | arguments
        'jar'     | []
        'check'   | []
        'publish' | ['-Ppublished']
    }

    private static List<String> allFiles(File directory) {
        List<String> paths = []
        directory.eachFileRecurse { File file ->
            if (file.isFile()) {
                paths << file.path.replace(File.separator, '/')
            }
        }
        paths
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
