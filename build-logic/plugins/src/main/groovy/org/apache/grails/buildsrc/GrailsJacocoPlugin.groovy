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
package org.apache.grails.buildsrc

import groovy.transform.CompileDynamic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Convention plugin for JaCoCo code coverage. Apply to each subproject that compiles code.
 */
@CompileDynamic
class GrailsJacocoPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.logger.info("Configuring JaCoCo for project: ${project.name}")
        project.pluginManager.apply(JacocoPlugin)

        project.extensions.configure(JacocoPluginExtension) {
            it.toolVersion = '0.8.14'
        }

        project.tasks.withType(Test).configureEach {
            it.finalizedBy 'jacocoTestReport'
        }

        project.tasks.withType(JacocoReport).configureEach {
            it.dependsOn project.tasks.withType(Test)
            it.reports {
                it.xml.required = true
                it.html.required = true
                it.csv.required = true
            }
        }
    }
}
