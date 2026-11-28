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
package org.apache.grails.core.cli.script

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.control.customizers.ImportCustomizer

import grails.build.logging.GrailsConsole
import grails.util.BuildSettings
import grails.util.Environment
import grails.util.GrailsNameUtils
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.gradle.model.cli.CommandArgumentsFile
import org.grails.build.parsing.CommandLine
import org.grails.build.parsing.CommandLineParser
import org.grails.build.parsing.DefaultCommandLine

/**
 * Compiles and runs a script command. It is what the Gradle task {@code runScriptCommand} of a
 * project starts, in a JVM of the build that has the dependencies of the project and the command
 * line tier of Grails on its class path: a script is code of the project or of one of its plugins
 * and is run where the build of the project runs, by nothing else.
 *
 * <p>A script is found by the name of the command it is: first among the files
 * {@code src/main/scripts/*.groovy} of the project, then among the files of
 * {@code META-INF/commands} on the class path, where plugins have theirs: those of the jars Grails
 * publishes first, then those of every other jar and directory, each group in the order of the names
 * of the jars (of a directory, its path) and then of the files, whatever order the class path has. A
 * jar is searched also when it has no entry for the directory, by its manifest: a jar without a
 * manifest is no jar of a plugin, and no script is taken from it. The name of a file gives
 * the name of the command as the Grails shell made it: a name with a hyphen in lower case, any
 * other with a hyphen in front of each of its words ({@code DataImport.groovy} is
 * {@code data-import}). A script of the project is a plain file in a directory that is reached
 * through no link, by the rule the Grails Forge CLI lists the scripts of a project with: what that
 * lists under a name is what is run under it, and a link is never followed out of the project.</p>
 *
 * <p>The arguments of a script are data. They are read for the flags and arguments the script asks
 * for, and nothing of them changes the JVM: {@code -Dgrails.env=production} among them is in
 * {@code commandLine.systemProperties} and does not set the environment the script runs in, which
 * is that of the build.</p>
 *
 * <p>The JVM is started with {@code --} and then the name and the arguments, or with the one
 * argument {@code --grails-args-file=<path>} that names a file that holds them (see
 * {@link CommandArgumentsFile}).</p>
 *
 * @since 9.0
 */
@CompileStatic
class ScriptCommandRunner {

    /** What the JVM is started with in front of a name and arguments that are given as they are. */
    public static final String VECTOR = '--'

    /** Where the scripts of a project are, in its directory. */
    public static final String PROJECT_LOCATION = 'src/main/scripts'

    /** Where the scripts of a plugin are, on the class path. */
    public static final String CLASSPATH_LOCATION = 'META-INF/commands'

    private static final String ENDING = '.groovy'
    // the vendor in the manifest of every jar Grails publishes
    private static final String GRAILS_VENDOR = 'grails.apache.org'
    private static final int MAX_NAME = 64
    private static final Pattern FILE_NAME = Pattern.compile('[A-Za-z][A-Za-z0-9]*(-[A-Za-z0-9]+)*')
    private static final Pattern NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')
    // stands where the name of the command is while the arguments are read: a name is not read as an environment
    private static final String PLACEHOLDER = 'script-command'
    // what a script had in the Grails shell and has not here, with how the sentence that says so ends
    private static final Map<String, String> ONLY_IN_THE_SHELL = [
            gradle: ': a script that the build runs cannot run a build. Run the Gradle tasks from the command line, or make the script a task of the build.',
            spring: '.',
            profile: ': there are no profiles here.',
    ].asImmutable()

    private final File baseDir
    private final ClassLoader classLoader
    private final PrintStream out
    private final PrintStream err
    private final Map<String, List<Closure>> listeners = [:]

    ScriptCommandRunner() {
        this(BuildSettings.BASE_DIR, Thread.currentThread().contextClassLoader, System.err)
    }

    /**
     * @param baseDir the directory of the project
     * @param classLoader where the scripts and the templates of plugins are found, and what a script is compiled against
     * @param err where it is said why a script was not run or did not succeed
     */
    ScriptCommandRunner(File baseDir, ClassLoader classLoader, PrintStream err) {
        this(baseDir, classLoader, null, err)
    }

