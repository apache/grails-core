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

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.OptionRelation
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.RecordedPackOptions
import org.apache.grails.forge.core.change.ChangePlan
import org.apache.grails.forge.core.change.ChangePlan.Action
import org.apache.grails.forge.core.change.ChangePlan.PlannedFile
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Adds features to the application in the current directory, which has to have the state the
 * generator wrote under {@code .grails}.
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureAddCommand.NAME, description = [
        'Adds features to the application in the current directory: its files and its record become those of an application that was created with them. ' +
                'A feature the application already has is left as it is.',
        'A feature takes the place of one the application has by default, as postgres takes that of h2, and the command says which it replaces; ' +
                'a feature that was asked for by name, when the application was created or added to, is replaced only with --replace, ' +
                'and an option the application was created with is never replaced.',
        'The files you changed keep your changes. When a feature changes or takes out what you changed too, or a file it writes is there already, ' +
                'nothing is written and the files are named.',
        'An application with the feature of a feature pack that is not part of Grails gets a feature only with that very pack: the jar of a plugin is ' +
                'fetched again by the coordinates and the SHA-256 its record has, and the pack of a directory is given again with --feature-pack-dir.'])
@Component
@Scope('prototype')
@CompileStatic
class FeatureAddCommand extends BaseCommand implements Callable<Integer>, ProjectCommand, OptionRelation.Declared {

    public static final String NAME = 'add'

    private static final Map<Action, String> ACTIONS = [(Action.ADD): 'add', (Action.UPDATE): 'update', (Action.MERGE): 'merge', (Action.ADOPT): 'keep',
                                                        (Action.CONFLICT): 'conflict', (Action.REMOVE): 'remove'] as Map<Action, String>

    @CommandLine.Parameters(arity = '1..*', paramLabel = 'FEATURE', split = ',', completionCandidates = FeatureNameCandidates,
            description = 'The names of the features to add: feature list lists those that can be added to the application.')
    List<String> features = new ArrayList<>()

    @CommandLine.Option(names = ['--dry-run'], description = 'Say which files would be added, changed and removed, which features would be replaced, and which conflicts there are, and change nothing.')
    boolean dryRun

    @CommandLine.Option(names = ['--replace'], description = 'Replace a feature that was asked for by name with the feature that takes its place, ' +
            'as mysql takes that of a postgres the application was created with. Without it only a feature the application has by default is replaced. ' +
            'It changes nothing when no such feature is in the way, and it does not choose between features of one exclusive group that are named together: ' +
            'those are refused.')
    boolean replace

    @CommandLine.Option(names = ['--diff'], description = 'Show the changes that would be made to the files, as a diff, and change nothing.')
    boolean diff

    @CommandLine.Option(names = ['--json'], description = 'With --dry-run: write what adding the features comes to as one JSON document for a tool to read, ' +
            'and nothing else: the outcome, the reason, the files with their paths in the project and what would be done to each, and whether the change can be made.')
    boolean json

    @CommandLine.Mixin
    RecordedPackOptions packOptions = new RecordedPackOptions()

    // asked of the Forge application when the command first needs it
    private final Supplier<ProjectGenerator> projectGeneratorSource

    @Autowired
    FeatureAddCommand(ProjectGenerator projectGenerator) {
        this.projectGeneratorSource = { projectGenerator } as Supplier<ProjectGenerator>
    }

    /** On a Forge application that is started when the command first needs it. */
    FeatureAddCommand(ForgeContext context) {
        this.projectGeneratorSource = { context.get().getBean(ProjectGenerator) } as Supplier<ProjectGenerator>
    }

    @Override
    List<OptionRelation> getOptionRelations() {
        // what call checks first
        return [OptionRelation.requires('--json', '--dry-run'), OptionRelation.exclusive(false, '--json', '--diff')]
    }

    @Override
    Integer call() throws Exception {
        if (json && (!dryRun || diff)) {
            throw new CommandLine.ParameterException(spec.commandLine(), '--json writes what --dry-run says as one JSON document: give it with --dry-run, and without --diff')
        }
        // the project the current directory is in, so that the command does the same in every directory of it
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        if (json) {
            return document(current)
        }
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            err(search.none('feature add'))
            return 1
        }
        Path directory = search.project == null ? current : search.project.directory
        boolean preview = dryRun || diff
        try {
            if (!ProjectState.exists(directory)) {
                throw new ProjectStateException(noRecord())
            }
            FeatureAddition addition = new FeatureAddition(projectGeneratorSource.get())
            for (String message : (preview ? Collections.<String>emptyList() : addition.recover(directory).getMessages())) {
                out("@|blue ||@ ${message}")
            }
            List<Pack> packs = packOptions.packs(directory, createPackArtifacts(), grailsRepoUrl(), { String line -> out("@|blue ||@ ${shown(line)}") } as Consumer<String>,
                    { String line -> warning(shown(line)) } as Consumer<String>)
            ChangePlan plan = addition.plan(directory, features, getOperatingSystem(), this, replace, packs)
            return apply(plan, preview)
        } catch (FeatureSelectionException e) {
            // features an application cannot have are a mistake in the command line, as they are when it is created
            throw new CommandLine.ParameterException(spec.commandLine(), shown(e.getMessage()))
        } catch (ProjectStateException | ProjectChangeException | IllegalArgumentException | PackArtifactException e) {
            // a record that another version wrote is moved to this one by a command that is named
            err(shown(ProjectMigrateCommand.withMove(spec, directory, e)))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
    }

