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
package org.apache.grails.security.ui.cli

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.GrailsTool
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.ToolSupport
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser

/**
 * What {@code s2ui-create-challenge-questions} writes into a project, from the templates the plugin has on the
 * class path, where its jar has them.
 */
class S2uiCreateChallengeQuestionsCommandSpec extends Specification {

    private static final String CONFIG = 'grails-app/conf/application.groovy'
    private static final String MESSAGES = 'grails-app/i18n/messages.properties'
    private static final String EXISTING_CONFIG = "grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.example.User'\n"
    private static final String EXISTING_MESSAGES = 'app.name=Example\n'

    private static final List<String> PROFILE_FILES = [
            'grails-app/controllers/com/example/ProfileController.groovy',
            'grails-app/domain/com/example/Profile.groovy',
            'grails-app/services/com/example/ProfileListenerService.groovy',
            'grails-app/services/com/example/ProfileService.groovy',
            'grails-app/views/profile/create.gsp',
            'grails-app/views/profile/edit.gsp',
            'grails-app/views/profile/index.gsp'
    ]

    @TempDir
    File project

    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setup() {
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(new ByteArrayOutputStream(), true), new PrintStream(errors, true)))
        File config = new File(project, CONFIG)
        config.parentFile.mkdirs()
        config.text = EXISTING_CONFIG
        File messages = new File(project, MESSAGES)
        messages.parentFile.mkdirs()
        messages.text = EXISTING_MESSAGES
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
    }

    def "the command writes the domain class, its services, its controller and its views for two questions"() {
        when:
        boolean result = run('s2ui-create-challenge-questions com.example Profile com.example.User')

        then:
        result
        errors.toString() == ''
        generated() == PROFILE_FILES

        and: 'the domain class has two questions and belongs to the user class, which is in its package'
        String domain = written('grails-app/domain/com/example/Profile.groovy')
        domain.startsWith('package  com.example\n')
        domain.contains('class Profile implements Serializable {')
        domain.contains('String myQuestion1') && domain.contains('String myAnswer2')
        !domain.contains('myQuestion3')
        domain.contains('    User user\n')
        domain.contains('static belongsTo = [ user: User]')

        and:
        String controller = written('grails-app/controllers/com/example/ProfileController.groovy')
        controller.startsWith('package  com.example\n')
        controller.contains('class ProfileController extends grails.plugin.springsecurity.ui.AbstractS2UiDomainController {')
        controller.contains('ProfileService profileService')
        controller.contains("'myQuestion1','myAnswer1','myQuestion2','myAnswer2',")
        controller.contains("'id', 'user.id'")

        and:
        written('grails-app/services/com/example/ProfileService.groovy').startsWith('package  com.example\n')
        written('grails-app/services/com/example/ProfileListenerService.groovy').contains('class ProfileListenerService {')

        and: 'the views are GSPs: what a template escapes is an expression of the view'
        String index = written('grails-app/views/profile/index.gsp')
        index.contains('<meta name="layout" content="${layoutUi}" />')
        index.contains("<div id=\"list-profile\" role=\"main\">")
        index.contains("<s2ui:sortableColumn property='myQuestion2' titleDefault='myQuestion2'/>")
        index.contains("<s2ui:sortableColumn property='user' titleDefault='user'/>")
        written('grails-app/views/profile/create.gsp').contains('myAnswer2')
        written('grails-app/views/profile/edit.gsp').contains('myAnswer2')
    }

    def "the command adds the configuration of the workflow and the caption of the menu to what the files have"() {
        when:
        boolean result = run('s2ui-create-challenge-questions com.example Profile com.example.User')

        then:
        result
        written(CONFIG) == EXISTING_CONFIG + lines(
                '',
                '// Added by the Spring Security UI plugin:',
                "grails.plugin.springsecurity.ui.forgotPassword.forgotPasswordExtraValidationDomainClassName = 'com.example.Profile'",
                'grails.plugin.springsecurity.ui.forgotPassword.forgotPasswordExtraValidation = [',
                "\t[labelDomain: 'myQuestion1', prop:'myAnswer1'],",
                "\t[labelDomain: 'myQuestion2', prop:'myAnswer2'],",
                ']',
                '')
        written(MESSAGES) == EXISTING_MESSAGES + lines('', 'spring.security.ui.menu.Profile=Profile Questions', '')
    }

    def "the configuration that is added is Groovy configuration that names the class and each question"() {
        when:
        run('s2ui-create-challenge-questions com.example Profile com.example.User 3')
        ConfigObject config = new ConfigSlurper().parse(written(CONFIG))

        then:
        config.grails.plugin.springsecurity.ui.forgotPassword.forgotPasswordExtraValidationDomainClassName == 'com.example.Profile'
        config.grails.plugin.springsecurity.ui.forgotPassword.forgotPasswordExtraValidation == [
                [labelDomain: 'myQuestion1', prop: 'myAnswer1'],
                [labelDomain: 'myQuestion2', prop: 'myAnswer2'],
                [labelDomain: 'myQuestion3', prop: 'myAnswer3']
        ]
        config.grails.plugin.springsecurity.userLookup.userDomainClassName == 'com.example.User'
    }

    def "the files that are added to are made when the project does not have them"() {
        given:
        new File(project, 'grails-app').deleteDir()

        when:
        boolean result = run('s2ui-create-challenge-questions com.example Profile com.example.User')

        then:
        result
        written(CONFIG).contains("forgotPasswordExtraValidationDomainClassName = 'com.example.Profile'")
        written(MESSAGES) == lines('', 'spring.security.ui.menu.Profile=Profile Questions', '')
    }

    def "the number of questions is the fourth argument"() {
        when:
        boolean result = run('s2ui-create-challenge-questions com.example Profile com.example.User 4')

        then:
        result
        String domain = written('grails-app/domain/com/example/Profile.groovy')
        (1..4).every { domain.contains("String myQuestion${it}") && domain.contains("String myAnswer${it}") }
        !domain.contains('myQuestion5')
        written(CONFIG).contains("\t[labelDomain: 'myQuestion4', prop:'myAnswer4'],")
        !written(CONFIG).contains('myQuestion5')
        written('grails-app/views/profile/index.gsp').contains("property='myAnswer4'")
    }

    @Unroll
    def "the user class #userClass is in the domain class as '#declared'"() {
        when:
        boolean result = run("s2ui-create-challenge-questions com.example SecurityAnswer ${userClass}")

        then:
        result
        String domain = written('grails-app/domain/com/example/SecurityAnswer.groovy')
        domain.contains("    ${declared}\n")
        domain.contains("static belongsTo = [ ${belongsTo}]")
        domain.contains("@EqualsAndHashCode(includes='user')")

        and: 'the views are in the directory of the controller'
        written('grails-app/views/securityAnswer/index.gsp').contains('list-securityAnswer')
        written('grails-app/controllers/com/example/SecurityAnswerController.groovy').contains('SecurityAnswerService securityAnswerService')
        written(MESSAGES).contains('spring.security.ui.menu.SecurityAnswer=SecurityAnswer Questions')

        where:
        userClass                  | declared                           | belongsTo
        'com.example.User'         | 'User user'                        | 'user: User'
        'User'                     | 'User user'                        | 'user: User'
        'org.other.User'           | 'org.other.User user'              | 'user: org.other.User'
        'com.example.AppUser'      | 'AppUser appUser'                  | 'appUser: AppUser'
        'org.other.auth.AppUser'   | 'org.other.auth.AppUser appUser'   | 'appUser: org.other.auth.AppUser'
    }

    def "the package of the domain class is taken in lower case"() {
        when:
        boolean result = run('s2ui-create-challenge-questions Com.Example Profile com.example.User')

        then:
        result
        generated() == PROFILE_FILES
        written('grails-app/domain/com/example/Profile.groovy').contains('    User user\n')
        written(CONFIG).contains("forgotPasswordExtraValidationDomainClassName = 'com.example.Profile'")
    }

    @Unroll
    def "#line fails, says '#said' with the usage, and writes nothing"() {
        when:
        boolean result = run(line)

        then:
        !result
        errors.toString().contains(said)
        errors.toString().contains('grails s2ui-create-challenge-questions <domain-class-package> <challenge-qa-class-name> <user-domain-class-name> [number-of-questions]')
        generated() == []
        written(CONFIG) == EXISTING_CONFIG
        written(MESSAGES) == EXISTING_MESSAGES

        where:
        line                                                                       | said
        's2ui-create-challenge-questions'                                          | 'Three or four arguments are required'
        's2ui-create-challenge-questions com.example Profile'                      | 'Three or four arguments are required'
        's2ui-create-challenge-questions com.example Profile com.example.User 2 3' | 'Three or four arguments are required'
        's2ui-create-challenge-questions com.example Profile com.example.User two' | "The number of questions 'two' is not a number of 1 or more"
        's2ui-create-challenge-questions com.example Profile com.example.User 0'   | "The number of questions '0' is not a number of 1 or more"
        's2ui-create-challenge-questions com.example Profile com.example.User 2.5' | "The number of questions '2.5' is not a number of 1 or more"
        's2ui-create-challenge-questions ../outside Profile com.example.User'      | "'../outside' and 'Profile' are not the names of a package and of a class"
        's2ui-create-challenge-questions com.example ../Profile com.example.User'  | "'com.example' and '../Profile' are not the names of a package and of a class"
        's2ui-create-challenge-questions com.example Profile com/example/User'     | "'com/example/User' is not the name of a class"
    }

    def "the files that are there are kept, and the configuration and the caption are added again"() {
        given: 'what the command wrote, edited since'
        assert run('s2ui-create-challenge-questions com.example Profile com.example.User')
        PROFILE_FILES.each { String path -> new File(project, path).text = 'edited' }
        String config = written(CONFIG)
        String messages = written(MESSAGES)

        when:
        boolean result = run('s2ui-create-challenge-questions com.example Profile com.example.User')

        then: 'passing over a file is no failure'
        result
        generated() == PROFILE_FILES
        PROFILE_FILES.every { String path -> written(path) == 'edited' }

        and:
        written(CONFIG).count('forgotPasswordExtraValidationDomainClassName') == 2
        written(CONFIG).startsWith(config)
        written(MESSAGES).count('spring.security.ui.menu.Profile=Profile Questions') == 2
        written(MESSAGES).startsWith(messages)
    }

    def "a template the project has under src/main/templates is taken in place of the one of the plugin"() {
        given:
        File template = new File(project, 'src/main/templates/ChallengeQuestions.groovy.template')
        template.parentFile.mkdirs()
        template.text = 'package ${packageName}\n\nclass ${saClassName} { // ${numberOfQuestions} questions of ${userDomainName} ${userPropName}, ${camelCaseSaName}, ${saClassProperty}\n}\n'

        when:
        boolean result = run('s2ui-create-challenge-questions com.example Profile org.other.AppUser 5')

        then:
        result
        written('grails-app/domain/com/example/Profile.groovy') == 'package com.example\n\nclass Profile { // 5 questions of org.other.AppUser appUser, profile, profile\n}\n'
        written('grails-app/services/com/example/ProfileService.groovy').startsWith('package  com.example\n')
    }

    def "the command fails when a template cannot be rendered, and adds nothing to the configuration"() {
        given: 'a template of the project that names what no template is given'
        File template = new File(project, 'src/main/templates/ChallengeQuestionsIndex.gsp.template')
        template.parentFile.mkdirs()
        template.text = '${noSuchAttribute}'

        when:
        boolean result = run('s2ui-create-challenge-questions com.example Profile com.example.User')

        then:
        !result
        errors.toString().contains('| Error ')
        errors.toString().contains('grails-app/views/profile/index.gsp')
        !new File(project, 'grails-app/views/profile/index.gsp').exists()
        written(CONFIG) == EXISTING_CONFIG
        written(MESSAGES) == EXISTING_MESSAGES
    }

    def "the commands of the plugin are registered, named as the scripts were, and say what they do"() {
        given: 'the commands the plugin registers'
        List<String> registered = []
        getClass().classLoader.getResources('META-INF/grails-cli.factories').each { URL url ->
            Properties properties = new Properties()
            url.withInputStream { properties.load(it) }
            String commands = properties.getProperty(GrailsTool.name)
            if (commands) {
                registered.addAll(commands.split(',')*.trim().findAll { it.startsWith('org.apache.grails.security.ui.') })
            }
        }
        List<GrailsTool> commands = registered.unique().sort().collect { String className ->
            (GrailsTool) getClass().classLoader.loadClass(className).getDeclaredConstructor().newInstance()
        }

        expect:
        commands*.name == ['s2ui-create-challenge-questions', 's2ui-override']
        commands.every { GrailsTool command ->
            command.description && command.description != command.name && !command.description.contains('\n')
        }
        new S2uiCreateChallengeQuestionsCommand().skipBootstrap
    }

    /**
     * Runs the command in the project of the spec. A command that the Gradle task starts gets the directory of
     * the project from the build settings of the JVM, which a spec cannot set.
     */
    private boolean run(String line) {
        ToolSupport command = new S2uiCreateChallengeQuestionsCommand()
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    /**
     * @return the files the command made other than the two it adds to, as paths in the project, in order
     */
    private List<String> generated() {
        List<String> paths = []
        File at = new File(project, 'grails-app')
        if (at.isDirectory()) {
            at.eachFileRecurse { File file ->
                if (file.isFile()) {
                    paths.add(project.toPath().relativize(file.toPath()).toString().replace(File.separator, '/'))
                }
            }
        }
        (paths - [CONFIG, MESSAGES]).sort()
    }

    private String written(String path) {
        File file = new File(project, path)
        assert file.isFile()
        file.text
    }

    private static String lines(String... each) {
        each.collect { it + System.lineSeparator() }.join('')
    }
}
