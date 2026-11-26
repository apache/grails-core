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

package org.grails.forge.features.pack

import java.nio.file.Files

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf

import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.GeneratedBuild
import org.grails.forge.utils.CommandSpec

/**
 * The tasks of the Grails Gradle plugin that check and test the feature pack of a plugin, run on a
 * generated plugin with the Forge of this build. The plugin tests the tasks against stand-ins of the
 * classes of Forge they start, and Forge tests those classes by themselves; here the task of the
 * one starts the class of the other, so what one gives and the other reads is held end to end: the
 * arguments, the environment, what is written and the status the JVM ends with.
 */
class FeaturePackTasksSpec extends CommandSpec {

    private static final String DESCRIPTOR = '''\
        format: 1
        kind: feature
        templateApi: 1
        name: acme-audit
        compatibility:
          grails: '[9.0.0,10.0.0)'
          forge: '[9.0.0,10.0.0)'
        title: Acme Audit Trail
        description: Adds the Acme audit plugin
        applicationTypes: [web]
        files:
          - from: Audit.groovy.gsp
            to: 'src/main/groovy/{packagePath}/Audit.groovy'
        '''.stripIndent()

    private static final String TEMPLATE = 'package ${project.packageName}\n\n// ${applicationType} ${options.data}\nclass Audit {\n}\n'

    void 'checkForgePack passes for a pack Forge can use, and fails with the problems Forge has with one'() {
        given:
        pluginWithPack()
        File marker = new File(dir, 'build/grails/forge-pack-check/checked')

        when:
        BuildResult checked = executeGradle('checkForgePack')

        then:
        checked.task(':checkForgePack').outcome == TaskOutcome.SUCCESS
        marker.isFile()

        when: 'the descriptor has a key Forge does not read'
        descriptor().text = DESCRIPTOR + 'colour: red\n'
        BuildResult ofDescriptor = fail('checkForgePack')

        then:
        ofDescriptor.task(':checkForgePack').outcome == TaskOutcome.FAILED
        ofDescriptor.output.contains('The feature pack in src/main/forge cannot be used:')
        ofDescriptor.output.contains("  - the descriptor: has the key 'colour'")
        !marker.exists()

        when: 'a template uses what a template has not'
        descriptor().text = DESCRIPTOR
        template().text = 'package ${project.pakageName}\n'
        BuildResult ofTemplate = fail('checkForgePack')

        then:
        ofTemplate.task(':checkForgePack').outcome == TaskOutcome.FAILED
        ofTemplate.output.contains('The feature pack in src/main/forge cannot be used:')
        ofTemplate.output.contains('  - files[0].from: ')
        ofTemplate.output.contains("There is no 'project.pakageName' for a template")
        !marker.exists()
    }

    void 'forgePackTest has Forge generate the fixtures the build names, and builds each with its wrapper'() {
        given:
        pluginWithPack()
        new File(dir, 'build.gradle') << """
            grails {
                forgePackTest {
                    tasks = ['help']
                    arguments = ['--no-daemon']
                    fixture('mongo') {
                        applicationType = 'web'
                        options = [data: 'mongodb']
                        features = ['views-json']
                    }
                    if (providers.gradleProperty('withPlugin').present) {
                        fixture('as-plugin') {
                            applicationType = 'plugin'
                        }
                    }
                }
            }
            // where the Gradle of this build is, so that the wrapper of a fixture does not download it again
            tasks.named('forgePackTest') {
                gradleUserHome = '${gradleUserHome().replace('\\', '\\\\')}'
            }
            """.stripIndent()
        File fixture = new File(dir, 'build/forge-pack-fixtures/mongo')

        when:
        BuildResult tested = executeGradle('forgePackTest')

        then: 'the fixture was generated with the options and the features of the build, and with the feature of the pack'
        tested.task(':forgePackTest').outcome == TaskOutcome.SUCCESS
        new File(fixture, 'src/main/groovy/com/example/Audit.groovy').text.contains('// web mongodb')
        new File(fixture, 'build.gradle').text.contains('mongodb')
        new File(fixture, '.grails/project.yml').text.contains('views-json')
        new File(fixture, '.grails/project.yml').text.contains('acme-audit')

        and: 'and built with its wrapper'
        new File(dir, 'build/forge-pack-test/report.txt').text == 'mongo: built\n'
        new File(dir, 'build/forge-pack-test/mongo/build.log').text.contains('BUILD SUCCESSFUL')

        when: 'a fixture of a type the pack is not for'
        BuildResult refused = fail('forgePackTest', '-PwithPlugin')

        then:
        refused.task(':forgePackTest').outcome == TaskOutcome.FAILED
        refused.output.contains('could not be tested: of 2 fixtures the generator refused 1, and none was built:')
        refused.output.contains('  - as-plugin: ')
        !new File(dir, 'build/forge-pack-test/report.txt').exists()

        when: 'a pack Forge cannot read'
        descriptor().text = DESCRIPTOR + 'colour: red\n'
        BuildResult unreadable = fail('forgePackTest')

        then:
        unreadable.task(':forgePackTest').outcome == TaskOutcome.FAILED
        unreadable.output.contains('The feature pack in src/main/forge cannot be used:')
        unreadable.output.contains("  - the descriptor: has the key 'colour'")
    }