    /**
     * @param out where the console of a script writes, or {@code null} for the standard output of the JVM
     * @see #ScriptCommandRunner(File, ClassLoader, PrintStream)
     */
    ScriptCommandRunner(File baseDir, ClassLoader classLoader, PrintStream out, PrintStream err) {
        this.out = out
        this.baseDir = baseDir
        this.classLoader = classLoader
        this.err = err
    }

    static void main(String[] args) {
        System.exit(new ScriptCommandRunner().start(args))
    }

    /**
     * Runs the script the JVM was started for.
     *
     * @param args what the JVM was started with
     * @return 0 when the script ran and did not return {@code false}, 1 otherwise; why is said in one
     * line, and with the system property {@code grails.full.stacktrace} the stack trace of what a script threw follows it
     */
    int start(String... args) {
        List<String> vector
        if (args.length == 1 && args[0].startsWith(CommandArgumentsFile.ARGUMENT)) {
            try {
                vector = CommandArgumentsFile.read(new File(args[0].substring(CommandArgumentsFile.ARGUMENT.length())))
            } catch (CommandArgumentsFile.Refused refused) {
                err.println("The file of the arguments ${refused.message}")
                return 1
            }
        } else if (args.length > 0 && args[0] == VECTOR) {
            vector = args.toList().subList(1, args.length)
        } else {
            err.println("A script command is started with ${VECTOR} and then its name and its arguments, or with ${CommandArgumentsFile.ARGUMENT} and the path of a file that holds them.")
            return 1
        }
        // Gradle is no terminal: what the script says through the console is whole lines of plain text.
        // The console there was is put back afterwards; when there was none, none is made to be put back,
        // which would open a terminal and the history file of the user for a JVM that has neither.
        GrailsConsole console = GrailsConsole.hasInstance() ? GrailsConsole.getInstance() : null
        try {
            GrailsConsole.setInstance(new ScriptConsole(out, err))
            int ended = run(vector)
            if (ended != 0) {
                err.println("The script ${vector[0]} returned false, which is how a script says that it did not succeed.")
            }
            return ended
        } catch (ScriptCommandException e) {
            err.println(e.message)
        } catch (CompilationFailedException e) {
            err.println(notCompiled(e))
        } catch (MissingMethodException e) {
            // the method of a script that is not there, which nothing in the script caught
            if (e.type != null && ScriptCommand.isAssignableFrom(e.type) && !vector.isEmpty()) {
                err.println("The script ${vector[0]} calls ${e.method}(), and there is neither such a method nor a script command ${GrailsNameUtils.getScriptName(e.method)}. " +
                        'A script that the build runs can call another script command; a command of the Grails command line that is no script, ' +
                        'such as run-app or test-app, is run from the command line.')
            } else {
                failed(e)
            }
        } catch (MissingPropertyException e) {
            String only = e.type != null && ScriptCommand.isAssignableFrom(e.type) ? ONLY_IN_THE_SHELL.get(e.property) : null
            if (only != null && !vector.isEmpty()) {
                err.println("The script ${vector[0]} uses ${e.property}, which a script has only in the Grails shell${only}")
            } else {
                failed(e)
            }
        } catch (Throwable e) {
            failed(e)
        } finally {
            if (console == null) {
                GrailsConsole.removeInstance()
            } else {
                GrailsConsole.setInstance(console)
            }
        }
        return 1
    }

    private void failed(Throwable e) {
        err.println("The script failed: ${e.getClass().name}: ${e.message}")
        if (Boolean.getBoolean('grails.full.stacktrace')) {
            e.printStackTrace(err)
        }
    }

    // Why a script was not compiled: the first error and its line, and for a class of the Grails
    // shell that is not there, that it is the shell that is missing.
    private static String notCompiled(CompilationFailedException e) {
        String first = null
        int line = 0
        boolean shell = false
        if (e instanceof MultipleCompilationErrorsException) {
            for (Object each : ((MultipleCompilationErrorsException) e).errorCollector.errors ?: []) {
                String text = each instanceof SyntaxErrorMessage ? ((SyntaxErrorMessage) each).cause.originalMessage : null
                if (text == null && each instanceof Message) {
                    StringWriter written = new StringWriter()
                    ((Message) each).write(new PrintWriter(written))
                    text = written.toString()
                }
                if (first == null && text != null) {
                    first = text
                    SyntaxException cause = each instanceof SyntaxErrorMessage ? ((SyntaxErrorMessage) each).cause : null
                    line = cause == null ? 0 : cause.line
                }
                shell = shell || (text != null && text.contains('unable to resolve class org.grails.cli.'))
            }
        }
        first = (first ?: String.valueOf(e.message)).readLines().find { String each -> !each.trim().isEmpty() } ?: 'no reason is given'
        return 'The script could not be compiled' + (line > 0 ? ", line ${line}" : '') + ": ${first.trim()}" +
                (shell ? '. A script is compiled without the Grails shell: what it imports from org.grails.cli is not there.' : '')
    }

