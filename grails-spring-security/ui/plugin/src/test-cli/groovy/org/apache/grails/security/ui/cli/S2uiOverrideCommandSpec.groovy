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

import org.apache.grails.core.cli.GradleCommand
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser

/**
 * What {@code s2ui-override} writes into a project for each type it takes, from the templates and the views the
 * plugin has on the class path, where its jar has them.
 */
class S2uiOverrideCommandSpec extends Specification {

    private static final List<String> CREATE_EDIT_SEARCH = ['create', 'edit', 'search']

    @TempDir
    File project

    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setup() {
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(new ByteArrayOutputStream(), true), new PrintStream(errors, true)))
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
    }

    @Unroll
    def "s2ui-override #type writes the controller #controller in the package it is given, and the GSPs of that controller"() {
        when:
        boolean result = run("s2ui-override ${type} com.example.admin")

        then:
        result
        errors.toString() == ''

        and: 'a controller in that package that extends the controller of the plugin'
        String source = written("grails-app/controllers/com/example/admin/${controller}Controller.groovy")
        source.startsWith('package com.example.admin\n')
        source.contains("class ${controller}Controller extends grails.plugin.springsecurity.ui.${controller}Controller {\n")
        !source.contains('packageDeclaration')

        and: 'which is empty, but for the ACL types, whose controller names the ACL classes it looks up'
        type.startsWith('acl') || source.trim() == "package com.example.admin\n\nclass ${controller}Controller extends grails.plugin.springsecurity.ui.${controller}Controller {\n}"
        !type.startsWith('acl') || source.contains('import grails.plugin.springsecurity.acl.')

        and: 'the views of the plugin, as the plugin has them, and nothing else'
        generated() == (["grails-app/controllers/com/example/admin/${controller}Controller.groovy".toString()] +
                views.collect { "grails-app/views/${it}.gsp".toString() }).sort()
        views.every { String view -> written("grails-app/views/${view}.gsp") == new File("grails-app/views/${view}.gsp").text }

        where:
        type                | controller          | views
        'aclclass'          | 'AclClass'          | CREATE_EDIT_SEARCH.collect { "aclClass/${it}" }
        'aclentry'          | 'AclEntry'          | CREATE_EDIT_SEARCH.collect { "aclEntry/${it}" }
        'aclobjectidentity' | 'AclObjectIdentity' | CREATE_EDIT_SEARCH.collect { "aclObjectIdentity/${it}" }
        'aclsid'            | 'AclSid'            | CREATE_EDIT_SEARCH.collect { "aclSid/${it}" }
        'persistentlogin'   | 'PersistentLogin'   | ['persistentLogin/edit', 'persistentLogin/search']
        'register'          | 'Register'          | ['register/_forgotPasswordMail', 'register/_verifyRegistrationMail', 'register/forgotPassword', 'register/register', 'register/resetPassword', 'register/securityQuestions', 'layouts/register', 'layouts/email']
        'registrationcode'  | 'RegistrationCode'  | ['registrationCode/edit', 'registrationCode/search']
        'requestmap'        | 'Requestmap'        | CREATE_EDIT_SEARCH.collect { "requestmap/${it}" }
        'role'              | 'Role'              | CREATE_EDIT_SEARCH.collect { "role/${it}" }
        'securityinfo'      | 'SecurityInfo'      | ['config', 'currentAuth', 'filterChains', 'logoutHandlers', 'mappings', 'providers', 'secureChannel', 'usercache', 'voters'].collect { "securityInfo/${it}" }
        'user'              | 'User'              | CREATE_EDIT_SEARCH.collect { "user/${it}" }
    }

    @Unroll
    def "s2ui-override #type copies #views and no controller"() {
        when:
        boolean result = run("s2ui-override ${type}")

        then:
        result
        errors.toString() == ''
        generated() == views.collect { "grails-app/views/${it}".toString() }.sort()
        views.every { String view -> written("grails-app/views/${view}") == new File("grails-app/views/${view}").text }

        where:
        type     | views
        'auth'   | ['login/auth.gsp']
        'layout' | ['layouts/springSecurityUI.gsp', 'includes/_ajaxLogin.gsp']
    }

    def "every view the plugin has is copied by one of the types"() {
        given: 'the views of the plugin'
        List<String> views = []
        File root = new File('grails-app/views')
        root.eachFileRecurse { File file ->
            if (file.isFile()) {
                views.add('grails-app/views/' + root.toPath().relativize(file.toPath()).toString().replace(File.separator, '/'))
            }
        }

        when:
        boolean result = ['aclclass', 'aclentry', 'aclobjectidentity', 'aclsid', 'persistentlogin', 'register',
                          'registrationcode', 'requestmap', 'role', 'securityinfo', 'user'].every { run("s2ui-override ${it} com.example") }
        result = result && run('s2ui-override auth') && run('s2ui-override layout')

        then:
        result
        generated().findAll { it.startsWith('grails-app/views/') } == views.sort()
    }

    @Unroll
    def "the type is not case-sensitive: #line"() {
        expect:
        run(line)
        generated() == files

        where:
        line                             | files
        's2ui-override USER com.example' | ['grails-app/controllers/com/example/UserController.groovy', 'grails-app/views/user/create.gsp', 'grails-app/views/user/edit.gsp', 'grails-app/views/user/search.gsp']
        's2ui-override AclSid a.b'       | ['grails-app/controllers/a/b/AclSidController.groovy', 'grails-app/views/aclSid/create.gsp', 'grails-app/views/aclSid/edit.gsp', 'grails-app/views/aclSid/search.gsp']
        's2ui-override Auth'             | ['grails-app/views/login/auth.gsp']
        's2ui-override LAYOUT'           | ['grails-app/views/includes/_ajaxLogin.gsp', 'grails-app/views/layouts/springSecurityUI.gsp']
    }

    @Unroll
    def "#line fails, says '#said' with the usage, and writes nothing"() {
        when:
        boolean result = run(line)

        then:
        !result
        errors.toString().contains(said)
        errors.toString().contains('grails s2ui-override <type> [controllerPackage]') == usage
        generated() == []

        where:
        line                                                 | said                                                                 | usage
        's2ui-override'                                      | 'The type is required'                                               | true
        's2ui-override nothing com.example'                  | "Unknown type 'nothing'"                                             | true
        's2ui-override nothing'                              | "Unknown type 'nothing'"                                             | true
        's2ui-override User'                                 | "The controller package is required for type 'User'"                 | true
        's2ui-override auth com.example'                     | "The type 'auth' has no controller and takes no controller package: com.example" | true
        's2ui-override Layout com.example'                   | "The type 'Layout' has no controller and takes no controller package: com.example" | true
        's2ui-override user grails.plugin.springsecurity.ui' | 'The controller package cannot be the same as the plugin controller' | false
        's2ui-override user ../../outside'                   | "The controller package '../../outside' is not the name of a package" | true
        's2ui-override user com..example'                    | "The controller package 'com..example' is not the name of a package" | true
        's2ui-override user com.example more'                | 'Too many arguments: user com.example more'                          | true
    }

    def "a controller that is there is kept, and the GSPs are written again"() {
        given: 'what the command wrote, edited since'
        assert run('s2ui-override user com.example')
        List<String> files = generated()
        files.each { String path -> new File(project, path).text = 'edited' }

        when:
        boolean result = run('s2ui-override user com.example')

        then: 'passing over the controller is no failure'
        result
        generated() == files
        written('grails-app/controllers/com/example/UserController.groovy') == 'edited'
        CREATE_EDIT_SEARCH.every { written("grails-app/views/user/${it}.gsp") == new File("grails-app/views/user/${it}.gsp").text }
    }

    def "a view and a controller template the project has under src/main/templates are taken in place of those of the plugin"() {
        given:
        File view = new File(project, 'src/main/templates/views/user/edit.gsp')
        view.parentFile.mkdirs()
        view.text = 'the edit view of the project'
        new File(project, 'src/main/templates/UserController.groovy.template').text = '${packageDeclaration}\n\n// of the project\n'

        when:
        boolean result = run('s2ui-override user com.example')

        then:
        result
        written('grails-app/views/user/edit.gsp') == 'the edit view of the project'
        written('grails-app/views/user/create.gsp') == new File('grails-app/views/user/create.gsp').text
        written('grails-app/controllers/com/example/UserController.groovy') == 'package com.example\n\n// of the project\n'
    }

    def "the command fails when a view cannot be written"() {
        given: 'a file where the directory of the views would be'
        new File(project, 'grails-app').mkdirs()
        new File(project, 'grails-app/views').text = 'a file'

        when:
        boolean result = run('s2ui-override auth')

        then:
        !result
        errors.toString().contains('The directory grails-app/views/login of the view login/auth.gsp could not be made')
        new File(project, 'grails-app/views').text == 'a file'
    }

    def "the command is listed under its name with what it does, and does not run the BootStrap classes"() {
        given:
        S2uiOverrideCommand command = new S2uiOverrideCommand()

        expect:
        command.name == 's2ui-override'
        command.description == 'Copies plugin controllers and GSPs to the application so they can be overridden'
        command.skipBootstrap
    }

    /**
     * Runs the command in the project of the spec. A command that the Gradle task starts gets the directory of
     * the project from the build settings of the JVM, which a spec cannot set.
     */
    private boolean run(String line) {
        GrailsApplicationCommand command = new S2uiOverrideCommand()
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    /**
     * @return the files the command wrote, as paths in the project, in order
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
        paths.sort()
    }

    private String written(String path) {
        File file = new File(project, path)
        assert file.isFile()
        file.text
    }

    void 'the commands only write files of the project, so they are run without the application being started'() {
        expect: 'an application that cannot start, as one that is not configured yet, has them all the same'
        [S2uiOverrideCommand, S2uiCreateChallengeQuestionsCommand].every { Class command -> GradleCommand.isAssignableFrom(command) }
    }
}
