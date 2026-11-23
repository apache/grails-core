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

import javax.inject.Inject

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

import org.apache.grails.gradle.model.cli.CommandArgumentsFile
import org.grails.gradle.plugin.core.GrailsSystemPropertyArgumentProvider

/**
 * Runs a script command: a script {@code src/main/scripts/<name>.groovy} of the project, or one a
 * plugin has under {@code META-INF/commands}. The script is compiled and run by a JVM of the build
 * that has the dependencies of the project and the command line tier of Grails on its class path,
 * and neither the classes of the application nor the application itself: a script generates code and
 * works on the files of the project. The project itself is therefore not compiled for a script; a
 * project it depends on ({@code implementation project(':lib')}) is built first, as for anything
 * that uses the run time class path.
 *
 * <p>The name of the script and its arguments are given in one of two ways, never both:</p>
 * <ul>
 *   <li>{@code --arg=<value>}, once for the name and once for each argument, in their order. Each
 *       value is taken as it is. Gradle does not take an empty value.</li>
 *   <li>{@code --args-file=<file>}: a UTF-8 file with a JSON array of strings, the name first, for
 *       callers that are programs and for what a command line cannot carry. A path that is not
 *       absolute is in the project directory. The file is read by the JVM of the script, which is
 *       given its path and not what it holds; it is left where it is.</li>
 * </ul>
 *
 * @since 9.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'A script command produces no cacheable output')
abstract class ScriptCommandTask extends JavaExec {

    /** The name of the task every project has. */
    public static final String TASK_NAME = 'runScriptCommand'

    /** The class that compiles and runs a script, in the command line tier of Grails. */
    public static final String RUNNER = 'org.apache.grails.core.cli.script.ScriptCommandRunner'

    /** The system property of the build with which the stack trace of what a script threw is shown: {@code -Dgrails.full.stacktrace=true}. */
    public static final String FULL_STACKTRACE = 'grails.full.stacktrace'

    /** What the class that runs a script is started with in front of a name and arguments that are given as they are. */
    public static final String VECTOR = '--'

    @Inject
    ScriptCommandTask() {
        mainClass.convention(RUNNER)
        // read when the task runs, so that a build that reuses a configuration cache entry passes its own
        jvmArgumentProviders.add(new GrailsSystemPropertyArgumentProvider(
                project.providers.systemPropertiesPrefixedBy(GrailsSystemPropertyArgumentProvider.PREFIX), systemProperties, true))
        argumentsFileBase.convention(project.layout.projectDirectory)
    }

    /**
     * The name of the script and its arguments given with {@code --arg}, each as it was given.
     */
    @Input
    @Optional
    @Option(option = 'arg', description = 'The name of the script, and then one argument of it, as it is. Repeat the option for each, in their order.')
    abstract ListProperty<String> getArg()

    /**
     * The file given with {@code --args-file}, as it was given.
     */
    @Internal
    @Option(option = 'args-file', description = 'A UTF-8 file with the name of the script and its arguments as a JSON array of strings. A relative path is in the project directory.')
    abstract Property<String> getArgsFile()

    /**
     * The directory a path given with {@code --args-file} is in when it is not absolute: the project directory.
     */
    @Internal
    abstract DirectoryProperty getArgumentsFileBase()

    /**
     * Puts together what the class that runs a script is started with: {@code --} and then the name
     * and the arguments of {@code --arg}, or for {@code --args-file} the one argument that names the
     * file. What the file holds is read by that class, in its JVM: it is not on the command line of
     * the JVM, where a process listing and the log of the build would show it. The file is read
     * here as well, so that one that cannot be used fails the task with the reason.
     *
     * @return what to start the class that runs a script with
     * @throws GradleException when no script is named, when one is named in both ways, or when the file cannot be used
     */
    List<String> runnerArguments() {
        List<String> given = arg.getOrElse([])
        boolean file = argsFile.isPresent()
        if (file && given) {
            throw new GradleException("Task '${name}' was given a script in more than one way. Use one of --arg and --args-file.")
        }
        if (file) {
            File argumentsFile = argumentsFileBase.file(argsFile.get()).get().asFile
            List<String> read
            try {
                read = CommandArgumentsFile.read(argumentsFile)
            } catch (CommandArgumentsFile.Refused refused) {
                throw new GradleException("The file given to task '${name}' with --args-file ${refused.message}")
            }
            if (read.isEmpty()) {
                throw new GradleException("The file given to task '${name}' with --args-file names no script: its first argument is the name of the script.")
            }
            return [CommandArgumentsFile.ARGUMENT + argumentsFile.absolutePath]
        }
        if (!given) {
            throw new GradleException("Task '${name}' was given no script. Name one with --arg=<name>, and give each of its arguments with another --arg.")
        }
        List<String> all = new ArrayList<String>()
        all << VECTOR
        all.addAll(given)
        all
    }

    @Override
    void exec() {
        args(runnerArguments())
        // The properties of the build reach the JVM of the script when the task runs (see the constructor),
        // but not over a value the task has itself, and the Grails plugin gives every JavaExec one for this
        // property: the build that is started for stack traces says so here.
        if (Boolean.getBoolean(FULL_STACKTRACE)) {
            systemProperty(FULL_STACKTRACE, 'true')
        }
        // a property the task was given as it started, also with a reused configuration cache entry, is kept
        GrailsSystemPropertyArgumentProvider.readTaskSystemProperties(this)
        super.exec()
    }
}
