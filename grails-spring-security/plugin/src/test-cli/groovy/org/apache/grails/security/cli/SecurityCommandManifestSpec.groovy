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

import java.nio.file.Path

import groovy.json.JsonSlurper

import org.apache.grails.core.cli.ApplicationCommandManifestWriter
import spock.lang.Specification
import spock.lang.TempDir

class SecurityCommandManifestSpec extends Specification {

    @TempDir
    Path tempDir

    void 's2-quickstart describes its arguments and options'() {
        when:
        Map quickstart = command('s2-quickstart')

        then:
        quickstart.description == 'Creates domain classes and updates config settings for the Spring Security plugin.'
        quickstart.arguments*.name == ['domain-class-package', 'user-class-name', 'role-class-name', 'requestmap-class-name']
        quickstart.arguments*.required == [false, false, false, false]

        and: 'the options the command reads'
        quickstart.options.collectEntries { [(it.name): it.flag] } == [groupClassName: false, uiOnly: true, salt: true]
    }

    void '#name takes the domain class name it creates'() {
        when:
        Map described = command(name)

        then:
        described.description == description
        described.arguments*.name == ['domain-class-name']
        described.arguments*.required == [true]
        described.options == []

        where:
        name                             | description
        's2-create-persistent-token'     | 'Creates a persistent token domain class for remember-me logins.'
        's2-create-role-hierarchy-entry' | 'Creates a domain class for a persistent role hierarchy.'
    }

    private Map command(String name) {
        File manifest = tempDir.resolve('commands.json').toFile()
        ApplicationCommandManifestWriter.main(manifest.path)
        Map found = (Map) ((Map) new JsonSlurper().parse(manifest)).commands.find { it.name == name }
        assert found : "${name} is not in the manifest"
        found
    }
}
