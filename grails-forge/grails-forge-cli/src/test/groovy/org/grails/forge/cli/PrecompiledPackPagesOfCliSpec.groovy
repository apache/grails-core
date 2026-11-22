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

import grails.util.Environment
import org.apache.grails.forge.core.pack.BundledPackFeatures
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The application the CLI runs takes the pages the build of Forge compiled for the templates of its
 * packs: it runs in the environment they were compiled for, and with the jar of Forge that has them.
 */
class PrecompiledPackPagesOfCliSpec extends Specification {

    @Shared
    @AutoCleanup
    ConfigurableApplicationContext context = Application.builder().run()

    private String environmentBefore

    void setup() {
        // the environment the command line gives itself when none is set, as none is for a user: the
        // JVM of the tests has one set, which Application.builder() leaves as it is
        environmentBefore = System.getProperty(Environment.KEY)
        System.setProperty(Environment.KEY, Environment.PRODUCTION.getName())
    }

    void cleanup() {
        if (environmentBefore == null) {
            System.clearProperty(Environment.KEY)
        } else {
            System.setProperty(Environment.KEY, environmentBefore)
        }
    }

    void "the application of the command line compiles no template of a pack of Forge"() {
        given:
        BundledPackFeatures packs = context.getBean(BundledPackFeatures)
        MapOutputHandler output = new MapOutputHandler()

        when: 'an application with the features it has by default and two that have templates'
        context.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('example.grails.foo'), new Options(),
                OperatingSystem.LINUX, ['asciidoctor', 'github-workflow-java-ci'], output, ConsoleOutput.NOOP)

        then:
        output.project['src/docs/asciidoc/index.adoc']
        output.project['.github/workflows/gradle.yml']
        packs.precompiledTemplates >= 6
        packs.compiledTemplates == 0
    }
}
