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
package org.grails.forge.cli.command

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest
import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

import org.grails.forge.io.ConsoleOutput

/**
 * Finds the commands of the Grails project in a directory: those of the application and of its
 * plugins, which {@code runCommand} runs.
 *
 * <p>The project's build describes them in a manifest, written by the Grails Gradle plugin's
 * {@code generateCommandManifest} task, which also names the directories and files that decide the
 * commands. Each time the build runs, the files of those directories are recorded, with their times and
 * sizes; the manifest is read as long as they are unchanged, and otherwise the task is run first, so a
 * plugin added to the build brings its commands with it. When the build fails, the commands of the last
 * manifest are kept, and the build is not run for them again until one of those files changes, or one of
 * the project's main sources, which are then recorded too, since any of them can have broken the build.</p>
 */
@CompileStatic
class ProjectCommands {

    /** The task of the Grails Gradle plugin that writes the manifest */
    static final String MANIFEST_TASK = 'generateCommandManifest'

    /** The project property that tells the task where to write the manifest */
    static final String MANIFEST_PROPERTY = 'grailsCommandManifest'

    /** Where the manifest is kept, relative to the project directory */
    static final String MANIFEST_PATH = 'build/grails/commands.json'

    /**
     * Where the files the commands depend on are recorded when the build runs, relative to the project
     * directory: outside the build directory, so that cleaning the build does not forget a failed build
     */
    static final String RECORD_PATH = '.gradle/grails/commands.record'

    /** Kept next to the record when the build could not write the manifest, so it is not run again for nothing */
    static final String FAILED_PATH = '.gradle/grails/commands.failed'

    /**
     * A copy of the inputs the last manifest named, kept next to the record, so they are still watched when
     * cleaning the build removed the manifest
     */
    static final String INPUTS_PATH = '.gradle/grails/commands.inputs'

    /** The newest version of the manifest's format this CLI reads */
    static final int FORMAT_VERSION = 1

    private static final List<String> BUILD_FILE_SUFFIXES = ['.gradle', '.gradle.kts', '.properties', '.toml']

    /** What builds and tools write, rather than read, outside a directory of sources */
    private static final List<String> OUTPUT_DIRS = ['build', '.gradle', '.kotlin', 'out', 'bin', '.git', '.idea', '.settings']

    /** Directories whose files are sources, so a package below them named like an output is read */
    private static final List<String> SOURCE_DIRS = ['src', 'grails-app']

    /** How the file of a command is named, after the class whose name gives the command its own */
    private static final List<String> COMMAND_FILE_SUFFIXES = ['Command.groovy', 'Command.java']

    /** What makes a directory a project or an included build of the build, when no manifest says which they are */
    private static final List<String> PROJECT_FILES = ['build.gradle', 'build.gradle.kts', 'settings.gradle',
                                                       'settings.gradle.kts']

    /** What makes a directory a project of the build too, when no manifest says which they are */
    private static final List<String> PROJECT_DIRECTORIES = ['src/main', 'grails-app']

    private static final List<String> SETTINGS_FILES = ['settings.gradle', 'settings.gradle.kts']

    /** The line of the failure that holds the digest of the manifest there was */
    private static final String MANIFEST_LINE = 'manifest\t'

