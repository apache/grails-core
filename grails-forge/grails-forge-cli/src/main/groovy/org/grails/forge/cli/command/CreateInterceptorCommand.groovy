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

@CommandLine.Command(name = CreateInterceptorCommand.NAME, description = 'Creates an interceptor')
@CompileStatic
class CreateInterceptorCommand extends CodeGenCommand {

    public static final String NAME = 'create-interceptor'

    @CommandLine.Parameters(paramLabel = 'INTERCEPTOR-NAME', description = 'The name of the interceptor to create')
    String interceptorName

    @Inject
    CreateInterceptorCommand(CodeGenConfig config) {
        super(config)
    }

    CreateInterceptorCommand(CodeGenConfig config,
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
        return [ArtefactTemplate.INTERCEPTOR, ArtefactTemplate.INTERCEPTOR_TEST]
    }

    @Override
    Integer call() throws Exception {
        return generate('interceptor class', getProject(interceptorName, 'Interceptor'), [
                'grails-app/controllers/{packagePath}/{className}Interceptor.groovy': ArtefactTemplate.INTERCEPTOR,
                'src/test/groovy/{packagePath}/{className}InterceptorSpec.groovy': ArtefactTemplate.INTERCEPTOR_TEST
        ])
    }
}
