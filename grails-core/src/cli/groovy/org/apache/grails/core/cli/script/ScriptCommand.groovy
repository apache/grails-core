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

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.ModelBuilder
import grails.util.Environment
import grails.util.GrailsNameUtils
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.io.FileSystemInteraction
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.template.TemplateRenderer
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLine
import org.grails.config.CodeGenConfig

/**
 * The class a script command is compiled as: a script {@code src/main/scripts/<name>.groovy} of an
 * application, or {@code META-INF/commands/<name>.groovy} of a plugin, run by the build of the
 * project (the Gradle task {@code runScriptCommand}).
 *
 * <p>It has what the guide documents for such a script: {@code description}, the arguments
 * ({@code args}, {@code argsMap}, {@code flag}), {@code model}, {@code render}, {@code template}
 * and {@code templates}, the methods that work on the files of the project, the console, Ant,
 * events, and calling another script command as a method. A script is not run by the Grails shell
 * here and has no profile: it cannot run Gradle, a command of the command line that is no script,
 * or the application.</p>
 *
 * @since 9.0
 * @deprecated script commands are kept for the applications and plugins that have them; a new
 * command is written as an {@link org.apache.grails.core.cli.ApplicationCommand}
 */
@Deprecated
@CompileStatic
abstract class ScriptCommand extends Script implements ModelBuilder, ConsoleLogger {

    /** The name of the command the script is. */
    String name

    /** What the script was started with. */
    ExecutionContext executionContext

    @Delegate TemplateRenderer templateRenderer
    @Delegate FileSystemInteraction fileSystemInteraction
    @Delegate ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    /** The home directory of the user. */
    String userHome = System.getProperty('user.home')

    /** The version of Grails the script is run with. */
    String grailsVersion = ScriptCommand.getPackage()?.getImplementationVersion()

    private ScriptCommandRunner runner
    private File baseDir
    private CodeGenConfig config
    private Object ant

    /**
     * Describes the command. The description of a script is not used where the build runs it, and
     * nothing is done with it.
     *
     * @param desc the description
     * @param usage how the command is used
     */
    void description(String desc, String usage) {
    }

    /**
     * Describes the command. The description of a script is not used where the build runs it: the
     * closure is not called.
     *
     * @param desc the description
     * @param detail the usage, the arguments and the flags
     */
    void description(String desc, Closure detail) {
    }

    /**
     * @param name the name of a flag
     * @return the value the flag was given, {@code true} for a flag without one, or {@code null} when it was not given
     */
    def flag(String name) {
        CommandLine commandLine = executionContext.commandLine
        if (commandLine.hasOption(name)) {
            return commandLine.optionValue(name)
        }
        def value = commandLine.undeclaredOptions?.get(name)
        return value ?: null
    }

    /**
     * @return the flags the script was given, by their names
     */
    Map<String, Object> getArgsMap() {
        executionContext.commandLine.undeclaredOptions
    }

    /**
     * @return the arguments of the script that are no flags
     */
    List<String> getArgs() {
        executionContext.commandLine.remainingArgs
    }

    /**
     * @return the command line the script was given
     */
    CommandLine getCommandLine() {
        executionContext.commandLine
    }

    /**
     * @return the directory of the project
     */
    File getBaseDir() {
        baseDir
    }

    /**
     * @return the name of the Grails environment
     */
    String getGrailsEnv() {
        Environment.current.name
    }

    /**
     * @return the console
     */
    GrailsConsole getGrailsConsole() {
        GrailsConsole.getInstance()
    }

    /**
     * @return the console
     */
    GrailsConsole getConsole() {
        GrailsConsole.getInstance()
    }

    /**
     * @return the configuration of the application as its files have it: {@code grails-app/conf/application.yml}
     * and {@code application.groovy}, without the application being started
     */
    CodeGenConfig getConfig() {
        if (config == null) {
            CodeGenConfig read = new CodeGenConfig()
            File yml = new File(baseDir, 'grails-app/conf/application.yml')
            File groovy = new File(baseDir, 'grails-app/conf/application.groovy')
            if (yml.isFile()) {
                read.loadYml(yml)
            }
            if (groovy.isFile()) {
                read.loadGroovy(groovy)
            }
            config = read
        }
        config
    }

