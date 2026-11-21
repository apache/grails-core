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

package org.apache.grails.security.acl.cli

import org.apache.grails.core.cli.StandaloneCommand
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.template.TemplateException
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser

/**
 * What {@code s2-create-acl-domains} writes into a project. The templates are the ones of the plugin, read
 * from the class path as they are in an application that has the plugin.
 */
class S2CreateAclDomainsCommandSpec extends Specification {

    private static final List<String> NAMES = ['AclClass', 'AclEntry', 'AclObjectIdentity', 'AclSid']

    private static final String DIRECTORY = 'grails-app/domain/grails/plugin/springsecurity/acl'

    @TempDir
    File project

    def "the command has the name and the description of the script it replaces, and does not run BootStrap"() {
        given:
        S2CreateAclDomainsCommand command = new S2CreateAclDomainsCommand()

        expect:
        command.name == 's2-create-acl-domains'
        command.description == 'Copies ACL domain classes to the project'
        command.skipBootstrap
    }

    @Unroll
    def "the command writes #name as the template of the plugin has it"() {
        when:
        boolean result = run('s2-create-acl-domains')

        then:
        result
        written(name) == expected(name)
        written(name).contains('package grails.plugin.springsecurity.acl\n')
        written(name).contains("class ${name} ")
        written(name).contains(said)

        where:
        name                | said
        'AclClass'          | '"AclClass id $id, className $className"'
        'AclEntry'          | '"AclEntry id $id, aceOrder $aceOrder, mask $mask, granting $granting, "'
        'AclObjectIdentity' | '"AclObjectIdentity id $id, aclClass $aclClass.className, "'
        'AclSid'            | '"AclSid id $id, sid $sid, principal $principal"'
    }

    def "the command writes the four classes and nothing else"() {
        when:
        boolean result = run('s2-create-acl-domains')

        then:
        result
        generated() == NAMES.collect { String name -> "${DIRECTORY}/${name}.groovy".toString() }
    }

    def "a class that is in the project is kept, the others are written, and that is no failure"() {
        given: 'a class of the project, edited'
        File edited = new File(project, "${DIRECTORY}/AclSid.groovy")
        edited.parentFile.mkdirs()
        edited.text = 'edited'

        when:
        boolean result = run('s2-create-acl-domains')

        then:
        result
        edited.text == 'edited'
        (NAMES - 'AclSid').every { String name -> written(name) == expected(name) }

        when: 'the command has no flag that writes the class again'
        boolean again = run('s2-create-acl-domains --force')

        then:
        again
        edited.text == 'edited'
    }

    def "a template of the project is used in place of the template of the plugin"() {
        given:
        File template = new File(project, 'src/main/templates/_AclSid.groovy')
        template.parentFile.mkdirs()
        template.text = 'package grails.plugin.springsecurity.acl\n\nclass AclSid {\n    String own = "\\$id"\n}\n'

        when:
        boolean result = run('s2-create-acl-domains')

        then:
        result
        written('AclSid') == 'package grails.plugin.springsecurity.acl\n\nclass AclSid {\n    String own = "$id"\n}\n'
        written('AclClass') == expected('AclClass')
    }

    def "arguments are not looked at"() {
        when:
        boolean result = run('s2-create-acl-domains com.example Other')

        then:
        result
        generated() == NAMES.collect { String name -> "${DIRECTORY}/${name}.groovy".toString() }
    }

    def "the command fails when a class cannot be written"() {
        given: 'a file where the directory of the domain classes would be'
        new File(project, 'grails-app').mkdirs()
        new File(project, 'grails-app/domain').text = 'a file'

        when:
        run('s2-create-acl-domains')

        then: 'which the task that runs the command answers with its error and the exit status 1'
        TemplateException failure = thrown()
        failure.message.contains("${DIRECTORY}/AclClass.groovy")
        new File(project, 'grails-app/domain').text == 'a file'
    }

    /**
     * Runs the command in the project of the spec. A command that the Gradle task starts gets the directory of
     * the project from the build settings of the JVM, which a spec cannot set.
     */
    private boolean run(String line) {
        GrailsApplicationCommand command = new S2CreateAclDomainsCommand()
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    private String written(String name) {
        new File(project, "${DIRECTORY}/${name}.groovy").text
    }

    /**
     * @return the template of the plugin as it is rendered: it has no placeholders, and its dollar signs are escaped
     */
    private static String expected(String name) {
        new File("src/main/templates/_${name}.groovy").text.replace('\\$', '$')
    }

    /**
     * @return the files below the project, as paths in the project, in order
     */
    private List<String> generated() {
        List<String> paths = []
        project.eachFileRecurse { File file ->
            if (file.isFile()) {
                paths << project.toPath().relativize(file.toPath()).toString().replace(File.separator, '/')
            }
        }
        paths.sort()
    }

    void 'the commands only write files of the project, so they are run without the application being started'() {
        expect: 'an application that cannot start, as one that is not configured yet, has them all the same'
        [S2CreateAclDomainsCommand].every { Class command -> StandaloneCommand.isAssignableFrom(command) }
    }
}