    private String noRecord() {
        return "There is no application here that a feature can be added to: ${ProjectState.MANIFEST} is missing. " +
                'Run the command in the directory of an application that Grails 9 created. For an application that was there before, ' +
                "${UsageHelp.toType(spec, "${ProjectGroupCommand.NAME} ${ProjectMigrateCommand.NAME}")} writes the record the commands that create its classes read; " +
                'a feature is added to such an application by hand'
    }

    /**
     * What {@code --dry-run} says, as one JSON document and nothing else. The exit code is the one the
     * command ends with without {@code --json}: 2 for features that cannot be added as they were
     * asked for, 1 for a conflict and any other refusal or failure, and 0 otherwise.
     */
    private Integer document(Path current) {
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            return written(ChangeDocument.ofFeatureAdd(null).noProject(search.untrusted, search.none('feature add')), 1)
        }
        Path directory = search.project.directory
        ChangeDocument document = ChangeDocument.ofFeatureAdd(directory)
        if (!ProjectState.exists(directory)) {
            return written(document.ended(ChangeDocument.INVALID_RECORD, ChangeDocument.NO_RECORD, noRecord()), 1)
        }
        Notes notes = new Notes()
        ChangePlan plan
        try {
            // which jar of a pack is fetched again is said among the notes: the document is all that is written
            List<Pack> packs = packOptions.packs(directory, createPackArtifacts(), grailsRepoUrl(), { String line -> notes.out(line) } as Consumer<String>,
                    { String line -> notes.warning(line) } as Consumer<String>)
            plan = new FeatureAddition(projectGeneratorSource.get()).plan(directory, features, getOperatingSystem(), notes, replace, packs)
        } catch (Exception e) {
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return written(document.ended(e, ProjectMigrateCommand.withMove(spec, directory, e)), e instanceof FeatureSelectionException ? 2 : 1)
        }
        List<PlannedFile> conflicts = plan.getConflicts()
        document.planned(plan, notes.lines, conflicts.isEmpty() ? null : notAdded(plan, conflicts))
        return written(document, document.outcome in [ChangeDocument.NO_CHANGES, ChangeDocument.CHANGES] ? 0 : 1)
    }

    private int written(ChangeDocument document, int exitCode) {
        // written as it is: a document is not markup of the command line
        spec.commandLine().getOut().println(document.toJson())
        return exitCode
    }

    private static String notAdded(ChangePlan plan, List<PlannedFile> conflicts) {
        return "The ${plan.requested.size() == 1 ? 'feature was' : 'features were'} not added: ${conflicts.size()} ${conflicts.size() == 1 ? 'file' : 'files'} " +
                'cannot be written without losing what the application has. Nothing was changed. What is in the way is said with each file; ' +
                '--diff shows what the feature would change. Make room for it and add the feature again'
    }

    private Integer apply(ChangePlan plan, boolean preview) {
        for (String name : plan.alreadyThere) {
            out("@|blue ||@ The application already has the feature ${shown(name)}")
        }
        if (plan.isNothingToAdd()) {
            out('@|blue ||@ Nothing was changed')
            return 0
        }
        if (plan.isRefused() && !preview) {
            err(shown(plan.refusal))
            return 1
        }
        List<String> required = plan.added.findAll { String name -> !plan.requested.contains(name) }
        out("@|blue ||@ ${preview ? 'To add' : 'Adding'} the ${plan.requested.size() == 1 ? 'feature' : 'features'} ${shown(plan.requested.join(', '))}" +
                (required.isEmpty() ? '' : ", and with ${plan.requested.size() == 1 ? 'it' : 'them'} ${shown(required.join(', '))}"))
        for (String replacement : plan.replacements) {
            out("    ${shown(replacement)}")
        }
        for (PlannedFile file : plan.files) {
            out("    ${ACTIONS.get(file.action).padRight(8)} ${shown(file.path)}" +
                    (file.action == Action.MERGE ? ' (your changes are kept)' : '') +
                    (file.action == Action.ADOPT ? ' (it is what the feature makes it already)' : '') +
                    (file.action == Action.CONFLICT ? ": ${shown(file.reason)}" : ''))
            for (String line : file.details) {
                out("             ${shown(line)}")
            }
        }
        for (String lost : plan.lostFeatures) {
            out("    remove   the feature ${shown(lost)}")
        }
        for (String option : plan.changedOptions) {
            out("    change   ${shown(option)}")
        }
        if (diff) {
            // what a file has is not markup of this command
            for (String line : plan.diff()) {
                spec.commandLine().getOut().println(line)
            }
        }
        if (plan.isRefused()) {
            err(shown(plan.refusal))
            return 1
        }
        List<PlannedFile> conflicts = plan.getConflicts()
        if (!conflicts.isEmpty()) {
            err(notAdded(plan, conflicts))
            return 1
        }
        if (preview) {
            out('@|blue ||@ Nothing was changed')
            return 0
        }
        plan.apply()
        out("@|blue ||@ ${plan.requested.size() == 1 ? 'Feature' : 'Features'} ${shown(plan.requested.join(', '))} added" +
                (plan.replaced.isEmpty() ? '' : ", in the place of ${shown(plan.replaced.join(', '))}") +
                ". Commit the changes together with the directory ${ProjectState.DIRECTORY}")
        return 0
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

    private static String shown(String text) {
        return text.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
    }

    /** What the features say while the application is generated with them, kept for the document. */
    private static final class Notes implements ConsoleOutput {

        final List<String> lines = []

        @Override
        void out(String message) {
            lines << message
        }

        @Override
        void err(String message) {
            lines << message
        }

        @Override
        void warning(String message) {
            lines << message
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
