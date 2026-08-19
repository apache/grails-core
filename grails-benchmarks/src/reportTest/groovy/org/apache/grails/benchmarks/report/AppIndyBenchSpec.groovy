/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.grails.benchmarks.report

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class AppIndyBenchSpec extends Specification {

    @TempDir
    Path temporaryDirectory

    void 'parse requires project-dir and applies defaults'() {
        when:
        AppIndyBench.Options options = AppIndyBench.parse(['--project-dir', temporaryDirectory.toString()] as String[])

        then:
        options.projectDir == temporaryDirectory.toAbsolutePath().normalize()
        options.outputDir == options.projectDir.resolve('build').resolve('app-bench')
        options.warmup == 200
        options.samples == 1000
        options.forks == 2
        options.maxWorkers == 4
    }

    void 'parse rejects a missing project-dir'() {
        when:
        AppIndyBench.parse(['--warmup', '10'] as String[])

        then:
        IllegalArgumentException error = thrown()
        error.message.contains('--project-dir is required')
    }

    void 'run invokes six nested builds then compares directory results'() {
        given:
        Path outputDir = temporaryDirectory.resolve('out')
        Path summary = temporaryDirectory.resolve('summary.md')
        List<List<String>> invocations = []
        AppIndyBench.GradleRunner runner = { Path projectDir, List<String> args ->
            invocations.add(args)
            writeDummyResult(args)
        } as AppIndyBench.GradleRunner

        when:
        int exit = AppIndyBench.run(
                [
                        '--project-dir', temporaryDirectory.toString(),
                        '--output-dir', outputDir.toString(),
                        '--warmup', '80',
                        '--samples', '300',
                        '--forks', '2',
                        '--max-workers', '3'
                ] as String[],
                runner,
                new GitHubComments(),
                [GITHUB_STEP_SUMMARY: summary.toString()]
        )

        then:
        exit == 0
        invocations.size() == 6
        invocations[0].contains(':grails-test-examples-latency:integrationTest')
        invocations[0].contains('latencyapp.AppBenchFastPingSpec')
        invocations[0].contains('-PgrailsIndy=false')
        invocations[0].contains('-PappBench=true')
        invocations[0].contains('-PappBenchWarmup=80')
        invocations[0].contains('-PappBenchSamples=300')
        invocations[0].contains('-PappBenchForks=2')
        invocations[0].contains('--no-daemon')
        !invocations[0].contains('--rerun-tasks')
        invocations[0].contains('--max-workers=3')
        invocations[1].contains(':grails-test-examples-app1:integrationTest')
        invocations[1].contains('functionaltests.AppBenchInterceptorDemoSpec')
        invocations[1].contains('-PgrailsIndy=false')
        invocations[2].contains(':grails-test-examples-gsp-layout:integrationTest')
        invocations[2].contains('org.example.grails.layout.AppBenchDemoRenderTextSpec')
        invocations[2].contains('-PgrailsIndy=false')
        invocations[3].contains('-PgrailsIndy=true')
        invocations[3].contains(':grails-test-examples-latency:integrationTest')
        invocations[4].contains('-PgrailsIndy=true')
        invocations[5].contains('-PgrailsIndy=true')
        Files.isRegularFile(outputDir.resolve('noindy').resolve('latency.json'))
        Files.isRegularFile(outputDir.resolve('indy').resolve('gsp-layout.json'))
        Files.isRegularFile(outputDir.resolve('indy-vs-noindy.md'))
        Files.size(summary) > 0
    }

    void 'run fails when a nested build does not write JSON'() {
        given:
        AppIndyBench.GradleRunner runner = { Path projectDir, List<String> args ->
        } as AppIndyBench.GradleRunner

        when:
        int exit = AppIndyBench.run(
                ['--project-dir', temporaryDirectory.toString(), '--output-dir', temporaryDirectory.resolve('missing').toString()] as String[],
                runner
        )

        then:
        exit == 2
    }

    void 'recreateDirectory drops leftover json shards'() {
        given:
        Path leftover = temporaryDirectory.resolve('noindy')
        Files.createDirectories(leftover)
        Files.writeString(leftover.resolve('stale.json'), '[]', StandardCharsets.UTF_8)

        when:
        Path cleaned = AppIndyBench.recreateDirectory(leftover)

        then:
        Files.isDirectory(cleaned)
        !Files.exists(leftover.resolve('stale.json'))
    }

    void 'parse rejects an unknown option'() {
        when:
        AppIndyBench.parse(['--project-dir', temporaryDirectory.toString(), '--bogus', '1'] as String[])

        then:
        IllegalArgumentException error = thrown()
        error.message.contains('unknown option: --bogus')
    }

    void 'wrapper command uses GradleWrapperMain and the project wrapper jar'() {
        given:
        Path projectDir = temporaryDirectory.resolve('proj')
        Path wrapperJar = projectDir.resolve('gradle').resolve('wrapper').resolve('gradle-wrapper.jar')
        Files.createDirectories(wrapperJar.parent)
        Files.writeString(wrapperJar, 'jar', StandardCharsets.UTF_8)
        Path javaHome = Path.of(System.getProperty('java.home'))

        when:
        List<String> command = AppIndyBench.WrapperGradleRunner.commandLine(javaHome, projectDir, [':help'])

        then:
        command[0] == AppIndyBench.WrapperGradleRunner.javaExecutable(javaHome).toString()
        command[1] == '-cp'
        command[2] == wrapperJar.toString()
        command[3] == 'org.gradle.wrapper.GradleWrapperMain'
        command[4] == ':help'
    }

    void 'wrapper command fails when the wrapper jar is missing'() {
        when:
        AppIndyBench.WrapperGradleRunner.commandLine(Path.of(System.getProperty('java.home')), temporaryDirectory, [':help'])

        then:
        IllegalStateException error = thrown()
        error.message.contains('Gradle wrapper jar not found')
    }

    void 'wrapper command fails when java is missing'() {
        given:
        Path projectDir = temporaryDirectory.resolve('nojava')
        Path wrapperJar = projectDir.resolve('gradle').resolve('wrapper').resolve('gradle-wrapper.jar')
        Files.createDirectories(wrapperJar.parent)
        Files.writeString(wrapperJar, 'jar', StandardCharsets.UTF_8)

        when:
        AppIndyBench.WrapperGradleRunner.commandLine(temporaryDirectory.resolve('empty-jre'), projectDir, [':help'])

        then:
        IllegalStateException error = thrown()
        error.message.contains('Java executable not found')
    }

    void 'run fails when the nested Gradle runner throws'() {
        given:
        AppIndyBench.GradleRunner runner = { Path projectDir, List<String> args ->
            throw new IllegalStateException('Nested Gradle exited 1: boom')
        } as AppIndyBench.GradleRunner

        when:
        int exit = AppIndyBench.run(
                ['--project-dir', temporaryDirectory.toString(), '--output-dir', temporaryDirectory.resolve('fail').toString()] as String[],
                runner
        )

        then:
        exit == 2
    }

    private static void writeDummyResult(List<String> args) {
        String outArg = args.find { String arg -> arg.startsWith('-PappBenchOut=') }
        assert outArg
        Path out = Path.of(outArg.substring('-PappBenchOut='.length()))
        Files.createDirectories(out.parent)
        String name = out.fileName.toString().replace('.json', '')
        String benchmark = "appbench.${name}.Dummy.httpGet"
        Files.writeString(out, """[
  {
    "benchmark": "${benchmark}",
    "mode": "avgt",
    "primaryMetric": {
      "score": 100.0,
      "scoreError": 1.0,
      "scoreConfidence": [99.0, 101.0],
      "scoreUnit": "ns/op"
    }
  }
]
""", StandardCharsets.UTF_8)
    }
}