    /** A string in quotes, in Groovy or Kotlin, which a settings file names a project's path or directory with */
    private static final Pattern STRING_LITERAL = Pattern.compile(/(['"])((?:\\.|(?!\1)[^\\\r\n])*)\1/)

    /** What tools write anywhere, sources included */
    private static final List<String> TOOL_FILES = ['node_modules', '.DS_Store']

    private final File projectDir
    private final ProjectGradle gradle

    ProjectCommands(File projectDir) {
        this(projectDir, new ProjectGradle(projectDir))
    }

    ProjectCommands(File projectDir, ProjectGradle gradle) {
        this.projectDir = projectDir
        this.gradle = gradle
    }

    /**
     * @return whether the directory holds a Grails project built with Gradle
     */
    boolean isGrailsProject() {
        new File(projectDir, 'grails-app').isDirectory() &&
                (new File(projectDir, 'build.gradle').isFile() || new File(projectDir, 'build.gradle.kts').isFile())
    }

    /**
     * Reads the commands of the project, after updating the manifest when it is missing or one of the
     * files it depends on changed.
     *
     * @param console where to say that the build is being run, and why the commands could not be read
     * @return the commands, or none when the directory holds no Grails project or none could be read
     */
    List<ProjectCommand> load(ConsoleOutput console) {
        load(console, true)
    }

    /**
     * Reads the commands of the project.
     *
     * @param console where to say that the build is being run, and why the commands could not be read
     * @param runBuild whether the manifest may be updated by running the build; when not, a manifest
     * that is there is read as it is, and none means no commands
     * @return the commands, or none when the directory holds no Grails project or none could be read
     */
    List<ProjectCommand> load(ConsoleOutput console, boolean runBuild) {
        if (!grailsProject) {
            return []
        }
        if (!gradle.available) {
            console.warning('The build of the project has no Gradle wrapper, so its commands are not offered.')
            return []
        }
        File manifest = new File(projectDir, MANIFEST_PATH)
        if (runBuild && outdated(manifest)) {
            update(manifest, console)
        }
        manifest.isFile() ? read(manifest, console) : []
    }

    private void update(File manifest, ConsoleOutput console) {
        // the files are recorded before the build runs, so one changed while it runs counts next time
        Inputs before = inputsOf(manifest)
        List<String> recorded = stateOrNull(before, false)
        // a failed build is recorded with every file of the main sources, so fixing the one that did not
        // compile runs the build again, while nothing changing still does not
        List<String> recordedWhole = stateOrNull(withDiscovered(before), true)
        console.out('Reading the commands of the project from its build...')
        String problem
        try {
            int exitCode = gradle.runSilently([MANIFEST_TASK, "-P${MANIFEST_PROPERTY}=${MANIFEST_PATH}".toString()])
            problem = exitCode == 0 ? null : "its build failed with exit code ${exitCode}"
        }
        catch (IOException e) {
            problem = e.message
        }
        if (problem == null && !manifest.isFile()) {
            problem = 'its build wrote no manifest'
        }
        if (problem == null) {
            // a manifest naming other files is recorded as they are now, unless one of the files known
            // before the build changed while it ran: then the record from before makes the build run again
            Inputs after = inputsOf(manifest)
            boolean changedDuringBuild = after != before && stateOrNull(before, false) != recorded
            save(after == before || changedDuringBuild ? recorded : stateOrNull(after, false), null, console, null)
            keepInputs(manifest)
            return
        }
        save(recordedWhole, problem, console, fingerprint(manifest))
        String kept = manifest.isFile() ? 'The commands it listed before are offered' : 'Its commands are not offered'
        console.warning("Could not read the commands of the project: ${problem}. ${kept}, and the build is not run " +
                'for them again until a build file or a source changes. A project of Grails 9 or later lists them with its ' +
                "${MANIFEST_TASK} Gradle task; run ./gradlew ${MANIFEST_TASK} -P${MANIFEST_PROPERTY}=${MANIFEST_PATH} " +
                'to see what went wrong, which also writes them where the Forge CLI reads them.')
    }

    /**
     * Records the files the commands depend on, and whether the build failed. When they cannot be
     * written, the build is run again next time, which is said.
     */
    private void save(List<String> recorded, String problem, ConsoleOutput console, String manifestFingerprint) {
        File record = new File(projectDir, RECORD_PATH)
        File failed = new File(projectDir, FAILED_PATH)
        if (recorded == null) {
            console.warning('Could not read the files the commands of the project depend on. The build is run for ' +
                    'them again next time.')
            return
        }
        try {
            Files.createDirectories(record.parentFile.toPath())
            Files.write(record.toPath(), recorded.collect { String line -> line + '\n' }.join('').getBytes('UTF-8'))
            if (problem == null) {
                Files.deleteIfExists(failed.toPath())
            }
            else {
                // the manifest there was, so one written since, the build working again, is told from it
                Files.write(failed.toPath(), "${problem}\n${MANIFEST_LINE}${manifestFingerprint}\n".toString().getBytes('UTF-8'))
            }
        }
        catch (IOException e) {
            console.warning("Could not record what the commands of the project depend on in ${record.parentFile}: " +
                    "${e.message ?: e.class.simpleName}. The build is run for them again next time.")
        }
    }

    private List<ProjectCommand> read(File manifest, ConsoleOutput console) {
        List<Object> commands
        try {
            Object parsed = new JsonSlurper().parse(manifest, 'UTF-8')
            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException('it is not a JSON object')
            }
            Map<String, Object> content = (Map<String, Object>) parsed
            Object version = content.version
            if (!(version instanceof Integer)) {
                throw new IllegalArgumentException('it has no format version')
            }
            if ((int) version > FORMAT_VERSION) {
                throw new IllegalArgumentException("it was written in format ${version}, by a newer Grails, and this " +
                        "Grails Forge CLI reads up to format ${FORMAT_VERSION}")
            }
            if (!(content.commands instanceof List)) {
                throw new IllegalArgumentException('it has no list of commands')
            }
            commands = (List<Object>) content.commands
        }
        catch (JsonException | IOException | RuntimeException e) {
            console.warning("Could not read the commands of the project from ${manifest}, so they are not offered: ${e.message}")
            return []
        }
        // a command picocli cannot take, such as one declaring an option twice, costs only that command
        List<ProjectCommand> created = []
        for (Object command : commands) {
            try {
                created << create(command)
            }
            catch (RuntimeException e) {
                console.warning("The command ${nameOf(command)} of the project is not offered: ${e.message}")
            }
        }
        created
    }

    private ProjectCommand create(Object command) {
        if (!(command instanceof Map) || !(((Map) command).name instanceof String) || !((Map) command).name) {
            throw new IllegalArgumentException("it has no name: ${command}")
        }
        new ProjectCommand((Map<String, Object>) command, gradle)
    }

    private static String nameOf(Object command) {
        Object name = command instanceof Map ? ((Map) command).name : null
        name instanceof String && name ? (String) name : 'without a name'
    }

    /**
     * Whether the manifest needs the build to run: there is none and the build did not fail last time,
     * nothing was recorded, or a recorded file changed.
     */
    private boolean outdated(File manifest) {
        if (!manifest.isFile() && !new File(projectDir, FAILED_PATH).isFile()) {
            return true
        }
        File record = new File(projectDir, RECORD_PATH)
        try {
            // without a record, as when the project's own build wrote the manifest, a file deleted since
            // cannot be told from one that was never there
            File failed = new File(projectDir, FAILED_PATH)
            boolean failedLast = failed.isFile()
            // a manifest other than the one there was when the build failed, as ./gradlew generateCommandManifest
            // writes, says it works again: the build runs once more, to record what the commands depend on and
            // forget the failure; told by what it holds, as its time may be anything
            if (failedLast && manifest.isFile() && fingerprint(manifest) != failedManifest(failed)) {
                return true
            }
            Inputs inputs = inputsOf(manifest)
            return !record.isFile() || Files.readAllLines(record.toPath()) !=
                    state(failedLast ? withDiscovered(inputs) : inputs, failedLast)
        }
        catch (IOException | RuntimeException ignored) {
            return true
        }
    }

    /** What the manifest holds, as a digest, or {@code none} when there is none */
    private static String fingerprint(File manifest) {
        if (!manifest.isFile()) {
            return 'none'
        }
        try {
            MessageDigest digest = MessageDigest.getInstance('SHA-256')
            return digest.digest(Files.readAllBytes(manifest.toPath())).encodeHex().toString()
        }
        catch (IOException ignored) {
            return 'unreadable'
        }
    }

    /** The digest of the manifest there was when the build failed, as the failure records it */
    private static String failedManifest(File failed) {
        String line = Files.readAllLines(failed.toPath()).find { String it -> it.startsWith(MANIFEST_LINE) }
        line?.substring(MANIFEST_LINE.length())
    }

    /**
     * Keeps a copy of the inputs the manifest names, or forgets the last one when it names none.
     */
    private void keepInputs(File manifest) {
        Path kept = new File(projectDir, INPUTS_PATH).toPath()
        try {
            Object parsed = new JsonSlurper().parse(manifest, 'UTF-8')
            Object inputs = parsed instanceof Map ? ((Map) parsed).inputs : null
            if (inputs instanceof Map) {
                Files.createDirectories(kept.parent)
                Files.write(kept, JsonOutput.toJson(inputs).getBytes('UTF-8'))
            }
            else {
                Files.deleteIfExists(kept)
            }
        }
        catch (JsonException | IOException | RuntimeException ignored) {
            // without the copy, a manifest removed by cleaning the build is watched through the project's inputs
        }
    }

    /**
     * The directories the commands depend on: those the manifest names; without a manifest, as after
     * cleaning the build, those the last manifest named; or else those of the root of the build and of
     * every project found below it, as a first build has nothing else to go by.
     */
    private Inputs inputsOf(File manifest) {
        if (manifest.isFile()) {
            Object parsed = parseOrNull(manifest)
            Inputs named = inputsFrom(parsed instanceof Map ? ((Map) parsed).inputs : null)
            if (named != null) {
                return named
            }
        }
        else {
            File kept = new File(projectDir, INPUTS_PATH)
            Inputs named = kept.isFile() ? inputsFrom(parseOrNull(kept)) : null
            if (named != null) {
                return named
            }
        }
        discoveredInputs()
    }

    /**
     * The inputs of the projects of the build as the Forge CLI finds them without a manifest: those the
     * settings files name, and the directories below the root of the build that look like projects. An
     * included build, which has a settings file of its own, is watched whole, as the manifest names it.
     */
    private Inputs discoveredInputs() {
        File rootDir = gradle.rootDir
        List<File> projects = []
        projects << projectDir << rootDir
        projectsBelow(rootDir, rootDir, projects)
        Set<File> visited = new HashSet<File>()
        declaredProjects(rootDir, projects, visited)
        // an included build the walk found names projects in its settings too, also outside the root
        for (File found : new ArrayList<File>(projects)) {
            if (SETTINGS_FILES.any { String name -> new File(found, name).isFile() }) {
                declaredProjects(found, projects, visited)
            }
        }
        projects = projects.unique()
        List<File> sourceDirectories = [new File(rootDir, 'gradle'), new File(rootDir, 'buildSrc')]
        List<File> commandDirectories = []
        List<File> commandFileDirectories = []
        for (File project : projects) {
            if (project != rootDir && SETTINGS_FILES.any { String name -> new File(project, name).isFile() }) {
                sourceDirectories << project
            }
            sourceDirectories << new File(project, 'grails-app/commands') << new File(project, 'src/main/resources/META-INF')
            commandDirectories << new File(project, 'src/cli')
            commandFileDirectories << new File(project, 'grails-app') << new File(project, 'src/main/groovy') <<
                    new File(project, 'src/main/java')
        }
        new Inputs(projects, sourceDirectories, commandDirectories, commandFileDirectories, [])
    }

    /**
     * The inputs, and those the Forge CLI finds now: while the build fails, a project added since the
     * last manifest, which that manifest does not name, may be what broke it.
     */
    private Inputs withDiscovered(Inputs inputs) {
        Inputs discovered = discoveredInputs()
        new Inputs((inputs.buildDirectories + discovered.buildDirectories).unique(),
                (inputs.sourceDirectories + discovered.sourceDirectories).unique(),
                (inputs.commandDirectories + discovered.commandDirectories).unique(),
                (inputs.commandFileDirectories + discovered.commandFileDirectories).unique(),
                inputs.sourceFiles)
    }

    /**
     * Adds the directories below the given one that look like projects or included builds of the build,
     * with a build or settings file, main sources or a {@code grails-app} directory, whatever they are
     * named, such as below a hidden directory or one named {@code src}: a settings file may name its
     * projects through code. What builds and tools write, the inside of a source tree, such as a test
     * fixture with a build file of its own, and the {@code buildSrc} of the root, which is watched whole,
     * are not searched; links are not followed.
     */
    private static void projectsBelow(File dir, File rootDir, List<File> projects) {
        File[] children = dir.listFiles()
        if (children == null) {
            return
        }
        for (File child : children.sort(false) { File it -> it.name }) {
            if (!child.isDirectory() || Files.isSymbolicLink(child.toPath()) ||
                    (dir == rootDir && child.name == 'buildSrc')) {
                continue
            }
            boolean project = isProject(child)
            if (project) {
                projects << child
            }
            boolean sources = !project && child.name in SOURCE_DIRS
            if (!sources && !(child.name in OUTPUT_DIRS) && !(child.name in TOOL_FILES)) {
                projectsBelow(child, rootDir, projects)
            }
        }
    }

    private static boolean isProject(File dir) {
        PROJECT_FILES.any { String name -> new File(dir, name).isFile() } ||
                PROJECT_DIRECTORIES.any { String name -> new File(dir, name).isDirectory() }
    }

    /**
     * Adds the directories a settings file names as paths in quotes, as {@code projectDir = file('bin/audit')}
     * or {@code includeBuild('../shared')} do, or as project paths, as {@code include ':plugins:audit'} does,
     * wherever they are, and those the settings files of the included builds among them name. A comment
     * names nothing, and a string that is no path on this system is passed over.
     */
    private static void declaredProjects(File settingsDir, List<File> projects, Set<File> visited) {
        if (!visited.add(settingsDir.absoluteFile.toPath().normalize().toFile())) {
            return
        }
        for (String name : SETTINGS_FILES) {
            File settings = new File(settingsDir, name)
            String text
            try {
                text = settings.isFile() ? new String(Files.readAllBytes(settings.toPath()), 'UTF-8') : null
            }
            catch (IOException ignored) {
                text = null
            }
            if (text == null) {
                continue
            }
            Matcher literals = STRING_LITERAL.matcher(withoutComments(text))
            while (literals.find()) {
                String literal = literals.group(2)
                if (!literal || literal.contains('$')) {
                    continue
                }
                // a project path first, as a colon is no part of a path on some systems
                for (String path : [literal.replaceFirst(/^:/, '').replace(':', '/'), literal].unique()) {
                    File candidate = directoryOrNull(settingsDir, path)
                    if (candidate == null) {
                        continue
                    }
                    if (!(candidate in projects)) {
                        projects << candidate
                    }
                    if (SETTINGS_FILES.any { String settingsName -> new File(candidate, settingsName).isFile() }) {
                        declaredProjects(candidate, projects, visited)
                    }
                }
            }
        }
    }

    /** The directory a path names, relative to the given one, or none when it names none or is no path here */
    private static File directoryOrNull(File base, String path) {
        try {
            File candidate = new File(path).isAbsolute() ? new File(path) : new File(base, path)
            candidate = candidate.absoluteFile.toPath().normalize().toFile()
            return candidate.isDirectory() ? candidate : null
        }
        catch (InvalidPathException ignored) {
            return null
        }
    }

    /** The text with its comments, // to the end of a line and /* to *\/, as blanks, leaving strings alone */
    private static String withoutComments(String text) {
        StringBuilder kept = new StringBuilder(text.length())
        char quote = 0 as char
        int i = 0
        while (i < text.length()) {
            char c = text.charAt(i)
            if (quote != (0 as char)) {
                kept.append(c)
                if (c == ('\\' as char) && i + 1 < text.length()) {
                    kept.append(text.charAt(i + 1))
                    i++
                }
                else if (c == quote || c == ('\n' as char)) {
                    quote = 0 as char
                }
                i++
            }
            else if (c == ('"' as char) || c == ("'" as char)) {
                quote = c
                kept.append(c)
                i++
            }
            else if (text.startsWith('//', i)) {
                while (i < text.length() && text.charAt(i) != ('\n' as char)) {
                    kept.append(' ' as char)
                    i++
                }
            }
            else if (text.startsWith('/*', i)) {
                int end = text.indexOf('*/', i + 2)
                int stop = end < 0 ? text.length() : end + 2
                while (i < stop) {
                    kept.append(text.charAt(i) == ('\n' as char) ? ('\n' as char) : (' ' as char))
                    i++
                }
            }
            else {
                kept.append(c)
                i++
            }
        }
        kept.toString()
    }

    private static Object parseOrNull(File file) {
        try {
            return new JsonSlurper().parse(file, 'UTF-8')
        }
        catch (JsonException | IOException | RuntimeException ignored) {
            // the manifest is reported when it is read
            return null
        }
    }

    /** The inputs a manifest names, or none when it names none or they are not lists of absolute paths */
    private static Inputs inputsFrom(Object inputs) {
        if (!(inputs instanceof Map)) {
            return null
        }
        Map given = (Map) inputs
        List<File> buildDirectories = files(given.buildDirectories)
        List<File> sourceDirectories = files(given.sourceDirectories)
        List<File> commandDirectories = optionalFiles(given, 'commandDirectories')
        List<File> commandFileDirectories = optionalFiles(given, 'commandFileDirectories')
        List<File> sourceFiles = optionalFiles(given, 'sourceFiles')
        if (buildDirectories == null || sourceDirectories == null || commandDirectories == null ||
                commandFileDirectories == null || sourceFiles == null) {
            return null
        }
        new Inputs(buildDirectories, sourceDirectories, commandDirectories, commandFileDirectories, sourceFiles)
    }

    /** A list a manifest written by an earlier Grails may not have, which then names no files */
    private static List<File> optionalFiles(Map inputs, String key) {
        inputs.containsKey(key) ? files(inputs[key]) : []
    }

    /** The files of a list of absolute paths, or none when one is not such a path, such as a mistyped one */
    private static List<File> files(Object paths) {
        if (!(paths instanceof List)) {
            return null
        }
        List<File> files = []
        for (Object path : (List) paths) {
            if (!(path instanceof String) || !absolute((String) path)) {
                return null
            }
            files << new File((String) path)
        }
        files
    }

    private static boolean absolute(String path) {
        try {
            return Path.of(path).absolute && Path.of(path).nameCount > 0
        }
        catch (RuntimeException ignored) {
            return false
        }
    }

    /**
     * What the files the commands depend on are now: the build files directly in each build directory,
     * every file and directory below each source directory, apart from what builds and tools write next
     * to a build script, and below each directory of commands, all of which are sources. Each such file
     * is listed with its time and size, so a deleted, added, replaced or changed file changes the list,
     * whatever the clock said when it was written. Below each directory of main sources, only the files
     * named like a command are listed, by name only; after a failed build, every file there is listed
     * with its time and size instead, so fixing whatever did not compile counts as a change.
     */
    private static List<String> snapshot(Inputs inputs, boolean wholeSources) {
        List<String> lines = []
        for (File dir : inputs.buildDirectories) {
            File[] files = dir.listFiles()
            if (files == null) {
                lines << "absent\t${dir.absolutePath}".toString()
                continue
            }
            for (File file : files.sort(false) { File it -> it.name }) {
                if (file.isFile() && BUILD_FILE_SUFFIXES.any { String suffix -> file.name.endsWith(suffix) }) {
                    lines << describe(file)
                }
            }
        }
        for (File dir : inputs.sourceDirectories) {
            // the sources of commands are sources from the start; a tree of a build becomes so below src
            walk(dir, lines, dir.name == 'META-INF' || (dir.name == 'commands' && dir.parentFile?.name == 'grails-app'))
        }
        for (File dir : inputs.commandDirectories) {
            walk(dir, lines, true)
        }
        // a new command there registers itself, and names its file after itself; other sources are left
        // out, unless the build failed, which any of them can have made it do
        for (File dir : inputs.commandFileDirectories) {
            if (wholeSources) {
                walk(dir, lines, true)
            }
            else {
                walkNames(dir, lines)
            }
        }
        for (File file : inputs.sourceFiles) {
            walk(file, lines, true)
        }
        lines
    }

    /**
     * What the commands depend on now: the files of {@link #snapshot}, and the wrapper the build runs
     * through, with whether it can run, so repairing a wrapper that could not start runs the build again.
     */
    private List<String> state(Inputs inputs, boolean wholeSources) {
        List<String> lines = snapshot(inputs, wholeSources)
        File wrapper = gradle.wrapper
        lines << (wrapper == null ? 'wrapper\tnone' :
                "wrapper\t${wrapper.absolutePath}\t${wrapper.lastModified()}\t${wrapper.length()}\t${wrapper.canExecute()}".toString())
        lines
    }

    /** What the commands depend on now, or none when it cannot be read, such as a path the system refuses */
    private List<String> stateOrNull(Inputs inputs, boolean wholeSources) {
        try {
            return state(inputs, wholeSources)
        }
        catch (RuntimeException ignored) {
            return null
        }
    }

    private static void walk(File file, List<String> lines, boolean inSources) {
        Path path = file.toPath()
        if (Files.isSymbolicLink(path)) {
            // not followed, so a link to a directory above it cannot loop
            String target
            try {
                target = Files.readSymbolicLink(path).toString()
            }
            catch (IOException | UnsupportedOperationException ignored) {
                target = ''
            }
            lines << "link\t${file.absolutePath}\t${target}".toString()
        }
        else if (file.isDirectory()) {
            File[] children = file.listFiles()
            if (children == null) {
                lines << "unreadable\t${file.absolutePath}".toString()
                return
            }
            lines << "directory\t${file.absolutePath}".toString()
            for (File child : children.sort(false) { File it -> it.name }) {
                boolean output = !inSources && child.name in OUTPUT_DIRS && child.isDirectory()
                if (!output && !(child.name in TOOL_FILES)) {
                    walk(child, lines, inSources || child.name in SOURCE_DIRS)
                }
            }
        }
        else if (file.isFile()) {
            lines << describe(file)
        }
        else {
            lines << "absent\t${file.absolutePath}".toString()
        }
    }

    /** The files below the directory named like a command, by name only, so editing one changes nothing */
    private static void walkNames(File dir, List<String> lines) {
        if (Files.isSymbolicLink(dir.toPath())) {
            return
        }
        File[] children = dir.listFiles()
        if (children == null) {
            return
        }
        for (File child : children.sort(false) { File it -> it.name }) {
            if (child.isDirectory()) {
                walkNames(child, lines)
            }
            else if (COMMAND_FILE_SUFFIXES.any { String suffix -> child.name.endsWith(suffix) }) {
                lines << "named\t${child.absolutePath}".toString()
            }
        }
    }

    private static String describe(File file) {
        "file\t${file.absolutePath}\t${file.lastModified()}\t${file.length()}".toString()
    }

    @EqualsAndHashCode
    private static final class Inputs {

        final List<File> buildDirectories
        final List<File> sourceDirectories
        final List<File> commandDirectories
        final List<File> commandFileDirectories
        final List<File> sourceFiles

        Inputs(List<File> buildDirectories, List<File> sourceDirectories, List<File> commandDirectories,
               List<File> commandFileDirectories, List<File> sourceFiles) {
            this.buildDirectories = buildDirectories
            this.sourceDirectories = sourceDirectories
            this.commandDirectories = commandDirectories
            this.commandFileDirectories = commandFileDirectories
            this.sourceFiles = sourceFiles
        }
    }
}
