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
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.util.ThrowingSupplier
import picocli.CommandLine

/**
 * {@code create-functional-test}: a specification under {@code src/integration-test/groovy} that tests the
 * running application from outside. In a REST API application or REST API plugin it calls the application
 * over HTTP, as the command of that profile did. In another project that has a Geb feature it is a Geb
 * specification, which the script of the Geb plugin wrote: one that extends {@code ContainerGebSpec} where
 * the browser runs in a container ({@code geb-with-testcontainers}) and {@code GebSpec} where it is a
 * browser of the machine ({@code geb-with-local-browsers}).
 *
 * @since 9.0
 */
@CommandLine.Command(name = CreateFunctionalTestCommand.NAME, description = 'Creates a functional test')
@CompileStatic
class CreateFunctionalTestCommand extends CodeGenCommand {

    public static final String NAME = 'create-functional-test'

    /** The feature of a project whose Geb specifications run their browser in a container. */
    public static final String GEB_CONTAINER = 'geb-with-testcontainers'

    /** The feature of a project whose Geb specifications run a browser of the machine. */
    public static final String GEB_LOCAL = 'geb-with-local-browsers'

    @CommandLine.Parameters(paramLabel = 'TEST-NAME', description = 'The name of the functional test to create')
    String testName

    @Inject
    CreateFunctionalTestCommand(CodeGenConfig config) {
        super(config)
    }

    CreateFunctionalTestCommand(CodeGenConfig config,
            ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
            ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        return template() != null
    }

    @Override
    List<ArtefactTemplate> getTemplates() {
        ArtefactTemplate template = template()
        return template == null ? Collections.<ArtefactTemplate>emptyList() : [template]
    }

    // the template of the test this project gets, or null for a project the command is no command of
    private ArtefactTemplate template() {
        if (config == null) {
            return null
        }
        ApplicationType type = config.getApplicationType()
        if (type != null && type.isRestApi()) {
            return ArtefactTemplate.FUNCTIONAL_TEST
        }
        List<String> features = config.getFeatures()
        if (features == null) {
            return null
        }
        // a project has one of the two; the browser of the machine was asked for where it is there
        if (features.contains(GEB_LOCAL)) {
            return ArtefactTemplate.GEB_FUNCTIONAL_TEST
        }
        return features.contains(GEB_CONTAINER) ? ArtefactTemplate.GEB_CONTAINER_FUNCTIONAL_TEST : null
    }

    @Override
    Integer call() throws Exception {
        ArtefactTemplate template = template()
        if (template == null) {
            throw new IllegalArgumentException('create-functional-test is a command of a REST API project and of a project with a Geb feature')
        }
        return generate('functional test', newArtefact(testName, 'Spec'), [
                'src/integration-test/groovy/{packagePath}/{className}Spec.groovy': template
        ])
    }
}
