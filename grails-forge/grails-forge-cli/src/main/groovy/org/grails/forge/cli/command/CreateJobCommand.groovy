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

@CommandLine.Command(name = CreateJobCommand.NAME, description = 'Creates a new Quartz scheduled job')
@CompileStatic
class CreateJobCommand extends CodeGenCommand {

    public static final String NAME = 'create-job'

    @CommandLine.Parameters(paramLabel = 'JOB-NAME', description = 'The name of the job')
    String jobName

    @Inject
    CreateJobCommand(CodeGenConfig config) {
        super(config)
    }

    CreateJobCommand(CodeGenConfig config,
                            ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
                            ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        return config.getFeatures().contains('grails-quartz')
    }

    @Override
    List<ArtefactTemplate> getTemplates() {
        return [ArtefactTemplate.JOB]
    }

    @Override
    Integer call() throws Exception {
        if (!applies()) {
            throw new IllegalArgumentException('Please first select Grails Quartz Plugin')
        }
        // as the script of the Quartz plugin did: a name that ends with Job is taken without it,
        // unless Job is all there is of the name of the class
        return generate('job', newArtefact(jobName, 'Job'), [
                'grails-app/jobs/{packagePath}/{className}Job.groovy': ArtefactTemplate.JOB
        ])
    }
}
