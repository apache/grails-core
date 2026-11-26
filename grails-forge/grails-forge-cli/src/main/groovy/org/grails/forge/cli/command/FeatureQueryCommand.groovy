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
import org.apache.grails.forge.cli.pack.AddedPackOptions
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
 * What {@code feature find}, {@code feature show} and {@code feature diff} share: the application
 * they speak of, see {@link FeatureScope}, which is that of the current directory or, with
 * {@code --type} and outside a project, a new application of a type. None of them changes anything.
 * A feature of a feature pack the application does not have is spoken of as {@code feature add}
 * would add it: the pack is taken with the options of that command, by that command.
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

    // declared where feature add declares them, so that what that command takes these take
    @CommandLine.Mixin
    AddedPackOptions addedOptions = new AddedPackOptions()

    // feature add of what this command was asked for, which takes the packs as it takes them when it adds their features
    private FeatureAddCommand taker

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
     * {@code feature add} of features, with the feature packs, the repositories and the options this
     * command was given: what takes the packs that are asked for, the same way for every command.
     *
     * @param features the features that were asked for
     */
    protected FeatureAddCommand taker(List<String> features) {
        FeatureQueryCommand query = this
        FeatureAddCommand add = new FeatureAddCommand(getProjectGenerator()) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return query.createPackArtifacts()
            }

            @Override
            protected String grailsRepoUrl() {
                return query.grailsRepoUrl()
            }
        }
        add.spec = spec
        add.features = features
        add.packOptions = packOptions
        add.addedOptions = addedOptions
        taker = add
        return add
    }

    /**
     * @return the {@code feature add} that took the packs of this command, once {@link #scope} was asked
     */
    protected FeatureAddCommand getTaker() {
        return taker
    }

    /**
     * @param notes given what is said while the packs of the application are had again, and the packs
     * of the features that were asked for are taken, one line each
     * @param features the features that were asked for, each as {@code feature add} takes it: a name,
     * the coordinates of a plugin, or a name a feature catalog has; none for a command that names none
     * @return the application the command speaks of, or {@code null} when it cannot be spoken of, which
     * was then said as an error
     * @throws CommandLine.ParameterException for a mistake in the command line
     */
    protected FeatureScope scope(List<String> notes, List<String> features) {
        FeatureAddCommand add = taker(features)
        // an option of a feature that is not written as one is a mistake in the command line, as for feature add
        add.optionsOfFeatures()
        try {
            return FeatureScope.of(getProjectGenerator(), FeatureScope.typeOf(spec.commandLine(), type), FileSystemOutputHandler.getDefaultBaseDirectory().toPath(),
                    packOptions, add.packArtifacts(), grailsRepoUrl(), getOperatingSystem(),
                    { String line -> notes << shown(line.replace('@|blue ||@ ', '')) } as Consumer<String>,
                    { String line -> notes << shown(line) } as Consumer<String>, add)
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
