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

import java.util.concurrent.TimeUnit

import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import grails.boot.GrailsApp
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.util.Environment
import org.grails.forge.ForgeGrailsPlugin
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureRegistry
import org.grails.forge.cli.command.CreateAppCommand

/**
 * The shape of the application the CLI runs: a Grails application without a web server, hosting
 * the core and Forge plugins and nothing else, with the commands as prototype beans.
 */
class ApplicationSpec extends Specification {

    @TempDir
    File projectDir

    @Shared
    @AutoCleanup
    ConfigurableApplicationContext context = Application.builder().run()

    void "the CLI is a Grails application without a web server"() {
        expect:
        !(context instanceof WebServerApplicationContext)
        context.getBean(GrailsApplication)
        context.getBean(GrailsPluginManager)
        !GrailsApp.developmentModeActive
    }

    void "the application runs the core plugin and the Forge plugin, and nothing the launcher's jar carries"() {
        given:
        GrailsPluginManager pluginManager = context.getBean(GrailsPluginManager)

        expect:
        pluginManager.allPlugins*.name.sort() == ['core', 'forge']
        pluginManager.getGrailsPlugin('forge').instance instanceof ForgeGrailsPlugin
    }

    void "the generator comes from the Forge plugin"() {
        expect:
        context.getBean(FeatureRegistry).all()
        context.getBean(ProjectGenerator)
    }

    void "the commands are prototype beans built with the generator"() {
        expect:
        !context.getBean(ForgeCommand).is(context.getBean(ForgeCommand))
        !context.getBean(CreateAppCommand).is(context.getBean(CreateAppCommand))
    }

    void "the environment is never development, so nothing watches the working directory"() {
        expect:
        Environment.current != Environment.DEVELOPMENT
        !Environment.current.reloadEnabled
    }

    void 'the CLI ignores application configuration in the working directory and its config directory'() {
        given:
        new File(projectDir, 'application.yml').text = 'spring:\n  main:\n    web-application-type: invalid\n'
        File config = new File(projectDir, 'config')
        config.mkdirs()
        new File(config, 'application.yml').text = 'spring:\n  config:\n    import: file:missing-required-configuration.yml\n'
        String classpath = System.getProperty('java.class.path')
        File output = new File(projectDir, 'cli.log')

        when:
        Process process = new ProcessBuilder(new File(System.getProperty('java.home'), 'bin/java').absolutePath,
                '-cp', classpath, Application.name, '--help')
                .directory(projectDir).redirectErrorStream(true).redirectOutput(output).start()

        then:
        process.waitFor(90, TimeUnit.SECONDS)
        assert process.exitValue() == 0: output.text
        output.text.contains('Usage:')

        cleanup:
        process?.destroyForcibly()
    }

}
