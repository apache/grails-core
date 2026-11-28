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
package org.grails.forge.features.querytail

import org.apache.grails.forge.cli.console.ToolCatalogFile
import org.apache.grails.forge.cli.console.DeclaredOptions
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.grails.forge.utils.CommandSpec

/**
 * The option of {@code run-app} that Grails Data declares, {@code --query-tail}, in the build of a
 * generated application, with the artifacts of the framework as they are published: the build lists
 * the option as one of the command line artifact of Grails Data, the {@code grails} command reads it
 * from what the build wrote, and the system property the command gives the build for it reaches the
 * JVM of the application as the property that turns capture on.
 */
class RunAppQueryTailOptionBuildSpec extends CommandSpec {

    void 'the build of a generated application lists --query-tail for run-app, and gives the application the property the option sets'() {
        given:
        generateProject()
        // what bootRun would start the application with: its JVM arguments, as they are when the task runs
        new File(dir, 'build.gradle') << '''
            tasks.register('applicationArguments') {
                def bootRun = tasks.named('bootRun')
                doLast {
                    bootRun.get().allJvmArgs.findAll { it.startsWith('-Dgrails.gorm.queryTail.') }.each { println "APPLICATION_ARGUMENT ${it}" }
                }
            }
        '''.stripIndent()

        when: 'the project is compiled, which lists its commands and the options declared for them'
        BuildResult compiled = executeGradle('classes')
        ToolCatalogFile catalog = ToolCatalogFile.read(dir.toPath())
        DeclaredOptions.Declared option = catalog.declaredOptions.options.find { DeclaredOptions.Declared each -> each.name == 'query-tail' }

        then: 'the command line reads the option from the file the build wrote'
        compiled.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalog.state == ToolCatalogFile.State.CURRENT
        catalog.declaredOptions.notUsed == 0
        option.command == 'run-app'
        option.type == 'boolean'
        option.property == 'grails.gorm.queryTail.enabled'
        option.value == 'true'
        option.description == 'Capture every statement GORM runs, for the query-tail command.'
        option.origin ==~ /org\.apache\.grails\.data:grails-datamapping-core-cli:[0-9A-Za-z.-]+/
        !catalog.diagnostics.any { String line -> line.contains('declares') }

        when: 'the build is given what grails run-app --query-tail gives it'
        BuildResult given = executeGradle('applicationArguments', "-D${DeclaredOptions.BUILD_PREFIX}${option.property}=${option.value}".toString(), '--no-configuration-cache')

        then: 'the application is started with the property'
        given.output.readLines().findAll { String line -> line.startsWith('APPLICATION_ARGUMENT ') } == ['APPLICATION_ARGUMENT -Dgrails.gorm.queryTail.enabled=true']

        when: 'without it'
        BuildResult without = executeGradle('applicationArguments', '--no-configuration-cache')

        then:
        !without.output.contains('APPLICATION_ARGUMENT ')
    }

    @Override
    String getTempDirectoryPrefix() {
        'run-app-query-tail'
    }
}
