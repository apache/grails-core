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
import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.core.change.RecordMigration
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
 * written, and the files of the application are not read.
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureListCommand.NAME, description = [
        'Lists the features of the application in the current directory, from its record: those it has, which of them were asked for by name, ' +
                'the features feature add can add to it, and for each of the others why it cannot.',
        'Outside a project the features an application can be created with are listed by the create commands, with --list-features.'])
@Component
@Scope('prototype')
@CompileStatic
class FeatureListCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    public static final String NAME = 'list'

    private static final int NAME_COLUMN = 30

    // the width the usage of the commands is laid out for
    private static final int WIDTH = 100

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
        FeatureAvailability availability = FeatureAvailability.of(projectGeneratorSource.get(), state, getOperatingSystem())
        listHas(state, availability)
        if (availability.refusal != null) {
            // the record of an earlier version is moved to this one by a command that is named
            String move = state.manifest.isTracked() && RecordMigration.isMovable(state.lock) ? ". ${ProjectMigrateCommand.moveAdvice(spec)}" : ''
            out("@|blue ||@ No feature is added to this application with feature add: ${shown(availability.refusal)}${move}")
            return 0
        }
        listAddable(availability)
        listNotAddable(availability)
        return 0
    }

    private void listHas(ProjectState state, FeatureAvailability availability) {
        List<String> has = state.lock.featureNames
        List<String> asked = availability.askedFor
        out("@|blue ||@ The application ${shown(state.manifest.name)} (${shown(written(state.manifest.applicationType))}) has ${has.size()} " +
                "${has.size() == 1 ? 'feature' : 'features'}, as its record in ${ProjectState.DIRECTORY} says:")
        row('asked for', asked.isEmpty() ? 'none' : asked.join(', '))
        row('with them', has.findAll { String name -> !asked.contains(name) }.sort().join(', ') ?: 'none')
    }

    private void listAddable(FeatureAvailability availability) {
        List<FeatureAvailability.Verdict> addable = availability.verdicts.findAll { FeatureAvailability.Verdict each -> each.addable }
        if (addable.isEmpty()) {
            out('@|blue ||@ No feature can be added to it')
            return
        }
        out("@|blue ||@ ${addable.size()} ${addable.size() == 1 ? 'feature' : 'features'} can be added to it with: ${UsageHelp.toType(spec, 'feature add FEATURE')}")
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
