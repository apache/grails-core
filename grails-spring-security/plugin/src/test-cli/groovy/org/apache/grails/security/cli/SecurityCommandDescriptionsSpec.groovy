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

package org.apache.grails.security.cli

import org.apache.grails.core.cli.GradleCommand
import org.apache.grails.core.cli.StandaloneCommand
import spock.lang.Specification

import org.apache.grails.core.cli.ApplicationCommand

/**
 * What the list of commands shows for the commands of the Spring Security Core plugin: the names the scripts
 * had, each with a description, since a command that does not describe itself is listed with its name.
 */
class SecurityCommandDescriptionsSpec extends Specification {

    private static final String FACTORIES = 'META-INF/grails-cli.factories'

    def "the plugin registers its three commands under the names of the scripts, and each says what it does"() {
        given: 'the commands the plugin registers'
        List<ApplicationCommand> commands = registeredCommands().collect { String className ->
            (ApplicationCommand) getClass().classLoader.loadClass(className).getDeclaredConstructor().newInstance()
        }

        expect:
        commands.collectEntries { ApplicationCommand command -> [(command.name): command.description] } == [
                's2-create-persistent-token'    : 'Creates a persistent token domain class for the Spring Security Core plugin',
                's2-create-role-hierarchy-entry': 'Creates a domain class for a persistent role hierarchy for the Spring Security Core plugin',
                's2-quickstart'                 : 'Creates domain classes and updates config settings for the Spring Security plugin'
        ]
    }

    private List<String> registeredCommands() {
        List<String> classNames = []
        getClass().classLoader.getResources(FACTORIES).each { URL url ->
            Properties properties = new Properties()
            url.withInputStream { properties.load(it) }
            String registered = properties.getProperty(ApplicationCommand.name)
            if (registered) {
                classNames.addAll(registered.split(',')*.trim().findAll { it.startsWith('org.apache.grails.security.cli.') })
            }
        }
        classNames.unique()
    }

    void 'the commands only write files of the project, so they are run without the application being started'() {
        expect: 'an application that cannot start, as one that is not configured yet, has them all the same'
        GradleCommand.isAssignableFrom(S2QuickstartCommand) && !StandaloneCommand.isAssignableFrom(S2QuickstartCommand)
        [S2CreatePersistentTokenCommand, S2CreateRoleHierarchyEntryCommand].every { Class command -> StandaloneCommand.isAssignableFrom(command) }
    }
}
