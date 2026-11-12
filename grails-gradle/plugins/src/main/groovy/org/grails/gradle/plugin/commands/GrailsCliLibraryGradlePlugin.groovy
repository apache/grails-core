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
package org.grails.gradle.plugin.commands

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Configures a library that consumes another project's cli tier in its <em>main</em> variants —
 * for example {@code api(project(':grails-core')) { capabilities { requireCapability('org.apache.grails:grails-core-cli') } }}
 * — so its published pom and Gradle Module Metadata record the companion coordinate
 * ({@code grails-core-cli}) instead of the capability request, which external consumers cannot
 * resolve against the published component tree.
 *
 * Projects that ship their <em>own</em> cli companion apply
 * {@code org.apache.grails.gradle.grails-plugin-cli} instead, which handles its cli variants the
 * same way automatically.
 *
 * @since 8.0
 */
@CompileStatic
abstract class GrailsCliLibraryGradlePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (!project.pluginManager.hasPlugin('java')) {
            throw new GradleException("The Grails cli-library plugin requires the `java` plugin (or a plugin that applies it, e.g. `java-library`) to be applied to project `${project.name}` first.")
        }
        CliPublishingSupport.publishCliDependenciesAsCompanionCoordinates(project)
    }
}
