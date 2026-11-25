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

import java.nio.file.Path
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.pack.AddedPackOptions
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.core.change.ChangePlan
import org.apache.grails.forge.core.change.ChangePlan.PlannedFile
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.diff.FeatureDiffer
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine

/**
 * {@code feature diff}: what {@code feature add} would change in the application of the current
 * directory, as a unified diff, and nothing is changed. The diff is the one {@code feature add --diff}
 * shows: both ask {@link FeatureAddition} for the same plan, so that what this command shows is what
 * that command writes. Outside a project, and with {@code --type}, it is what the features change in
 * a new application of a type, as {@link FeatureDiffer} works it out for the Forge web application.
 *
 * <p>The diff is all that is written on the standard output; what else there is to say, as which
 * feature is replaced, is said on the standard error.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureDiffCommand.NAME, description = [
        'Shows what feature add would change in the files of the application in the current directory, as a unified diff, and changes nothing. ' +
                'It is the diff feature add --diff shows, and is all that is written on the standard output.',
        'Outside a project, and with --type, the diff is what the features change in a new application of the type.',
        'The command ends with exit code 0 when the features can be added, also when that changes nothing; with 1 when they cannot, as when a file ' +
                'of yours is in the way, and the diff is then what they would change; and with 2 for features the application cannot have as they were asked for.'])
@Component
@Scope('prototype')
@CompileStatic
class FeatureDiffCommand extends FeatureQueryCommand {

    public static final String NAME = 'diff'

    @CommandLine.Parameters(arity = '1..*', paramLabel = 'FEATURE', split = ',', completionCandidates = FeatureNameCandidates,
            description = 'The names of the features, as they are given to feature add.')
    List<String> features = new ArrayList<>()

    @CommandLine.Option(names = ['--replace'], description = 'As for feature add: a feature that was asked for by name makes way for the feature that takes its place.')
    boolean replace

    // declared where feature add declares them, so that what that command takes this one takes
    @CommandLine.Mixin
    AddedPackOptions addedOptions = new AddedPackOptions()

    @Autowired
    FeatureDiffCommand(ProjectGenerator projectGenerator) {
        super({ projectGenerator })
    }

    /** On a Forge application that is started when the command first needs it. */
    FeatureDiffCommand(ForgeContext context) {
        super(generatorOf(context))
    }

    @Override
    Integer call() throws Exception {
        ApplicationType given = FeatureScope.typeOf(spec.commandLine(), type)
        ConsoleProject.Search search = ConsoleProject.search(FileSystemOutputHandler.getDefaultBaseDirectory().toPath())
        if (given != null || search.project == null) {
            List<String> ofAnAddition = addedOptions.given()
            if (!ofAnAddition.isEmpty()) {
                // there is no application a pack is added to: a new one has the features of Grails and of the packs that are given as directories
                throw new CommandLine.ParameterException(spec.commandLine(), "${ofAnAddition.join(', ')} ${ofAnAddition.size() == 1 ? 'is an option' : 'are options'} " +
                        "for a feature that is added to the application of the current directory. ${given != null ? 'With --type' : 'Outside a project'}, feature diff " +
                        'shows a new application, with the features of Grails and of the feature packs --feature-pack-dir gives')
            }
            return ofNewApplication()
        }
        return ofProject(search.project.directory)
    }

    // feature add of the same features, with the feature packs, the repositories and the options this command was given, which takes the packs that are added
    private FeatureAddCommand asAdded() {
        FeatureDiffCommand diff = this
        FeatureAddCommand add = new FeatureAddCommand(getProjectGenerator()) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return diff.createPackArtifacts()
            }

            @Override
            protected String grailsRepoUrl() {
                return diff.grailsRepoUrl()
            }
        }
        add.spec = spec
        add.features = features
        add.packOptions = packOptions
        add.addedOptions = addedOptions
        add.replace = replace
        return add
    }

    // the plan of feature add, of which the diff is written
    private Integer ofProject(Path directory) {
        FeatureAddCommand add = asAdded()
        // an option of a feature that is not written as one is a mistake in the command line, as for feature add
        Map<String, Map<String, String>> options = add.optionsOfFeatures()
        try {
            if (!ProjectState.exists(directory)) {
                throw new ProjectStateException("${FeatureAddCommand.noRecord(spec)}. With --type, feature diff shows what the features change in a new application of that type")
            }
            List<Pack> packs = packOptions.packs(directory, createPackArtifacts(), grailsRepoUrl(), { String line -> note(shown(line)) } as Consumer<String>,
                    { String line -> warned(shown(line)) } as Consumer<String>)
            // the feature of a plugin that is named by its coordinates, or of a pack that is given, is taken as feature add takes it
            FeatureAddCommand.Asked asked = add.asked(directory, packs, { String line -> note(shown(line.replace('@|blue ||@ ', ''))) } as Consumer<String>,
                    { String line -> warned(shown(line)) } as Consumer<String>)
            FeatureAddition addition = new FeatureAddition(getProjectGenerator())
            addition.featureOptions = options
            ChangePlan plan = addition.plan(directory, asked.features, getOperatingSystem(), new Notes(), replace, asked.ofTheApplication, asked.added)
            add.heldToItsBom(directory, plan)
            for (String name : plan.alreadyThere) {
                note("The application already has the feature ${shown(name)}")
            }
            if (plan.isNothingToAdd()) {
                note('Nothing would be changed')
                return 0
            }
            for (String replacement : plan.replacements) {
                note(shown(replacement))
            }
            for (String line : plan.diff()) {
                written(line)
            }
            if (plan.isRefused()) {
                err(shown(plan.refusal))
                return 1
            }
            List<PlannedFile> conflicts = plan.getConflicts()
            if (!conflicts.isEmpty()) {
                err(FeatureAddCommand.notAdded(plan, conflicts))
                return 1
            }
            return 0
        } catch (FeatureSelectionException e) {
            // features an application cannot have are a mistake in the command line, as they are for feature add
            throw new CommandLine.ParameterException(spec.commandLine(), shown(e.getMessage()) + add.notInCatalog())
        } catch (ProjectStateException | ProjectChangeException | IllegalArgumentException | PackArtifactException e) {
            err(shown(ProjectMigrateCommand.withMove(spec, directory, e)))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
    }

    // what the features change in an application of a type that is created with them
    private Integer ofNewApplication() {
        List<String> notes = []
        FeatureScope scope = scope(notes)
        if (scope == null) {
            return 1
        }
        for (String line : notes) {
            note(line)
        }
        Lines lines = new Lines()
        try {
            GeneratorContext context = getProjectGenerator().createGeneratorContext(scope.type, scope.project, scope.options, getOperatingSystem(), features,
                    new Notes(), scope.packs)
            new FeatureDiffer().produceDiff(getProjectGenerator(), context, lines)
        } catch (FeatureSelectionException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), shown(e.getMessage()))
        } catch (IllegalArgumentException e) {
            // a feature that refuses the application it is selected for, as one that needs a later JDK
            throw new CommandLine.ParameterException(spec.commandLine(), shown(e.getMessage() ?: e.getClass().getSimpleName()))
        }
        if (lines.written.isEmpty()) {
            note("${features.size() == 1 ? 'The feature changes' : 'The features change'} nothing in ${scope.described}: it has ${features.size() == 1 ? 'it' : 'them'} by default")
            return 0
        }
        for (String line : lines.written) {
            written(line)
        }
        return 0
    }

    /** What the features say while the application is generated with them, said where the diff is not. */
    private final class Notes implements ConsoleOutput {

        @Override
        void out(String message) {
            note(shown(message))
        }

        @Override
        void err(String message) {
            warned(shown(message))
        }

        @Override
        void warning(String message) {
            warned(shown(message))
        }

        @Override
        boolean showStacktrace() {
            return false
        }

        @Override
        boolean verbose() {
            return false
        }
    }

    /** The lines of a diff as they are written, a file after the other. */
    private static final class Lines implements ConsoleOutput {

        final List<String> written = []

        @Override
        void out(String message) {
            // the line that ends the diff of a file; what a file has is not what a terminal acts on
            written << (message == '\n' ? '' : message.replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}&&[^\t]]/, '?'))
        }

        @Override
        void err(String message) {
            written << message
        }

        @Override
        void warning(String message) {
            written << message
        }

        @Override
        boolean showStacktrace() {
            return false
        }

        @Override
        boolean verbose() {
            return false
        }
    }
}
