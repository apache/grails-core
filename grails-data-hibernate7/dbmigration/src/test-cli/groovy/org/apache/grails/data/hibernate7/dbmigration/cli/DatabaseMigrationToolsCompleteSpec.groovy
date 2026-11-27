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
package org.apache.grails.data.hibernate7.dbmigration.cli

import groovy.json.JsonSlurper

import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.core.cli.ApplicationContextCommandRegistry
import org.apache.grails.core.cli.GrailsTool

/**
 * Every tool of the Database Migration plugin describes itself completely, as the build lists it for
 * {@code help}, completion and agents: its descriptor has a description, the options every tool of the
 * plugin takes, its hints, and nothing the compiler found missing.
 */
class DatabaseMigrationToolsCompleteSpec extends Specification {

    def "the plugin registers its 31 tools"() {
        expect:
        toolClasses().size() == 31
    }

    @Unroll
    def "#className has a descriptor that says everything about it"() {
        when:
        Map descriptor = descriptorOf(className)

        then:
        descriptor.name.startsWith('dbm-')
        descriptor.description
        !descriptor.containsKey('incomplete')
        descriptor.hints.keySet() == ['readOnlyHint', 'destructiveHint', 'idempotentHint', 'openWorldHint'] as Set
        descriptor.options*.names.flatten().containsAll(['--dataSource', '--defaultSchema', '--contexts'])
        descriptor.options.every { Map option -> option.description }
        (descriptor.parameters ?: []).every { Map parameter -> parameter.description }

        where:
        className << toolClasses()
    }

    def "the tools that never change the database are read-only, though two of them write a file the caller names"() {
        when:
        Map<String, Map> hints = toolClasses().collectEntries { String className ->
            Map descriptor = descriptorOf(className)
            [(descriptor.name): descriptor.hints]
        }

        then:
        hints.findAll { String name, Map toolHints -> toolHints.readOnlyHint }.keySet() == ['dbm-validate', 'dbm-status', 'dbm-list-locks'] as Set
        hints['dbm-status'] == [readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: true]
        hints['dbm-list-locks'] == [readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: true]
    }

    // the tools of the plugin the registry of the tools finds, by the name of their class
    private static List<String> toolClasses() {
        ApplicationContextCommandRegistry.instance.findCommands()
                .collect { GrailsTool tool -> tool.getClass().name }
                .findAll { String name -> name.startsWith(DbmUpdateTool.package.name + '.') }
                .unique()
                .sort()
    }

    private static Map descriptorOf(String className) {
        URL descriptor = DatabaseMigrationToolsCompleteSpec.classLoader.getResource("META-INF/grails-tools/${className}.json")
        assert descriptor != null
        (Map) new JsonSlurper().parse(descriptor, 'UTF-8')
    }
}
