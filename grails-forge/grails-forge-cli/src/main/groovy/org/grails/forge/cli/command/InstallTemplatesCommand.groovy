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
import java.util.function.Supplier
import jakarta.inject.Inject
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.apache.grails.forge.cli.artefact.ArtefactTemplates
import org.apache.grails.forge.cli.artefact.ProjectFiles
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.util.ThrowingSupplier
import picocli.CommandLine

/**
 * {@code install-templates}: copies the templates of the code generation commands the project has into
 * {@code src/main/templates}, where those commands read them from then on. A template the project has
 * already is kept unless the command is forced.
 *
 * @since 9.0
 */
@CommandLine.Command(name = InstallTemplatesCommand.NAME, description = 'Copies the templates of the code generation commands into src/main/templates')
@CompileStatic
class InstallTemplatesCommand extends CodeGenCommand {

    public static final String NAME = 'install-templates'

    @Inject
    InstallTemplatesCommand(CodeGenConfig config) {
        super(config)
    }

    InstallTemplatesCommand(CodeGenConfig config,
            ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
            ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        return true
    }

    @Override
    Integer call() throws Exception {
        // of the commands this project has, which are those beside this one
        Set<ArtefactTemplate> templates = new TreeSet<>({ ArtefactTemplate a, ArtefactTemplate b -> a.location <=> b.location } as Comparator<ArtefactTemplate>)
        CommandLine parent = spec.commandLine().getParent()
        if (parent != null) {
            for (CommandLine each : parent.getSubcommands().values()) {
                Object command = each.getCommand()
                if (command instanceof CodeGenCommand) {
                    templates.addAll(((CodeGenCommand) command).getTemplates())
                }
            }
        }
        File project = new File(outputHandler().getOutputLocation())
        Map<String, Supplier<String>> copies = new LinkedHashMap<>()
        List<String> kept = []
        for (ArtefactTemplate template : templates) {
            String path = ArtefactTemplates.projectPath(template)
            if (ProjectFiles.isThere(project, path) && !overwrite) {
                kept.add(path)
            } else {
                copies.put(path, { -> ArtefactTemplates.builtIn(template) } as Supplier<String>)
            }
        }
        // what is in the way of a template that is kept is in the way of the command as well
        Map<String, String> refusals = ProjectFiles.refusals(project, kept)
        if (refusals) {
            refusals.values().each { String refusal -> err(refusal) }
            return 1
        }
        Integer exit = write('Copied template to ', copies, true)
        if (exit != 0) {
            return exit
        }
        kept.each { String path -> warning('Kept ' + path + ', which the project has already') }
        if (kept) {
            out('@|blue ||@ Run again with -f to replace the ' + (kept.size() == 1 ? 'template that was kept' : kept.size() + ' templates that were kept'))
        }
        return 0
    }
}
