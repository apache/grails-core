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
package org.grails.gradle.plugin.commands

import java.util.jar.JarFile

import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper

import org.gradle.testkit.runner.BuildResult

/**
 * A plugin with a tool of each kind publishes two companions: {@code -cli}, with the tools the build
 * runs, which depends on the plugin and on {@code -cli-standalone}, and {@code -cli-standalone}, with the
 * standalone tools, which depends on the tool API and never on the plugin. An application that depends on
 * the plugin gets each in the scope of its kind without a line, and one that takes only the standalone
 * tools has nothing of the plugin on any class path.
 */
class TwoCompanionsSpec extends CommandProjectSpecification {

    private static final String REPOSITORY = 'build/test-repo/org/example/test'

    def "a plugin with both folders publishes -cli and -cli-standalone, each with the classes of its folder and its own dependencies"() {
        given:
        setupCompanions()

        when:
        executeTask(':my-plugin:publishAllPublicationsToTestCaseMavenRepoRepository')

        then: 'the plugin jar has neither tool and names both companions'
        entries('my-plugin') == ['META-INF/MANIFEST.MF', 'com/example/MyPlugin.class'] as Set
        manifest('my-plugin') == ['Grails-Cli-Artifact': 'org.example.test:my-plugin-cli',
                                  'Grails-Cli-Standalone-Artifact': 'org.example.test:my-plugin-cli-standalone']

        and: 'each companion has the classes of its folder'
        entries('my-plugin-cli').contains('com/example/ReportTool.class')
        !entries('my-plugin-cli').any { it.contains('CopyTool') || it.contains('FilesHelper') || it.contains('MyPlugin') }
        entries('my-plugin-cli-standalone').containsAll(['com/example/CopyTool.class', 'com/example/FilesHelper.class'])
        !entries('my-plugin-cli-standalone').any { it.contains('ReportTool') || it.contains('MyPlugin') }

        and: 'the companion -cli depends on the plugin, the tool API and the companion -cli-standalone, in the POM'
        pomDependencies('my-plugin-cli') == ['org.apache.grails:grails-core-cli:0.0.1-test', 'org.example.test:my-plugin-cli-standalone:1.0.0-SNAPSHOT',
                                             'org.example.test:my-plugin:1.0.0-SNAPSHOT']

        and: 'and in the module metadata, without a capability, which a consumer could not resolve'
        moduleDependencies('my-plugin-cli') == ['org.apache.grails:grails-core-cli:0.0.1-test', 'org.example.test:my-plugin-cli-standalone:1.0.0-SNAPSHOT',
                                                'org.example.test:my-plugin:1.0.0-SNAPSHOT']
        moduleRequestsNoCapability('my-plugin-cli')

        and: 'the companion -cli-standalone depends on the tool API, and never on the plugin'
        pomDependencies('my-plugin-cli-standalone') == ['org.apache.grails:grails-core-cli:0.0.1-test']
        moduleDependencies('my-plugin-cli-standalone') == ['org.apache.grails:grails-core-cli:0.0.1-test']

        and: 'the plugin itself depends on neither'
        !published('my-plugin', '.pom').text.contains('-cli')
    }

    def "an application of the same build that depends on the plugin gets each companion in the scope of its kind, without a line"() {
        given:
        setupCompanions()

        when:
        BuildResult result = executeTask(':app:inspectScopes', [':app:inspectClasspaths'])

        then: 'each companion is the capability of its feature of the project of the plugin'
        result.output.contains('grailsCli: project :my-plugin org.example.test:my-plugin-cli')
        result.output.contains('grailsCliStandalone: project :my-plugin org.example.test:my-plugin-cli-standalone')
        !result.output.contains('grailsCli: project :my-plugin org.example.test:my-plugin-cli-standalone')

        and: 'the class path standalone tools are started with has the standalone companion and the tool API, and nothing of the plugin'
        line(result, 'STANDALONE_CLASSPATH') == ['grails-core-cli-0.0.1-test.jar', 'my-plugin-cli-standalone-1.0.0-SNAPSHOT.jar']

        and: 'the class path the build runs tools with has both companions and the plugin'
        line(result, 'CLASSPATH') == ['grails-console-0.0.1-test.jar', 'grails-core-cli-0.0.1-test.jar', 'my-plugin-1.0.0-SNAPSHOT.jar',
                                      'my-plugin-cli-1.0.0-SNAPSHOT.jar', 'my-plugin-cli-standalone-1.0.0-SNAPSHOT.jar']

        and: 'the application runs with the plugin and with nothing of its tools'
        line(result, 'RUNTIME') == ['my-plugin-1.0.0-SNAPSHOT.jar']
    }

