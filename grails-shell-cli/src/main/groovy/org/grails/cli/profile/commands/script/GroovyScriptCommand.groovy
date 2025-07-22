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
package org.grails.cli.profile.commands.script

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.ModelBuilder
import grails.util.Environment
import grails.util.GrailsNameUtils
import groovy.ant.AntBuilder
import groovy.transform.CompileStatic
import org.grails.build.logging.GrailsConsoleAntBuilder
import org.grails.cli.GrailsCli
import org.grails.cli.boot.SpringInvoker
import org.grails.cli.gradle.GradleInvoker
import org.grails.cli.profile.CommandDescription
import org.grails.cli.profile.ExecutionContext
import org.grails.cli.profile.Profile
import org.grails.cli.profile.ProfileCommand
import org.grails.cli.profile.ProfileRepository
import org.grails.cli.profile.ProfileRepositoryAware
import org.grails.cli.profile.commands.events.CommandEvents
import org.grails.cli.profile.commands.io.FileSystemInteraction
import org.grails.cli.profile.commands.io.FileSystemInteractionImpl
import org.grails.cli.profile.commands.io.ServerInteraction
import org.grails.cli.profile.commands.templates.TemplateRenderer
import org.grails.cli.profile.commands.templates.TemplateRendererImpl

/**
 * A base class for Groovy scripts that implement commands
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
abstract class GroovyScriptCommand extends Script implements ProfileCommand, ProfileRepositoryAware, ConsoleLogger, ModelBuilder, FileSystemInteraction, TemplateRenderer, CommandEvents, ServerInteraction {

    Profile profile
    ProfileRepository profileRepository
    String name = getClass().name.contains('-') ? getClass().name : GrailsNameUtils.getScriptName(getClass().name)
    CommandDescription description = new CommandDescription(name)
    @Delegate ExecutionContext executionContext
    @Delegate TemplateRenderer templateRenderer
    @Delegate ConsoleLogger consoleLogger = GrailsConsole.getInstance()
    @Delegate FileSystemInteraction fileSystemInteraction

    /**
     * Allows invoking of Gradle commands
     */
    GradleInvoker gradle
    /**
     * Allows invoking of Spring Boot's CLI
     */
    SpringInvoker spring = SpringInvoker.getInstance()
    /**
     * Access to Ant via AntBuilder
     */
    AntBuilder ant = new GrailsConsoleAntBuilder()

    /**
     * The location of the user.home directory
     */
    String userHome = System.getProperty('user.home')
    /**
     * The version of Grails being used
     */
    String grailsVersion = getClass().getPackage()?.getImplementationVersion()

    /**
     * Provides a description for the command
     *
     * @param desc The description
     * @param usage The usage information
     */
    void description(String desc, String usage) {
        // ignore, just a stub for documentation purposes, populated by CommandScriptTransform
    }

    /**
     * Provides a description for the command
     *
     * @param desc The description
     * @param usage The usage information
     */
    void description(String desc, Closure detail) {
        // ignore, just a stub for documentation purposes, populated by CommandScriptTransform
    }

    /**
     * Obtains details of the given flag if it has been set by the user
     *
     * @param name The name of the flag
     * @return The flag information, or null if it isn't set by the user
     */
    def flag(String name) {
        if(commandLine.hasOption(name)) {
            return commandLine.optionValue(name)
        }
        else {
            def value = commandLine?.undeclaredOptions?.get(name)
            return value ?: null
        }
    }

    /**
     * @return The undeclared command line arguments
     */
    Map<String, Object> getArgsMap() {
        executionContext.commandLine.undeclaredOptions
    }

    /**
     * @return The arguments as a list of strings
     */
    List<String> getArgs() {
        executionContext.commandLine.remainingArgs
    }

    /**
     * @return The name of the current Grails environment
     */
    String getGrailsEnv() {  Environment.current.name }

    /**
     * @return The {@link GrailsConsole} instance
     */
    GrailsConsole getGrailsConsole() { executionContext.console }

    /**
     * Implementation of the handle method that runs the script
     *
     * @param executionContext The ExecutionContext
     * @return True if the script succeeds, false otherwise
     */
    @Override
    boolean handle(ExecutionContext executionContext) {
        setExecutionContext(executionContext)
        notify("${name}Start", executionContext)
        def result = run()
        notify("${name}End", executionContext)
        if(result instanceof Boolean) {
            return ((Boolean)result)
        }
        return true
    }

    /**
     * Method missing handler used to invoke other commands from a command script
     *
     * @param name The name of the command as a method name (for example 'run-app' would be runApp())
     * @param args The arguments to the command
     */
    def methodMissing(String name, args) {
        Object[] argsArray = (Object[])args
        def commandName = GrailsNameUtils.getScriptName(name)
        def context = executionContext
        if(profile?.hasCommand(context, commandName )) {
            def commandLine = context.commandLine
            def newArgs = [commandName]
            newArgs.addAll argsArray.collect() { it.toString() }
            def newContext = new GrailsCli.ExecutionContextImpl(commandLine.parseNew(newArgs as String[]), context)
            return profile.handleCommand(newContext)
        }
        else {
            throw new MissingMethodException(name, getClass(), argsArray)
        }
    }

    public void setExecutionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext
        this.consoleLogger = executionContext.console
        this.templateRenderer = new TemplateRendererImpl(executionContext, profile, profileRepository)
        this.fileSystemInteraction = new FileSystemInteractionImpl(executionContext)
        this.gradle = new GradleInvoker(executionContext)
        setDefaultPackage( executionContext.navigateConfig('grails', 'codegen', 'defaultPackage') )
    }

    ExecutionContext getExecutionContext() {
        return executionContext
    }
}
