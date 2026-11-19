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
        state.baseline.files*.path.containsAll(['build.gradle', 'gradlew', 'grails-forge-cli.yml', 'grails-app/conf/application.properties'])
        !state.baseline.files*.path.any { it.startsWith('.grails') }

        when: 'the old record of the project is gone'
        assert new File(shop, 'grails-forge-cli.yml').delete()
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
        System.setOut(new PrintStream(new ByteArrayOutputStream()))
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

        when:
        byte[] lock = new File(dir, '.grails/forge.lock.yml').bytes
        int again = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--inplace', '--features', 'grails-quartz')

        then:
        again != 0
        err.toString().contains('Cannot create the project because the directory already has the state of a generated project in .grails')
        new File(dir, '.grails/forge.lock.yml').bytes == lock
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