    @IgnoreIf({ os.windows })
    void 'a directory of the pack that is a link is refused by every task of the pack, with the real Forge on the class path'() {
        given:
        pluginWithPack()
        File pack = new File(dir, 'src/main/forge')
        File elsewhere = new File(dir, 'pack-elsewhere')
        assert pack.renameTo(elsewhere)
        Files.createSymbolicLink(pack.toPath(), elsewhere.toPath())

        when:
        Map<String, BuildResult> failed = ['checkForgePack', 'forgePackTest', 'copyForgePack', 'jar'].collectEntries { String task ->
            [(task): fail(task)]
        }

        then: 'no task reads the pack through the link: Forge is not started, and nothing is staged or put into a jar'
        failed.every { String task, BuildResult result ->
            result.output.contains('src/main/forge is a link, which the directory of a feature pack cannot be')
        }
        failed.checkForgePack.task(':checkForgePack').outcome == TaskOutcome.FAILED
        failed.forgePackTest.task(':forgePackTest').outcome == TaskOutcome.FAILED
        failed.copyForgePack.task(':copyForgePack').outcome == TaskOutcome.FAILED
        !new File(dir, 'build/grails/forge-pack-check/checked').exists()
        !new File(dir, 'build/forge-pack-fixtures').exists()
        !new File(dir, 'build/libs').exists()

        when: 'the pack is in the directory itself again'
        assert pack.delete()
        assert elsewhere.renameTo(pack)
        BuildResult checked = executeGradle('checkForgePack')

        then:
        checked.task(':checkForgePack').outcome == TaskOutcome.SUCCESS
    }

    /**
     * A generated plugin with a feature pack, whose build checks the pack with the Forge of this build.
     */
    private void pluginWithPack() {
        generateProjectWithDefaults(ApplicationType.PLUGIN)
        File templates = new File(dir, 'src/main/forge/templates')
        templates.mkdirs()
        descriptor().text = DESCRIPTOR
        template().text = TEMPLATE
        List<String> forge = new File(System.getProperty('forge.packCheck.classpathFile')).readLines('UTF-8')
        new File(dir, 'build.gradle') << """
            dependencies {
                grailsForgePackTooling files(${forge.collect { String path -> "'${path.replace('\\', '\\\\')}'" }.join(', ')})
            }
            """.stripIndent()
    }

    private File descriptor() {
        new File(dir, 'src/main/forge/feature.yml')
    }

    private File template() {
        new File(dir, 'src/main/forge/templates/Audit.groovy.gsp')
    }

    private static String gradleUserHome() {
        System.getenv('GRADLE_USER_HOME') ?: new File(System.getProperty('user.home'), '.gradle').absolutePath
    }

    private BuildResult fail(String... arguments) {
        GeneratedBuild.asFromACommandLine {
            gradleRunner.withProjectDir(dir)
                    .withArguments(GeneratedBuild.arguments() + arguments.toList())
                    .buildAndFail()
        }
    }

    @Override
    String getTempDirectoryPrefix() {
        'featurepack'
    }
}
