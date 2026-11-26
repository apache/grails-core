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

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import groovy.transform.CompileStatic
import org.grails.forge.io.FileSystemOutputHandler
import picocli.CommandLine

/**
 * The commands of the CLI that build, test or look at the project of the current directory, and where
 * they are added to the command line. Most are there in every directory and say so when it is in no
 * project; the two that package and publish a plugin are there in a plugin project only.
 *
 * @since 9.0
 */
@CompileStatic
final class ConsoleCommands {

    /** How many files are looked at for the class of a plugin, at most. */
    public static final int MAX_FILES = 20_000

    private static final int MAX_DEPTH = 32
    private static final String PLUGIN_SOURCES = 'src/main/groovy'
    private static final String PLUGIN_CLASS_ENDING = 'GrailsPlugin.groovy'

    private static volatile boolean interactive

    private ConsoleCommands() {
    }

    /**
     * @return whether the commands run in the interactive shell, where a command that starts something
     * returns to the prompt and does not stay with it
     */
    static boolean isInteractive() {
        return interactive
    }

    /**
     * Says whether the commands run in the interactive shell. The shell says so while it runs.
     */
    static void setInteractive(boolean inShell) {
        interactive = inShell
    }

    /**
     * Adds the commands to a command line.
     *
     * @param commandLine the command line of the CLI
     * @param readsProject whether the project of the current directory is looked at, for the commands
     * that only a plugin project has
     */
    static void addTo(CommandLine commandLine, boolean readsProject) {
        List<Object> commands = [
                new CompileCommand(),
                new CleanCommand(),
                new AssembleCommand(),
                new DependencyReportCommand(),
                new TestAppCommand(),
                new StatsCommand(),
                new BugReportCommand(),
                new InstallCommand(),
                new RunAppCommand(),
                new StopAppCommand(),
                new ConsoleCommand(),
                new ShellCommand(),
                new RunScriptCommand(),
                new RunToolCommand(),
                new UpdateWrapperCommand()
        ] as List<Object>
        if (readsProject) {
            ConsoleProject project = ConsoleProject.find(FileSystemOutputHandler.getDefaultBaseDirectory().toPath())
            // outside a project they are there as the others are, and say that they are commands of a project
            if (project == null || isPlugin(project)) {
                commands.add(new PackagePluginCommand())
                commands.add(new PublishPluginCommand())
            }
        }
        for (Object command : commands) {
            commandLine.addSubcommand(command)
        }
    }

    /**
     * Whether a project is a Grails plugin: it has the class that describes one, a file whose name
     * ends with {@code GrailsPlugin.groovy} under {@code src/main/groovy}.
     */
    static boolean isPlugin(ConsoleProject project) {
        Path sources = project.directory.resolve(PLUGIN_SOURCES)
        if (project.isLinked(PLUGIN_SOURCES) || !Files.isDirectory(sources, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        boolean[] found = [false] as boolean[]
        int[] seen = [0] as int[]
        try {
            Files.walkFileTree(sources, Collections.emptySet(), MAX_DEPTH, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && file.getFileName().toString().endsWith(PLUGIN_CLASS_ENDING)) {
                        found[0] = true
                        return FileVisitResult.TERMINATE
                    }
                    return ++seen[0] > MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException ignored) {
            return false
        }
        return found[0]
    }
}
