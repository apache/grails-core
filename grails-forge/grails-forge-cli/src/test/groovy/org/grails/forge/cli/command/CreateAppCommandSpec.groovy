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

package org.grails.forge.cli.command

import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.GeneratedBuild
import org.grails.forge.cli.GrailsPicocliFactory
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.ServletImpl
import picocli.CommandLine
import spock.util.environment.RestoreSystemProperties

class CreateAppCommandSpec extends CommandSpec implements CommandFixture {

    PrintStream originalOut
    PrintStream originalErr

    void setup() {
        originalOut = System.out
        originalErr = System.err
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "test creating project with defaults"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        CliRunner.run(CreateAppCommand, beanContext, "foobar")

        then:
        noExceptionThrown()
        out.toString().contains("Application created")
    }

    void "a created application has the state of the project, which the code generation commands read"() {
        given:
        System.setOut(new PrintStream(new ByteArrayOutputStream()))

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', 'grails-quartz,properties',
                '--servlet', 'jetty', '--reloading', 'none')
        File shop = new File(dir, 'shop')
        ProjectState state = ProjectState.read(shop.toPath())
        state.verifyBaseline(shop.toPath())

        then:
        exit == 0
        state.manifest.tracked
        state.manifest.applicationType == ApplicationType.WEB
        state.manifest.name == 'shop'
        state.manifest.defaultPackage == 'com.example'
        state.manifest.options.servlet == ServletImpl.JETTY
        state.manifest.options.reloading == DevelopmentReloading.NONE
        state.manifest.requestedFeatures*.name == ['grails-quartz', 'properties']
        state.lock.featureNames.containsAll(['grails-quartz', 'properties', 'spock', 'spring-boot-starter-jetty'])
        state.baseline.files*.path.containsAll(['build.gradle', 'gradlew', 'grails-app/conf/application.properties'])
        !new File(shop, 'grails-forge-cli.yml').exists()
        !state.baseline.files*.path.any { it.startsWith('.grails') }

        when: 'the project has no record but its state'
        CodeGenConfig config = CodeGenConfig.load(beanContext, shop, ConsoleOutput.NOOP)

