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
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.grails.forge.util.ThrowingSupplier
import jakarta.inject.Inject
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import picocli.CommandLine

@CommandLine.Command(name = CreateDomainClassCommand.NAME, description = 'Creates a Domain Class')
@CompileStatic
class CreateDomainClassCommand extends CodeGenCommand {

    public static final String NAME = 'create-domain-class'

    @CommandLine.Parameters(paramLabel = 'DOMAIN-CLASS-NAME', description = 'The name of the domain class to create')
    String domainClassName

    @Inject
    CreateDomainClassCommand(CodeGenConfig config) {
        super(config)
    }

    CreateDomainClassCommand(CodeGenConfig config,
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
        return [ArtefactTemplate.DOMAIN_CLASS, ArtefactTemplate.DOMAIN_CLASS_TEST]
    }

    @Override
    Integer call() throws Exception {
        return generate('domain class', getProject(domainClassName), [
                'grails-app/domain/{packagePath}/{className}.groovy': ArtefactTemplate.DOMAIN_CLASS,
                'src/test/groovy/{packagePath}/{className}Spec.groovy': ArtefactTemplate.DOMAIN_CLASS_TEST
        ])
    }
}
