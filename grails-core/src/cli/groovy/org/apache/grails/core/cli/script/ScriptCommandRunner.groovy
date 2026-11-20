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
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import grails.util.BuildSettings
import grails.util.GrailsNameUtils
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.gradle.model.cli.CommandArgumentsFile
import org.grails.build.parsing.CommandLineParser
import org.grails.build.parsing.DefaultCommandLine

/**
 * Compiles and runs a script command. It is what the Gradle task {@code runScriptCommand} of a
 * project starts, in a JVM of the build that has the dependencies of the project and the command
 * line tier of Grails on its class path: a script is code of the project or of one of its plugins
 * and is run where the build of the project runs, by nothing else.
 *
 * <p>A script is found by the name of the command it is: first among the files
 * {@code src/main/scripts/*.groovy} of the project, then as {@code META-INF/commands/<name>.groovy}
 * on the class path, where plugins have theirs. The name of a file gives the name of the command
 * as the Grails shell made it: a name with a hyphen in lower case, any other with a hyphen in front
 * of each of its words ({@code DataImport.groovy} is {@code data-import}).</p>
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
    private static final int MAX_NAME = 64
    private static final Pattern FILE_NAME = Pattern.compile('[A-Za-z][A-Za-z0-9]*(-[A-Za-z0-9]+)*')
    private static final Pattern NAME = Pattern.compile('[a-z][a-z0-9]*(-[a-z0-9]+)*')
    // stands where the name of the command is while the arguments are read: a name is not read as an environment
    private static final String PLACEHOLDER = 'script-command'

    private final File baseDir
    private final ClassLoader classLoader
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
     * @return 0 when the script ran and did not return {@code false}, 1 otherwise; why is said in one line
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
        try {
            return run(vector)
        } catch (ScriptCommandException e) {
            err.println(e.message)
        } catch (CompilationFailedException e) {
            err.println('The script could not be compiled. A script is compiled without the Grails shell: what it imports from org.grails.cli is not there.')
            err.println(e.message)
        } catch (Throwable e) {
            err.println("The script failed: ${e.getClass().name}: ${e.message}")
            if (Boolean.getBoolean('grails.full.stacktrace')) {
                e.printStackTrace(err)
            }
        }
        return 1
    }

    /**
     * Runs a script command.
     *
     * @param vector the name of the command, and then its arguments
     * @return 0 when the script ran and did not return {@code false}, 1 when it returned {@code false}
     * @throws ScriptCommandException when there is no such script
     */
    int run(List<String> vector) {
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
        DefaultCommandLine commandLine = new DefaultCommandLine()
        new CommandLineParser().parse(commandLine, placed as String[])
        commandLine.setCommand(name)
        // what the script was given, as it was given: the name and every argument
        commandLine.setRawArguments(vector as String[])
        // the templates of plugins are looked for where the scripts of plugins are
        Thread thread = Thread.currentThread()
        ClassLoader before = thread.contextClassLoader
        thread.contextClassLoader = classLoader
        try {
            script.begin(this, name, baseDir, new ExecutionContext(commandLine))
            fire(script, "${name}Start".toString())
            Object result = script.run()
            fire(script, "${name}End".toString())
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
        File directory = new File(baseDir, PROJECT_LOCATION)
        String[] names = directory.isDirectory() ? directory.list() : null
        if (names != null) {
            // the one that is first in the order of file names, where two files give one name
            for (String fileName : names.toList().sort()) {
                File file = new File(directory, fileName)
                if (commandName(fileName) == name && file.isFile()) {
                    return file.toURI().toURL()
                }
            }
        }
        for (String fileName : [name, GrailsNameUtils.getNameFromScript(name)]) {
            URL resource = classLoader.getResource("${CLASSPATH_LOCATION}/${fileName}${ENDING}")
            if (resource != null) {
                return resource
            }
        }
        return null
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
