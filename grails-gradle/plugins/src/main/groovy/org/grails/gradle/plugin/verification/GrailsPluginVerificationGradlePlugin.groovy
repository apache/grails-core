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
package org.grails.gradle.plugin.verification

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Registers {@code verifyGrailsPlugins}, which {@code check} runs: it fails the build of a project whose Grails
 * plugins would stop its application from starting (see {@link VerifyGrailsPluginsTask}). Every Grails
 * application and plugin build applies it; a project that compiles Grails plugins without the Grails Gradle
 * plugin applies it by itself.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsPluginVerificationGradlePlugin implements Plugin<Project> {

    public static final String PLUGIN_ID = 'org.apache.grails.gradle.grails-plugin-verification'

    public static final String TASK_NAME = 'verifyGrailsPlugins'

    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('java') {
            SourceSet main = project.extensions.getByType(JavaPluginExtension).sourceSets
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            TaskProvider<VerifyGrailsPluginsTask> verify = project.tasks.register(TASK_NAME,
                    VerifyGrailsPluginsTask) { VerifyGrailsPluginsTask task ->
                task.group = LifecycleBasePlugin.VERIFICATION_GROUP
                task.description = 'Checks that the Grails plugins of the project would let its application start.'
                // The project's own classes and resources first, as its application has them, each with the task
                // that writes it. Not the whole source set output: a view compiler registers its own directory
                // into it without naming itself as the producer. Then the runtime class path, and the compile
                // class path, whose compileOnly dependencies a plugin's application provides, as Grails itself.
                task.classpath = project.files(main.output.classesDirs,
                        project.tasks.named(main.processResourcesTaskName),
                        project.configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME),
                        project.configurations.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME))
                task.environments.convention(['development', 'test', 'production'])
                task.report.convention(project.layout.buildDirectory.file('grails/plugin-order.txt'))
                task.failure.convention(project.layout.buildDirectory.file('tmp/verifyGrailsPlugins/failure.txt'))
            }
            project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { it.dependsOn(verify) }
        }
    }
}
