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

import org.grails.forge.application.ApplicationType
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.GeneratedBuild
import org.grails.forge.cli.GrailsPicocliFactory
import org.grails.forge.io.ConsoleOutput
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
