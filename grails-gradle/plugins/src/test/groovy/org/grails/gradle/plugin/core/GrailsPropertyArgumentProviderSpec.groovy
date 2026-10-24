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

    void 'each property is passed as a system property of the same name, a bare one as true'() {
        given:
        Project project = ProjectBuilder.builder().build()
        Map<String, String> properties = ['grails.gorm.queryTail.enabled': '', 'grails.gorm.queryTail.file': 'build/statements.log']
        def provider = new GrailsPropertyArgumentProvider(project.provider { properties })

        expect:
        provider.asArguments().toList() ==
                ['-Dgrails.gorm.queryTail.enabled=true', '-Dgrails.gorm.queryTail.file=build/statements.log']

        and: 'the properties are the input the task is up to date by'
        provider.settings == properties
    }

    void 'no properties pass nothing, whether none are set or the provider has no value'() {
        given:
        Project project = ProjectBuilder.builder().build()

        expect:
        new GrailsPropertyArgumentProvider(project.provider { [:] }).asArguments().toList() == []
        new GrailsPropertyArgumentProvider(project.provider { null }).asArguments().toList() == []
        new GrailsPropertyArgumentProvider(project.provider { null }).settings == [:]
    }

    void 'the startup progress settings are passed the same way'() {
        given:
        Project project = ProjectBuilder.builder().build()

        expect:
        new GrailsStartupProgressProvider(project.provider { ['grails.startup.progress.openBrowser': ''] })
                .asArguments().toList() == ['-Dgrails.startup.progress.openBrowser=true']
    }

    void 'the plugin hands the query-tail properties to the forked application and test JVMs'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.extensions.extraProperties.set('grailsVersion', System.getProperty('projectVersion'))
        project.pluginManager.apply('org.apache.grails.gradle.grails-app')

        expect:
        ['bootRun', 'test'].every { String name ->
            JavaForkOptions task = project.tasks.getByName(name) as JavaForkOptions
            task.jvmArgumentProviders.any { it instanceof GrailsPropertyArgumentProvider }
        }
    }
}