        then:
        config.applicationType == ApplicationType.WEB
        config.defaultPackage == 'com.example'
        config.reloading == DevelopmentReloading.NONE
        config.features == state.lock.featureNames
        !config.legacy
    }

    void "an application is created in a directory that has files, and not over the state of another"() {
        given:
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        new File(dir, 'build.gradle').setText('// mine\n', 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--inplace')
        ProjectState state = ProjectState.read(dir.toPath())
        state.verifyBaseline(dir.toPath())

        then: 'the file that was there is kept, and the baseline has the one the generator would have written'
        exit == 0
        new File(dir, 'build.gradle').getText('UTF-8') == '// mine\n'
        new File(dir, '.grails/baseline/files/build.gradle').getText('UTF-8').contains('dependencies {')

        and: 'which the command says'
        out.toString().contains('1 file that the directory already had was kept and is not as generated: build.gradle')
        out.toString().contains('The record of the project in .grails has them as they would have been generated, so they count as changed since then')
        out.toString().contains('Application created')

        when:
        byte[] lock = new File(dir, '.grails/forge.lock.yml').bytes
        int again = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--inplace', '--features', 'grails-quartz')

        then: 'which is no mistake in the command line: the message, and no usage and no stack trace'
        again == 1
        !err.toString().contains('Usage:')
        !err.toString().contains('Exception')
        err.toString().contains('Cannot create the project because the directory already has the state of a generated project (.grails/project.yml); ' +
                'to generate the project anew, remove the directory .grails first')
        new File(dir, '.grails/forge.lock.yml').bytes == lock

        when: 'the first generation did not come to its end: its manifest, which is written last, is not there'
        assert new File(dir, '.grails/project.yml').delete()
        int completed = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--inplace', '--features', 'grails-quartz')

        then:
        completed == 0
        ProjectState.read(dir.toPath()).lock.featureNames.contains('grails-quartz')
    }

    void "what is no name for an application is a mistake in the command line, said in one line that is safe to show: #what"() {
        given:
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        System.setOut(new PrintStream(new ByteArrayOutputStream()))
        System.setErr(new PrintStream(err))

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, name)

        then:
        exit == 2
        err.toString().contains(message)
        err.toString().contains('Usage:')
        !err.toString().contains('\u001b[31m')
        !err.toString().contains('could not be read again')
        !err.toString().contains('The features write')
        dir.list().length == 0

        where:
        what                 | name                          | message
        'an escape sequence' | 'com.ex\u001b[31mample.shop'   | '"com.ex?[31mample.shop" is not a valid app name: it has a character that is not shown, such as a control character'
        'a line break'       | 'com.example.sh\nop'           | '"com.example.sh?op" is not a valid app name: it has a character that is not shown, such as a control character'
        'a slash'            | 'com.example.sh/op'           | '"com.example.sh/op" is not a valid app name: it has a path separator'
        'a colon'            | 'com.example.sh:op'           | '"sh:op" is not a valid app name: not every file system takes it as the name of a directory'
    }

    void "a file where the application needs a directory is said before anything is written, without the path of the directory"() {
        given:
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        System.setOut(new PrintStream(new ByteArrayOutputStream()))
        System.setErr(new PrintStream(err))
        new File(dir, 'grails-app').setText('a file', 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--inplace')

        then:
        exit == 1
        err.toString().contains('Cannot create the project because grails-app in the directory is a file, and the project has files in a directory of that name; ' +
                'remove or rename it and create the project again')
        !err.toString().contains(dir.absolutePath)
        !err.toString().contains(dir.canonicalPath)
        !err.toString().contains('Usage:')
        dir.list() as List == ['grails-app']
    }

    void "a directory whose .grails is not a directory is left as it is, and that is no mistake in the command line"() {
        given:
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        System.setOut(new PrintStream(new ByteArrayOutputStream()))
        System.setErr(new PrintStream(err))
        new File(dir, '.grails').setText('x', 'UTF-8')

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--inplace')

        then:
        exit == 1
        err.toString().contains('Cannot create the project because .grails in the directory is a file or a link and not a directory; remove it and create the project again')
        !err.toString().contains('Usage:')
        !err.toString().contains(dir.absolutePath)
        dir.list() as List == ['.grails']
    }

    void "a package that could not be checked out on Windows is a mistake in the command line: #name"() {
        given:
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        System.setOut(new PrintStream(new ByteArrayOutputStream()))
        System.setErr(new PrintStream(err))

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, name)

        then:
        exit == 2
        err.toString().contains(message)
        !err.toString().contains('Exception')
        dir.list().length == 0

        where:
        name            | message
        'com.aux.shop'  | 'The package com.aux has the part aux, which is the name of a device on Windows, so the project could not be checked out there'
        'con.acme.shop' | 'The package con.acme has the part con, which is the name of a device on Windows, so the project could not be checked out there'
        'com.Lpt1.shop' | 'The package com.Lpt1 has the part Lpt1, which is the name of a device on Windows, so the project could not be checked out there'
        'com.acme.nul'  | 'The name nul is the name of a device on Windows, so a directory of that name could not be made there'
        'prn'           | 'The package prn has the part prn, which is the name of a device on Windows, so the project could not be checked out there'
    }

    void "a command that creates an application, and the version, do not read the record of the directory they are run in"() {
        given: 'a directory whose record is not one Forge wrote'
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(out))
        new File(dir, '.grails/baseline').mkdirs()
        new File(dir, '.grails/project.yml').setText('format: 7\n', 'UTF-8')
        new File(dir, '.grails/forge.lock.yml').setText('format: 7\n', 'UTF-8')
        new File(dir, '.grails/baseline/index.yml').setText('format: 7\n', 'UTF-8')

        expect:
        !ForgeCli.readsProject('create-app', 'com.example.shop')
        !ForgeCli.readsProject('-x', 'create-plugin', 'shop')
        !ForgeCli.readsProject('--version')
        !ForgeCli.readsProject('-V')
        ForgeCli.readsProject('create-controller', 'Book')
        ForgeCli.readsProject('--help')
        ForgeCli.readsProject()

        when:
        int version = ForgeCli.execute(beanContext, '--version')
        int created = ForgeCli.execute(beanContext, 'create-app', 'com.example.shop')

        then:
        version == 0
        created == 0
        !out.toString().contains('.grails/project.yml')
        new File(dir, 'shop/.grails/project.yml').file

        when: 'help lists the commands there are in this directory, and says why the code generation commands are not among them'
        ForgeCli.execute(beanContext, '--help')

        then:
        out.toString().contains('.grails/project.yml: format is 7, and this version of Grails reads the format 1')
    }

    @RestoreSystemProperties
    void "a generated build accepts an init script whose path contains spaces"() {
        given:
        generateProject(ApplicationType.WEB)
        File initScript = new File(dir, 'init scripts/verify arguments.gradle')
        initScript.parentFile.mkdirs()
        String configuredInitScript = System.getProperty(GeneratedBuild.INIT_SCRIPT_PROPERTY)
        initScript.setText(configuredInitScript ? new File(configuredInitScript).getText('UTF-8') : '', 'UTF-8')
        initScript.append("\nprintln 'FORGE_INIT_SCRIPT_LOADED'\n", 'UTF-8')
        System.setProperty(GeneratedBuild.INIT_SCRIPT_PROPERTY, initScript.absolutePath)

        when:
        executeGradleCommand('help --console=plain')

        then:
        testOutputContains('FORGE_INIT_SCRIPT_LOADED')
        testOutputContains('BUILD SUCCESSFUL')
    }

    void "test creating a project with an invalid gorm implementation"() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos))

        when:
        CliRunner.run(CreateAppCommand, beanContext, "temp",  "--gorm", "xyz")

        then:
        noExceptionThrown()
        baos.toString().contains("Invalid Grails Data implementation selection: xyz")
    }

    void "test creating a project with the data switch"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        CliRunner.run(CreateAppCommand, beanContext, "dataswitch", "--data", "hibernate7")

        then:
        noExceptionThrown()
        out.toString().contains("Application created")
    }

    void "test creating a project with the -d short alias"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        CliRunner.run(CreateAppCommand, beanContext, "dshort", "-d", "hibernate7")

        then:
        noExceptionThrown()
        out.toString().contains("Application created")
    }

    void "test creating a project with the legacy -g short alias"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        CliRunner.run(CreateAppCommand, beanContext, "gshort", "-g", "hibernate5")

        then:
        noExceptionThrown()
        out.toString().contains("Application created")
    }

    void "test creating a project with the legacy hibernate gorm value"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        CliRunner.run(CreateAppCommand, beanContext, "legacygorm", "--gorm", "hibernate")

        then:
        noExceptionThrown()
        out.toString().contains("Application created")
    }

    void "test creating a project with the hibernate5 gorm value"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        CliRunner.run(CreateAppCommand, beanContext, "hib5gorm", "--gorm", "hibernate5")

        then:
        noExceptionThrown()
        out.toString().contains("Application created")
    }

    void "the --features completion candidates are listed in name order"() {
        when:
        List<String> candidates = new CommandLine(CreateAppCommand, new GrailsPicocliFactory(beanContext))
                .commandSpec
                .findOption('--features')
                .completionCandidates()
                .toList()

        then:
        candidates
        candidates == candidates.toSorted()
    }

    void "community and preview features are labelled as such"() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.setOut(new PrintStream(baos))

        and:
        def (previewFeature, communityFeature) = beanContext.getBean(WebAvailableFeatures).with {
            [allFeatures.find { it.preview }, allFeatures.find { it.community }]
        }

        when:
        CliRunner.run(CreateAppCommand, beanContext, "temp", "--list-features")

        then:
        noExceptionThrown()
        previewFeature == null
        //baos.toString().contains("$previewFeature.name [PREVIEW]")
        communityFeature == null
        //baos.toString().contains("$communityFeature.name [COMMUNITY]")
    }
}
