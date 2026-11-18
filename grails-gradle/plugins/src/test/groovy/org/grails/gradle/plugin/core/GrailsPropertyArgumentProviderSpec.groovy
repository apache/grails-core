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
package org.grails.gradle.plugin.core

import spock.lang.Specification
import spock.lang.TempDir

import org.gradle.api.Project
import org.gradle.process.JavaForkOptions
import org.gradle.testfixtures.ProjectBuilder

class GrailsPropertyArgumentProviderSpec extends Specification {

    @TempDir
    File projectDir

    void 'each property is passed as a system property of the same name, a bare switch as true'() {
        given:
        Project project = ProjectBuilder.builder().build()
        Map<String, String> properties = ['grails.gorm.queryTail.enabled': '', 'grails.gorm.queryTail.file': 'build/statements.log']
        def provider = new GrailsPropertyArgumentProvider(project.provider { properties }, ['grails.gorm.queryTail.enabled'])

        expect:
        provider.asArguments().toList() ==
                ['-Dgrails.gorm.queryTail.enabled=true', '-Dgrails.gorm.queryTail.file=build/statements.log']

        and: 'the properties, and which of them are switches, are the input the task is up to date by'
        provider.settings == properties
        provider.switches == ['grails.gorm.queryTail.enabled']
    }

    void 'a property that is not a switch given without a value is passed empty, never as true'() {
        given:
        Project project = ProjectBuilder.builder().build()
        Map<String, String> properties = ['grails.gorm.queryTail.enabled': 'false', 'grails.gorm.queryTail.file': '',
                                          'grails.gorm.queryTail.run': '']
        def provider = new GrailsPropertyArgumentProvider(project.provider { properties }, ['grails.gorm.queryTail.enabled'])

        expect: 'a switch given a value keeps it'
        provider.asArguments().toList() ==
                ['-Dgrails.gorm.queryTail.enabled=false', '-Dgrails.gorm.queryTail.file=', '-Dgrails.gorm.queryTail.run=']
    }

    void 'no properties pass nothing, whether none are set or the provider has no value'() {
        given:
        Project project = ProjectBuilder.builder().build()

        expect:
        new GrailsPropertyArgumentProvider(project.provider { [:] }, []).asArguments().toList() == []
        new GrailsPropertyArgumentProvider(project.provider { null }, []).asArguments().toList() == []
        new GrailsPropertyArgumentProvider(project.provider { null }, []).settings == [:]
    }

    void 'the startup progress settings are passed the same way, its switches as true when given bare'() {
        given:
        Project project = ProjectBuilder.builder().build()
        Map<String, String> properties = ['grails.startup.progress.openBrowser': '', 'grails.startup.progress.showDetails': '',
                                          'grails.startup.progress.enabled': '', 'grails.startup.progress.endpoint.enabled': '',
                                          'grails.startup.progress.browserCommand': '', 'grails.startup.progress.statusPath': '']

        expect:
        new GrailsStartupProgressProvider(project.provider { properties }).asArguments().toList() == [
                '-Dgrails.startup.progress.openBrowser=true', '-Dgrails.startup.progress.showDetails=true',
                '-Dgrails.startup.progress.enabled=true', '-Dgrails.startup.progress.endpoint.enabled=true',
                '-Dgrails.startup.progress.browserCommand=', '-Dgrails.startup.progress.statusPath=']
    }

    void 'the plugin hands the query-tail properties to the forked application and test JVMs'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.extensions.extraProperties.set('grailsVersion', System.getProperty('projectVersion'))
        project.pluginManager.apply('org.apache.grails.gradle.grails-app')

        expect: 'with capture turned on by its switch alone'
        ['bootRun', 'test'].every { String name ->
            JavaForkOptions task = project.tasks.getByName(name) as JavaForkOptions
            task.jvmArgumentProviders.any {
                it instanceof GrailsPropertyArgumentProvider && it.switches == ['grails.gorm.queryTail.enabled']
            }
        }
    }
}
