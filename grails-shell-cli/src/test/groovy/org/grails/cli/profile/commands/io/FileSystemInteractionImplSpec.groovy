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
package org.grails.cli.profile.commands.io

import grails.build.logging.GrailsConsole
import grails.util.BuildSettings
import org.grails.cli.profile.ExecutionContext
import org.grails.io.support.FileSystemResource
import spock.lang.Specification
import spock.lang.TempDir

class FileSystemInteractionImplSpec extends Specification {

    @TempDir
    File directory

    FileSystemInteractionImpl interaction
    PrintStream originalOut
    PrintStream capturedOut

    void setup() {
        def context = Stub(ExecutionContext) { getBaseDir() >> directory }
        interaction = new FileSystemInteractionImpl(context)
        originalOut = GrailsConsole.instance.out
        capturedOut = new PrintStream(new ByteArrayOutputStream())
        GrailsConsole.instance.out = capturedOut
    }

    void cleanup() {
        GrailsConsole.instance.out = originalOut
        capturedOut.close()
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
        def pendingInteraction = new FileSystemInteractionImpl(Stub(ExecutionContext) { getBaseDir() >> pending })
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

    def 'mkdir and delete operate on project files and return the interaction for chaining'() {
        when:
        def result = interaction.mkdir('nested/child')
        def file = interaction.file('nested/child/example.txt')
        file.text = 'example'

        then:
        result.is(interaction)
        file.parentFile.isDirectory()

        when:
        result = interaction.delete(file)

        then:
        result.is(interaction)
        !file.exists()
        interaction.delete('missing.txt').is(interaction)
    }

    def 'copy and copy specifications preserve the contents of local resources'() {
        given:
        def first = interaction.file('first.txt')
        def second = interaction.file('second.txt')
        first.text = 'first contents'
        second.text = 'second contents'

        when:
        def result = interaction.copy('first.txt', 'single')
        interaction.copy {
            from([first, new FileSystemResource(second)])
            into('multiple')
        }
        interaction.copy {
            from('second.txt')
            into('closure')
        }

        then:
        result.is(interaction)
        interaction.file('single/first.txt').text == 'first contents'
        interaction.file('multiple/first.txt').text == 'first contents'
        interaction.file('multiple/second.txt').text == 'second contents'
        interaction.file('closure/second.txt').text == 'second contents'
    }

    def 'resource and file patterns find local files without a repository'() {
        given:
        interaction.mkdir('nested')
        interaction.file('nested/one.txt').text = 'one'
        interaction.file('nested/two.txt').text = 'two'
        interaction.file('nested/ignored.bin').text = 'ignored'
        def pattern = directory.toURI().toString() + 'nested/*.txt'

        expect:
        interaction.resources(pattern)*.filename.toSet() == ['one.txt', 'two.txt'].toSet()
        interaction.files(pattern)*.name.toSet() == ['one.txt', 'two.txt'].toSet()
        interaction.resources(directory.toURI().toString() + 'absent/*.txt').empty
        interaction.buildDir == BuildSettings.TARGET_DIR
        interaction.resourcesDir == BuildSettings.RESOURCES_DIR
        interaction.classesDir == BuildSettings.CLASSES_DIR
    }
}
