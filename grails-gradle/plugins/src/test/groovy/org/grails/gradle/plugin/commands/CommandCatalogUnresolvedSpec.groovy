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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

/**
 * The catalog of the commands is written by every build that compiles the project, which does not need
 * the class path the commands run with. A class path that does not resolve completely, such as a runtime
 * class path with versions that conflict, therefore does not fail that build, with or without the
 * configuration cache: the catalog lists the commands of what resolves, names what does not, and is not
 * complete. Running a command, which needs the class path, still fails.
 */
class CommandCatalogUnresolvedSpec extends CommandProjectSpecification {

    private static final String CATALOG = '.grails/cache/command-catalog.json'

    private static final String MISSING = '''
        dependencies {
            runtimeOnly 'org.example:missing:1.0'
        }
    '''

    // As a plugin for a database whose platform holds its driver at a version that the platform of the
    // application manages at another: the driver is required at both, and neither can be selected.
    private static final String CONFLICT = '''
        dependencies {
            implementation platform('org.example:application-bom:1.0')
            runtimeOnly 'org.example:database-plugin:1.0'
        }
    '''

    private static final String EXPLAINED = 'runtimeClasspath cannot be resolved completely (gradle dependencies --configuration runtimeClasspath says why), ' +
            'so the commands of what is missing from it are not listed: '

    private static final String PLUGIN = '''
        dependencies {
            runtimeOnly 'org.example:report-plugin:1.0'
        }
    '''

    def "a build that compiles the project is not failed by a runtime class path that #problem, and the catalog says what is not listed, from the configuration cache too"() {
        given: 'a plugin with a command on the runtime class path, beside what does not resolve'
        setupCommandProject(dependencies + PLUGIN)
        publishModules()

        when: 'the project is compiled, as Forge compiles a generated application'
        BuildResult result = executeTask('compileTestGroovy')

        then: 'the build succeeds and stores its configuration'
        result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        result.output.contains('Configuration cache entry stored')

        and: 'the catalog lists what resolves, and says what does not'
        Map catalog = catalog()
        catalog.complete == false
        catalog.diagnostics == [EXPLAINED + failure]
        catalog.commands*.name == ['config-report', 'plugin-report']

        and: 'the file is still ASCII on one line, and says nothing of this machine'
        catalogText().bytes.every { byte b -> b == 0x0a || (b >= 0x20 && b <= 0x7e) }
        catalogText().count('\n') == 1
        !catalogText().contains(projectDir.name)

        and: 'the task says so as a warning'
        result.output.contains(EXPLAINED + failure)

        when: 'the same build runs again'
        String written = catalogText()
        BuildResult again = executeTask('compileTestGroovy')

        then: 'it reuses the configuration, and the catalog is up to date and as it was'
        again.output.contains('Configuration cache entry reused')
        again.task(':grailsCommandCatalog').outcome == TaskOutcome.UP_TO_DATE
        catalogText() == written

        when: 'a command is run with that class path'
        BuildResult run = runner.withArguments('runCommand', '--arg=plugin-report').buildAndFail()

        then: 'it fails as Gradle fails a class path that does not resolve'
        run.output.contains("Could not resolve all files for configuration ':runtimeClasspath'")

        where:
        problem                          | dependencies | failure
        'has a module that is not found' | MISSING      | 'Could not find org.example:missing:1.0.'
        // once, although Gradle fails each of the three dependencies on the driver, with the same cause
        'has versions that conflict'     | CONFLICT     | 'Could not resolve org.example:driver:2.0. Component is the target of multiple version constraints with conflicting requirements.'
    }

    def "without the configuration cache a runtime class path that does not resolve gives the same catalog"() {
        given:
        setupCommandProject(CONFLICT + PLUGIN)
        publishModules()
        executeTask('classes')
        String cached = catalogText()
        new File(projectDir, CATALOG).delete()

        when:
        BuildResult result = executeTask('classes', ['--no-configuration-cache'])

        then:
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        catalogText() == cached
        catalog().complete == false
    }

    def "a runtime class path that resolves again gives a complete catalog in the next build that compiles the project"() {
        given: 'a catalog written while the runtime class path has versions that conflict'
        setupCommandProject(CONFLICT + PLUGIN)
        publishModules()
        executeTask('classes')

        expect:
        catalog().complete == false

        when: 'the build is changed to what resolves'
        commands(PLUGIN)
        BuildResult result = executeTask('classes')

        then:
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        catalog().complete == true
        catalog().diagnostics == []
        catalog().commands*.name == ['config-report', 'plugin-report']
    }

