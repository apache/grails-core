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
package org.grails.forge.build.gradle

import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir

class GradleRepositorySpec extends Specification {

    @TempDir
    Path tempDir

    def 'GRAILS_REPO_URL overrides accept HTTPS and local repositories'() {
        given:
        String localRepo = tempDir.resolve('local-repo').toString()
        String fileRepo = tempDir.resolve('file-repo').toUri().toString()

        when:
        List<DefaultGradleRepository> repositories = GradleRepository.getDefaultRepositories(
                '8.0.0',
                " ${localRepo} ; ; https://localhost/releases ; ${fileRepo} "
        ).findAll { it instanceof DefaultGradleRepository }
                .collect { it as DefaultGradleRepository }
                .sort { it.order }

        then:
        repositories[0].url == localRepo
        repositories[1].url == 'https://localhost/releases'
        repositories[2].url == fileRepo
    }

    def 'GRAILS_REPO_URL overrides reject HTTP remote repositories'() {
        when:
        GradleRepository.getDefaultRepositories('8.0.0', 'http://localhost/releases')

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Remote GRAILS_REPO_URL repositories must use HTTPS: http://localhost/releases'
    }

    def 'GRAILS_REPO_URL overrides reject malformed remote repositories'() {
        when:
        GradleRepository.getDefaultRepositories('8.0.0', 'https://repo example')

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Invalid GRAILS_REPO_URL repository: https://repo example'
    }

    def 'GRAILS_REPO_URL overrides accept Windows drive local repositories'() {
        when: 'a Windows drive path (backslash, forward-slash or drive-relative) is supplied'
        List<DefaultGradleRepository> repositories = GradleRepository.getDefaultRepositories('8.0.0', windowsPath)
                .findAll { it instanceof DefaultGradleRepository }
                .collect { it as DefaultGradleRepository }

        then: 'it is treated as a local repository, not rejected as a non-HTTPS remote URL'
        repositories.first().url == windowsPath

        where:
        windowsPath << ['C:\\Users\\me\\.m2\\repository', 'C:/Users/me/.m2/repository', 'C:repo', 'C:repo\\subdir', 'C:my repo\\releases']
    }

    def 'GRAILS_REPO_URL overrides support the Gradle repository aliases'() {
        when:
        List<GradleRepository> repositories = GradleRepository.getDefaultRepositories('8.0.0', 'mavenLocal();mavenCentral()').toList()

        then: 'the aliases render as their repository method calls, in override order'
        repositories[0] instanceof MavenLocalRepository
        repositories[0].toSnippet('') == 'mavenLocal()'
        repositories[1] instanceof MavenCentralRepository

        and: 'maven central is not added a second time by the defaults'
        repositories.count { it instanceof MavenCentralRepository } == 1
    }

    def 'GRAILS_REPO_URL overrides treat a single-letter scheme with an authority as remote'() {
        when: 'a URL whose single-letter scheme carries a "//" authority is supplied'
        GradleRepository.getDefaultRepositories('8.0.0', 'c://host/releases')

        then: 'it is not mistaken for a drive letter and is rejected for not using HTTPS'
        def e = thrown(IllegalArgumentException)
        e.message == 'Remote GRAILS_REPO_URL repositories must use HTTPS: c://host/releases'
    }
}
