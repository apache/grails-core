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
import jakarta.inject.Inject
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.grails.forge.application.Project
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.util.NameUtils
import org.grails.forge.util.ThrowingSupplier
import picocli.CommandLine

/**
 * {@code create-script}: a script under {@code src/main/scripts}, named in lower case with hyphens whichever way
 * the name is given.
 *
 * @since 9.0
 */
@CommandLine.Command(name = CreateScriptCommand.NAME, description = 'Creates a Grails script')
@CompileStatic
class CreateScriptCommand extends CodeGenCommand {

    public static final String NAME = 'create-script'

    @CommandLine.Parameters(paramLabel = 'SCRIPT-NAME', description = 'The name of the script to create')
    String scriptName

    @Inject
    CreateScriptCommand(CodeGenConfig config) {
        super(config)
    }

    CreateScriptCommand(CodeGenConfig config,
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
        return [ArtefactTemplate.SCRIPT]
    }

    @Override
    Integer call() throws Exception {
        if (scriptName != null && scriptName.contains('.')) {
            // a script is not in a package, and a name with the ending of a file is not taken for one without
            throw new CommandLine.ParameterException(spec.commandLine(), "\"${FeatureSelectionException.shown(scriptName)}\" is not a name for a script: " +
                    'a script has no package and its name is given without .groovy')
        }
        Project names = newArtefact(scriptName, null)
        String file = "src/main/scripts/${NameUtils.getScriptName(names.getClassName())}.groovy"
        return generate('script', names, [(file): ArtefactTemplate.SCRIPT])
    }
}
