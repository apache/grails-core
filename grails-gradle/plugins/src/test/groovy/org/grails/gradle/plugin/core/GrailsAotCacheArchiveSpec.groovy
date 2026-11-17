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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import org.apache.grails.gradle.plugin.aot.ExtractApplicationTask
import org.apache.grails.gradle.plugin.aot.TrainAotCacheTask

/**
 * Covers which archive an AOT cache is trained against: the one the application ships.
 *
 * <p>That is the executable war where the war plugin is applied, as it is in a generated
 * application, and the executable jar otherwise. The war plugin can be applied before or after the
 * Grails plugin, and either order has to train against the war.</p>
 */
class GrailsAotCacheArchiveSpec extends Specification {

    private static final String GRAILS_WEB_PLUGIN = 'org.apache.grails.gradle.grails-web'

    @TempDir
    File projectDir

    private Project project

    void setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDir).withName('library').build()
        project.version = '0.1'
        project.extensions.extraProperties.set('grailsVersion', System.getProperty('projectVersion'))
    }

    void 'an application without the war plugin is trained against its executable jar'() {
        when:
        project.pluginManager.apply(GRAILS_WEB_PLUGIN)

        then:
        extractedArchive() == archiveOf('bootJar')
        trainedApplication() == 'library-0.1.jar'
    }

    void 'an application with the war plugin is trained against its executable war when #order'() {
        when:
        plugins.each { String id -> project.pluginManager.apply(id) }

        then:
        extractedArchive() == archiveOf('bootWar')
        extract().taskDependencies.getDependencies(extract())*.name.contains('bootWar')

        and: 'the war is run as the jar its extraction writes'
        trainedApplication() == 'library-0.1.jar'

        where:
        order                                   | plugins
        'the Grails plugin is applied first'    | [GRAILS_WEB_PLUGIN, 'war']
        'the war plugin is applied first'       | ['war', GRAILS_WEB_PLUGIN]
    }

    void 'an application that switches its executable war off is trained against its executable jar'() {
        given:
        project.pluginManager.apply(GRAILS_WEB_PLUGIN)
        project.pluginManager.apply('war')

        when:
        project.tasks.named('bootWar').configure { Task task -> task.enabled = false }

        then:
        extractedArchive() == archiveOf('bootJar')
        trainedApplication() == 'library-0.1.jar'
    }

    private ExtractApplicationTask extract() {
        project.tasks.named('extractAotCacheApplication', ExtractApplicationTask).get()
    }

    private File extractedArchive() {
        extract().archiveFile.get().asFile
    }

    private String trainedApplication() {
        project.tasks.named('trainAotCache', TrainAotCacheTask).get().archiveFileName.get()
    }

    private File archiveOf(String taskName) {
        project.tasks.named(taskName).get().property('archiveFile').get().asFile
    }
}