    def "a module that was not found and is found now gives a complete catalog in the next build, although the build did not change"() {
        given: 'a catalog written while a module of the runtime class path is not in the repository'
        setupCommandProject(MISSING + PLUGIN)
        publishModules()
        executeTask('classes')

        expect:
        catalog().complete == false

        when: 'the module is published, and the project is compiled again'
        publish('org.example', 'missing', '1.0', [:])
        BuildResult result = executeTask('classes')

        then: 'what the configuration cache stored of the failure is not used again'
        !result.output.contains('Configuration cache entry reused')
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        catalog().complete == true
        catalog().diagnostics == []
    }

    private void publishModules() {
        publish('org.example', 'report-plugin', '1.0', [
                (CLI_FACTORIES): registrations('org.example.PluginReportCommand'),
                (descriptorPath('org.example.PluginReportCommand')): descriptor('org.example.PluginReportCommand', 'plugin-report', 'Reports'),
        ])
        publish('org.example', 'driver', '1.0', [:])
        publish('org.example', 'driver', '2.0', [:])
        // the platform of the application: a Maven BOM, whose versions are required and not strict
        File bom = new File(projectDir, 'maven-repo/org/example/application-bom/1.0/application-bom-1.0.pom')
        bom.parentFile.mkdirs()
        bom.text = '''\
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>application-bom</artifactId>
              <version>1.0</version>
              <packaging>pom</packaging>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>org.example</groupId>
                    <artifactId>driver</artifactId>
                    <version>2.0</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
            '''.stripIndent()
        // the platform of the plugin, which holds the driver at the version the plugin needs
        Map<String, Object> strict = [group: 'org.example', module: 'driver', version: [strictly: '1.0', requires: '1.0']]
        publishWithMetadata('database-bom', [
                variant('apiElements', [(CATEGORY): 'platform', (USAGE): 'java-api'], [dependencyConstraints: [strict]]),
                variant('runtimeElements', [(CATEGORY): 'platform', (USAGE): 'java-runtime'], [dependencyConstraints: [strict]]),
        ])
        // the plugin, which imports its platform as the Grails plugins for a database do
        File jar = new File(projectDir, 'maven-repo/org/example/database-plugin/1.0/database-plugin-1.0.jar')
        jar.parentFile.mkdirs()
        writeJar(jar, [:])
        publishWithMetadata('database-plugin', [
                variant('runtimeElements', [(CATEGORY): 'library', (USAGE): 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.dependency.bundling': 'external'], [
                        dependencies: [
                                [group: 'org.example', module: 'database-bom', version: [requires: '1.0'], attributes: [(CATEGORY): 'platform'], endorseStrictVersions: true],
                                [group: 'org.example', module: 'driver', version: [requires: '1.0']],
                        ],
                        files: [[name: jar.name, url: jar.name, size: jar.length(), sha1: jar.bytes.digest('SHA-1'), md5: jar.bytes.digest('MD5')]],
                ]),
        ])
    }

    private static final String CATEGORY = 'org.gradle.category'
    private static final String USAGE = 'org.gradle.usage'

    private static Map<String, Object> variant(String name, Map<String, String> attributes, Map<String, Object> content) {
        [name: name, attributes: attributes] + content
    }

    // a module with Gradle module metadata, which a POM cannot have: strict versions and platforms
    private void publishWithMetadata(String artifact, List<Map<String, Object>> variants) {
        File directory = new File(projectDir, "maven-repo/org/example/${artifact}/1.0")
        directory.mkdirs()
        new File(directory, "${artifact}-1.0.pom").text = """\
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <!-- do_not_remove: published-with-gradle-metadata -->
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>${artifact}</artifactId>
              <version>1.0</version>
            </project>
            """.stripIndent()
        new File(directory, "${artifact}-1.0.module").text = JsonOutput.toJson([
                formatVersion: '1.1',
                component    : [group: 'org.example', module: artifact, version: '1.0'],
                variants     : variants,
        ])
    }

    private String catalogText() {
        new File(projectDir, CATALOG).getText('US-ASCII')
    }

    private Map catalog() {
        (Map) new JsonSlurper().parseText(catalogText())
    }
}