    /**
     * Runs a script command.
     *
     * @param vector the name of the command, and then its arguments
     * @return 0 when the script ran and did not return {@code false}, 1 when it returned {@code false}
     * @throws ScriptCommandException when there is no such script
     */
    int run(List<String> vector) {
        return run(vector, null)
    }

    /**
     * Runs a script command for a script that calls it as a method. As in the Grails shell, the
     * command has the flags and the system properties of the command line of the calling script,
     * and what it is given itself comes over them; its arguments that are no flags are its own.
     *
     * @param vector the name of the command, and then its arguments
     * @param calling the command line of the script that calls the command, or {@code null} when no script does
     * @return 0 when the script ran and did not return {@code false}, 1 when it returned {@code false}
     * @throws ScriptCommandException when there is no such script
     */
    int run(List<String> vector, CommandLine calling) {
        if (vector.isEmpty() || !isName(vector[0])) {
            throw new ScriptCommandException('No script was run: the first argument is the name of a script command, ' +
                    "in lower-case letters and digits in words joined by single hyphens, of at most ${MAX_NAME} characters.")
        }
        String name = vector[0]
        URL source = find(name)
        if (source == null) {
            throw new ScriptCommandException("There is no script command ${name}: the project has no script of that name in ${PROJECT_LOCATION}, " +
                    "and no plugin on the class path has ${CLASSPATH_LOCATION}/${name}${ENDING}.")
        }
        ScriptCommand script = compile(source, name)
        List<String> placed = [PLACEHOLDER]
        placed.addAll(vector.subList(1, vector.size()))
        DefaultCommandLine commandLine
        // The arguments are data: reading -Dgrails.env=NAME among them sets the environment of the
        // JVM, as it did in the Grails shell, where it was typed for the shell. Here it is undone.
        String environment = System.getProperty(Environment.KEY)
        try {
            // a script another script calls starts from the flags and the system properties of that script
            CommandLine parsed = calling == null ?
                    new CommandLineParser().parse(new DefaultCommandLine(), placed as String[]) :
                    calling.parseNew(placed as String[])
            if (!(parsed instanceof DefaultCommandLine)) {
                throw new ScriptCommandException("The script ${name} was not run: the command line of the script that calls it cannot be passed on.")
            }
            commandLine = (DefaultCommandLine) parsed
        } finally {
            if (environment == null) {
                System.clearProperty(Environment.KEY)
            } else {
                System.setProperty(Environment.KEY, environment)
            }
        }
        commandLine.setCommand(name)
        // what the script was given, as it was given: the name and every argument
        commandLine.setRawArguments(vector as String[])
        // the templates of plugins are looked for where the scripts of plugins are
        Thread thread = Thread.currentThread()
        ClassLoader before = thread.contextClassLoader
        thread.contextClassLoader = classLoader
        try {
            ExecutionContext context = new ExecutionContext(commandLine)
            script.begin(this, name, baseDir, context)
            // a listener of the start and of the end of a command is given the context the command runs
            // with, as in the Grails shell: before('inner') { context -> ... } reads the arguments of inner
            fire(script, "${name}Start".toString(), context)
            Object result = script.run()
            fire(script, "${name}End".toString(), context)
            return result instanceof Boolean && !((Boolean) result) ? 1 : 0
        } finally {
            thread.contextClassLoader = before
        }
    }

    /**
     * @param name the name of a command
     * @return whether there is a script command of that name
     */
    boolean has(String name) {
        return isName(name) && find(name) != null
    }

    void listen(String event, Closure listener) {
        listeners.computeIfAbsent(event) { String key -> new ArrayList<Closure>() }.add(listener)
    }

