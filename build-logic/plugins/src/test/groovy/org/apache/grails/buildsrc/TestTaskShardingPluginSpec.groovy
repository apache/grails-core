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
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class TestTaskShardingPluginSpec extends Specification {

    @TempDir
    Path testProjectDir

    def setup() {
        writeMultiProjectFixture(false)
    }

    def "does nothing when shard properties are absent"() {
        when:
        BuildResult result = run('build')

        then:
        !result.output.contains('TEST_SHARD_MANIFEST')
        !result.output.contains('testShard')
        result.task(':alpha:test').outcome == TaskOutcome.NO_SOURCE
        result.task(':beta:test').outcome == TaskOutcome.NO_SOURCE
        result.task(':gamma:test').outcome == TaskOutcome.NO_SOURCE
        result.task(':disabled:test').outcome == TaskOutcome.SKIPPED
    }

    def "requires valid paired shard properties"() {
        when:
        BuildResult result = runFail(*arguments)

        then:
        result.output.contains(message)

        where:
        arguments                                                    | message
        ['help', '-PtestShardCount=2']                              | 'testShardCount and testShardIndex must be supplied together'
        ['help', '-PtestShardIndex=0']                              | 'testShardCount and testShardIndex must be supplied together'
        ['help', '-PtestShardCount=0', '-PtestShardIndex=0']        | 'testShardCount must be at least 1'
        ['help', '-PtestShardCount=2', '-PtestShardIndex=2']        | 'testShardIndex must be in the range [0, 2)'
        ['help', '-PtestShardCount=two', '-PtestShardIndex=0']      | 'testShardCount must be an integer'
        ['help', '-PtestShardCount=2', '-PtestShardIndex=zero']     | 'testShardIndex must be an integer'
    }

    def "rejects application to a non-root project"() {
        given:
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'child'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        def childDir = testProjectDir.resolve('child').toFile()
        childDir.mkdirs()
        new File(childDir, 'build.gradle').text = """
            plugins {
                id 'org.apache.grails.buildsrc.test-task-sharding'
            }
        """

        when:
        BuildResult result = runFail('help')

        then:
        result.output.contains('TestTaskShardingPlugin must be applied to the root project only.')
    }

    def "assigns Test task candidates deterministically, disjointly, and exhaustively"() {
        given:
        Set<String> baseline = [':alpha:test', ':beta:test', ':disabled:test', ':gamma:test'] as Set

        when:
        Map<Integer, Set<String>> twoWayAssignments = assignmentsFor(2)
        Map<Integer, Set<String>> threeWayAssignments = assignmentsFor(3)

        then:
        assignmentsAreDisjointAndExhaustive(twoWayAssignments, baseline)
        assignmentsAreDisjointAndExhaustive(threeWayAssignments, baseline)

        and: "repeated invocations select the same paths"
        shardPaths(run('testShard', '-PtestShardCount=3', '-PtestShardIndex=1')) == threeWayAssignments[1]
    }

    def "preserves existing false onlyIf predicates and filters full builds to the current shard"() {
        when:
        BuildResult result = run('build', '-PtestShardCount=2', '-PtestShardIndex=0')
        Set<String> selected = shardPaths(result)

        then:
        selected
        result.task(':disabled:test').outcome == TaskOutcome.SKIPPED

        and: "selected tasks remain enabled while the other eligible tasks are skipped"
        [':alpha:test', ':beta:test', ':gamma:test'].each { String path ->
            assert result.task(path).outcome == (selected.contains(path) ? TaskOutcome.NO_SOURCE : TaskOutcome.SKIPPED)
        }
    }

    def "testShard depends only on selected Test task candidates and emits a manifest"() {
        when:
        BuildResult result = run('testShard', '-PtestShardCount=3', '-PtestShardIndex=2')
        Set<String> selected = shardPaths(result)

        then:
        result.task(':testShard').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        result.output.contains('TEST_SHARD_MANIFEST totalCandidates=4 shardIndex=2 shardCount=3 selectedTasks=')

        and:
        [':alpha:test', ':beta:test', ':disabled:test', ':gamma:test'].each { String path ->
            assert result.output.contains("> Task ${path}") == selected.contains(path)
        }
    }

    def "fails when sharding is requested without Test tasks"() {
        given:
        writeEmptyFixture()

        when:
        BuildResult result = runFail('help', '-PtestShardCount=2', '-PtestShardIndex=0')

        then:
        result.output.contains('No Test tasks were found for sharding')
    }

    def "keeps existing onlyIf predicates for execution time"() {
        given:
        writeDynamicOnlyFixture()

        when:
        BuildResult result = run('testShard', '-PtestShardCount=1', '-PtestShardIndex=0')

        then:
        result.task(':dynamic:enableDynamic').outcome == TaskOutcome.SUCCESS
        result.task(':dynamic:dynamicTest').outcome == TaskOutcome.NO_SOURCE
    }

    def "includes Test tasks registered by later projectsEvaluated callbacks"() {
        given:
        writeLifecycleFixture()

        when:
        BuildResult result = run('testShard', '-PtestShardCount=1', '-PtestShardIndex=0')

        then:
        shardPaths(result).contains(':late:lateTest')
        result.task(':late:lateTest').outcome == TaskOutcome.NO_SOURCE
    }

    def "pins the aggregate Test facade to shard zero without scheduling its leaf closure in siblings"() {
        given:
        writeLifecycleFixture()

        when:
        BuildResult sibling = run('testShard', '-PtestShardCount=2', '-PtestShardIndex=1')
        Set<String> selected = shardPaths(sibling)
        String unselectedLeaf = [':alpha:leafTest', ':beta:leafTest', ':dynamic:dynamicTest', ':late:lateTest'].find { String path ->
            TestTaskShardingPlugin.shardFor(path, 2) != 1
        }

        then:
        !selected.contains(':grails-test-report:test')
        !sibling.output.contains('> Task :grails-test-report:test')
        unselectedLeaf != null
        !sibling.output.contains("> Task ${unselectedLeaf}")

        when:
        BuildResult shardZeroBuild = run('build', '-PtestShardCount=2', '-PtestShardIndex=0')

        then:
        shardPaths(shardZeroBuild).contains(':grails-test-report:test')
        shardZeroBuild.task(':grails-test-report:test').outcome == TaskOutcome.NO_SOURCE
        shardZeroBuild.task(':buildMarker').outcome == TaskOutcome.SUCCESS

        and: "the non-Test build marker is not part of sibling testShard jobs"
        !sibling.output.contains('> Task :buildMarker')
    }

    def "rejects duplicate normalized task paths"() {
        when:
        TestTaskShardingPlugin.validateUniqueTaskPaths([':alpha:test', ':alpha:test'])

        then:
        def error = thrown(IllegalArgumentException)
        error.message == 'Duplicate normalized Gradle Test task path: :alpha:test'
    }

    private Map<Integer, Set<String>> assignmentsFor(int shardCount) {
        (0..<shardCount).collectEntries { int shardIndex ->
            BuildResult result = run('testShard', "-PtestShardCount=${shardCount}", "-PtestShardIndex=${shardIndex}")
            [(shardIndex): shardPaths(result)]
        }
    }

    private static void assignmentsAreDisjointAndExhaustive(Map<Integer, Set<String>> assignments, Set<String> baseline) {
        Set<String> union = assignments.values().flatten() as Set
        assert union == baseline
        assignments.each { int shardIndex, Set<String> paths ->
            assignments.each { int otherShardIndex, Set<String> otherPaths ->
                if (shardIndex < otherShardIndex) {
                    assert paths.intersect(otherPaths).empty
                }
            }
        }
    }

    private BuildResult run(String... arguments) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(arguments + ['--stacktrace'])
                .withPluginClasspath()
                .build()
    }

    private BuildResult runFail(String... arguments) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(arguments + ['--stacktrace'])
                .withPluginClasspath()
                .buildAndFail()
    }

    private static Set<String> shardPaths(BuildResult result) {
        String manifest = result.output.readLines().find { it.startsWith('TEST_SHARD_MANIFEST ') }
        assert manifest != null
        String selected = manifest.substring(manifest.indexOf('selectedTasks=') + 'selectedTasks='.length())
        selected ? selected.split(',') as Set : [] as Set
    }

    private void writeMultiProjectFixture(boolean disableAllTests) {
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'alpha', 'beta', 'gamma', 'disabled'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'base'
                id 'org.apache.grails.buildsrc.test-task-sharding'
            }

            tasks.named('build') {
                dependsOn(':alpha:test', ':beta:test', ':gamma:test', ':disabled:test')
            }
        """
        ['alpha', 'beta', 'gamma', 'disabled'].each { String name ->
            def projectDir = testProjectDir.resolve(name).toFile()
            projectDir.mkdirs()
            boolean disabled = disableAllTests || name == 'disabled'
            new File(projectDir, 'build.gradle').text = """
                plugins {
                    id 'java'
                }

                tasks.named('test') {
                    onlyIf { ${!disabled} }
                }
            """
        }
    }

    private void writeEmptyFixture() {
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'base'
                id 'org.apache.grails.buildsrc.test-task-sharding'
            }
        """
    }

    private void writeLifecycleFixture() {
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'alpha', 'beta', 'dynamic', 'grails-test-report', 'late'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            import org.gradle.api.tasks.testing.Test

            plugins {
                id 'base'
                id 'org.apache.grails.buildsrc.test-task-sharding'
            }

            tasks.register('buildMarker') {
                doLast {
                    logger.lifecycle('BUILD_MARKER')
                }
            }
            tasks.named('build') {
                dependsOn('buildMarker', ':grails-test-report:test')
            }
            gradle.projectsEvaluated {
                project(':late').tasks.register('lateTest', Test) {
                    testClassesDirs = files(layout.buildDirectory.dir('late-test-classes'))
                    classpath = files()
                }
            }
        """
        writeTestTask('alpha', 'leafTest')
        writeTestTask('beta', 'leafTest')
        def dynamicDir = testProjectDir.resolve('dynamic').toFile()
        dynamicDir.mkdirs()
        new File(dynamicDir, 'build.gradle').text = """
            import org.gradle.api.tasks.testing.Test

            tasks.register('enableDynamic') {
                doLast {
                    rootProject.file('dynamic-enabled').text = 'enabled'
                }
            }
            tasks.register('dynamicTest', Test) {
                dependsOn('enableDynamic')
                onlyIf {
                    rootProject.file('dynamic-enabled').exists()
                }
                testClassesDirs = files(layout.buildDirectory.dir('dynamic-test-classes'))
                classpath = files()
            }
        """
        def reportDir = testProjectDir.resolve('grails-test-report').toFile()
        reportDir.mkdirs()
        new File(reportDir, 'build.gradle').text = """
            import org.gradle.api.tasks.testing.Test

            tasks.register('test', Test) {
                dependsOn(':alpha:leafTest', ':beta:leafTest', ':dynamic:dynamicTest', ':late:lateTest')
                testClassesDirs = files(layout.buildDirectory.dir('report-test-classes'))
                classpath = files()
            }
        """
        testProjectDir.resolve('late').toFile().mkdirs()
    }

    private void writeDynamicOnlyFixture() {
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'dynamic'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'base'
                id 'org.apache.grails.buildsrc.test-task-sharding'
            }
        """
        def dynamicDir = testProjectDir.resolve('dynamic').toFile()
        dynamicDir.mkdirs()
        new File(dynamicDir, 'build.gradle').text = """
            import org.gradle.api.tasks.testing.Test

            tasks.register('enableDynamic') {
                doLast {
                    rootProject.file('dynamic-enabled').text = 'enabled'
                }
            }
            tasks.register('dynamicTest', Test) {
                dependsOn('enableDynamic')
                onlyIf {
                    rootProject.file('dynamic-enabled').exists()
                }
                testClassesDirs = files(layout.buildDirectory.dir('dynamic-test-classes'))
                classpath = files()
            }
        """
    }

    private void writeTestTask(String projectName, String taskName) {
        def projectDir = testProjectDir.resolve(projectName).toFile()
        projectDir.mkdirs()
        new File(projectDir, 'build.gradle').text = """
            import org.gradle.api.tasks.testing.Test

            tasks.register('${taskName}', Test) {
                testClassesDirs = files(layout.buildDirectory.dir('${taskName}-classes'))
                classpath = files()
            }
        """
    }
}
