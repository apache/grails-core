/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.buildsrc

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class GrailsRepoSettingsPluginSpec extends Specification {

    @TempDir
    File projectDir

    def "dependency repositories prefer Central and do not fall back to repo.grails.org"() {
        given:
        new File(projectDir, 'settings.gradle').text = '''
            plugins {
                id 'org.apache.grails.buildsrc.repo'
            }

            def lines = []
            dependencyResolutionManagement.repositories.each { repo ->
                lines << repo.url.toString()
            }
            new File(settingsDir, 'repos.txt').text = lines.join(System.lineSeparator())
        '''
        new File(projectDir, 'build.gradle').text = ''

        when:
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments('--offline', 'help')
                .withEnvironment(System.getenv().findAll { key, value -> key != 'GRAILS_INCLUDE_MAVEN_LOCAL' })
                .build()

        then:
        List<String> urls = new File(projectDir, 'repos.txt').text.readLines()
        urls == [
                'https://repo.maven.apache.org/maven2/',
                'https://repo.gradle.org/gradle/libs-releases',
                'https://plugins.gradle.org/m2',
                'https://repository.apache.org/content/groups/snapshots',
                'https://central.sonatype.com/repository/maven-snapshots',
                'https://repository.apache.org/content/groups/staging',
        ]
        !urls.any { it.contains('repo.grails.org') }
    }
}
