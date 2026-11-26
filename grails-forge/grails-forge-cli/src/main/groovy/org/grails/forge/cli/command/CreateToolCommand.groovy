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

import java.util.function.Supplier
import groovy.transform.CompileStatic
import jakarta.inject.Inject
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.apache.grails.forge.cli.artefact.ArtefactTemplates
import org.apache.grails.forge.cli.artefact.ProjectFiles
import org.apache.grails.forge.cli.console.UsageHelp
import org.grails.forge.application.Project
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.util.NameUtils
import org.grails.forge.util.ThrowingSupplier
import picocli.CommandLine

/**
 * {@code create-tool}: a tool of the application, which the build runs, under {@code src/cli/groovy};
 * with {@code --standalone} a tool that runs as a process of its own, without Gradle and without the
 * application, under {@code src/standalone/groovy}. Nothing is written to {@code grails-app/commands},
 * where tools were before and which is deprecated, and no class of a deprecated type is written.
 *
 * <p>The template is {@code artifacts/Tool.groovy}, or {@code artifacts/StandaloneTool.groovy} with
 * {@code --standalone}, of the project when it has one under {@code src/main/templates}. A project
 * that has {@code artifacts/Command.groovy} there, the template of {@code create-command} of earlier
 * versions, and not the one of a tool, has a tool that the build runs written from it, after a
 * warning, into a file of the name that template gives its class.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = CreateToolCommand.NAME, description = [
        'Creates a tool of the application: a class the grails command runs by its name.',
        'A tool is written to src/cli/groovy and is run by the build, with the application started around it unless it says otherwise.',
        'With --standalone it is written to src/standalone/groovy and runs as a process of its own, without Gradle and without the application, on the files of the project.'
])
@CompileStatic
class CreateToolCommand extends CodeGenCommand {

    public static final String NAME = 'create-tool'

    /** Where a tool the build runs is written. */
    public static final String CLI_SOURCES = 'src/cli/groovy'

    /** Where a tool that runs as a process of its own is written. */
    public static final String STANDALONE_SOURCES = 'src/standalone/groovy'

    /** The deprecated folder of tools, which is never written to. */
    public static final String LEGACY_SOURCES = 'grails-app/commands'

    /** The template of create-command of earlier versions, which a project may have. */
    public static final String COMMAND_TEMPLATE = ArtefactTemplates.DIRECTORY + '/artifacts/Command.groovy'

    @CommandLine.Parameters(paramLabel = 'TOOL-NAME', description = 'The name of the tool to create')
    String toolName

    @CommandLine.Option(names = ['--standalone'], description = 'Creates a tool that runs as a process of its own, without Gradle and without the application, in src/standalone/groovy')
    boolean standalone

    @Inject
    CreateToolCommand(CodeGenConfig config) {
        super(config)
    }

    CreateToolCommand(CodeGenConfig config,
            ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
            ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        return true
    }

    @Override
    List<ArtefactTemplate> getTemplates() {
        return [ArtefactTemplate.TOOL, ArtefactTemplate.STANDALONE_TOOL]
    }

    @Override
    Integer call() throws Exception {
        Project names = newArtefact(toolName, 'Tool')
        File project = new File(outputHandler().getOutputLocation())
        ArtefactTemplate template = standalone ? ArtefactTemplate.STANDALONE_TOOL : ArtefactTemplate.TOOL
        // the template of create-command a project has, for a tool the build runs, where it has none of a tool
        ArtefactTemplates.Source command = standalone || ArtefactTemplates.findInProject(project, ArtefactTemplates.projectPath(template)) != null ? null :
                ArtefactTemplates.findInProject(project, COMMAND_TEMPLATE)
        String ending = command == null ? 'Tool' : 'Command'
        String folder = standalone ? STANDALONE_SOURCES : CLI_SOURCES
        String path = DefaultTemplateRenderer.resolvePath("${folder}/{packagePath}/{className}${ending}.groovy", names.getProperties()).replace('//', '/')
        String other = otherSource(project, names, ending)
        if (other != null) {
            err("${other} is a tool of the name ${NameUtils.getScriptName(names.getClassName())} already, and a project has one tool of a name. " +
                    "${other.startsWith(LEGACY_SOURCES + '/') ? "${LEGACY_SOURCES} is deprecated: move that tool to ${CLI_SOURCES} as it is" : 'Rename one of them'}. Nothing was written")
            return 1
        }
        Supplier<String> text
        if (command == null) {
            text = { -> artefact(template, names) } as Supplier<String>
        } else {
            warning("Using the template ${COMMAND_TEMPLATE} of the project, the template of create-command, which is deprecated: " +
                    "a tool is written from ${ArtefactTemplates.projectPath(ArtefactTemplate.TOOL)}, which install-templates copies into the project")
            text = { -> ArtefactTemplates.render(command, ArtefactTemplate.TOOL, names) } as Supplier<String>
        }
        Integer exit = write('Rendered tool to ', [(path): text] as Map<String, Supplier<String>>, overwrite)
        if (exit == 0) {
            // the name the tool has by its class: the build lists it when it compiles the project, and
            // the command line has the build list it when the name is typed before that
            out("@|blue ||@ Run it with: ${UsageHelp.toType(spec, NameUtils.getScriptName(names.getClassName()))}")
        }
        return exit
    }

    // The source of a tool of the same name in the same package in another folder of tools, where the
    // build would have two tools of the name, or two classes of the name: its path in the project, or null.
    private String otherSource(File project, Project names, String written) {
        String own = standalone ? STANDALONE_SOURCES : CLI_SOURCES
        for (String folder : [CLI_SOURCES, 'src/cli/java', STANDALONE_SOURCES, 'src/standalone/java', LEGACY_SOURCES]) {
            for (String ending : ['Tool.groovy', 'Command.groovy', 'Tool.java', 'Command.java']) {
                String path = DefaultTemplateRenderer.resolvePath("${folder}/{packagePath}/{className}${ending}", names.getProperties()).replace('//', '/')
                // what is in the folder the tool is written to is replaced with -f, or refused as being there already
                if (folder == own && ending == written + '.groovy') {
                    continue
                }
                if (ProjectFiles.isThere(project, path)) {
                    return path
                }
            }
        }
        return null
    }
}