    def "an application that depends on the published plugin gets both companions without a line, and a POM-only copy of the repository resolves the same files"() {
        given:
        setupCompanions()
        executeTask(':my-plugin:publishAllPublicationsToTestCaseMavenRepoRepository')
        copyWithoutModuleMetadata(new File(projectDir, 'build/test-repo'), new File(projectDir, 'build/pom-only-repo'))

        when:
        BuildResult result = executeTask(':consumer:inspectScopes', [':consumer:inspectClasspaths'])
        BuildResult pomOnly = executeTask(':pom-consumer:inspectScopes', [':pom-consumer:inspectClasspaths'])

        then: 'each companion is in the scope of its kind, at the version of the plugin'
        result.output.contains('grailsCli: module org.example.test:my-plugin-cli:1.0.0-SNAPSHOT')
        result.output.contains('grailsCliStandalone: module org.example.test:my-plugin-cli-standalone:1.0.0-SNAPSHOT')
        !result.output.contains('grailsCli: module org.example.test:my-plugin-cli-standalone')

        and: 'the class path standalone tools are started with has nothing of the plugin'
        line(result, 'STANDALONE_CLASSPATH') == ['grails-core-cli-0.0.1-test.jar', 'my-plugin-cli-standalone-1.0.0-SNAPSHOT.jar']
        line(result, 'CLASSPATH') == ['grails-console-0.0.1-test.jar', 'grails-core-cli-0.0.1-test.jar', 'my-plugin-1.0.0-SNAPSHOT.jar',
                                      'my-plugin-cli-1.0.0-SNAPSHOT.jar', 'my-plugin-cli-standalone-1.0.0-SNAPSHOT.jar']
        line(result, 'RUNTIME') == ['my-plugin-1.0.0-SNAPSHOT.jar']

        and: 'a repository of POMs and jars alone gives the same'
        pomOnly.output.contains('grailsCli: module org.example.test:my-plugin-cli:1.0.0-SNAPSHOT')
        pomOnly.output.contains('grailsCliStandalone: module org.example.test:my-plugin-cli-standalone:1.0.0-SNAPSHOT')
        ['STANDALONE_CLASSPATH', 'CLASSPATH', 'RUNTIME', 'TEST_RUNTIME'].every { String label ->
            // read from the POM alone, a snapshot jar keeps the name of its time stamp
            line(pomOnly, label).collect { String name -> name.replaceAll(/-\d{8}\.\d{6}-\d+\.jar$/, '-SNAPSHOT.jar') }.sort() == line(result, label)
        }
    }

    def "an application with only the standalone companion in grailsCliStandalone has nothing of the plugin on any class path"() {
        given:
        setupCompanions()
        executeTask(':my-plugin:publishAllPublicationsToTestCaseMavenRepoRepository')

        when:
        BuildResult result = executeTask(':standalone-only:inspectScopes', [':standalone-only:inspectClasspaths'])

        then:
        line(result, 'STANDALONE_CLASSPATH') == ['grails-core-cli-0.0.1-test.jar', 'my-plugin-cli-standalone-1.0.0-SNAPSHOT.jar']
        line(result, 'CLASSPATH') == ['grails-console-0.0.1-test.jar', 'grails-core-cli-0.0.1-test.jar', 'my-plugin-cli-standalone-1.0.0-SNAPSHOT.jar']
        line(result, 'RUNTIME') == []
        !line(result, 'TEST_RUNTIME').any { it.startsWith('my-plugin-1') || it.startsWith('my-plugin-cli-1') }
        !result.output.contains('grailsCli: module org.example.test:my-plugin')
    }

