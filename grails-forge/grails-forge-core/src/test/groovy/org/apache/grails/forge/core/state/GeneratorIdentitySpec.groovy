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
package org.apache.grails.forge.core.state

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The build of the generator that wrote the state of a project, which the lock names beside its
 * version, and what a merge of two branches that both changed the state leaves of it.
 */
class GeneratorIdentitySpec extends ApplicationContextSpec {

    @TempDir
    Path directory

    void "the generator knows its build, by a digest of what it generates from"() {
        expect:
        VersionInfo.generatorSha256 ==~ /[0-9a-f]{64}/
    }

    void "the lock names the build of the generator that wrote it, after its version"() {
        given:
        Path project = generate('shop', [])
        String lock = project.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8')

        expect:
        lock.contains("generator:\n  name: grails-forge\n  version: ${VersionInfo.version}\n  sha256: ${VersionInfo.generatorSha256}\ngrailsVersion: ".toString())
        ProjectState.read(project).lock.generatorSha256 == VersionInfo.generatorSha256
    }

    void "a lock that a build before the identity was recorded wrote has none, and reads"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lock, sealed(lock.toFile().getText('UTF-8').replace("  sha256: ${VersionInfo.generatorSha256}\ngrailsVersion: ", 'grailsVersion: ')))

        when:
        ProjectState state = ProjectState.read(project)

        then:
        state.lock.generatorSha256 == null
        state.lock.generatorVersion == VersionInfo.version
        !lock.toFile().getText('UTF-8').contains(VersionInfo.generatorSha256)
    }

    void "a lock whose identity of the build is no digest is refused"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lock, sealed(lock.toFile().getText('UTF-8').replace("  sha256: ${VersionInfo.generatorSha256}\n", '  sha256: snapshot-42\n')))

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == ".grails/forge.lock.yml: generator.sha256 must be a SHA-256 digest in hexadecimal, but is 'snapshot-42'"
    }

    void "the identity is sealed with the rest of the lock"() {
        given:
        Path project = generate('shop', [])
        Path lock = project.resolve('.grails/forge.lock.yml')
        Files.writeString(lock, lock.toFile().getText('UTF-8').replace("  sha256: ${VersionInfo.generatorSha256}\n", "  sha256: ${'ab' * 32}\n"))

        when:
        ProjectState.read(project)

        then:
        ProjectStateException e = thrown()
        e.message == '.grails/forge.lock.yml was changed after it was written: it does not have the digest it ends with'
    }

    @Unroll
    void "a state with the markers of a merge conflict in #file says how to have one that reads again"() {
        given:
        Path project = generate('shop', [])
        Path path = project.resolve(file)
        List<String> lines = path.toFile().getText('UTF-8').readLines()
        // what Git leaves where both branches changed the same line
        lines.addAll(2, ['<<<<<<< HEAD', lines[1], '=======', lines[1].reverse(), '>>>>>>> other'])
        lines.remove(1)
        Files.writeString(path, lines.join('\n') + '\n')

        when:
        read.call(project)

        then:
        ProjectStateException e = thrown()
        e.message == "${file} has the markers of a merge conflict in it (line 2). The files of .grails hold the digests of each other and are not merged " +
                'line by line: take them as one side of the merge committed them, as git restore --source=HEAD --staged --worktree -- .grails does for ' +
                'this branch, resolve the conflicts of the other files, and then add the features the other side added with feature add'

        where:
        file                         | read
        '.grails/project.yml'        | { Path it -> ProjectState.read(it) }
        '.grails/forge.lock.yml'     | { Path it -> ProjectState.read(it) }
        '.grails/baseline/index.yml' | { Path it -> ProjectState.read(it) }
        '.grails/project.yml'        | { Path it -> ProjectState.readSelection(it) }
        '.grails/forge.lock.yml'     | { Path it -> ProjectState.readSelection(it, true) }
    }

    private Path generate(String name, List<String> features) {
        Path project = Files.createDirectory(directory.resolve('app'))
        beanContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse("com.acme.${name}"), new Options(), OperatingSystem.LINUX,
                features, new FileSystemOutputHandler(project.toFile(), ConsoleOutput.NOOP), ConsoleOutput.NOOP)
        return project
    }

    private static String sealed(String lock) {
        String body = lock.substring(0, lock.lastIndexOf('seal:\n'))
        return body + 'seal:\n  sha256: ' + sha256(body.getBytes('UTF-8')) + '\n'
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
    }
}
