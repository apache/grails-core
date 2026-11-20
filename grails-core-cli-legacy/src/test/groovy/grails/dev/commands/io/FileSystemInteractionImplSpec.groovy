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
package grails.dev.commands.io

import org.grails.io.support.FileSystemResource
import spock.lang.Specification
import spock.lang.TempDir

class FileSystemInteractionImplSpec extends Specification {

    @TempDir
    File directory

    FileSystemInteractionImpl interaction

    void setup() {
        interaction = new FileSystemInteractionImpl(directory)
    }

    def 'files and resources are resolved relative to the project and existing objects are preserved'() {
        given:
        def file = new File(directory, 'example.txt')
        file.text = 'example'
        def resource = new FileSystemResource(file)

        expect:
        interaction.file('example.txt') == file
        interaction.file(file).is(file)
        interaction.file(resource) == file
        interaction.resource(resource).is(resource)
        interaction.resource('example.txt').file == file
        interaction.resource(null) == null
        interaction.resource('') == null
        !interaction.resource('missing.txt').exists()
        interaction.projectPath(file) == 'example.txt'
    }

    def 'files that do not exist yet are resolved like existing ones'() {
        given: 'a project directory that has not been created and files below it'
        def pending = new File(directory, 'pending')
        def pendingInteraction = new FileSystemInteractionImpl(pending)
        def missing = new File(directory, 'nested/missing.txt')
        def existing = interaction.file('present.txt')
        existing.text = 'present'

        when:
        interaction.copy {
            from(existing)
            into(new File(directory, 'target'))
        }

        then:
        interaction.projectPath(missing) == new File('nested', 'missing.txt').path
        interaction.projectPath('missing.txt') == 'missing.txt'
        !interaction.resource(missing).exists()
        pendingInteraction.file('build.gradle') == new File(pending, 'build.gradle')
        interaction.file('target/present.txt').text == 'present'
    }
}
