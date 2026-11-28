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
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.RecordedPackOptions
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.change.RecordMigration
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.io.FileSystemOutputHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine

/**
 * Lists the features of the application in the current directory as its record says them: those it
 * has, those {@code feature add} can add to it, and for each of the others why it cannot. Nothing is
 * written, and the files of the application are not read. The feature packs that are not part of
 * Grails the application was generated with are given again as {@code feature add} is given them.
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureListCommand.NAME, description = [
        'Lists the features of the application in the current directory, from its record: those it has, which of them were asked for by name, ' +
                'the features feature add can add to it, and for each of the others why it cannot.',
        'An application with the feature of a feature pack that is not part of Grails, or made from a preset, gets a feature only with that very pack, ' +
                'and its features are listed with it, given as to feature add: the jar of a plugin is fetched again by the coordinates and the SHA-256 ' +
                'its record has, and the pack of a directory is given again with --feature-pack-dir. Without it, the list says which pack is needed.',
        'Outside a project the features an application can be created with are listed by the create commands, with --list-features.'])
@Component
@Scope('prototype')
@CompileStatic
class FeatureListCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    public static final String NAME = 'list'

    private static final int NAME_COLUMN = 30

    // the width the usage of the commands is laid out for
    private static final int WIDTH = 100

    @CommandLine.Mixin
    RecordedPackOptions packOptions = new RecordedPackOptions()

    // asked of the Forge application when the command first needs it
    private final Supplier<ProjectGenerator> projectGeneratorSource

    @Autowired
    FeatureListCommand(ProjectGenerator projectGenerator) {
        this.projectGeneratorSource = { projectGenerator } as Supplier<ProjectGenerator>
    }

    /** On a Forge application that is started when the command first needs it. */
    FeatureListCommand(ForgeContext context) {
        this.projectGeneratorSource = { context.get().getBean(ProjectGenerator) } as Supplier<ProjectGenerator>
    }

    @Override
    Integer call() throws Exception {
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            err("${search.none('feature list')}. The features an application is created with are listed by: ${UsageHelp.toType(spec, 'create-app --list-features')}")
            return 1
        }
        Path directory = search.project.directory
        if (!ProjectState.exists(directory)) {
            err(ProjectShowCommand.noRecord(spec))
            return 1
        }
        ProjectState state
        try {
            // what the application has is all that is read: the files and the baseline are not looked at
            state = ProjectState.readSelection(directory, true)
        } catch (ProjectStateException e) {
            err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
            return 1
        }
        FeatureAvailability availability
        String packsMissing
        try {
            // the packs are those feature add would generate the application with, given and fetched as it does:
            // nothing is asked for an application without them, and a pack it does not have is refused
            List<Pack> packs = FeatureAvailability.refusal(state) != null ? Collections.<Pack>emptyList() : packs(directory)
            availability = FeatureAvailability.of(projectGeneratorSource.get(), state, getOperatingSystem(), packs)
            packsMissing = availability.packsMissing
        } catch (ProjectChangeException e) {
            if (e.getReason() != ProjectChangeException.PACK_MISSING) {
                err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
                return 1
            }
            // a jar of the record that could not be fetched again is missing as a pack that was not given is
            availability = null
            packsMissing = e.getMessage()
        } catch (IllegalArgumentException | PackArtifactException e) {
            err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
            return 1
        }
        listHas(state)
        if (availability?.refusal != null) {
            // the record of an earlier version is moved to this one by a command that is named
            String move = state.manifest.hasBaseline() && RecordMigration.isMovable(state.lock) ? ". ${ProjectMigrateCommand.moveAdvice(spec)}" : ''
            out("@|blue ||@ No feature is added to this application with feature add: ${shown(availability.refusal)}${move}")
            return 0
        }
        if (packsMissing != null) {
            out('@|blue ||@ The features feature add can add to it are listed with the feature packs it was generated with, which feature add needs too: ' +
                    "give them to feature list as to feature add. ${shown(packsMissing)}")
            return 0
        }
        listAddable(availability, hasPacks(state))
        listNotAddable(availability)
        return 0
    }

    // the packs of the record, as feature add has them: a jar is fetched again, and said to be
    private List<Pack> packs(Path directory) {
        return packOptions.packs(directory, createPackArtifacts(), grailsRepoUrl(), { String line -> out("@|blue ||@ ${shown(line)}") } as Consumer<String>,
                { String line -> warning(shown(line)) } as Consumer<String>)
    }

    private static boolean hasPacks(ProjectState state) {
        return state.lock.features.any { LockedFeature feature -> feature.source == LockedFeature.PACK }
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

    private void listHas(ProjectState state) {
        List<String> has = state.lock.featureNames
        List<String> asked = state.manifest.requestedFeatures*.name
        out("@|blue ||@ The application ${shown(state.manifest.name)} (${shown(written(state.manifest.applicationType))}) has ${has.size()} " +
                "${has.size() == 1 ? 'feature' : 'features'}, as its record in ${ProjectState.DIRECTORY} says:")
        row('asked for', asked.isEmpty() ? 'none' : asked.join(', '))
        row('with them', has.findAll { String name -> !asked.contains(name) }.sort().join(', ') ?: 'none')
    }

    private void listAddable(FeatureAvailability availability, boolean withPacks) {
        List<FeatureAvailability.Verdict> addable = availability.verdicts.findAll { FeatureAvailability.Verdict each -> each.addable }
        if (addable.isEmpty()) {
            out('@|blue ||@ No feature can be added to it')
            return
        }
        // an application with packs gets a feature only with them, given to feature add as they were given here
        out("@|blue ||@ ${addable.size()} ${addable.size() == 1 ? 'feature' : 'features'} can be added to it with: ${UsageHelp.toType(spec, 'feature add FEATURE')}" +
                (withPacks ? ', given the feature packs of the application as here' : ''))
        Map<String, List<FeatureAvailability.Verdict>> byCategory = new TreeMap<>(addable.groupBy { FeatureAvailability.Verdict each -> each.category })
        byCategory.each { String category, List<FeatureAvailability.Verdict> verdicts ->
            out("  @|bold ${shown(category)}|@")
            for (FeatureAvailability.Verdict verdict : verdicts) {
                row(verdict.name, verdict.description)
                for (String note : verdict.notes) {
                    row('', note)
                }
            }
        }
    }

    private void listNotAddable(FeatureAvailability availability) {
        List<FeatureAvailability.Verdict> refused = availability.verdicts.findAll { FeatureAvailability.Verdict each -> !each.addable }
        if (refused.isEmpty()) {
            return
        }
        out("@|blue ||@ ${refused.size()} ${refused.size() == 1 ? 'feature' : 'features'} of Grails for this type of application cannot be added to it:")
        for (FeatureAvailability.Verdict verdict : refused) {
            row(verdict.name, verdict.notes.join('; '))
        }
    }

    // a name and its value, the value broken between words into lines that fit the width of the help
    private void row(String what, String value) {
        String start = "    ${shown(what).padRight(NAME_COLUMN)} "
        StringBuilder line = new StringBuilder(start)
        boolean first = true
        for (String word : shown(value).split(' ')) {
            if (!first && line.length() + 1 + word.length() > WIDTH) {
                out(line.toString())
                line = new StringBuilder(' ' * start.length())
                first = true
            }
            line.append(first ? word : ' ' + word)
            first = false
        }
        out(line.toString())
    }

    private static String written(Enum value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-')
    }

    // a name or a message as one line that is no markup of this command
    private static String shown(String text) {
        return GradleRunner.line(text ?: '')
    }
}
