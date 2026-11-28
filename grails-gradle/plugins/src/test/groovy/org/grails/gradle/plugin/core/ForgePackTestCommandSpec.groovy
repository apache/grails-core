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

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The command {@code forgePackTest} builds a generated application with, and what it shows of an
 * address: the parts of the task that do not need a build to be looked at, and the one for Windows
 * that no test of this build runs.
 */
class ForgePackTestCommandSpec extends Specification {

    @TempDir
    File directory

    void "the wrapper is started by itself, with each argument as one argument, whatever spaces the paths have"() {
        given:
        File application = new File('/work/my plugin/build/forge-pack-fixtures/web')

        when:
        List<String> command = ForgePackTestTask.wrapperCommand(application, false, ['assemble', 'test'], '/Users/a b/.gradle', false, ['--stacktrace'])

        then:
        command == [new File(application, 'gradlew').absolutePath, 'assemble', 'test', '--console=plain', '--gradle-user-home', '/Users/a b/.gradle',
                    '--stacktrace']
    }

    void "on Windows the batch file is the command, not a line for cmd that would have to quote it"() {
        given:
        File application = new File('C:\\work\\my plugin\\build\\forge-pack-fixtures\\web')

        when:
        List<String> command = ForgePackTestTask.wrapperCommand(application, true, ['assemble'], 'C:\\Users\\a b\\.gradle', true, ['-Pname=a value'])

        then: 'the process is given its arguments one by one, and quotes those with spaces itself'
        command.size() == 7
        command[0] == new File(application, 'gradlew.bat').absolutePath
        !command.any { it.equalsIgnoreCase('cmd') || it == '/c' }
        command.drop(1) == ['assemble', '--console=plain', '--gradle-user-home', 'C:\\Users\\a b\\.gradle', '--offline', '-Pname=a value']
    }

    @Unroll
    void "a line is shown without the user and the password of an address: #line"() {
        expect:
        ForgePackTestTask.withoutUserInfo(line) == shown

        where:
        line                                                         || shown
        'https://alice:hunter2@host.example/maven'                   || 'https://host.example/maven'
        "Could not GET 'https://alice:hunter2@host.example/a.pom'."  || "Could not GET 'https://host.example/a.pom'."
        'https://token@host.example/maven and sftp://u:p@other/x'    || 'https://host.example/maven and sftp://other/x'
        'https://host.example/maven'                                 || 'https://host.example/maven'
        'mail alice@host.example about https://host.example/a@b'     || 'mail alice@host.example about https://host.example/a@b'
    }

    void "the repositories stand for themselves as an input of the task by a digest, which tells them apart and does not hold them"() {
        expect:
        ForgePackFixtureRepositories.digest(['https://alice:hunter2@host.example/maven']) ==~ /[0-9a-f]{64}/
        ForgePackFixtureRepositories.digest(['a', 'b']) != ForgePackFixtureRepositories.digest(['ab'])
        ForgePackFixtureRepositories.digest(['a', 'b']) != ForgePackFixtureRepositories.digest(['b', 'a'])
        ForgePackFixtureRepositories.digest([]) == ForgePackFixtureRepositories.digest([])
    }

    void "a repository at a file address is given to the applications as its path, whatever the case of the scheme"() {
        given:
        File repository = new File(directory, 'repository')
        Project project = ProjectBuilder.builder().withProjectDir(new File(directory, 'project')).build()
        project.repositories.maven { MavenArtifactRepository each -> each.url = repository.toURI() }
        project.repositories.maven { MavenArtifactRepository each -> each.url = URI.create('FILE:' + repository.toURI().rawPath + 'upper') }

        when:
        ForgePackFixtureRepositories of = ForgePackFixtureRepositories.of(project.repositories.withType(MavenArtifactRepository))

        then:
        of.used == [repository.absolutePath, new File(directory, 'repositoryupper').absolutePath]
        of.leftOut == []
    }

    @IgnoreIf({ os.windows })
    void "a file address with a host, a share of Windows, is left out with the reason on another system, and fails nothing"() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(new File(directory, 'project')).build()
        project.repositories.maven { MavenArtifactRepository each -> each.url = URI.create('file://server/share/repository') }

        when:
        ForgePackFixtureRepositories of = ForgePackFixtureRepositories.of(project.repositories.withType(MavenArtifactRepository))

        then:
        of.used == []
        of.leftOut == ['The repository of the build at file://server/share/repository is a file address that is no path of this computer, ' +
                               'and the applications of forgePackTest do not get it']
    }

    @Requires({ os.windows })
    void "on Windows a file address with a host is the path of the share"() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(new File(directory, 'project')).build()
        project.repositories.maven { MavenArtifactRepository each -> each.url = URI.create('file://server/share/repository') }

        when:
        ForgePackFixtureRepositories of = ForgePackFixtureRepositories.of(project.repositories.withType(MavenArtifactRepository))

        then:
        of.used == ['\\\\server\\share\\repository']
        of.leftOut == []
    }
}
