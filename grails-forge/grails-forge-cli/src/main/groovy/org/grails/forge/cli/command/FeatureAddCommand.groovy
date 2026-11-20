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
import org.apache.grails.forge.core.change.ChangePlan
import org.apache.grails.forge.core.change.ChangePlan.Action
import org.apache.grails.forge.core.change.ChangePlan.PlannedFile
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.file.Path
import java.util.concurrent.Callable

/**
 * Adds features to the application in the current directory, which has to have the state the
 * generator wrote under {@code .grails}.
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureAddCommand.NAME, description = [
        'Adds features to the application in the current directory, as if it had been created with them.',
        'The files you changed keep your changes. When a feature changes what you changed too, or a file it writes is there already, ' +
                'nothing is written and the files are named.'])
@Component
@Scope('prototype')
@CompileStatic
class FeatureAddCommand extends BaseCommand implements Callable<Integer> {

    public static final String NAME = 'add'

    private static final Map<Action, String> ACTIONS = [(Action.ADD): 'add', (Action.UPDATE): 'update', (Action.MERGE): 'merge', (Action.ADOPT): 'keep',
                                                        (Action.CONFLICT): 'conflict'] as Map<Action, String>

    @CommandLine.Parameters(arity = '1..*', paramLabel = 'FEATURE', split = ',',
            description = 'The names of the features to add, as the create commands list them with --list-features.')
    List<String> features = new ArrayList<>()

    @CommandLine.Option(names = ['--dry-run'], description = 'Say which files would be added and changed, and which conflicts there are, and change nothing.')
    boolean dryRun

    @CommandLine.Option(names = ['--diff'], description = 'Show the changes that would be made to the files, as a diff, and change nothing.')
    boolean diff

    private final ProjectGenerator projectGenerator

    FeatureAddCommand(ProjectGenerator projectGenerator) {
        this.projectGenerator = projectGenerator
    }

    @Override
    Integer call() throws Exception {
        Path directory = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        boolean preview = dryRun || diff
        try {
            if (!ProjectState.exists(directory)) {
                throw new ProjectStateException("There is no application here that a feature can be added to: ${ProjectState.MANIFEST} is missing. " +
                        'Run the command in the directory of an application that Grails 9 created')
            }
            if (!preview && FeatureAddition.recover(directory)) {
                out('@|blue ||@ An earlier change of the application that was interrupted was undone')
            }
            ChangePlan plan = new FeatureAddition(projectGenerator).plan(directory, features, getOperatingSystem(), this)
            return apply(plan, preview)
        } catch (FeatureSelectionException e) {
            // features an application cannot have are a mistake in the command line, as they are when it is created
            throw new CommandLine.ParameterException(spec.commandLine(), shown(e.getMessage()))
        } catch (ProjectStateException | ProjectChangeException | IllegalArgumentException e) {
            err(shown(e.getMessage() ?: e.getClass().getSimpleName()))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
    }

    private Integer apply(ChangePlan plan, boolean preview) {
        for (String name : plan.alreadyThere) {
            out("@|blue ||@ The application already has the feature ${shown(name)}")
        }
        if (plan.isNothingToAdd()) {
            out('@|blue ||@ Nothing was changed')
            return 0
        }
        List<String> required = plan.added.findAll { String name -> !plan.requested.contains(name) }
        out("@|blue ||@ ${preview ? 'To add' : 'Adding'} the ${plan.requested.size() == 1 ? 'feature' : 'features'} ${shown(plan.requested.join(', '))}" +
                (required.isEmpty() ? '' : ", and with ${plan.requested.size() == 1 ? 'it' : 'them'} ${shown(required.join(', '))}"))
        for (PlannedFile file : plan.files) {
            out("    ${ACTIONS.get(file.action).padRight(8)} ${shown(file.path)}" +
                    (file.action == Action.MERGE ? ' (your changes are kept)' : '') +
                    (file.action == Action.ADOPT ? ' (it is what the feature makes it already)' : '') +
                    (file.action == Action.CONFLICT ? ": ${shown(file.reason)}" : ''))
        }
        if (diff) {
            // what a file has is not markup of this command
            for (String line : plan.diff()) {
                spec.commandLine().getOut().println(line)
            }
        }
        List<PlannedFile> conflicts = plan.getConflicts()
        if (!conflicts.isEmpty()) {
            err("The ${plan.requested.size() == 1 ? 'feature was' : 'features were'} not added: ${conflicts.size()} ${conflicts.size() == 1 ? 'file' : 'files'} " +
                    'cannot be written without losing what the application has. Nothing was changed. See what the feature would change with --diff, ' +
                    'make room for it in the files that are named, and add the feature again')
            return 1
        }
        if (preview) {
            out('@|blue ||@ Nothing was changed')
            return 0
        }
        plan.apply()
        out("@|blue ||@ ${plan.requested.size() == 1 ? 'Feature' : 'Features'} ${shown(plan.requested.join(', '))} added. " +
                "Commit the changes together with the directory ${ProjectState.DIRECTORY}")
        return 0
    }

    private static String shown(String text) {
        return text.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
    }
}
