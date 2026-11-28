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
package org.grails.forge.cli

import java.nio.file.Files
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.DevelopmentReloading
import spock.lang.Unroll

/**
 * The record of the oldest kind: a {@code grails-forge-cli.yml} (or {@code grails-cli.yml}) that
 * names the profile the application was created with and not its type and features. Every profile
 * of Grails is a type of application now, and the directory has the commands a new application of
 * that type has; and {@code update-cli-config}, which the command line of Grails 7 told to run for
 * such a record and which never was a command.
 */
class CodeGenConfigLegacyRecordSpec extends CommandSpec {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    /** Where an application of this version is created, to compare with */
    File other

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        other = Files.createTempDirectory('grailsforgeother').toFile()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        other.deleteDir()
    }

    @Unroll
    void "a record that names the profile #profile is that of the type #type, and the directory has the commands of #create"() {
        given: 'the commands of an application this version creates with that command'
        System.setProperty('user.dir', other.absolutePath)
        assert ForgeCli.execute(beanContext, create, 'com.example.made', '--reloading', 'none') == 0
        Set<String> ofThisVersion = projectCommands(new File(other, 'made'))
        System.setProperty('user.dir', dir.absolutePath)

        and: 'a directory with the record of the oldest kind and nothing else'
        new File(dir, record).setText("profile: ${profile}\ndefaultPackage: com.example\n", 'UTF-8')
        if (type.name().contains('PLUGIN')) {
            // what makes a project a plugin for the commands that package and publish one
            File descriptor = new File(dir, 'src/main/groovy/com/example/LegacyGrailsPlugin.groovy')
            descriptor.parentFile.mkdirs()
            descriptor.setText('package com.example\n\nclass LegacyGrailsPlugin {\n}\n', 'UTF-8')
        }
        out.reset()
        err.reset()

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP)

        then: 'the type is that of the profile, and what the record does not say is what the commands need'
        config.legacy
        config.applicationType == type
        config.defaultPackage == 'com.example'
        config.reloading == DevelopmentReloading.NONE
        config.projectDirectory == dir
        !config.features.isEmpty()
        config.features.contains('gorm-hibernate5')
        config.features.contains('gsp') == gsp

        when: 'the commands there are in the directory'
        Set<String> commands = projectCommands(dir)

        then: 'are those of the application of this version, with the commands that tell a type from another'
        commands == ofThisVersion
        commands.containsAll(has)
        lacks.every { !commands.contains(it) }

        when: 'a class is created'
        err.reset()
        int exit = ForgeCli.execute(beanContext, 'create-domain-class', 'Book')

        then: 'in the package of the record'
        exit == 0
        new File(dir, 'grails-app/domain/com/example/Book.groovy').text.contains('package com.example')

        and: 'what is said is the type the record was taken for, and the command that writes the record of this version'
        err.toString().readLines() == ["| Warning ${record} names a profile, as the record of an earlier command line did, and not the features of the application: " +
                                               "the commands take the application to have the features an application of the type ${written} has by default. " +
                                               'project migrate writes the record of this version, from what the project has']

        where:
        profile           | record                 | type                            | written           | create                   | gsp   | has                                                                                               | lacks
        'web'             | 'grails-forge-cli.yml' | ApplicationType.WEB             | 'web'             | 'create-app'             | true  | ['create-controller', 'create-taglib', 'create-integration-test', 'add-property', 'create-functional-test']                 | ['create-restful-controller', 'create-domain-resource', 'create-job']
        'rest-api'        | 'grails-forge-cli.yml' | ApplicationType.REST_API        | 'rest-api'        | 'create-restapi'         | false | ['create-restful-controller', 'create-domain-resource', 'create-functional-test', 'create-integration-test'] | ['create-job']
        'plugin'          | 'grails-forge-cli.yml' | ApplicationType.PLUGIN          | 'plugin'          | 'create-plugin'          | false | ['create-controller', 'create-service', 'create-unit-test']                                       | ['create-integration-test', 'create-restful-controller', 'create-functional-test']
        'web-plugin'      | 'grails-cli.yml'       | ApplicationType.WEB_PLUGIN      | 'web-plugin'      | 'create-web-plugin'      | true  | ['create-taglib', 'create-integration-test']                                                      | ['create-restful-controller', 'create-domain-resource', 'create-functional-test']
        'rest-api-plugin' | 'grails-forge-cli.yml' | ApplicationType.REST_API_PLUGIN | 'rest-api-plugin' | 'create-rest-api-plugin' | false | ['create-restful-controller', 'create-domain-resource', 'create-functional-test']                 | ['create-job']
    }

    @Unroll
    void "a record whose profile is #what is not used, and the directory has no command that creates a class"() {
        given:
        new File(dir, 'grails-forge-cli.yml').setText(content, 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        !out.toString().contains('create-controller')
        err.toString().readLines() == ["| Warning grails-forge-cli.yml: ${message}; the commands that create the classes of an application are not there without it".toString()]

        and: 'nothing is read as a type by a guess'
        CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP) == null

        when: 'a command that creates a class is typed'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'create-controller', 'Book')

        then:
        exit == 1
        !new File(dir, 'grails-app').exists()

        where:
        what                           | content                                                     || message
        'one Grails has no type for'   | 'profile: angular\ndefaultPackage: com.example\n'           || 'the profile angular has no type of application in this version of Grails'
        'of another making'            | 'profile: com.acme:intranet\ndefaultPackage: com.example\n' || 'the profile com.acme:intranet has no type of application in this version of Grails'
        'written in upper case'        | 'profile: WEB\ndefaultPackage: com.example\n'               || 'the profile WEB has no type of application in this version of Grails'
        'without a value'              | 'profile:\ndefaultPackage: com.example\n'                   || 'profile has no value'
        'a list'                       | 'profile: [web]\ndefaultPackage: com.example\n'             || 'profile must be one line of at most 255 characters, none of them a control character or one that is not shown'
    }

    void "a record that names a profile and a type goes by the profile, and keeps the way of reloading it names"() {
        given:
        new File(dir, 'grails-forge-cli.yml').setText('profile: rest-api\napplicationType: web\ndefaultPackage: com.example\nreloading: devtools\n' +
                'features: [grails-quartz]\nexcludedFeatures: [mockito]\n', 'UTF-8')

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP)

        then: 'the features are those of the type of the profile, whatever the record lists'
        config.legacy
        config.applicationType == ApplicationType.REST_API
        config.reloading == DevelopmentReloading.DEVTOOLS
        !config.features.contains('grails-quartz')
        !config.features.contains('gsp')
        config.excludedFeatures == ['mockito']
    }

    void "update-cli-config, which the command line of Grails 7 told to run for such a record, is no command and says which command writes the record"() {
        given:
        new File(dir, 'grails-forge-cli.yml').setText('profile: web\ndefaultPackage: com.example\n', 'UTF-8')
        Map<String, byte[]> before = dir.listFiles().collectEntries { [it.name, it.bytes] }

        when:
        int exit = ForgeCli.execute(beanContext, 'update-cli-config')
        List<String> lines = err.toString().readLines()

        then: 'it is refused as any name that is no command, with the line of the table of former commands'
        exit == 2
        out.toString().isEmpty()
        lines.any { it.startsWith('There is no command update-cli-config') }
        lines.contains('update-cli-config is what the command line of Grails 7 told to run for a grails-cli.yml or grails-forge-cli.yml that names a profile, ' +
                'and it never was a command. In the project, grails-forge-cli project migrate writes the record of this version from what the project has')

        and: 'what reading the record has to say is said, as for every other name that is no command'
        lines.first().startsWith('| Warning grails-forge-cli.yml names a profile')

        and: 'nothing is written'
        dir.listFiles().collectEntries { [it.name, it.bytes] } == before

        when: 'a name that only begins with it is a name like any other'
        err.reset()
        exit = ForgeCli.execute(beanContext, 'update-cli-configuration')
        lines = err.toString().readLines()

        then:
        exit == 2
        lines.first().startsWith('| Warning grails-forge-cli.yml names a profile')
        lines.any { it.startsWith('There is no command update-cli-configuration') }
        !lines.any { it.startsWith('update-cli-config is what') }
    }

    void "outside a project update-cli-config is refused in the same way, and no project is looked for"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'update-cli-config')
        List<String> lines = err.toString().readLines()

        then:
        exit == 2
        lines[0].startsWith('There is no command update-cli-config')
        lines[1].startsWith('update-cli-config is what the command line of Grails 7 told to run')
        dir.list() as List == []
    }

    // the commands of the project a directory is in, as help --json describes them
    private Set<String> projectCommands(File directory) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            out.reset()
            assert ForgeCli.execute(beanContext, 'help', '--json') == 0
            Map described = (Map) new JsonSlurper().parseText(out.toString())
            return ((List<Map>) described.commands).findAll { it.project }*.name as Set<String>
        } finally {
            System.setProperty('user.dir', current)
            out.reset()
        }
    }
}