    /**
     * @param path the names that lead to a value of the configuration
     * @return the value, or {@code null}
     */
    def navigateConfig(String... path) {
        getConfig().navigate(path)
    }

    /**
     * @return Ant, for the tasks it has
     */
    Object getAnt() {
        if (ant == null) {
            // by name: Ant is with the command line tier where a script runs, and is not needed to compile this class
            ant = Class.forName('groovy.ant.AntBuilder', true, Thread.currentThread().contextClassLoader).getDeclaredConstructor().newInstance()
        }
        ant
    }

    /**
     * Registers what is done when an event is fired.
     *
     * @param eventName the name of the event
     * @param callable what is done
     */
    void on(String eventName, Closure callable) {
        runner.listen(eventName, callable)
    }

    /**
     * Registers what is done before a script command runs.
     *
     * @param commandName the name of the command
     * @param callable what is done
     */
    void before(String commandName, Closure callable) {
        runner.listen("${commandName}Start".toString(), callable)
    }

    /**
     * Registers what is done after a script command ran.
     *
     * @param commandName the name of the command
     * @param callable what is done
     */
    void after(String commandName, Closure callable) {
        runner.listen("${commandName}End".toString(), callable)
    }

    /**
     * Fires an event.
     *
     * @param eventName the name of the event
     * @param args what the listeners are given
     */
    void notify(String eventName, Object... args) {
        runner.fire(this, eventName, args)
    }

    /**
     * Gradle is not run from a script that the build runs.
     *
     * @throws ScriptCommandException always
     */
    Object getGradle() {
        throw new ScriptCommandException("The script ${name} uses gradle, which a script has only in the Grails shell: " +
                'a script that the build runs cannot run a build. Run the Gradle tasks from the command line, or make the script a task of the build.')
    }

    /**
     * There is no Spring Boot CLI where the build runs a script.
     *
     * @throws ScriptCommandException always
     */
    Object getSpring() {
        throw new ScriptCommandException("The script ${name} uses spring, which a script has only in the Grails shell.")
    }

    /**
     * There is no profile where the build runs a script.
     *
     * @throws ScriptCommandException always
     */
    Object getProfile() {
        throw new ScriptCommandException("The script ${name} uses profile, which a script has only in the Grails shell: there are no profiles here.")
    }

    /**
     * Runs another script command when a method is called that the script does not have:
     * {@code generateController('Book')} runs the script {@code generate-controller}.
     *
     * @param method the name of the method
     * @param arguments the arguments of the command
     * @return whether the command succeeded
     * @throws ScriptCommandException when there is no script command of that name
     */
    def methodMissing(String method, Object arguments) {
        Object[] given = arguments instanceof Object[] ? (Object[]) arguments : [arguments] as Object[]
        String commandName = GrailsNameUtils.getScriptName(method)
        List<String> vector = [commandName]
        for (Object each : given) {
            vector.add(String.valueOf(each))
        }
        if (!runner.has(commandName)) {
            throw new ScriptCommandException("The script ${name} calls ${method}(), and there is neither such a method nor a script command ${commandName}. " +
                    'A script that the build runs can call another script command; a command of the Grails command line that is no script, ' +
                    'such as run-app or test-app, is run from the command line.')
        }
        return runner.run(vector) == 0
    }

    /**
     * Gives the script what it runs with. Called by what runs the script, before it runs.
     */
    void begin(ScriptCommandRunner runner, String name, File baseDir, ExecutionContext executionContext) {
        this.runner = runner
        this.name = name
        this.baseDir = baseDir
        this.executionContext = executionContext
        this.templateRenderer = new TemplateRendererImpl(baseDir)
        this.fileSystemInteraction = new FileSystemInteractionImpl(baseDir)
        Object configured = navigateConfig('grails', 'codegen', 'defaultPackage')
        setDefaultPackage(configured == null ? null : configured.toString())
    }
}
