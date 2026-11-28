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
 * here and has no profile: it has no {@code gradle}, {@code spring} or {@code profile}, and a method
 * that names a command of the command line that is no script is a method that is missing.</p>
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
     * Registers what is done before a script command runs: the event {@code <commandName>Start}.
     *
     * @param commandName the name of the command
     * @param callable what is done; it is given the {@link ExecutionContext} the command runs with,
     * whose {@code commandLine} has the name and the arguments of that command, and its delegate is that command
     */
    void before(String commandName, Closure callable) {
        runner.listen("${commandName}Start".toString(), callable)
    }

    /**
     * Registers what is done after a script command ran and did not throw: the event {@code <commandName>End}.
     *
     * @param commandName the name of the command
     * @param callable what is done; it is given the {@link ExecutionContext} the command ran with,
     * whose {@code commandLine} has the name and the arguments of that command, and its delegate is that command
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
     * Runs another script command when a method is called that the script does not have:
     * {@code generateController('Book')} runs the script {@code generate-controller}. The script that
     * is run has the flags and the system properties this script was given, as it had in the Grails
     * shell, and its own arguments: a flag or a system property among them comes over one of the same
     * name that this script was given, and the arguments of this script that are no flags are not
     * passed on.
     *
     * @param method the name of the method
     * @param arguments the arguments of the command
     * @return whether the command succeeded
     * @throws MissingMethodException when there is no script command of that name, as for any method
     * that is not there: a closure of the script whose delegate has the method then calls it there
     * ({@code copy { from ...; into ... }}, a builder, Ant), and a script can catch it. One that
     * nothing catches is said in one line by what runs the script.
     */
    def methodMissing(String method, Object arguments) {
        Object[] given = arguments instanceof Object[] ? (Object[]) arguments : [arguments] as Object[]
        String commandName = GrailsNameUtils.getScriptName(method)
        if (runner == null || !runner.has(commandName)) {
            throw new MissingMethodException(method, getClass(), given)
        }
        List<String> vector = [commandName]
        for (Object each : given) {
            vector.add(String.valueOf(each))
        }
        return runner.run(vector, executionContext?.commandLine) == 0
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
