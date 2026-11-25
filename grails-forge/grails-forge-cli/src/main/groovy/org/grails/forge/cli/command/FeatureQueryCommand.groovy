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

import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.RecordedPackOptions
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.io.FileSystemOutputHandler
import picocli.CommandLine

/**
 * What {@code feature search}, {@code feature info} and {@code feature diff} share: the application
 * they speak of, see {@link FeatureScope}, which is that of the current directory or, with
 * {@code --type} and outside a project, a new application of a type. None of them changes anything.
 *
 * @since 9.0
 */
@CompileStatic
abstract class FeatureQueryCommand extends BaseCommand implements Callable<Integer> {

    /** The format of the documents {@code --json} writes */
    public static final int FORMAT = 1

    @CommandLine.Option(names = ['--type'], paramLabel = 'TYPE', completionCandidates = ProjectMigrateCommand.TypeChoices,
            description = 'Speak of a new application of this type, and not of the application of the current directory: web (what create-app creates), ' +
                    'rest-api, plugin, web-plugin or rest-api-plugin. Outside a project the type is web when the option is not given.')
    String type

    @CommandLine.Mixin
    RecordedPackOptions packOptions = new RecordedPackOptions()

    // asked of the Forge application when the command first needs it
    private final Supplier<ProjectGenerator> projectGeneratorSource

    protected FeatureQueryCommand(Supplier<ProjectGenerator> projectGeneratorSource) {
        this.projectGeneratorSource = projectGeneratorSource
    }

    /** On a Forge application that is started when the command first needs it. */
    protected static Supplier<ProjectGenerator> generatorOf(ForgeContext context) {
        return { context.get().getBean(ProjectGenerator) } as Supplier<ProjectGenerator>
    }

    protected ProjectGenerator getProjectGenerator() {
        return projectGeneratorSource.get()
    }

    /**
     * @param notes given what is said while the packs of the application are had again, one line each
     * @return the application the command speaks of, or {@code null} when it cannot be spoken of, which
     * was then said as an error
     */
    protected FeatureScope scope(List<String> notes) {
        try {
            return FeatureScope.of(getProjectGenerator(), FeatureScope.typeOf(spec.commandLine(), type), FileSystemOutputHandler.getDefaultBaseDirectory().toPath(),
                    packOptions, createPackArtifacts(), grailsRepoUrl(), getOperatingSystem(), { String line -> notes << shown(line) } as Consumer<String>,
                    { String line -> notes << shown(line) } as Consumer<String>)
        } catch (ProjectStateException | ProjectChangeException | IllegalArgumentException | PackArtifactException e) {
            err(shown(e.getMessage() ?: e.getClass().getSimpleName()))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return null
        }
    }

    /** Says something that is no part of what the command writes, where the warnings and the errors are. */
    protected void note(String message) {
        spec.commandLine().getErr().println(CommandLine.Help.Ansi.AUTO.string("@|blue ||@ ${message}"))
    }

    /** Writes a line as it is: what a file or a document has is not markup of the command line. */
    protected void written(String line) {
        spec.commandLine().getOut().println(line)
    }

    /**
     * @return what fetches the jar of a plugin the application has the feature pack of
     */
    protected PackArtifacts createPackArtifacts() {
        return new PackArtifacts()
    }

    /**
     * @return the repositories of the build of the application, which are asked for such a jar when
     * the command names none
     */
    protected String grailsRepoUrl() {
        return System.getenv('GRAILS_REPO_URL')
    }

    // a name or a message as one line that is no markup of this command
    protected static String shown(String text) {
        return GradleRunner.line(text ?: '')
    }
}
