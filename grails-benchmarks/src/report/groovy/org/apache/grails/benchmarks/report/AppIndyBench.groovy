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
import java.util.concurrent.TimeUnit

@CompileStatic
class AppIndyBench {

    static final String OUTPUT_DIRECTORY_NAME = 'run'

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
            Path runDir = recreateOwnedDirectory(options.outputDir)
            Path noindyDir = Files.createDirectories(runDir.resolve('noindy'))
            Path indyDir = Files.createDirectories(runDir.resolve('indy'))
            Path report = runDir.resolve('indy-vs-noindy.md')
            boolean nestedFailed = false

            APPS.eachWithIndex { App app, int index ->
                List<String> modes = index % 2 == 0 ? ['false', 'true'] : ['true', 'false']
                modes.each { String indy ->
                    Path modeDir = indy == 'true' ? indyDir : noindyDir
                    Path out = modeDir.resolve(app.name + '.json')
                    try {
                        runner.run(options.projectDir, gradleArgs(options, app, indy, out))
                    } catch (Exception error) {
                        nestedFailed = true
                        System.err.println("Nested Gradle failed for ${app.name} with grailsIndy=${indy}: ${error.message}")
                        error.printStackTrace(System.err)
                    }
                    if (!Files.isRegularFile(out)) {
                        nestedFailed = true
                        System.err.println(missingResultMessage(out))
                    }
                }
            }

            int compareExit = JmhCompare.run(
                    ['--base', noindyDir.toString(), '--head', indyDir.toString(), '--output', report.toString()] as String[],
                    poster,
                    environment
            )
            if (compareExit != 0 || nestedFailed) {
                appendFallbackStepSummary(environment, 'JmhCompare could not produce an app indy benchmark comparison. Nested result files were retained as artifacts for diagnosis.')
            } else {
                appendStepSummary(report, environment)
            }
            return nestedFailed ? 2 : compareExit
        } catch (Exception error) {
            error.printStackTrace(System.err)
            return 2
        }
    }

    static List<String> gradleArgs(Options options, App app, String indy, Path out) {
        return [
                '--no-daemon',
                "--max-workers=${options.maxWorkers}".toString(),
                '--project-cache-dir',
                ownedOutputDirectory(options.outputDir).resolve('project-cache').resolve(indy == 'true' ? 'indy' : 'noindy').resolve(app.name).toString(),
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

    private static void appendFallbackStepSummary(Map<String, String> environment, String message) {
        String summary = environment.get('GITHUB_STEP_SUMMARY')
        if (summary) {
            Files.writeString(
                    Path.of(summary),
                    "## App indy benchmark comparison\n\n${message}\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )
        }
    }

    static String missingResultMessage(Path result) {
        return "Missing benchmark result file: ${result}. Possible causes: -PskipTests, -PskipFunctionalTests, -PonlyCoreTests, the app benchmark spec was ignored because the app.bench system property is missing, or the --tests filter did not match the benchmark spec."
    }

    static Path recreateOwnedDirectory(Path outputDir) {
        Path directory = ownedOutputDirectory(outputDir)
        if (Files.exists(directory)) {
            Files.walk(directory).withCloseable { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Path path -> Files.deleteIfExists(path) }
            }
        }
        return Files.createDirectories(directory)
    }

    static Path ownedOutputDirectory(Path outputDir) {
        Path normalizedOutputDir = outputDir.toAbsolutePath().normalize()
        Path directory = normalizedOutputDir.resolve(OUTPUT_DIRECTORY_NAME).normalize()
        if (directory.parent != normalizedOutputDir) {
            throw new IllegalArgumentException("benchmark output directory must be contained by output directory: ${directory}")
        }
        return directory
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
        static final long NESTED_GRADLE_TIMEOUT_MINUTES = 90L

        @Override
        void run(Path projectDir, List<String> args) {
            Path javaHome = Path.of(System.getProperty('java.home'))
            List<String> command = commandLine(javaHome, projectDir, args, System.getenv())
            ProcessBuilder processBuilder = new ProcessBuilder(command)
            processBuilder.directory(projectDir.toFile())
            processBuilder.inheritIO()
            processBuilder.environment().put('JAVA_HOME', javaHome.toString())
            Process process = processBuilder.start()
            boolean completed = process.waitFor(NESTED_GRADLE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            if (!completed) {
                process.destroyForcibly()
                process.waitFor()
                throw new IllegalStateException("Nested Gradle timed out after ${NESTED_GRADLE_TIMEOUT_MINUTES} minutes: ${command}")
            }
            int exit = process.exitValue()
            if (exit != 0) {
                throw new IllegalStateException("Nested Gradle exited ${exit}: ${command}")
            }
        }

        static List<String> commandLine(Path javaHome, Path projectDir, List<String> args) {
            return commandLine(javaHome, projectDir, args, System.getenv())
        }

        static List<String> commandLine(Path javaHome, Path projectDir, List<String> args, Map<String, String> environment) {
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
            ['DEFAULT_JVM_OPTS', 'JAVA_OPTS', 'GRADLE_OPTS'].each { String name ->
                command.addAll(parseJavaOptions(environment.get(name)))
            }
            command.add('-cp')
            command.add(wrapperJar.toString())
            command.add('org.gradle.wrapper.GradleWrapperMain')
            command.addAll(args)
            return command
        }

        static List<String> parseJavaOptions(String options) {
            if (!options) {
                return Collections.emptyList()
            }
            List<String> parsed = new ArrayList<>()
            StringBuilder current = new StringBuilder()
            char quote = (char) 0
            for (int index = 0; index < options.length(); index++) {
                char character = options.charAt(index)
                if (quote != (char) 0) {
                    if (character == quote) {
                        quote = (char) 0
                    } else {
                        current.append(character)
                    }
                } else if (character == '\'' || character == '"') {
                    quote = character
                } else if (Character.isWhitespace(character)) {
                    if (current.length() > 0) {
                        parsed.add(current.toString())
                        current.setLength(0)
                    }
                } else {
                    current.append(character)
                }
            }
            if (quote != (char) 0) {
                throw new IllegalArgumentException("Unterminated quote in JVM options: ${options}")
            }
            if (current.length() > 0) {
                parsed.add(current.toString())
            }
            return parsed
        }

        static Path javaExecutable(Path javaHome) {
            String executable = System.getProperty('os.name', '').toLowerCase(Locale.ROOT).contains('win') ? 'java.exe' : 'java'
            return javaHome.resolve('bin').resolve(executable)
        }
    }
}
