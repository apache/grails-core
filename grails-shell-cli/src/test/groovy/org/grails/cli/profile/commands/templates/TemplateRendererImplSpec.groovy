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
package org.grails.cli.profile.commands.templates

import grails.build.logging.GrailsConsole
import org.grails.cli.profile.ExecutionContext
import org.grails.cli.profile.ProfileRepository
import org.grails.io.support.FileSystemResource
import spock.lang.Specification
import spock.lang.TempDir

class TemplateRendererImplSpec extends Specification {

    @TempDir
    File directory

    GrailsConsole console = Mock(GrailsConsole)
    ProfileRepository repository = Mock(ProfileRepository)
    TemplateRendererImpl renderer

    void setup() {
        def context = Stub(ExecutionContext) {
            getBaseDir() >> directory
            getConsole() >> console
        }
        renderer = new TemplateRendererImpl(context, repository)
    }

    def 'text templates render their model as UTF-8 and create parent directories'() {
        given:
        def destination = new File(directory, 'nested/greeting.txt')

        when:
        renderer.render('Hello ${name}!', destination, [name: 'Grails café'])

        then:
        destination.getText('UTF-8') == 'Hello Grails café!'
        0 * repository._
    }

    def 'existing destinations are preserved unless overwrite is requested'() {
        given:
        def destination = new File(directory, 'greeting.txt')
        destination.text = 'original'

        when:
        renderer.render('Hello ${name}!', destination, [name: 'Grails'])

        then:
        destination.getText('UTF-8') == 'original'
        1 * console.warn('Destination file greeting.txt already exists, skipping...')

        when:
        renderer.render('Hello ${name}!', destination, [name: 'Grails'], true)

        then:
        destination.getText('UTF-8') == 'Hello Grails!'
        0 * repository._
    }

    def 'file and resource templates render through their public overloads'() {
        given:
        def template = new File(directory, 'greeting.tpl')
        template.setText('Hello ${name}!', 'UTF-8')
        def fileDestination = new File(directory, 'file.txt')
        def resourceDestination = new File(directory, 'resource.txt')

        when:
        renderer.render(template, fileDestination, [name: 'File'])
        renderer.render(new FileSystemResource(template), resourceDestination, [name: 'Resource'])

        then:
        fileDestination.getText('UTF-8') == 'Hello File!'
        resourceDestination.getText('UTF-8') == 'Hello Resource!'
        1 * console.addStatus('Rendered template greeting.tpl to destination file.txt')
        1 * console.addStatus('Rendered template greeting.tpl to destination resource.txt')
        0 * repository._
    }

    def 'named rendering finds project templates without accessing the profile repository'() {
        given:
        def template = new File(directory, 'src/main/templates/greeting.tpl')
        template.parentFile.mkdirs()
        template.text = 'Hello ${name}!'

        when:
        renderer.render(template: 'greeting.tpl', destination: 'generated/greeting.txt', model: [name: 'Project'])

        then:
        new File(directory, 'generated/greeting.txt').getText('UTF-8') == 'Hello Project!'
        renderer.template('greeting.tpl').file == template
        0 * repository._
    }

    def 'missing resource templates report a descriptive error'() {
        given:
        def missing = new FileSystemResource(new File(directory, 'missing.tpl'))
        def destination = new File(directory, 'result.txt')

        when:
        renderer.render(missing, destination, [:])

        then:
        def exception = thrown(TemplateException)
        exception.message == 'Template [missing.tpl] not found.'
        !destination.exists()
        0 * repository._
    }

    def 'failed model evaluation wraps the cause and removes partial output'() {
        given:
        def destination = new File(directory, 'result.txt')

        when:
        renderer.render('Hello ${missing}!', destination, [:])

        then:
        def exception = thrown(TemplateException)
        exception.message.startsWith('Error rendering template to destination result.txt:')
        exception.cause != null
        !destination.exists()
        0 * repository._
    }

    def 'incomplete named arguments do not create files'() {
        when:
        renderer.render([:])
        renderer.render(template: 'greeting.tpl')
        renderer.render(destination: 'result.txt')

        then:
        directory.list().length == 0
        0 * repository._
    }
}
