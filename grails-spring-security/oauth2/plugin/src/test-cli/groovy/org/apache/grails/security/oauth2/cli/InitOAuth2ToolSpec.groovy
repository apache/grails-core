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

package org.apache.grails.security.oauth2.cli

import org.apache.grails.core.cli.StandaloneTool
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.ToolSupport
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateException
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser

/**
 * What {@code init-oauth2} writes into a project. The template is the one of the plugin, read from the class
 * path as it is in an application that has the plugin.
 */
class InitOAuth2ToolSpec extends Specification {

    private static final String CONFIG = 'grails-app/conf/application.groovy'

    private static final String YAML = 'grails-app/conf/application.yml'

    private static final String ADDED = '\n\n// Added by the Spring Security OAuth2 Google Plugin:\n' +
            "grails.plugin.springsecurity.oauth2.domainClass = 'com.example.OAuthID'\n"

    @TempDir
    File project

    ByteArrayOutputStream output = new ByteArrayOutputStream()
    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setup() {
        // a command takes the console when it is made
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(output, true), new PrintStream(errors, true)))
        // an application as it is generated: configured in YAML, without an application.groovy
        File yaml = new File(project, YAML)
        yaml.parentFile.mkdirs()
        yaml.text = 'grails:\n    profile: web\n'
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
    }

    def "the command has the name of the script it replaces, a description, and does not run BootStrap"() {
        given:
        InitOAuth2Tool command = new InitOAuth2Tool()

        expect:
        command.name == 'init-oauth2'
        command.description == 'Creates the OAuthID domain class and updates the config settings for the Spring Security OAuth2 plugin'
        command.skipBootstrap
    }

    def "the command writes the domain class for a user class of the same package and names it in the configuration"() {
        when:
        boolean result = run('init-oauth2 com.example User OAuthID')

        then:
        result
        written('grails-app/domain/com/example/OAuthID.groovy') == rendered('com.example', 'com.example.User', 'User', 'OAuthID')
        written('grails-app/domain/com/example/OAuthID.groovy').startsWith('package com.example\n\nimport com.example.User\n')
        written('grails-app/domain/com/example/OAuthID.groovy').contains('class OAuthID implements Serializable {')
        written('grails-app/domain/com/example/OAuthID.groovy').contains('static belongsTo = [user: User]')
        normalized(CONFIG) == ADDED
        generated() == [CONFIG, YAML, 'grails-app/domain/com/example/OAuthID.groovy']
        written(YAML) == 'grails:\n    profile: web\n'
        errors.toString() == ''
        output.toString().contains("Creating OAuthID class 'OAuthID'")
        output.toString().contains('* Created domain class.')
    }

    def "a user class that is given with its package is imported from that package"() {
        when:
        boolean result = run('init-oauth2 com.example.oauth org.users.Person Identity')

        then:
        result
        written('grails-app/domain/com/example/oauth/Identity.groovy') == rendered('com.example.oauth', 'org.users.Person', 'Person', 'Identity')
        written('grails-app/domain/com/example/oauth/Identity.groovy').contains('import org.users.Person\n')
        written('grails-app/domain/com/example/oauth/Identity.groovy').contains('class Identity implements Serializable {')
        written('grails-app/domain/com/example/oauth/Identity.groovy').contains('static belongsTo = [user: Person]')
        normalized(CONFIG).endsWith("grails.plugin.springsecurity.oauth2.domainClass = 'com.example.oauth.Identity'\n")
    }

    def "the setting is added at the end of a configuration that is there"() {
        given:
        File config = new File(project, CONFIG)
        config.parentFile.mkdirs()
        config.text = "grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.example.User'\n"

        when:
        boolean result = run('init-oauth2 com.example User OAuthID')

        then:
        result
        normalized(CONFIG) == "grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.example.User'\n" + ADDED
    }

    def "a domain class that is in the project is kept, and the setting is added again"() {
        given: 'what the command wrote, edited since'
        assert run('init-oauth2 com.example User OAuthID')
        File domain = new File(project, 'grails-app/domain/com/example/OAuthID.groovy')
        domain.text = 'edited'

        when:
        boolean result = run('init-oauth2 com.example User OAuthID')

        then: 'a file that is there is passed over, which is no failure'
        result
        domain.text == 'edited'
        normalized(CONFIG) == ADDED + ADDED

        when: 'the command has no flag that writes the class again'
        boolean again = run('init-oauth2 com.example User OAuthID --force')

        then:
        again
        domain.text == 'edited'
    }

    def "a template of the project is used in place of the template of the plugin"() {
        given:
        File template = new File(project, 'src/main/templates/OAuthID.groovy.template')
        template.parentFile.mkdirs()
        template.text = 'package ${oAuthIDPackageName}\n\nclass ${oAuthIDClassName} {\n    ${userClassName} owner // ${userClassFullName}\n}\n'

        when:
        boolean result = run('init-oauth2 com.example User OAuthID')

        then:
        result
        written('grails-app/domain/com/example/OAuthID.groovy') == 'package com.example\n\nclass OAuthID {\n    User owner // com.example.User\n}\n'
    }

    @Unroll
    def "with #what the command fails, says how it is used and writes nothing"() {
        when:
        boolean result = run(line)

        then:
        !result
        errors.toString().contains('Usage:')
        errors.toString().contains('grails init-oauth2 <domain-class-package> <user-class-name> <oauthid-class-name>')
        errors.toString().contains('./gradlew runTool -Pargs="init-oauth2 <domain-class-package> <user-class-name> <oauthid-class-name>"')
        generated() == [YAML]

        where:
        what            | line
        'no arguments'  | 'init-oauth2'
        'one argument'  | 'init-oauth2 com.example'
        'two arguments' | 'init-oauth2 com.example User'
    }

    def "the command fails when the domain class cannot be written, and leaves the configuration as it is"() {
        given: 'a file where the directory of the domain classes would be'
        new File(project, 'grails-app/domain').text = 'a file'

        when:
        run('init-oauth2 com.example User OAuthID')

        then: 'which the task that runs the command answers with its error and the exit status 1'
        TemplateException failure = thrown()
        failure.message.contains('grails-app/domain/com/example/OAuthID.groovy')
        generated() == [YAML, 'grails-app/domain']
    }

    def "the configuration is written also where the project has no directory for it"() {
        given:
        new File(project, 'grails-app').deleteDir()

        when:
        boolean result = run('init-oauth2 com.example User OAuthID')

        then:
        result
        normalized(CONFIG) == ADDED
        generated() == [CONFIG, 'grails-app/domain/com/example/OAuthID.groovy']
    }

    /**
     * Runs the command in the project of the spec. A command that the Gradle task starts gets the directory of
     * the project from the build settings of the JVM, which a spec cannot set.
     */
    private boolean run(String line) {
        ToolSupport command = new InitOAuth2Tool()
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    private String written(String path) {
        new File(project, path).text
    }

    private String normalized(String path) {
        written(path).replace(System.lineSeparator(), '\n')
    }

    /**
     * @return the template of the plugin with its four placeholders filled in
     */
    private static String rendered(String packageName, String userClassFullName, String userClassName, String className) {
        new File('src/main/templates/OAuthID.groovy.template').text
                .replace('${oAuthIDPackageName}', packageName)
                .replace('${userClassFullName}', userClassFullName)
                .replace('${userClassName}', userClassName)
                .replace('${oAuthIDClassName}', className)
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
        [InitOAuth2Tool].every { Class command -> StandaloneTool.isAssignableFrom(command) }
    }
}
