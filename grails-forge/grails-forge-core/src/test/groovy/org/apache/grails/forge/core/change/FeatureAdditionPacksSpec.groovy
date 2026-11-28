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
package org.apache.grails.forge.core.change

import java.nio.file.Files
import java.nio.file.Path
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackLoader
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils

/**
 * A feature of Grails added to an application that has the feature of a feature pack that is not part
 * of Grails, with that pack given again: held to the lock by the digest of the jar or of the content,
 * and the application generated again with it as it was, whichever repository the jar came from now.
 */
class FeatureAdditionPacksSpec extends AbstractFeatureAdditionSpec {

    static final String DIGEST = 'cd' * 32

    void "a feature is added with the pack of a directory the application has, and is refused with another"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Pack original = pack('packs/acme-audit', 'kept as it is\n')
        Path project = createWith(generator, 'app', [original])
        Path with = createWith(generator, 'with', [original], [FEATURE])

        when: 'the same pack, from another directory'
        ChangePlan plan = addition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP, false, [pack('elsewhere/acme-audit', 'kept as it is\n')])
        plan.apply()

        then: 'the application is the one created with both'
        tree(project) == tree(with)
        ProjectState.read(project).verifyBaseline(project)

        when: 'a pack of the name with another content'
        addition(generator).plan(project, ['cache'], OperatingSystem.LINUX, ConsoleOutput.NOOP, false, [pack('other/acme-audit', 'changed\n')])

        then:
        ProjectChangeException unlike = thrown()
        unlike.message.contains("acme-audit is a feature pack whose content has the SHA-256 ${original.contentSha256}")

        when: 'no pack'
        addition(generator).plan(project, ['cache'], OperatingSystem.LINUX, ConsoleOutput.NOOP, false, [])

        then:
        ProjectChangeException missing = thrown()
        missing.message.contains("acme-audit, a feature pack that was given as a directory or a file whose content has the SHA-256 ${original.contentSha256}")

        when: 'and generating it again needs it as well'
        addition(generator).regenerate(project, OperatingSystem.LINUX)

        then:
        thrown(ProjectChangeException)
        addition(generator).regenerate(project, OperatingSystem.LINUX, [original]) != null
    }

    void "the jar of the lock is the jar of the coordinates and the digest it has, wherever it was read from now"() {
        given: 'an application created with the pack of a jar found in one repository'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Pack found = pack('packs/acme-audit', 'kept as it is\n')
        Pack ofRepository = found.withArtifact(artifact('https://repo.acme.example/maven/', DIGEST))
        Path project = createWith(generator, 'app', [ofRepository])

        when: 'the same jar is given as read from another repository'
        Pack elsewhere = found.withArtifact(artifact('https://mirror.acme.example/maven/', DIGEST))
        ChangePlan plan = addition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP, false, [elsewhere])
        plan.apply()

        then: 'the lock still names the repository it was found in when the application was created'
        new File(project.toFile(), '.grails/forge.lock.yml').text.contains("origin: https://repo.acme.example/maven/\n  coordinates: com.acme:acme-audit-grails\n" +
                "  version: 2.3.0\n  sha256: ${DIGEST}\n")
        new File(project.toFile(), 'build.gradle').text.contains('quartz')

        when: 'a jar with another digest'
        addition(generator).plan(project, ['cache'], OperatingSystem.LINUX, ConsoleOutput.NOOP, false, [found.withArtifact(artifact('https://repo.acme.example/maven/', 'ef' * 32))])

        then:
        ProjectChangeException e = thrown()
        e.message.contains("acme-audit is the feature pack of the jar com.acme:acme-audit-grails:2.3.0 with the SHA-256 ${DIGEST}, and the one that was given is " +
                "com.acme:acme-audit-grails:2.3.0 with the SHA-256 ${'ef' * 32}")

        when: 'the pack of a directory in its place'
        addition(generator).plan(project, ['cache'], OperatingSystem.LINUX, ConsoleOutput.NOOP, false, [found])

        then:
        ProjectChangeException directory = thrown()
        directory.message.contains('and the one that was given is no jar of a repository')
    }

    private static PackArtifact artifact(String repository, String sha256) {
        return new PackArtifact('acme-audit', 'com.acme:acme-audit-grails:2.3.0', repository, 'acme-audit-grails-2.3.0.jar', sha256, null, false)
    }

    private Pack pack(String at, String content) {
        Path pack = Files.createDirectories(directory.resolve(at))
        Files.writeString(pack.resolve('feature.yml'), 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
                "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
                "files:\n  - from: audit.txt\n    to: grails-app/conf/audit.txt\n")
        Files.createDirectories(pack.resolve('templates'))
        Files.writeString(pack.resolve('templates/audit.txt'), content)
        return new PackLoader().fromDirectory(pack)
    }

    private Path createWith(ProjectGenerator generator, String into, List<Pack> packs, List<String> more = []) {
        Path project = Files.createDirectory(directory.resolve(into))
        Project named = NameUtils.parse('com.acme.shop')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, named, new Options(), OperatingSystem.LINUX, ['acme-audit'] + more,
                ConsoleOutput.NOOP, packs)
        generator.generate(ApplicationType.WEB, named, new FileSystemOutputHandler(project.toFile(), ConsoleOutput.NOOP), context)
        return project
    }
}
