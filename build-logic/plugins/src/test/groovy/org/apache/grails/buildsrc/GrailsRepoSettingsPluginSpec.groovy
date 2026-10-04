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
package org.apache.grails.buildsrc

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class GrailsRepoSettingsPluginSpec extends Specification {

    private static final String MAVEN_CENTRAL = 'https://repo.maven.apache.org/maven2'
    private static final String GRAILS_RESTRICTED = 'https://repo.grails.org/grails/restricted'
    private static final String APACHE_SNAPSHOTS = 'https://repository.apache.org/content/groups/snapshots'
    private static final String SONATYPE_SNAPSHOTS = 'https://central.sonatype.com/repository/maven-snapshots'
    private static final String APACHE_STAGING = 'https://repository.apache.org/content/groups/staging'
    private static final String PLUGIN_PORTAL = 'https://plugins.gradle.org/m2'

    @TempDir
    File projectDir

    def setup() {
        new File(projectDir, 'settings.gradle').text = '''
            plugins {
                id 'org.apache.grails.buildsrc.repo'
            }

            rootProject.name = 'repo-settings-fixture'

            println 'PLUGIN_REPOSITORIES=' + pluginManagement.repositories.collect { it.url }.join(' ')
            println 'DEPENDENCY_REPOSITORIES=' + dependencyResolutionManagement.repositories.collect { it.url }.join(' ')
            println 'REPOSITORIES_MODE=' + dependencyResolutionManagement.repositoriesMode.get()
        '''
        new File(projectDir, 'build.gradle').text = ''
    }

    void 'Maven Central is consulted before the repo.grails.org fallback for project dependencies'() {
        when:
        BuildResult result = runner().build()
        List<String> repositories = urls(result, 'DEPENDENCY_REPOSITORIES')

        then:
        repositories.size() == 5
        repositories[0].startsWith(MAVEN_CENTRAL)
        repositories[1].startsWith(GRAILS_RESTRICTED)
        repositories[2].startsWith(APACHE_SNAPSHOTS)
        repositories[3].startsWith(SONATYPE_SNAPSHOTS)
        repositories[4].startsWith(APACHE_STAGING)
    }

    void 'plugin repositories start with Maven Central and the plugin portal'() {
        when:
        BuildResult result = runner().build()
        List<String> repositories = urls(result, 'PLUGIN_REPOSITORIES')

        then:
        repositories.size() == 5
        repositories[0].startsWith(MAVEN_CENTRAL)
        repositories[1].startsWith(PLUGIN_PORTAL)
        repositories[2].startsWith(APACHE_SNAPSHOTS)
        repositories[3].startsWith(SONATYPE_SNAPSHOTS)
        repositories[4].startsWith(APACHE_STAGING)
    }

    void 'project level repositories are rejected'() {
        when:
        BuildResult result = runner().build()

        then:
        result.output.contains('REPOSITORIES_MODE=FAIL_ON_PROJECT_REPOS')
    }

    void 'mavenLocal is only added ahead of the remote repositories when GRAILS_INCLUDE_MAVEN_LOCAL is set'() {
        when:
        BuildResult result = runner()
                .withEnvironment(System.getenv() + [GRAILS_INCLUDE_MAVEN_LOCAL: 'true'])
                .build()
        List<String> dependencyRepositories = urls(result, 'DEPENDENCY_REPOSITORIES')
        List<String> pluginRepositories = urls(result, 'PLUGIN_REPOSITORIES')

        then:
        dependencyRepositories.size() == 6
        dependencyRepositories[0].startsWith('file:')
        dependencyRepositories[1].startsWith(MAVEN_CENTRAL)
        dependencyRepositories[2].startsWith(GRAILS_RESTRICTED)
        pluginRepositories.size() == 6
        pluginRepositories[0].startsWith('file:')
        pluginRepositories[1].startsWith(MAVEN_CENTRAL)
    }

    private GradleRunner runner() {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('help', '--offline', '--stacktrace')
                .withPluginClasspath()
    }

    private static List<String> urls(BuildResult result, String label) {
        String line = result.output.readLines().find { it.startsWith("${label}=") }
        assert line != null, "Expected ${label} in build output:\n${result.output}"
        line.substring(label.length() + 1).trim().split(' ').toList()
    }
}
