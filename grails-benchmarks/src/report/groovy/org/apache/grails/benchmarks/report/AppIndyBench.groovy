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

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@CompileStatic
class AppIndyBench {

    static final List<App> APPS = [
            new App('latency', ':grails-test-examples-latency:integrationTest', 'latencyapp.AppBenchFastPingSpec'),
            new App('app1', ':grails-test-examples-app1:integrationTest', 'functionaltests.AppBenchInterceptorDemoSpec'),
            new App('gsp-layout', ':grails-test-examples-gsp-layout:integrationTest', 'org.example.grails.layout.AppBenchDemoRenderTextSpec')
    ].asImmutable()

    static void main(String[] args) {
        int exit = run(args, new WrapperGradleRunner())
        if (exit != 0) {
            System.exit(exit)
        }
    }

    static int run(String[] args, GradleRunner runner) {
        return run(args, runner, new GitHubComments(), System.getenv())
    }

    static int run(String[] args, GradleRunner runner, CommentPoster poster, Map<String, String> environment) {
        try {
            Options options = parse(args)
            Path noindyDir = recreateDirectory(options.outputDir.resolve('noindy'))
            Path indyDir = recreateDirectory(options.outputDir.resolve('indy'))

            ['false', 'true'].each { String indy ->
                Path modeDir = indy == 'true' ? indyDir : noindyDir
                APPS.each { App app ->
                    Path out = modeDir.resolve(app.name + '.json')
                    runner.run(options.projectDir, gradleArgs(options, app, indy, out))
                    if (!Files.isRegularFile(out)) {
                        throw new IllegalStateException("Missing result file: ${out}")
                    }
                }
            }

            Path report = options.outputDir.resolve('indy-vs-noindy.md')
            int compareExit = JmhCompare.run(
                    ['--base', noindyDir.toString(), '--head', indyDir.toString(), '--output', report.toString()] as String[],
                    poster,
                    environment
            )
            if (compareExit != 0) {
                return compareExit
            }
            appendStepSummary(report, environment)
            return 0
        } catch (Exception error) {
            error.printStackTrace(System.err)
            return 2
        }
    }

    static List<String> gradleArgs(Options options, App app, String indy, Path out) {
        return [
                '--no-daemon',
                "--max-workers=${options.maxWorkers}".toString(),
                app.task,
                '--tests',
                app.tests,
                "-PgrailsIndy=${indy}".toString(),
                '-PappBench=true',
                "-PappBenchWarmup=${options.warmup}".toString(),
                "-PappBenchSamples=${options.samples}".toString(),
                "-PappBenchForks=${options.forks}".toString(),
                "-PappBenchOut=${out.toAbsolutePath()}".toString()
        ]
    }

    static Options parse(String[] args) {
        Set<String> values = ['project-dir', 'output-dir', 'warmup', 'samples', 'forks', 'max-workers'] as Set<String>
        Map<String, String> options = new LinkedHashMap<>()
        for (int index = 0; index < args.length; index++) {
            String option = args[index]
            if (!option.startsWith('--')) {
                throw new IllegalArgumentException("unknown option: ${option}")
            }
            String key = option.substring(2)
            if (!values.contains(key)) {
                throw new IllegalArgumentException("unknown option: --${key}")
            }
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for --${key}")
            }
            options.put(key, args[++index])
        }
        String projectDirValue = options.get('project-dir')
        if (!projectDirValue) {
            throw new IllegalArgumentException('--project-dir is required')
        }
        Path projectDir = Path.of(projectDirValue).toAbsolutePath().normalize()
        Path outputDir = options.containsKey('output-dir')
                ? Path.of(options.get('output-dir')).toAbsolutePath().normalize()
                : projectDir.resolve('build').resolve('app-bench')
        return new Options(
                projectDir,
                outputDir,
                parsePositiveInt(options.getOrDefault('warmup', '200'), 'warmup', true),
                parsePositiveInt(options.getOrDefault('samples', '1000'), 'samples', false),
                parsePositiveInt(options.getOrDefault('forks', '2'), 'forks', false),
                parsePositiveInt(options.getOrDefault('max-workers', '4'), 'max-workers', false)
        )
    }

    private static int parsePositiveInt(String raw, String name, boolean allowZero) {
        int value
        try {
            value = Integer.parseInt(raw)
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("--${name} must be an integer")
        }
        if (value < 0 || (!allowZero && value < 1)) {
            throw new IllegalArgumentException("--${name} must be ${allowZero ? '>= 0' : '>= 1'}")
        }
        return value
    }

    private static void appendStepSummary(Path report, Map<String, String> environment) {
        String summary = environment.get('GITHUB_STEP_SUMMARY')
        if (!summary || !Files.isRegularFile(report)) {
            return
        }
        Files.writeString(
                Path.of(summary),
                Files.readString(report, StandardCharsets.UTF_8),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )
    }

    static Path recreateDirectory(Path directory) {
        if (Files.exists(directory)) {
            Files.walk(directory).withCloseable { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Path path -> Files.deleteIfExists(path) }
            }
        }
        return Files.createDirectories(directory)
    }

    @CompileStatic
    static final class App {
        final String name
        final String task
        final String tests

        App(String name, String task, String tests) {
            this.name = name
            this.task = task
            this.tests = tests
        }
    }

    @CompileStatic
    static final class Options {
        final Path projectDir
        final Path outputDir
        final int warmup
        final int samples
        final int forks
        final int maxWorkers

        Options(Path projectDir, Path outputDir, int warmup, int samples, int forks, int maxWorkers) {
            this.projectDir = projectDir
            this.outputDir = outputDir
            this.warmup = warmup
            this.samples = samples
            this.forks = forks
            this.maxWorkers = maxWorkers
        }
    }

    @CompileStatic
    interface GradleRunner {
        void run(Path projectDir, List<String> args)
    }

    @CompileStatic
    static final class WrapperGradleRunner implements GradleRunner {
        @Override
        void run(Path projectDir, List<String> args) {
            Path javaHome = Path.of(System.getProperty('java.home'))
            List<String> command = commandLine(javaHome, projectDir, args)
            ProcessBuilder processBuilder = new ProcessBuilder(command)
            processBuilder.directory(projectDir.toFile())
            processBuilder.inheritIO()
            processBuilder.environment().put('JAVA_HOME', javaHome.toString())
            Process process = processBuilder.start()
            int exit = process.waitFor()
            if (exit != 0) {
                throw new IllegalStateException("Nested Gradle exited ${exit}: ${command}")
            }
        }

        static List<String> commandLine(Path javaHome, Path projectDir, List<String> args) {
            Path java = javaExecutable(javaHome)
            Path wrapperJar = projectDir.resolve('gradle').resolve('wrapper').resolve('gradle-wrapper.jar')
            if (!Files.isRegularFile(java)) {
                throw new IllegalStateException("Java executable not found: ${java}")
            }
            if (!Files.isRegularFile(wrapperJar)) {
                throw new IllegalStateException("Gradle wrapper jar not found: ${wrapperJar}")
            }
            List<String> command = new ArrayList<>()
            command.add(java.toString())
            command.add('-cp')
            command.add(wrapperJar.toString())
            command.add('org.gradle.wrapper.GradleWrapperMain')
            command.addAll(args)
            return command
        }

        static Path javaExecutable(Path javaHome) {
            String executable = System.getProperty('os.name', '').toLowerCase(Locale.ROOT).contains('win') ? 'java.exe' : 'java'
            return javaHome.resolve('bin').resolve(executable)
        }
    }
}
