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
import org.grails.forge.cli.SpecWaits

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
 *
 * <p>The builds do not watch the file system ({@code org.gradle.vfs.watch=false}). A specification
 * changes files of a project between two builds, and a daemon that watches knows of a change by the
 * events of the system, which on a busy machine come late: the next build then took a changed file for
 * unchanged, and reused what the configuration cache had stored for it. Without watching, a daemon looks
 * at the files at the start of each build. A specification of a continuous build, which waits for a
 * change while the build runs, has the builds of its projects watch ({@link #IsolatedGradle(boolean)}).</p>
 */
final class IsolatedGradle {

    /** What a daemon ends by itself after, in milliseconds, when it is left behind all the same. */
    static final int IDLE_MILLIS = 120_000

    private static final boolean WINDOWS = System.getProperty('os.name').toLowerCase(Locale.ROOT).contains('windows')

    final File registry = Files.createTempDirectory('grailsdaemons').toFile()

    /** The JVM argument that the daemons of the projects have and no other process has. */
    final String marker = "-Dgrails.spec.daemon=${UUID.randomUUID()}"

    /** Whether the builds of the projects watch the file system, which only a continuous build needs. */
    final boolean watching

    /** A fixture whose builds do not watch the file system. */
    IsolatedGradle() {
        this(false)
    }

    /**
     * @param watching whether the builds of the projects watch the file system: only for a continuous
     * build, which waits for a change of a file while it runs
     */
    IsolatedGradle(boolean watching) {
        this.watching = watching
    }

    /** The line of {@code gradle.properties} that says whether the builds watch the file system. */
    String getWatch() {
        return watchOf(watching)
    }

    private static String watchOf(boolean watches) {
        return "org.gradle.vfs.watch=${watches}\n"
    }

    /** What the settings of a project begin with: its name, and the line that writes down which process reads them. */
    String getSettings() {
        return "rootProject.name = 'plain'\n" + recording
    }

    /** The line of the settings of a project that writes down which process reads them, for settings that have a name of their own. */
    String getRecording() {
        return "new File('${new File(registry, 'daemons.txt').canonicalPath}') << (ProcessHandle.current().pid() + ' ' + " +
                "ProcessHandle.current().info().startInstant().map { it.toEpochMilli() }.orElse(0L) + '\\n')\n"
    }

    /**
     * The {@code settings.gradle} of a project named {@code plain} whose builds have nothing to compile, in a
     * specification that makes such a project for every feature: with a daemon, one daemon of this fixture runs
     * the builds of all of them, and only the first waits for a JVM of Gradle to start. Without one, and on
     * Windows, where this fixture was not run, every build has a JVM of its own that ends with it.
     *
     * @param withDaemon whether the builds of the project may be run by a daemon
     */
    String settingsOf(boolean withDaemon) {
        return "rootProject.name = 'plain'\n" + recordingOf(withDaemon)
    }

    /**
     * The line {@link #getRecording} for a project whose builds may be run by a daemon, and nothing for any other.
     */
    String recordingOf(boolean withDaemon) {
        return withDaemon && !WINDOWS ? recording : ''
    }

    /**
     * The {@code gradle.properties} that goes with {@link #settingsOf}.
     */
    String propertiesOf(boolean withDaemon) {
        return propertiesOf(withDaemon, watching)
    }

    /**
     * The {@code gradle.properties} of {@link #propertiesOf(boolean)}, for a project whose builds watch the file
     * system or do not whatever the other projects of the fixture do: a continuous build in a specification of
     * other builds.
     *
     * @param watches whether the builds of the project watch the file system
     */
    String propertiesOf(boolean withDaemon, boolean watches) {
        return withDaemon && !WINDOWS ? daemonProperties + watchOf(watches) : 'org.gradle.daemon=false\n' + watchOf(watches)
    }

    /**
     * What the {@code gradle.properties} of a project has so that its builds are run by a daemon of this fixture:
     * the registry, the JVM argument and the time after which a daemon that is left behind ends, and whether the
     * builds watch the file system.
     */
    String getProperties() {
        return daemonProperties + watch
    }

    // the registry, the JVM argument and the time after which a daemon that is left behind ends
    private String getDaemonProperties() {
        return "org.gradle.daemon.registry.base=${registry.canonicalPath}\n" +
                "org.gradle.jvmargs=-Xmx256m ${marker}\n" +
                "org.gradle.daemon.idletimeout=${IDLE_MILLIS}\n"
    }

    /**
     * What is added to the {@code gradle.properties} of a generated application, which has its own JVM arguments
     * for its daemons, so that its builds are run by daemons of this fixture: the registry, the time after which a
     * daemon that is left behind ends, and whether the builds watch the file system.
     */
    String getOfAGeneratedApplication() {
        return "\norg.gradle.daemon.registry.base=${registry.canonicalPath}\norg.gradle.daemon.idletimeout=${IDLE_MILLIS}\n" + watch
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
        new File(dir, 'gradle.properties').text = getProperties() + properties
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
        if (!process.waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)) {
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
        return known.readLines().findAll { String line -> line ==~ /[0-9]+ [0-9]+/ }.unique().collect { String line -> recorded(line) }
                .findAll { ProcessHandle each -> each != null && each.alive && isDaemon(each) }
    }

    // the process a line names by its number, when it is the one that wrote the line: began when the line says
    private static ProcessHandle recorded(String line) {
        List<String> parts = line.split(' ') as List<String>
        ProcessHandle process = ProcessHandle.of(parts[0] as long).orElse(null)
        if (process == null) {
            return null
        }
        long began = process.info().startInstant().map { it.toEpochMilli() }.orElse(0L)
        return began == (parts[1] as long) ? process : null
    }

    // The number of a process that has ended is given to another one, which can be a Gradle daemon of another build that
    // runs on this computer, of another specification too: a process is taken for a daemon of these projects only when it
    // began when the line that names it says (recorded), and is a Gradle daemon.
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
                // a daemon is given ten seconds to end when it is asked to, and is then ended by force
                each.onExit().get(10, TimeUnit.SECONDS)
            } catch (Exception ignored) {
                each.destroyForcibly()
            }
        }
        registry.deleteDir()
    }
}
