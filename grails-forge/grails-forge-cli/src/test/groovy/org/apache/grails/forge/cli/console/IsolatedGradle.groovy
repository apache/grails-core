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
package org.apache.grails.forge.cli.console

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * What a specification that runs Gradle itself, and not a script in its place, builds its projects
 * with, so that the Gradle daemons it starts are no daemons of whoever runs the specification and
 * none of them is left behind.
 *
 * <p>A project keeps its daemons in a registry of its own ({@code org.gradle.daemon.registry.base}),
 * where {@code --status} and {@code --stop} see no other, and gives them a JVM argument no other
 * build has, so no other build is given such a daemon and this build no other. The wrapper is the one
 * of the build the specification is part of. The Gradle user home is the one there is: a user home of
 * its own would have Gradle download its distribution, and a link from one to the distributions there
 * are has the cleanup of that Gradle delete the ones it has not used.</p>
 *
 * <p>Nothing here asks Gradle to stop daemons. The settings of a project write down the process that
 * reads them, which is the daemon, and the daemons are ended as those processes; one that is not
 * known that way ends by itself when the registry is gone.</p>
 */
final class IsolatedGradle {

    /** What a daemon ends by itself after, in milliseconds, when it is left behind all the same. */
    static final int IDLE_MILLIS = 120_000

    final File registry = Files.createTempDirectory('grailsdaemons').toFile()

    /** The JVM argument that the daemons of the projects have and no other process has. */
    final String marker = "-Dgrails.spec.daemon=${UUID.randomUUID()}"

    /** What the settings of a project begin with: its name, and the line that writes down which process reads them. */
    String getSettings() {
        return "rootProject.name = 'plain'\n" +
                "new File('${new File(registry, 'daemons.txt').canonicalPath}') << (ProcessHandle.current().pid() + '\\n')\n"
    }

    /**
     * Makes a directory a Gradle build with a wrapper, a Grails project by its {@code grails-app} directory.
     *
     * @param dir the directory of the project
     * @param build the directory of the build the specification is part of, whose wrapper is copied
     * @param properties lines for {@code gradle.properties} besides those that keep the daemons apart
     */
    void project(File dir, File build, String properties = '') {
        Files.copy(new File(build, 'gradlew').toPath(), new File(dir, 'gradlew').toPath(), StandardCopyOption.COPY_ATTRIBUTES)
        new File(dir, 'gradle/wrapper').mkdirs()
        new File(build, 'gradle/wrapper').eachFile { File file ->
            Files.copy(file.toPath(), new File(dir, "gradle/wrapper/${file.name}").toPath())
        }
        new File(dir, 'settings.gradle').text = settings
        new File(dir, 'gradle.properties').text = "org.gradle.daemon.registry.base=${registry.canonicalPath}\n" +
                "org.gradle.jvmargs=-Xmx256m ${marker}\n" +
                "org.gradle.daemon.idletimeout=${IDLE_MILLIS}\n" + properties
        new File(dir, 'grails-app').mkdirs()
    }

    /**
     * Runs the Gradle wrapper of a project of this fixture.
     *
     * @return the exit code, -1 when it did not end, and what it wrote
     */
    Map gradle(File dir, String... arguments) {
        assert new File(dir, 'gradle.properties').text.contains(registry.canonicalPath)
        File output = Files.createTempFile(registry.toPath(), 'gradle', '.log').toFile()
        Process process = new ProcessBuilder([new File(dir, 'gradlew').path] + arguments.toList())
                .directory(dir).redirectErrorStream(true).redirectOutput(output)
                .redirectInput(ProcessBuilder.Redirect.from(new File('/dev/null'))).start()
        if (!process.waitFor(300, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return [exit: -1, output: output.text]
        }
        return [exit: process.exitValue(), output: output.text]
    }

    /**
     * @return the Gradle daemons of the projects of this fixture that are running
     */
    List<ProcessHandle> daemons() {
        File known = new File(registry, 'daemons.txt')
        if (!known.isFile()) {
            return []
        }
        return known.readLines().findAll { String line -> line ==~ /[0-9]+/ }.unique().collect { String line -> ProcessHandle.of(line as long).orElse(null) }
                .findAll { ProcessHandle each -> each != null && each.alive && isDaemon(each) }
    }

    // the number of a process that has ended is given to another one
    private static boolean isDaemon(ProcessHandle process) {
        Process ps = new ProcessBuilder('ps', '-ww', '-o', 'command=', '-p', process.pid() as String).redirectErrorStream(true).start()
        String command = ps.inputStream.text
        ps.waitFor()
        return command.contains('org.gradle.launcher.daemon.bootstrap.GradleDaemon')
    }

    /**
     * Ends the daemons of the projects of this fixture, and removes the registry.
     */
    void close() {
        List<ProcessHandle> left = daemons()
        left.each { ProcessHandle each -> each.destroy() }
        left.each { ProcessHandle each ->
            try {
                each.onExit().get(10, TimeUnit.SECONDS)
            } catch (Exception ignored) {
                each.destroyForcibly()
            }
        }
        registry.deleteDir()
    }
}
