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
import java.nio.file.attribute.PosixFilePermissions
import org.apache.grails.forge.core.change.ChangePlan.Action
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.StateLimits
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * What a plan refuses and what it calls a conflict where the project is not what a feature can be
 * added to: a feature that would change an option or take out what was asked for, and a file of the
 * project that is a link, a directory, too large, or not executable as the feature has it. In every
 * case the project stays as it is, to the byte and the mode.
 */
class FeatureAdditionRefusalsSpec extends AbstractFeatureAdditionSpec {

    static final String HOW = 'to change this application by hand, feature add %s --diff shows what would be added to its files and what would be removed, ' +
            'and the record under .grails then does not know of the change. Nothing was changed'

    @Unroll
    void "a feature that would put #changed is refused with what it would take out, and nothing is written"() {
        given: 'No feature of Grails that can be asked for by name changes an option today. A generator that makes the application with another option when it has the feature ' +
                'stands in for one; the application has a file it has only without the feature'
        ProjectGenerator files = generator { GeneratorContext context ->
            if (!context.features.contains(FEATURE)) {
                context.addTemplate('only', new Fixed('only-without.txt', 'x\n'.bytes, false, false))
            }
        }
        ProjectGenerator generator = new OtherSelection(real: files, change: { Options options, List<String> features ->
            [features.contains(FEATURE) ? change.call(options) : options, features]
        })
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)

        when:
        ChangePlan plan = planOf(generator, project)

        then: 'the plan says which option would change, which feature would go, and how an application can have the feature'
        plan.refused
        plan.changedOptions == [changed]
        plan.lostFeatures == lost
        plan.replaced == []
        plan.refusal == "Adding grails-quartz would change what the application was created with (${changed})" +
                (lost ? " and take the feature ${lost[0]} out of it" : '') + ': adding a feature does not change the options of an application. ' +
                "An application has it when it is created with it (--features grails-quartz, ${option}); " + String.format(HOW, FEATURE)

