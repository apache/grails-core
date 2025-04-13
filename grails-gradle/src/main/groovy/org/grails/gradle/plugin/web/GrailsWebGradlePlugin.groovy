/*
 * Copyright 2014-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gradle.plugin.web

import grails.util.Environment
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.grails.gradle.plugin.commands.ApplicationContextCommandTask
import org.grails.gradle.plugin.core.GrailsGradlePlugin

import javax.inject.Inject

/**
 * Adds web specific extensions
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsWebGradlePlugin extends GrailsGradlePlugin {
    @Inject
    GrailsWebGradlePlugin(ToolingModelBuilderRegistry registry) {
        super(registry)
    }

    @CompileDynamic
    @Override
    void apply(Project project) {
        super.apply(project)

        TaskContainer taskContainer = project.tasks
        if (taskContainer.findByName("urlMappingsReport") == null) {
            FileCollection fileCollection = buildClasspath(project, project.configurations.runtimeClasspath, project.configurations.console)
            taskContainer.create("urlMappingsReport", ApplicationContextCommandTask) {
                classpath = fileCollection
                systemProperty Environment.KEY, System.getProperty(Environment.KEY, Environment.DEVELOPMENT.getName())
                command = 'url-mappings-report'
            }
        }
    }
}
