package org.grails.gradle.test

import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

abstract class GradleSpecification extends Specification {
    private static Path basePath

    private static GradleRunner gradleRunner

    void setupSpec() {
        basePath = Files.createTempDirectory("gradle-projects")
        Path testKitDirectory = Files.createDirectories(basePath.resolve('.gradle'))
        gradleRunner = GradleRunner.create()
                .withPluginClasspath()
                .withTestKitDir(testKitDirectory.toFile())
    }

    void setup() {
        gradleRunner.environment?.clear()
        gradleRunner = addEnvironmentVariable(
                "LOCAL_MAVEN_PATH",
                System.getProperty("localMavenPath"),
                gradleRunner
        )

        gradleRunner = setGradleProperty(
                "grailsGradlePluginVersion",
                System.getProperty("grailsGradlePluginVersion"),
                gradleRunner
        )

        gradleRunner = setGradleProperty(
                "groovyVersion",
                System.getProperty("groovyVersion"),
                gradleRunner
        )
    }

    GradleRunner addEnvironmentVariable(String key, String value, GradleRunner runner) {
        Map environment = runner.environment
        if (environment) {
            environment.put(key, value)

            return runner
        } else {
            return runner.withEnvironment([(key): value])
        }
    }

    GradleRunner setGradleProperty(String key, String value, GradleRunner runner) {
        addEnvironmentVariable("ORG_GRADLE_PROJECT_${key}", value, runner)
    }

    void cleanup() {
        basePath.toFile().listFiles().each {
            // Reuse the gradle cache from previous tests
            if (it.name == ".gradle") {
                return
            }

            FileUtils.deleteQuietly(it)
        }
    }

    void cleanupSpec() {
        FileUtils.deleteQuietly(basePath.toFile())
    }

    protected GradleRunner setupTestResourceProject(String type, String projectName, String nestedProject = null) {
        Objects.requireNonNull(projectName, "projectName must not be null")

        Path destinationDir = basePath.resolve(type)
        Files.createDirectories(destinationDir)

        Path sourceProjectDir = Path.of("src/e2eTest/resources/publish-projects/$type/$projectName")
        FileUtils.copyDirectoryToDirectory(sourceProjectDir.toFile(), destinationDir.toFile())

        setupProject(destinationDir.resolve(projectName).resolve(nestedProject ?: '.'))
    }

    protected GradleRunner setupProject(Path projectDirectory) {
        gradleRunner.withProjectDir(projectDirectory.toFile())
    }

    protected Path createProjectDir(String projectName) {
        Objects.requireNonNull(projectName, "projectName must not be null")

        Path destinationDir = basePath.resolve(projectName)
        Files.createDirectories(destinationDir)

        destinationDir
    }

    protected BuildResult executeTask(String taskName, List<String> otherArguments = [], GradleRunner gradleRunner) {
        List arguments = [taskName, "--stacktrace"]
        arguments.addAll(otherArguments)

        gradleRunner.withArguments(arguments).forwardOutput().build()
    }

    protected void assertTaskSuccess(String taskName, BuildResult result) {
        def tasks = result.tasks.find { it.path.endsWith(":${taskName}") }
        if (!tasks) {
            throw new IllegalStateException("No tasks were found for `${taskName}`")
        }

        tasks.each { BuildTask task ->
            if (task.outcome != TaskOutcome.SUCCESS) {
                throw new IllegalStateException("Task $taskName failed with outcome $task.outcome")
            }
        }
    }

    protected void assertBuildSuccess(BuildResult result, List<String> ignoreTaskNames = []) {
        def results = result.tasks.groupBy { it.outcome }

        for (String ignoredTaskName : ignoreTaskNames) {
            for (BuildTask ignoredTask : result.tasks.findAll { it.path.endsWith("${ignoredTaskName}") }) {
                def taskOutComeTasks = results.get(ignoredTask.outcome)
                taskOutComeTasks.remove(ignoredTask)
                if (!taskOutComeTasks) {
                    results.remove(ignoredTask.outcome)
                }
            }
        }

        if (results.keySet().size() != 1) {
            throw new IllegalStateException("Unexpected Task failures: ${results.findAll { it.key != TaskOutcome.SUCCESS }}")
        }
    }
}
