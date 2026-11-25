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
package org.apache.grails.forge.cli.artefact

import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.core.state.BaselineIndex
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.Project
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.cli.command.CodeGenCommand
import org.grails.forge.template.DefaultTemplateRenderer
import picocli.CommandLine

/**
 * A command a feature of the application gives the command line: {@code create-<name>}, which writes
 * a class, and its test when the feature has a template for one, as the commands of the command line
 * itself write theirs. There is one of these for each command the record of the project has under a
 * feature, see {@link FeatureArtefact}, and none outside a project or in one without the feature.
 *
 * <p>The templates are files of the project, below {@code src/main/templates/features} and the name
 * of the feature, where the feature pack had them written when the application was generated. They
 * are text in which tokens are replaced, and nothing of them is run. Neither the feature pack nor the
 * project generator is asked for anything.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'create-class-of-feature')
@CompileStatic
class PackArtefactCommand extends CodeGenCommand {

    /** What the origin of such a command starts with in the document {@code help --json} writes, before the name of the feature. */
    public static final String ORIGIN = 'feature:'

    private static final String VARIABLE = '${'

    @CommandLine.Parameters(paramLabel = 'NAME', description = 'The name of the class to create')
    String className

    private final FeatureArtefact artefact

    PackArtefactCommand(CodeGenConfig config, FeatureArtefact artefact) {
        super(config)
        this.artefact = artefact
    }

    /**
     * @param config the record of the project
     * @param artefact a command the record has under a feature
     * @param factory the factory of the command line the command is added to
     * @return the command as picocli has it, under the name the feature gives it
     */
    static CommandLine.Model.CommandSpec specOf(CodeGenConfig config, FeatureArtefact artefact, CommandLine.IFactory factory) {
        CommandLine.Model.CommandSpec spec = CommandLine.Model.CommandSpec.forAnnotatedObject(new PackArtefactCommand(config, artefact), factory)
        spec.name(artefact.commandName)
        spec.usageMessage().description(shown(described(artefact)))
        return spec
    }

    private static String described(FeatureArtefact artefact) {
        return "${artefact.artefact.description} (feature ${artefact.feature})".toString()
    }

    /**
     * @return what the command is for and which feature it is of, as the text it is: one line of the
     * feature pack, which whoever shows it shows as plain text
     */
    String getDescription() {
        return described(artefact)
    }

    // text of a feature pack without what the command line would read as its markup, as a format or as a variable of its own
    private static String shown(String text) {
        return text.replace('@|', '@ |').replace('%', '%%').replace(VARIABLE, '$' + VARIABLE)
    }

    /**
     * @return the command of the feature this command is
     */
    FeatureArtefact getArtefact() {
        return artefact
    }

    /**
     * @return where the command comes from, as the document {@code help --json} writes says it
     */
    String getOrigin() {
        return ORIGIN + artefact.feature
    }

    @Override
    boolean applies() {
        return true
    }

    @Override
    Integer call() throws Exception {
        String suffix = artefact.artefact.suffix
        // as the commands of the command line: a name that ends with what the kind of class adds is taken without it
        Project names = newArtefact(className, suffix)
        File project = new File(outputHandler().getOutputLocation())
        Map<String, String> templates = new LinkedHashMap<>()
        templates.put(artefact.artefact.to, artefact.templatePath)
        if (artefact.artefact.testTo != null) {
            templates.put(artefact.artefact.testTo, artefact.testTemplatePath)
        }
        // every template before anything is written: the command writes all of its files or none
        Map<String, Supplier<String>> texts = new LinkedHashMap<>()
        for (Map.Entry<String, String> each : templates.entrySet()) {
            String template = each.value
            ArtefactTemplates.Source source = ArtefactTemplates.findInProject(project, template)
            if (source == null) {
                err("The template ${template} is not in the project, and ${spec.name()} writes ${each.key == artefact.artefact.to ? 'its class' : 'the test'} from it. " +
                        "It is a file the feature ${artefact.feature} wrote when the application was generated: restore it from version control, " +
                        "or from the copy the record of the project keeps of it, ${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(template)}, " +
                        "which ${UsageHelp.toType(spec, 'project regenerate --output DIRECTORY')} writes too")
                return 1
            }
            String path = DefaultTemplateRenderer.resolvePath(each.key, names.getProperties()).replace('//', '/')
            texts.put(path, { -> ArtefactTemplates.renderTokens(source, names) } as Supplier<String>)
        }
        return write('Rendered ' + artefact.artefact.name + ' to ', texts, overwrite)
    }
}