    def "a plugin whose tools are all standalone tools publishes a -cli without tools that brings them, so a build that names -cli by hand has them on the class path of standalone tools"() {
        given:
        setupCompanions()

        when:
        executeTask(':standalone-plugin:publishAllPublicationsToTestCaseMavenRepoRepository')

        then: 'the plugin publishes both companions and names both: -cli has no class and depends on -cli-standalone'
        manifest('standalone-plugin') == ['Grails-Cli-Artifact': 'org.example.test:standalone-plugin-cli',
                                          'Grails-Cli-Standalone-Artifact': 'org.example.test:standalone-plugin-cli-standalone']
        entries('standalone-plugin-cli') == ['META-INF/MANIFEST.MF'] as Set
        pomDependencies('standalone-plugin-cli') == ['org.apache.grails:grails-core-cli:0.0.1-test', 'org.example.test:standalone-plugin-cli-standalone:1.0.0-SNAPSHOT',
                                                     'org.example.test:standalone-plugin:1.0.0-SNAPSHOT']
        entries('standalone-plugin-cli-standalone').containsAll(['com/example/files/CopyFilesTool.class', 'META-INF/grails-cli.factories',
                                                                 'META-INF/grails-tools/com.example.files.CopyFilesTool.json'])
        pomDependencies('standalone-plugin-cli-standalone') == ['org.apache.grails:grails-core-cli:0.0.1-test']

        when: 'an application names the -cli of the plugin in grailsCli, and nothing else of it'
        BuildResult result = executeTask(':cli-line-consumer:inspectScopes', [':cli-line-consumer:inspectClasspaths', ':cli-line-consumer:grailsToolCatalog',
                                                                              ':cli-line-consumer:grailsStandaloneTools'])
        Map catalog = (Map) new JsonSlurper().parse(new File(projectDir, 'cli-line-consumer/.grails/cache/tool-catalog.json'))
        Map record = (Map) new JsonSlurper().parse(new File(projectDir, 'cli-line-consumer/.grails/cache/standalone-tools.json'))

        then: 'the line resolves, and the standalone companion it brings is on the class path standalone tools are started with'
        result.output.contains('grailsCli: module org.example.test:standalone-plugin-cli:1.0.0-SNAPSHOT')
        result.output.contains('grailsCliStandalone: module org.example.test:standalone-plugin-cli-standalone:1.0.0-SNAPSHOT')
        line(result, 'STANDALONE_CLASSPATH') == ['grails-core-cli-0.0.1-test.jar', 'standalone-plugin-cli-standalone-1.0.0-SNAPSHOT.jar']
        line(result, 'CLASSPATH').containsAll(['standalone-plugin-1.0.0-SNAPSHOT.jar', 'standalone-plugin-cli-1.0.0-SNAPSHOT.jar', 'standalone-plugin-cli-standalone-1.0.0-SNAPSHOT.jar'])

        and: 'the tool is listed as a standalone tool, and the command line starts it without Gradle'
        catalog.tools.find { it.name == 'copy-files' }.subMap(['target', 'startsApplication', 'origin']) ==
                [target: 'standalone', startsApplication: false, origin: 'org.example.test:standalone-plugin-cli-standalone:1.0.0-SNAPSHOT']
        record.tools == ['copy-files']
        record.throughGradle == null
        record.classpath.collect { new File(it).name }.sort() == ['grails-core-cli-0.0.1-test.jar', 'standalone-plugin-cli-standalone-1.0.0-SNAPSHOT.jar']
    }

    private void setupCompanions() {
        runner = setupTestResourceProject('tool-companions')
        projectDir = runner.projectDir
        publish('org.apache.grails', 'grails-core-cli', GRAILS_VERSION, [:])
        publish('org.apache.grails', 'grails-console', GRAILS_VERSION, [:])
    }

    private static List<String> line(BuildResult result, String label) {
        String found = result.output.readLines().find { String line -> line.startsWith("${label}=") }
        assert found != null : "no line ${label}"
        String value = found.substring(label.length() + 1)
        value ? value.split(',').toList() : []
    }

    private File published(String artifact, String extension) {
        // a snapshot is published under a time stamp: the latest one of the ending
        File directory = new File(projectDir, "${REPOSITORY}/${artifact}/1.0.0-SNAPSHOT")
        File[] files = directory.listFiles({ File file -> file.name.startsWith("${artifact}-1.0.0-") && file.name.endsWith(extension) &&
                !file.name.substring("${artifact}-1.0.0-".length()).contains('-cli') } as FileFilter)
        assert files : "no ${extension} was published in ${directory}"
        files.sort { it.name }.last()
    }

    private Set<String> entries(String artifact) {
        new JarFile(published(artifact, '.jar')).withCloseable { JarFile jar ->
            jar.entries().toList().findAll { !it.directory }*.name as Set<String>
        }
    }

    private Map<String, String> manifest(String artifact) {
        new JarFile(published(artifact, '.jar')).withCloseable { JarFile jar ->
            jar.manifest.mainAttributes.findAll { it.key.toString().startsWith('Grails-') }
                    .collectEntries { [(it.key.toString()): it.value.toString()] } as Map<String, String>
        }
    }

    private List<String> pomDependencies(String artifact) {
        def pom = new XmlSlurper().parse(published(artifact, '.pom'))
        pom.dependencies.dependency.collect { "${it.groupId}:${it.artifactId}:${it.version}".toString() }.unique().sort()
    }

    private List<String> moduleDependencies(String artifact) {
        Map module = (Map) new JsonSlurper().parse(published(artifact, '.module'))
        ((List<Map>) module.variants).collectMany { Map variant -> (List<Map>) (variant.dependencies ?: []) }
                .collect { Map dependency -> "${dependency.group}:${dependency.module}:${((Map) dependency.version)?.requires}".toString() }.unique().sort()
    }

    private boolean moduleRequestsNoCapability(String artifact) {
        Map module = (Map) new JsonSlurper().parse(published(artifact, '.module'))
        ((List<Map>) module.variants).every { Map variant -> ((List<Map>) (variant.dependencies ?: [])).every { Map dependency -> !dependency.requestedCapabilities } }
    }

    private static void copyWithoutModuleMetadata(File from, File to) {
        from.eachFileRecurse { File file ->
            if (file.isFile() && !file.name.endsWith('.module') && !file.name.contains('.module.')) {
                File target = new File(to, from.toPath().relativize(file.toPath()).toString())
                target.parentFile.mkdirs()
                target.bytes = file.bytes
            }
        }
    }
}
