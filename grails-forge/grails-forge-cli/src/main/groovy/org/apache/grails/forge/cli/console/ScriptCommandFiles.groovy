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

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic

/**
 * The scripts of an application as commands: the files {@code src/main/scripts/<name>.groovy} of a
 * project, each listed under the name its file name gives it.
 *
 * <p>A script is code of the project, as its build is, and the CLI does not run it: it is run by the
 * build of the project, through the Gradle task {@code runScriptCommand}. So nothing of a script is
 * read here. The names of the files of the one directory are looked at, and of each file whether it
 * is a file; no file is opened, and what a script says of itself (its description) is therefore not
 * known. A directory with more files than are looked at has no commands, since which of them were
 * looked at would be the order the file system has them in; of more scripts than are made commands
 * the first in the order of their names are. The build finds the script to run by the same rule
 * ({@code ScriptCommandRunner} of the command line tier of Grails), and a table of cases that both
 * are tested against keeps the two from drifting apart.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ScriptCommandFiles {

    /** Where the scripts are, in the directory of the project. */
    public static final String LOCATION = 'src/main/scripts'

    /** The Gradle task that runs a script. */
    public static final String TASK = 'runScriptCommand'

    /** How a script is registered, in the description of a command for a tool. */
    public static final String REGISTRATION = 'script'

    /** What runs a script, in the description of a command for a tool: a JVM of the build, without the application. */
    public static final String TARGET = 'gradle'

    /** How many entries of the directory are looked at, at most. */
    public static final int MAX_ENTRIES = 4096

    /** How many scripts are commands, at most. */
    public static final int MAX_SCRIPTS = 256

    private static final String ENDING = '.groovy'
    // what the name of a command can be made from: words of letters and digits, set apart by capitals or by hyphens
    private static final Pattern FILE_NAME = Pattern.compile('[A-Za-z][A-Za-z0-9]*(-[A-Za-z0-9]+)*')
    private static final Pattern NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')
    private static final String OF_WHAT = 'The scripts of the application'

    /** The scripts, by the name of the command each is, in the order of the names. */
    final List<ApplicationCommandFile.Entry> commands

    /** What there is to say about files that are not listed, one line each; nothing of the name of a file is in them. */
    final List<String> diagnostics

    /** Whether every script of the directory that can be a command is among the commands. */
    final boolean complete

    private ScriptCommandFiles(List<ApplicationCommandFile.Entry> commands, List<String> diagnostics, boolean complete) {
        this.commands = commands.asImmutable()
        this.diagnostics = diagnostics.asImmutable()
        this.complete = complete
    }

    /**
     * Lists the scripts of a project. Nothing is thrown, and no file is opened.
     *
     * @param project the directory of the project
     * @return the scripts that are commands, and what there is to say about the others
     */
    static ScriptCommandFiles list(Path project) {
        try {
            Path directory = project
            for (Path step : Path.of(LOCATION)) {
                directory = directory.resolve(step)
                // a directory on the way that is a link leads to files that are not those of the project
                if (Files.isSymbolicLink(directory)) {
                    return notListed("${project.relativize(directory).join('/')} is a link")
                }
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    return none()
                }
            }
            if (directory.toRealPath() != project.toRealPath().resolve(LOCATION)) {
                return notListed("${LOCATION} is not in the directory of the project")
            }
            return read(directory)
        } catch (IOException | RuntimeException ignored) {
            return notListed("${LOCATION} cannot be read")
        }
    }

    /**
     * @param fileName the name of a file
     * @return the name of the command a script in a file of that name is, as the Grails shell made
     * it (a name with a hyphen in lower case, any other with a hyphen in front of each of its
     * words); {@code null} when the file is no script or its name gives no name of a command
     */
    static String commandName(String fileName) {
        if (fileName == null || !fileName.endsWith(ENDING) || fileName.length() > ApplicationCommandFile.MAX_NAME + ENDING.length()) {
            return null
        }
        String base = fileName.substring(0, fileName.length() - ENDING.length())
        if (!FILE_NAME.matcher(base).matches()) {
            return null
        }
        String name = base.contains('-') ? base.toLowerCase(Locale.ROOT) : GrailsNameUtils.getScriptName(base)
        return name != null && name.length() <= ApplicationCommandFile.MAX_NAME && NAME.matcher(name).matches() ? name : null
    }

    private static ScriptCommandFiles none() {
        return new ScriptCommandFiles(Collections.<ApplicationCommandFile.Entry>emptyList(), Collections.<String>emptyList(), true)
    }

    private static ScriptCommandFiles notListed(String reason) {
        return new ScriptCommandFiles(Collections.<ApplicationCommandFile.Entry>emptyList(), ["${OF_WHAT} are not listed: ${reason}".toString()], false)
    }

    private static ScriptCommandFiles read(Path directory) throws IOException {
        // by the name of the command, the name of the file that is first in the order of file names
        TreeMap<String, String> scripts = new TreeMap<>()
        int looked = 0
        int notCommands = 0
        boolean more = false
        DirectoryStream<Path> entries = Files.newDirectoryStream(directory)
        try {
            for (Path entry : entries) {
                if (looked == MAX_ENTRIES) {
                    more = true
                    break
                }
                looked++
                String fileName = entry.getFileName().toString()
                if (!fileName.endsWith(ENDING)) {
                    continue
                }
                String name = commandName(fileName)
                if (name == null || !isFile(entry)) {
                    notCommands++
                    continue
                }
                String other = scripts.get(name)
                if (other != null) {
                    notCommands++
                }
                if (other == null || fileName < other) {
                    scripts.put(name, fileName)
                }
            }
        } finally {
            entries.close()
        }
        if (more) {
            // which of them would be looked at is the order the file system gives them in: none is a command
            return notListed("${LOCATION} has more than the ${MAX_ENTRIES} files that are looked at")
        }
        List<String> said = []
        List<String> names = new ArrayList<String>(scripts.keySet())
        if (names.size() > MAX_SCRIPTS) {
            said.add("${OF_WHAT} are not all listed: ${LOCATION} has more than the ${MAX_SCRIPTS} scripts that are made commands".toString())
            names = names.subList(0, MAX_SCRIPTS)
        }
        if (notCommands > 0) {
            said.add(("${notCommands} of the .groovy files in ${LOCATION} ${notCommands == 1 ? 'is no command' : 'are no commands'}: a script is a file, " +
                    'and not a link, that is named with the letters a to z and digits in words that capitals or hyphens set apart, as in ' +
                    "data-import.groovy or DataImport.groovy, with at most ${ApplicationCommandFile.MAX_NAME} characters, and the only one of its name").toString())
        }
        return new ScriptCommandFiles(names.collect { String name -> ApplicationCommandFile.Entry.script(name) }, said, scripts.size() <= MAX_SCRIPTS)
    }

    // a link, a pipe, a device or a directory is no script, and is not opened to find out
    private static boolean isFile(Path entry) {
        try {
            return Files.readAttributes(entry, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS).isRegularFile()
        } catch (IOException ignored) {
            return false
        }
    }
}