        and: 'what it would remove is shown as a removal'
        plan.files.find { it.path == 'only-without.txt' }.action == Action.REMOVE
        plan.diff().containsAll(['--- a/only-without.txt', '+++ /dev/null', '-x'])

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == plan.refusal
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))
        !Files.exists(home.resolve('.grails'))

        where:
        option               | change                                                                         | changed                                       | lost
        '--servlet jetty'    | { Options it -> it.withServletImpl(ServletImpl.JETTY) }                        | '--servlet jetty in the place of tomcat'      | ['spring-boot-starter-tomcat']
        '--reloading jrebel' | { Options it -> it.withDevelopmentReloading(DevelopmentReloading.JREBEL) }     | '--reloading jrebel in the place of devtools' | ['spring-boot-devtools']
        '--jdk 25'           | { Options it -> it.withJavaVersion(JdkVersion.JDK_25) }                        | '--jdk 25 in the place of 21'                 | []
    }

    @Unroll
    void "a feature the application was created without is refused with the option it was left out with, and nothing is written: #added"() {
        given: 'an application created without two of its defaults'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path project = Files.createDirectory(directory.resolve('app'))
        Project named = NameUtils.parse('com.acme.shop')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, named, new Options(), OperatingSystem.LINUX, [], ConsoleOutput.NOOP,
                Collections.<Pack>emptyList(), ['mockito', 'geb-with-testcontainers'])
        generator.generate(ApplicationType.WEB, named, new FileSystemOutputHandler(project.toFile(), ConsoleOutput.NOOP), context)
        ProjectState state = ProjectState.read(project)
        Map<String, String> before = tree(project)

        expect: 'which of the features were left out'
        FeatureAddition.leftOut(generator.availableFeatures(ApplicationType.WEB, Collections.<Pack>emptyList()), state.manifest, added) == leftOut

        when:
        planOf(generator, project, added)

        then: 'refused as a selection, in words that name the option, and not as selected and excluded'
        FeatureSelectionException e = thrown()
        e.message == said
        e.option == null
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))

        where:
        added                                | leftOut                              || said
        ['mockito']                          | ['mockito']                          || 'The feature mockito was left out with --exclude-features when the application was created: ' +
                                                                                       'adding a feature does not change what the application was created with'
        ['grails-quartz', 'mockito']         | ['mockito']                          || 'The feature mockito was left out with --exclude-features when the application was created: ' +
                                                                                       'adding a feature does not change what the application was created with'
        ['geb-with-testcontainers', 'mockito'] | ['geb-with-testcontainers', 'mockito'] || 'The features geb-with-testcontainers, mockito were left out with --exclude-features when the ' +
                                                                                       'application was created: adding a feature does not change what the application was created with'
    }

    void "a feature that would take out a feature that was asked for, and changes no option, is refused, and nothing is written"() {
        given: 'no feature of Grails takes another out that is not of its group: a generator that does, when it has the feature, stands in for one'
        ProjectGenerator generator = new OtherSelection(real: generator(), change: { Options options, List<String> features ->
            [options, features.contains(FEATURE) ? features - 'asciidoctor' : features]
        })
        Path project = create(generator, 'app', ['asciidoctor'])
        Map<String, String> before = tree(project)

        expect:
        ProjectState.read(project).manifest.requestedFeatures*.name == ['asciidoctor']

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.refused
        plan.changedOptions == []
        plan.lostFeatures == ['asciidoctor']
        plan.replaced == []
        plan.refusal == 'Adding grails-quartz would take the feature asciidoctor out of the application, which was asked for when it was created: ' +
                'a feature that was asked for is not replaced by adding another. An application has it when it is created with it (--features grails-quartz); ' +
                String.format(HOW, FEATURE)

        and: 'what the application would lose is shown'
        plan.diff().any { it.startsWith('-') && it.contains('asciidoctor') }

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == plan.refusal
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))
    }

    @IgnoreIf({ os.windows })
    void "a file the feature takes out that is a link or a directory in the project is a conflict, and nothing is removed"() {
        given:
        ProjectGenerator generator = leavingFilesOut()
        Path project = create(generator, 'app', [])
        Path elsewhere = Files.writeString(directory.resolve('elsewhere.txt'), 'x\n')
        Files.delete(project.resolve('only-without.txt'))
        Files.createSymbolicLink(project.resolve('only-without.txt'), elsewhere)
        Files.delete(project.resolve('old/only/gone.bin'))
        Files.createDirectory(project.resolve('old/only/gone.bin'))
        Files.writeString(project.resolve('old/only/gone.bin/mine.txt'), 'the user wrote this\n')
        Map<String, String> before = tree(project)

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        !plan.refused
        plan.conflicts*.path == ['old/only/gone.bin', 'only-without.txt']
        plan.conflicts*.reason == ['the feature takes this file out of the application, and in the project it is a directory',
                                   'the feature takes this file out of the application, and in the project it is a symbolic link']
        plan.diff().contains('rejected: only-without.txt (the feature takes this file out of the application, and in the project it is a symbolic link)')

        when:
        plan.apply()

        then: 'neither the link, what it leads to, nor the directory and what is in it is touched'
        thrown(IllegalStateException)
        tree(project) == before
        elsewhere.toFile().text == 'x\n'
        project.resolve('old/only/gone.bin/mine.txt').toFile().text == 'the user wrote this\n'
        !Files.exists(project.resolve('.grails/transactions'))
    }

    @IgnoreIf({ os.windows })
    void "a file the feature adds as one that can be executed, and that the project has without that, is a conflict until the project has it so"() {
        given:
        ProjectGenerator generator = withNewFiles()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        Files.createDirectory(project.resolve('bin'))
        Path tool = Files.writeString(project.resolve('bin/tool.sh'), '#!/bin/sh\necho tool\n')
        Files.setPosixFilePermissions(tool, PosixFilePermissions.fromString('rw-r--r--'))
        Map<String, String> before = tree(project)

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts*.path == ['bin/tool.sh']
        plan.conflicts[0].reason == 'the feature adds this file as one that can be executed, and the file of the project cannot be'
        plan.conflicts[0].details == ['Make the file executable and add the feature again']

        when:
        plan.apply()

        then:
        thrown(IllegalStateException)
        tree(project) == before

        when: 'the advice is taken'
        Files.setPosixFilePermissions(tool, PosixFilePermissions.fromString('rwxr-xr-x'))
        ChangePlan again = planOf(generator, project)
        again.apply()

        then: 'the file of the project is the file of the feature'
        again.conflicts == []
        again.files.find { it.path == 'bin/tool.sh' }.action == Action.ADOPT
        tree(project).findAll { it.key != 'bin/tool.sh' } == tree(with).findAll { it.key != 'bin/tool.sh' }
        Files.isExecutable(tool)
        ProjectState.read(project).verifyBaseline(project)
    }

    void "a file of the project that is larger than a generated file can be is a conflict and is not read"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        new RandomAccessFile(project.resolve('build.gradle').toFile(), 'rw').withCloseable { it.setLength(StateLimits.MAX_BASELINE_FILE + 1) }
        Map<String, String> before = tree(project)

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts*.path == ['build.gradle']
        plan.conflicts[0].reason == 'the feature changes this file, and in the project it is larger than a generated file can be'
        plan.diff().contains('rejected: build.gradle (the feature changes this file, and in the project it is larger than a generated file can be)')

        when:
        plan.apply()

        then:
        thrown(IllegalStateException)
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))
    }

    void "the record of an earlier version that a project has beside its state is no file of a feature, and stays as it is"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        String earlier = 'applicationType: web\ndefaultPackage: com.acme\nfeatures: [spock]\n'
        Files.writeString(project.resolve(ProjectState.EARLIER_RECORD), earlier)

        expect: 'the generator writes no such file'
        !Files.exists(with.resolve(ProjectState.EARLIER_RECORD))

        when:
        ChangePlan plan = planOf(generator, project)
        List<String> written = []
        plan.apply { String path -> written << path }

        then:
        plan.files.find { it.path == ProjectState.EARLIER_RECORD } == null
        !written.contains(ProjectState.EARLIER_RECORD)
        project.resolve(ProjectState.EARLIER_RECORD).toFile().text == earlier
        tree(project).findAll { it.key != ProjectState.EARLIER_RECORD } == tree(with)
    }

    void "an application whose manifest has parameters, which this generator writes none of, is not changed and not generated again"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path manifest = project.resolve(ProjectState.MANIFEST)
        Path lock = project.resolve(ProjectState.LOCK)

        expect:
        manifest.toFile().text.endsWith('parameters: {}\n')

        when: 'a manifest with a parameter, and a lock that is of it, as a tool that wrote both would have them'
        Files.writeString(manifest, manifest.toFile().text.replace('parameters: {}\n', 'parameters:\n  region: eu\n'))
        String body = lock.toFile().text.replaceFirst('manifest:\n  sha256: [0-9a-f]{64}', "manifest:\n  sha256: ${sha(manifest)}")
        body = body.substring(0, body.lastIndexOf('seal:\n'))
        Files.writeString(lock, body + "seal:\n  sha256: ${sha(body.getBytes('UTF-8'))}\n")
        Map<String, String> before = tree(project)

        then: 'the state is one that is read'
        ProjectState.read(project).manifest.parameters == [region: 'eu']

        when:
        planOf(generator, project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('The record of the application under .grails is not what this generator writes for the same choices, so a feature cannot be added to it')
        tree(project) == before

        when:
        addition(generator).regenerate(project, OperatingSystem.LINUX)

        then:
        ProjectChangeException again = thrown()
        again.message == e.message
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))
    }

    /**
     * A generator that is asked for another selection than the one it is given: what a feature would
     * do that changed an option, or took a feature out, of the application it is added to.
     */
    static class OtherSelection implements ProjectGenerator {

        @Delegate
        ProjectGenerator real
        Closure<List> change

        @Override
        GeneratorContext createGeneratorContext(ApplicationType applicationType, Project project, Options options, OperatingSystem operatingSystem,
                                                List<String> selectedFeatures, ConsoleOutput consoleOutput, Collection<Pack> packs, Collection<String> excludedFeatures) {
            List changed = change.call(options, selectedFeatures)
            return real.createGeneratorContext(applicationType, project, (Options) changed[0], operatingSystem, (List<String>) changed[1], consoleOutput, packs, excludedFeatures)
        }
    }
}
