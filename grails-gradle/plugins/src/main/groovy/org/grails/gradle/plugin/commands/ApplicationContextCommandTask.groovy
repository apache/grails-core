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
package org.grails.gradle.plugin.commands

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

import org.apache.grails.gradle.model.cli.CommandArgumentsFile
import org.grails.gradle.plugin.core.GrailsSystemPropertyArgumentProvider

/**
 * Runs a tool of the project or of one of its plugins: the type of {@code runTool}, of its deprecated
 * other name {@code runCommand} and of the task of each tool. The runner it starts decides, from the
 * tool, whether the application is started around it.
 *
 * <p>The arguments of the tool are given in one of three ways, never two of them at once:</p>
 * <ul>
 *   <li>{@code --arg=<argument>}, once for each argument, in their order. Each value is one argument as
 *       it is, whatever it contains. Gradle does not take an empty value.</li>
 *   <li>{@code --args-file=<file>}: a UTF-8 file with a JSON array of strings, one for each argument,
 *       for callers that are programs and for what a command line cannot carry, an empty argument
 *       among it. A path that is not absolute is in the project directory. The file is read when the
 *       task runs, by the JVM of the command, which is given its path and not what it holds; it is
 *       left where it is.</li>
 *   <li>the project property {@code args} ({@code -Pargs="..."}), one text that is split at spaces
 *       with single and double quotes read as a shell reads them, as it always was.</li>
 * </ul>
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'Application command tasks produce no cacheable output')
abstract class ApplicationContextCommandTask extends JavaExec {

    /** The option that gives one argument of the command. */
    public static final String ARGUMENT_OPTION = 'arg'

    /** The option that names a file with the arguments of the command. */
    public static final String ARGUMENTS_FILE_OPTION = 'args-file'

    /** The most bytes a file of arguments has. */
    public static final int MAX_ARGUMENTS_FILE_BYTES = CommandArgumentsFile.MAX_BYTES

    /** The most arguments a file of arguments has. */
    public static final int MAX_ARGUMENTS = CommandArgumentsFile.MAX_ARGUMENTS

    @Inject
    ApplicationContextCommandTask() {
        mainClass.set('grails.ui.command.GrailsApplicationContextCommandRunner')
        dependsOn('classes', 'findMainClass')
        // read when the task runs, so that a build that reuses a configuration cache entry passes its own
        jvmArgumentProviders.add(new GrailsSystemPropertyArgumentProvider(
                project.providers.systemPropertiesPrefixedBy(GrailsSystemPropertyArgumentProvider.PREFIX), systemProperties, true))
        argumentsFileBase.convention(project.layout.projectDirectory)
    }

    @Override
    void exec() {
        // a property the task was given as it started, also with a reused configuration cache entry, is kept
        GrailsSystemPropertyArgumentProvider.readTaskSystemProperties(this)
        super.exec()
    }

    void setCommand(String commandName) {
        args(commandName)
    }

    /**
     * The arguments given with {@code --arg}, each as it was given.
     */
    @Input
    @Optional
    @Option(option = 'arg', description = 'One argument of the command, as it is. Repeat the option for each argument, in their order.')
    abstract ListProperty<String> getArg()

    /**
     * The file given with {@code --args-file}, as it was given.
     */
    @Internal
    @Option(option = 'args-file', description = 'A UTF-8 file with the arguments of the command as a JSON array of strings. A relative path is in the project directory.')
    abstract Property<String> getArgsFile()

    /**
     * The directory a path given with {@code --args-file} is in when it is not absolute: the project directory.
     */
    @Internal
    abstract DirectoryProperty getArgumentsFileBase()

    /**
     * Puts together what the class that runs the command is started with after what the task was
     * configured with: the arguments of the project property {@code args} or those of {@code --arg},
     * then the class of the application, and for {@code --args-file} the argument that names the
     * file. What the file holds is read by the class that runs the command, in its JVM: it is not on
     * the command line of that JVM, where a process listing and the log of the build would show it.
     * The file is read here as well, so that one that cannot be used fails the task with the reason.
     *
     * @param propertyArguments the arguments read from the project property {@code args}; empty when it is not set
     * @param applicationClass the class of the application
     * @return what to start the class that runs the command with
     * @throws GradleException when arguments were given in more than one way, or when the file cannot be used
     */
    List<String> runnerArguments(List<String> propertyArguments, String applicationClass) {
        List<String> given = arg.getOrElse([])
        boolean file = argsFile.isPresent()
        int ways = (propertyArguments ? 1 : 0) + (given ? 1 : 0) + (file ? 1 : 0)
        if (ways > 1) {
            throw new GradleException("Task '${name}' was given arguments in more than one way. " +
                    "Use one of --${ARGUMENT_OPTION}, --${ARGUMENTS_FILE_OPTION} and the project property args (-Pargs).")
        }
        if (file) {
            File argumentsFile = argumentsFileBase.file(argsFile.get()).get().asFile
            try {
                CommandArgumentsFile.read(argumentsFile)
            } catch (CommandArgumentsFile.Refused refused) {
                throw new GradleException("The file given to task '${name}' with --${ARGUMENTS_FILE_OPTION} ${refused.message}")
            }
            return [applicationClass, CommandArgumentsFile.ARGUMENT + argumentsFile.absolutePath]
        }
        List<String> all = new ArrayList<String>(given ?: propertyArguments)
        all << applicationClass
        all
    }
}
