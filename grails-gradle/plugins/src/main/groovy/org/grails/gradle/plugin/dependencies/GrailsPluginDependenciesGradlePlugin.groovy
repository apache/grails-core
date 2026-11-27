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
package org.grails.gradle.plugin.dependencies

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.language.base.plugins.LifecycleBasePlugin

import org.apache.grails.plugin.descriptor.PluginDescriptorWriter

/**
 * Registers {@code verifyPluginDependencies}, which {@code check} runs: it fails the build of a Grails plugin
 * that {@code dependsOn} a plugin none of its project's dependencies provides (see
 * {@link VerifyPluginDependenciesTask}). The Grails plugin Gradle plugin applies it; a project that compiles
 * Grails plugins without it applies it by itself.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsPluginDependenciesGradlePlugin implements Plugin<Project> {

    public static final String PLUGIN_ID = 'org.apache.grails.gradle.grails-plugin-dependencies'

    public static final String TASK_NAME = 'verifyPluginDependencies'

    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('java') {
            SourceSet main = project.extensions.getByType(JavaPluginExtension).sourceSets
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            TaskProvider<VerifyPluginDependenciesTask> verify = project.tasks.register(TASK_NAME,
                    VerifyPluginDependenciesTask) { VerifyPluginDependenciesTask task ->
                task.group = LifecycleBasePlugin.VERIFICATION_GROUP
                task.description = 'Checks that a dependency of the project provides every plugin a Grails plugin of it depends on.'
                task.pluginDescriptors.from(main.output.asFileTree
                        .matching { PatternFilterable pattern ->
                            pattern.include(PluginDescriptorWriter.PLUGIN_DESCRIPTORS_PREFIX + '*/' +
                                    PluginDescriptorWriter.PLUGIN_PROPERTIES_FILE)
                        })
                task.classpath.from(project.configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME))
                task.projectPath.set(project.path == ':' ? "project ${project.name}" as String : "project ${project.path}" as String)
            }
            project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { it.dependsOn(verify) }
        }
    }
}
