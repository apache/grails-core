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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

/**
 * A plugin that keeps a Forge feature pack under {@code src/main/forge} has it checked by Forge, in a
 * JVM of its own, when the plugin is checked.
 *
 * <p>Forge is built from this plugin and cannot be resolved by its tests, so the test project puts a
 * stand-in on {@code grailsForgePackTooling}, as a build can put another Forge there: a class that is
 * started and read as Forge is. What Forge itself says of a pack is tested with Forge.</p>
 */
class GrailsPluginForgePackCheckSpec extends GradleSpecification {

    def "the feature pack is checked in a JVM that has the tooling and nothing else"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult result = executeTask('checkForgePack', ['--info'])

        then:
        assertTaskSuccess('checkForgePack', result)

        and: 'it is told the directory of the pack'
        result.output.contains("stand-in directory: ${new File(runner.projectDir.canonicalFile, 'src/main/forge')}")

        and: 'and has the one jar of the configuration on its class path'
        String classPath = result.output.readLines().find { it.contains('stand-in class path: ') }.split('stand-in class path: ')[1]
        classPath.endsWith('forge-stub.jar')
        !classPath.contains(File.pathSeparator)
    }

    def "checking the plugin checks its feature pack, and building its jar does not"() {
        given:
        setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult jar = executeTask('jar')
        BuildResult check = executeTask('check')

        then:
        !jar.tasks.any { it.path == ':checkForgePack' }
        assertTaskSuccess('checkForgePack', check)
    }

    def "a pack that is as it was is not checked again"() {
        given:
        setupTestResourceProject('plugin-forge-pack')
        executeTask('checkForgePack')

        when:
        BuildResult result = executeTask('checkForgePack')

        then:
        result.output.contains('Reusing configuration cache')
        result.tasks.find { it.path == ':checkForgePack' }.outcome == TaskOutcome.UP_TO_DATE
    }

    def "the problems Forge has with a pack fail the build, each on a line"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge/feature.yml') << 'colour: red\n'
        new File(runner.projectDir, 'src/main/forge/templates/Audit.groovy.gsp') << '${nope}'

        when:
        BuildResult result = runner.withArguments('checkForgePack').buildAndFail()

        then:
        result.output.contains('The feature pack in src/main/forge cannot be used:')
        result.output.contains("  - the descriptor: has the key 'colour', which this version of Forge does not read here")
        result.output.contains("  - files[0].from: The template Audit.groovy.gsp cannot be rendered: There is no 'nope' for a template")

        when: 'the pack is put right'
        File descriptor = new File(runner.projectDir, 'src/main/forge/feature.yml')
        descriptor.setText(descriptor.getText('UTF-8').replace('colour: red\n', ''), 'UTF-8')
        File template = new File(runner.projectDir, 'src/main/forge/templates/Audit.groovy.gsp')
        template.setText(template.getText('UTF-8').replace('${nope}', ''), 'UTF-8')
        result = executeTask('checkForgePack')

        then:
        assertTaskSuccess('checkForgePack', result)
    }

    def "a pack that is spoiled after it was checked once fails the next build: #problem"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        executeTask('checkForgePack')
        change.call(new File(runner.projectDir, 'src/main/forge'))

        when: 'the configuration of the first build is used again'
        BuildResult result = runner.withArguments('checkForgePack').buildAndFail()

        then:
        result.output.contains('Reusing configuration cache')
        result.output.contains(message)

        where:
        problem                               | change                                                                    || message
        'a template'                          | { File pack -> new File(pack, 'templates/Audit.groovy.gsp') << '${nope}' } || 'The template Audit.groovy.gsp cannot be rendered'
        'a file Gradle does not look at'      | { File pack -> new File(pack, 'templates/.gitignore').text = '${nope}' }   || 'The template .gitignore cannot be rendered'
        'a file replaced by a link to it'     | { File pack -> GrailsPluginGradlePluginForgePackSpec.replaceByLink(new File(pack, 'templates/conf/audit.txt')) } || "'conf/audit.txt' is not a file under templates of the pack, or is a link"
        'the descriptor, which is taken away' | { File pack -> new File(pack, 'feature.yml').delete() }                    || 'There is no feature.yml in the directory, or it is not a file'
    }

    def "a plugin without a feature pack has none to check, and resolves no Forge"() {
        given: 'a build that would take Forge from a repository, and has none'
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge').deleteDir()

        when:
        BuildResult result = executeTask('checkForgePack', ['-PforgeOfGrails'])

        then:
        result.tasks.find { it.path == ':checkForgePack' }.outcome == TaskOutcome.SKIPPED
    }

    def "a pack is checked with the Forge of the Grails version of the project, unless the build says otherwise"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')

        when: 'this build has no repository to take it from'
        BuildResult result = runner.withArguments('checkForgePack', '-PforgeOfGrails').buildAndFail()

        then:
        result.output.contains("org.apache.grails.forge:grails-forge-core:${PROJECT_VERSION}")
    }

    def "a pack that is added to a plugin that was built without one is checked"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File pack = new File(runner.projectDir, 'src/main/forge')
        File aside = new File(runner.projectDir, 'forge-aside')
        pack.renameTo(aside)
        executeTask('checkForgePack', ['-PforgeOfGrails'])

        when:
        aside.renameTo(pack)
        BuildResult result = runner.withArguments('checkForgePack', '-PforgeOfGrails').buildAndFail()

        then: 'Forge is asked for, which the build without a pack did not do'
        result.output.contains("org.apache.grails.forge:grails-forge-core:${PROJECT_VERSION}")
    }

    def "a Forge that #failure fails the build and is not taken for a pack without problems"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge/feature.yml') << "stand-in: ${how}\n"

        when:
        BuildResult result = runner.withArguments('checkForgePack').buildAndFail()

        then:
        result.output.contains("The feature pack in src/main/forge could not be checked (Forge ended with ${exit})")
        said == null || result.output.contains(said)

        where:
        failure                             | how      || exit | said
        'ends as no check does'             | 'crash'  || 3    | '  stand-in crashed'
        'names no problem and says it has'  | 'silent' || 1    | null
    }

    def "what Forge writes is shown in bounds, without the control characters it has"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge/feature.yml') << 'stand-in: noisy\n'

        when:
        BuildResult result = runner.withArguments('checkForgePack').buildAndFail()

        then:
        result.output.contains('  - problem 0  [31mred  caf\u00e9')
        result.output.contains('  - problem 59 ')
        !result.output.contains('  - problem 60 ')
        result.output.contains('  - and 40 more lines')
        !result.output.contains('\u001b[31mred')
    }

    def "a directory for a pack that has no descriptor fails the check"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        new File(runner.projectDir, 'src/main/forge/feature.yml').delete()

        when:
        BuildResult result = runner.withArguments('checkForgePack').buildAndFail()

        then:
        result.output.contains('  - There is no feature.yml in the directory, or it is not a file')
    }
}
