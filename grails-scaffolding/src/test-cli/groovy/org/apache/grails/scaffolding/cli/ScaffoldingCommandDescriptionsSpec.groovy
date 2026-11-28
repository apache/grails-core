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
package org.apache.grails.scaffolding.cli

import spock.lang.Specification

import org.apache.grails.core.cli.GrailsTool
import org.apache.grails.core.cli.StandaloneTool

/**
 * What the list of commands shows for the commands of the scaffolding plugin: a command that does not
 * describe itself is listed with its name in place of a description.
 */
class ScaffoldingCommandDescriptionsSpec extends Specification {

    private static final String FACTORIES = 'META-INF/grails-cli.factories'

    def "every command of the plugin says what it does"() {
        given: 'the commands the plugin registers'
        List<String> registered = registeredCommands()
        List<GrailsTool> commands = registered.collect { String className ->
            (GrailsTool) getClass().classLoader.loadClass(className).getDeclaredConstructor().newInstance()
        }

        expect:
        registered.contains(GenerateViewsTool.name)
        registered.size() >= 9
        commands.every { GrailsTool command ->
            command.description && command.description != command.name && !command.description.contains('\n')
        }

        and:
        new GenerateViewsTool().name == 'generate-views'
        new GenerateViewsTool().description == 'Generates the views for the specified domain class: GSP views, or JSON views in a REST application'
    }

    def "the commands only read and write files of the project, so each runs as a process of its own, without Gradle and without the application"() {
        expect:
        registeredCommands().size() >= 9
        registeredCommands().every { String className -> StandaloneTool.isAssignableFrom(getClass().classLoader.loadClass(className)) }
    }

    private List<String> registeredCommands() {
        List<String> classNames = []
        getClass().classLoader.getResources(FACTORIES).each { URL url ->
            Properties properties = new Properties()
            url.withInputStream { properties.load(it) }
            String registered = properties.getProperty(GrailsTool.name)
            if (registered) {
                classNames.addAll(registered.split(',')*.trim().findAll { it.startsWith('org.apache.grails.scaffolding.') })
            }
        }
        classNames.unique()
    }
}
