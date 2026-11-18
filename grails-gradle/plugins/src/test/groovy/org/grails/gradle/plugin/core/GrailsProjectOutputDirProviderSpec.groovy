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

import java.nio.file.Path
import java.nio.file.Paths

import spock.lang.Specification

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.testfixtures.ProjectBuilder

class GrailsProjectOutputDirProviderSpec extends Specification {

    void 'the classes directory is passed relative to the project, its names joined by /'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def classesDir = project.layout.buildDirectory.dir('classes/groovy/main')

        expect:
        new GrailsProjectOutputDirProvider('grails.project.class.dir', project.projectDir, classesDir, [:])
                .asArguments().toList() == ['-Dgrails.project.class.dir=build/classes/groovy/main']
    }

    void 'the resources directory is passed under its own property'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def resourcesDir = project.layout.buildDirectory.dir('resources/main')

        expect:
        new GrailsProjectOutputDirProvider('grails.project.resource.dir', project.projectDir, resourcesDir, [:])
                .asArguments().toList() == ['-Dgrails.project.resource.dir=build/resources/main']

        and: 'a value the task sets for the other property does not stop it'
        new GrailsProjectOutputDirProvider('grails.project.resource.dir', project.projectDir, resourcesDir,
                ['grails.project.class.dir': 'user/chosen/dir']).asArguments().toList() ==
                ['-Dgrails.project.resource.dir=build/resources/main']
    }

    void 'a grails.project.class.dir the task sets itself is kept: nothing is passed over it'() {
        given:
        Project project = ProjectBuilder.builder().build()
        def classesDir = project.layout.buildDirectory.dir('classes/groovy/main')
        Map<String, Object> taskSystemProperties = [:]

        when: 'the build sets it after the provider is added, as a configure block after the plugin does'
        def provider = new GrailsProjectOutputDirProvider('grails.project.class.dir', project.projectDir, classesDir,
                taskSystemProperties)
        taskSystemProperties['grails.project.class.dir'] = 'user/chosen/dir'

        then:
        provider.asArguments().toList() == []
    }

    void 'a classes directory with no path relative to the project passes nothing, and the task still runs'() {
        given: 'a classes directory that cannot be relativized against the project, as on another Windows drive'
        Project project = ProjectBuilder.builder().build()
        Directory elsewhere = [getAsFile: { new File('build/classes/groovy/main') }] as Directory

        expect:
        new GrailsProjectOutputDirProvider('grails.project.class.dir', project.projectDir, project.provider { elsewhere }, [:])
                .asArguments().toList() == []
    }

    void 'the warning when #property cannot be passed says what its own fallback breaks'() {
        expect:
        GrailsProjectOutputDirProvider.withoutIt(property).contains(consequence)

        where:
        property                      | consequence
        'grails.project.class.dir'    | 'the change is not reloaded'
        'grails.project.resource.dir' | 'reads its resources from build/resources/main'
        'grails.project.target.dir'   | 'restart marker (.grailspid) in build/'
    }

    void 'a path is joined by / whatever the platform separator: #classesDir'() {
        expect:
        GrailsProjectOutputDirProvider.relativePath(Paths.get('/app'), Paths.get(classesDir)) == relative

        where:
        classesDir                                         | relative
        '/app/build/classes/groovy/main'                   | 'build/classes/groovy/main'
        '/app/build-parent/build-8070/classes/groovy/main' | 'build-parent/build-8070/classes/groovy/main'
        '/elsewhere/build/classes/groovy/main'             | '../elsewhere/build/classes/groovy/main'
    }

    void 'a classes directory with no path relative to the project, such as one on another drive, passes nothing'() {
        given: 'paths that cannot be relativized, as for C:\\app and D:\\build on Windows'
        Path projectDir = Paths.get('/app')
        Path classesDir = Paths.get('build/classes/groovy/main')

        expect: 'the application keeps its fallback rather than bootRun failing'
        GrailsProjectOutputDirProvider.relativePath(projectDir, classesDir) == null
    }
}
