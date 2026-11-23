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

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import groovy.transform.CompileStatic

/**
 * What has Gradle run the tasks a command named by the path of its project only where that path is the
 * project. The path is the one the directories give ({@code :apps:shop} for the directory
 * {@code apps/shop} of the root of the build), or the root project where the directory of the project has
 * the settings of a build, and Gradle is started in the directory of the project. The settings of a build
 * can give that directory another path, and the path to another directory: Gradle then runs the tasks of
 * that other project, which can be another application that succeeds.
 *
 * <p>Which project the directory is cannot be told without the settings, and they are a program: a
 * settings plugin, an applied script or a callback the settings register ({@code gradle.settingsEvaluated})
 * can move any project anywhere. So Gradle itself is asked, in the build the command runs and at no other
 * time: it is given an init script that, once Gradle has made the projects of the build from the settings
 * and before any project is configured, compares the directory of the project of the path with the
 * directory Gradle was started in, and fails the build when they are not the same, with a message that
 * says which project the directory is and which directory the path is. Nothing is run then. What is
 * compared is the projects Gradle made, and not what the settings say while they are evaluated: every
 * callback of the settings has run by then, the one an init script registers first among them, and the
 * directory of a project that was made does not change. A directory that is in no project of the build
 * Gradle refuses itself before that, once the callbacks have run, saying that it is not part of the
 * build; nothing is run then either. The script is the same for every build of the project, and Gradle
 * compiles it once for the project; what it costs a build could not be told apart from the time of the
 * same build without it (some 420 ms for a build that has nothing to do, with or without it). Where the
 * settings give the directory the path its directories give, which they do where a project is included
 * by its directory, nothing else differs.</p>
 *
 * <p>A build that reuses an entry of the configuration cache of Gradle evaluates no settings and runs no
 * script, this one neither. Such an entry is one a build stored that the script let through, since Gradle
 * does not reuse what a build that failed stored, and Gradle reuses it only for the same tasks, the same
 * script and settings that read what they read then: the projects are the ones that were compared.</p>
 *
 * <p>The script is a file of {@code .grails/run} of the project ({@link RunDirectory}), which only its
 * owner can write, and it is written there only when what is there is not the script: a build that reads
 * it while another command writes it reads the one or the other. Its path is the same for every build of
 * the project, which the configuration cache of Gradle needs to reuse an entry. Gradle is given it before
 * the arguments of the command, which can end the options of Gradle with a {@code --} of their own.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectPathCheck {

    /** The name of the init script in {@code .grails/run} of the project. */
    static final String FILE = 'project-path.gradle'

    private static final String PLACE = 'PROJECT_PATH'
    // what a path of a project the command line names is made of: the names of directories Gradle takes as they are
    private static final Pattern PATH = Pattern.compile(/:|(:[A-Za-z0-9_][A-Za-z0-9_.-]{0,199})+/)
    private static final int MAX_BYTES = 64 * 1024

    private static final String SCRIPT = '''\
        // Written by the Grails command line, which writes it again when it is not what the command line writes.
        // A command of a Grails project names the tasks of the project by the path of the project in its build:
        // the path its directories give (:apps:shop for the directory apps/shop of the root of the build), or the
        // root project where the directory has the settings of a build. Gradle is started in the directory of the
        // project and given this script with those tasks, and the build fails here, before any project is
        // configured, where the path is not the project of that directory: the settings of a build can give the
        // directory another path, and the path to another directory, whose tasks would run in its place. The
        // projects compared are those Gradle made once the settings, and every callback of them, were done.
        if (gradle.parent == null) {
            gradle.projectsLoaded { loaded ->
                String named = 'PROJECT_PATH'
                File here = loaded.startParameter.currentDir.canonicalFile
                def project = loaded.rootProject.findProject(named)
                File there = project == null ? null : project.projectDir.canonicalFile
                if (there == here) {
                    return
                }
                def found = null
                List left = [loaded.rootProject]
                while (found == null && !left.isEmpty()) {
                    def each = left.remove(0)
                    if (each.projectDir.canonicalFile == here) {
                        found = each
                    }
                    left.addAll(each.childProjects.values())
                }
                String asked = named == ':' ? 'as those of the root project of the build, which its settings make it,' : "by the path its directories give, ${named},"
                String what = found == null ? 'no project of this build is in that directory' : "in this build the project in that directory is ${found.path}"
                String where = project == null ? "the build has no project ${named}" : (named == ':' ? "the root project is in ${there}" : "${named} is the project in ${there}")
                String task = loaded.startParameter.taskNames.find { String requested -> requested.startsWith(named == ':' ? ':' : named + ':') }
                String name = task == null ? 'TASK' : task.substring(task.lastIndexOf(':') + 1)
                String advice
                if (found != null) {
                    advice = "Run the task of this project by its path yourself, as in: grails gradle ${found.path == ':' ? '' : found.path}:${name}"
                } else if (named != ':') {
                    advice = "Include the directory in the settings of the build under that path: include '${named.substring(1)}'"
                } else {
                    advice = 'Run the task of the project you mean by its path yourself, with grails gradle'
                }
                throw new GradleException("The Grails command line ran Gradle for the project in ${here} and named its tasks ${asked} but ${what} (and ${where}). " +
                        "Nothing was run, so that no task of another project runs in its place. ${advice}")
            }
        }
        '''.stripIndent()

    private ProjectPathCheck() {
    }

    /**
     * Writes the script for a project, where it is not there as it is to be, and says how Gradle is given it.
     *
     * @param project the project
     * @param path the path the tasks of the project were named by, {@code :} for the root project of a build
     * @return the arguments that give Gradle the script, which go in front of the arguments of the command:
     * an option of Gradle is one only before a {@code --}
     * @throws IOException with one line, when the script cannot be written
     */
    static List<String> arguments(ConsoleProject project, String path) throws IOException {
        if (!(path ==~ PATH)) {
            // what task names: the path is made of names of directories that are names of projects as they are
            throw new IOException("${GradleRunner.line(path)} is no path the command line names a project by")
        }
        Path run = RunDirectory.of(project)
        Path file = run.resolve(FILE)
        String text = SCRIPT.replace(PLACE, path)
        if (!isWritten(file, text)) {
            write(run, file, text)
        }
        return ['--init-script', file.toString()]
    }

    /**
     * @param project the project
     * @param e why the script could not be written
     * @return the one line that says that Gradle was not run for it, without the full stop
     */
    static String notRun(ConsoleProject project, IOException e) {
        return "Gradle was not run for the project in ${GradleRunner.line(project.directory)}: its tasks are named by the path of the project in its build, " +
                "and Gradle is given ${RunDirectory.LOCATION}/${FILE} to make sure that the path is this project, which cannot be done: ${GradleRunner.line(e.getMessage())}"
    }

    private static boolean isWritten(Path file, String text) {
        try {
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= MAX_BYTES &&
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8) == text
        } catch (IOException ignored) {
            return false
        }
    }

    // into a file of its own first, which then takes the place of what is there, so that no build reads half of it
    private static void write(Path run, Path file, String text) throws IOException {
        Path written = null
        while (written == null) {
            written = RunDirectory.createNew(run, "${FILE}.${UUID.randomUUID()}.new")
        }
        try {
            Files.write(written, text.getBytes(StandardCharsets.UTF_8))
            try {
                Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(written, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (IOException e) {
            Files.deleteIfExists(written)
            throw new IOException("${RunDirectory.LOCATION}/${FILE} cannot be written (${e.getClass().getSimpleName()}: ${GradleRunner.line(e.getMessage())})")
        }
    }
}