    void fire(ScriptCommand from, String event, Object... arguments) {
        for (Closure listener : new ArrayList<Closure>(listeners.get(event) ?: Collections.<Closure>emptyList())) {
            Closure called = (Closure) listener.clone()
            called.delegate = from
            // each argument is one parameter of the listener, as it is where a script notifies
            called.invokeMethod('call', arguments)
        }
    }

    /**
     * @param fileName the name of a file
     * @return the name of the command a script in a file of that name is, or {@code null} when it is none
     */
    static String commandName(String fileName) {
        if (fileName == null || !fileName.endsWith(ENDING)) {
            return null
        }
        String base = fileName.substring(0, fileName.length() - ENDING.length())
        if (!FILE_NAME.matcher(base).matches()) {
            return null
        }
        String name = base.contains('-') ? base.toLowerCase(Locale.ROOT) : GrailsNameUtils.getScriptName(base)
        return isName(name) ? name : null
    }

    private static boolean isName(String name) {
        return name != null && name.length() <= MAX_NAME && NAME.matcher(name).matches()
    }

    private URL find(String name) {
        File ofProject = projectScript(baseDir, name)
        return ofProject == null ? pluginScript(name) : ofProject.toURI().toURL()
    }

    /**
     * Finds the script of a project that is a command, by the rule the Grails Forge CLI lists the
     * scripts of a project with.
     *
     * @param baseDir the directory of the project
     * @param name the name of a command
     * @return the plain file in {@code src/main/scripts} whose name gives that name, the first in the
     * order of file names where there are several; {@code null} when there is none, or when
     * {@code src}, {@code src/main} or {@code src/main/scripts} is a link or leads out of the project
     */
    static File projectScript(File baseDir, String name) {
        try {
            Path project = baseDir.toPath()
            Path directory = project
            for (Path step : Path.of(PROJECT_LOCATION)) {
                directory = directory.resolve(step)
                if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    return null
                }
            }
            if (directory.toRealPath() != project.toRealPath().resolve(PROJECT_LOCATION)) {
                return null
            }
            String[] names = directory.toFile().list()
            for (String fileName : (names == null ? [] : names.toList().sort())) {
                // a link, a pipe or a directory is no script: a link would lead to a file that is not the project's
                if (commandName(fileName) == name && Files.isRegularFile(directory.resolve(fileName), LinkOption.NOFOLLOW_LINKS)) {
                    return directory.resolve(fileName).toFile()
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // what cannot be read has no script
        }
        return null
    }

    // The script of a plugin: among the files of META-INF/commands of every jar and directory on the
    // class path, those of the jars Grails publishes first and those of every other place after them.
    // Within each, the places are taken by the name of the jar (a directory by its path) and the files
    // of a place by their names, so the script that is run does not depend on the order the class
    // loader returns places in, which another build of the same class path need not keep. A jar that
    // has no entry for the directory is found by its manifest. A jar without a manifest is not searched,
    // with or without an entry for the directory: every jar a build makes has one, and the scripts of a
    // file that has none are not run.
    // the jars without a manifest that it was said of that no script is taken from them
    private final Set<String> saidOfJars = new HashSet<String>()

    private URL pluginScript(String name) {
        Map<String, Place> places = new LinkedHashMap<String, Place>()
        Set<String> withoutManifest = new TreeSet<String>()
        for (URL each : Collections.list(classLoader.getResources(CLASSPATH_LOCATION))) {
            add(places, each, withoutManifest)
        }
        for (URL each : Collections.list(classLoader.getResources(JarFile.MANIFEST_NAME))) {
            // a directory with a manifest has its scripts in a directory the first search finds
            if (each.protocol == 'jar') {
                add(places, each, withoutManifest)
            }
        }
        for (String jar : withoutManifest) {
            // said once for a jar, however many scripts are looked for
            if (saidOfJars.add(jar)) {
                err.println("No script is taken from ${jar}: it has files in ${CLASSPATH_LOCATION} and no manifest, and the jar of a plugin has a manifest.")
            }
        }
        List<Place> ordered = new ArrayList<Place>(places.values())
        ordered.sort { Place a, Place b ->
            a.grails != b.grails ? (a.grails ? -1 : 1) : a.sortName != b.sortName ? a.sortName <=> b.sortName : a.location <=> b.location
        }
        for (Place place : ordered) {
            for (String fileName : place.fileNames) {
                if (commandName(fileName) == name) {
                    return new URL(place.commands, fileName)
                }
            }
        }
        return null
    }

    // adds the place a resource of the class path is in, unless it is there already or has no scripts
    private static void add(Map<String, Place> places, URL resource, Set<String> withoutManifest) {
        try {
            if (resource.protocol == 'file') {
                File commands = new File(resource.toURI())
                File root = commands.parentFile?.parentFile
                String location = root?.toURI()?.toString()
                if (root != null && !places.containsKey(location)) {
                    String[] listed = commands.list()
                    File manifest = new File(root, JarFile.MANIFEST_NAME)
                    boolean grails = manifest.isFile() && manifest.withInputStream { InputStream input -> isGrails(new Manifest(input)) }
                    places.put(location, new Place(location, root.path, grails, listed == null ? [] : listed.toList(),
                            new URL(resource.toExternalForm().endsWith('/') ? resource.toExternalForm() : resource.toExternalForm() + '/')))
                }
            } else if (resource.protocol == 'jar') {
                JarURLConnection connection = (JarURLConnection) resource.openConnection()
                String location = connection.jarFileURL.toExternalForm()
                if (places.containsKey(location)) {
                    return
                }
                // the class loader has the file open already, and a JarFile of it shares what it read of the file
                connection.setUseCaches(false)
                JarFile jar = connection.jarFile
                try {
                    String prefix = CLASSPATH_LOCATION + '/'
                    List<String> names = []
                    for (JarEntry entry : Collections.list(jar.entries())) {
                        String entryName = entry.name
                        if (entryName.startsWith(prefix) && entryName.indexOf('/', prefix.length()) < 0 && entryName.length() > prefix.length()) {
                            names.add(entryName.substring(prefix.length()))
                        }
                    }
                    String path = connection.jarFileURL.path
                    if (jar.manifest == null) {
                        // no jar of a plugin: it is said, where it has scripts, and none of them is run
                        if (names.any { String each -> commandName(each) != null }) {
                            withoutManifest.add(path)
                        }
                        return
                    }
                    places.put(location, new Place(location, path.substring(path.lastIndexOf('/') + 1), !names.isEmpty() && isGrails(jar.manifest), names,
                            new URL("jar:${location}!/${prefix}")))
                } finally {
                    jar.close()
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException ignored) {
            // a place that cannot be listed has no scripts
        }
    }

    // Whether a manifest is that of a jar Grails publishes: the build of Grails writes this vendor into the
    // manifest of every jar it publishes (CompilePlugin of build-logic).
    private static boolean isGrails(Manifest manifest) {
        return manifest != null && manifest.mainAttributes.getValue('Implementation-Vendor') == GRAILS_VENDOR
    }

    // a jar or a directory of the class path, with the names of the files of META-INF/commands in it in their order
    @CompileStatic
    private static final class Place {

        final String location
        final String sortName
        final boolean grails
        final List<String> fileNames
        final URL commands

        Place(String location, String sortName, boolean grails, List<String> fileNames, URL commands) {
            this.location = location
            this.sortName = sortName
            this.grails = grails
            this.fileNames = fileNames.sort(false)
            this.commands = commands
        }
    }

    private ScriptCommand compile(URL source, String name) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.setScriptBaseClass(ScriptCommand.name)
        configuration.setSourceEncoding(StandardCharsets.UTF_8.name())
        ImportCustomizer imports = new ImportCustomizer()
        imports.addStarImports('grails.util', 'grails.codegen.model')
        configuration.addCompilationCustomizers(imports)
        GroovyClassLoader loader = new GroovyClassLoader(classLoader, configuration)
        String text = source.getText(StandardCharsets.UTF_8.name())
        Class<?> type = loader.parseClass(text, "${name}${ENDING}".toString())
        if (!ScriptCommand.isAssignableFrom(type)) {
            throw new ScriptCommandException("${name} is not a script: its file declares a class and has no statements of its own.")
        }
        return (ScriptCommand) type.getDeclaredConstructor().newInstance()
    }
}
