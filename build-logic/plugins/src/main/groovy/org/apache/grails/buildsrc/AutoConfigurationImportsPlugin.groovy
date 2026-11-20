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

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

/**
 * Convention plugin that wires {@link GenerateAutoConfigurationImportsTask} into the {@code main}
 * source set's output, so a module authoring {@code @AutoConfiguration} classes never has to
 * hand-maintain {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>The generated resources directory is registered via {@code sourceSets.main.output.dir(...)}
 * rather than routed through {@code processResources}, so it lands on the compile/runtime/test
 * classpath and in the final jar without depending on {@code Copy}-task ordering semantics.
 */
@CompileStatic
class AutoConfigurationImportsPlugin implements Plugin<Project> {

    static final String TASK_NAME = 'generateAutoConfigurationImports'
    static final String GENERATED_RESOURCES_PATH = 'generated/resources/autoConfigurationImports'

    @Override
    void apply(Project project) {
        project.pluginManager.apply('java-base')

        SourceSet main = project.extensions.getByType(JavaPluginExtension).sourceSets.getByName('main')
        Provider<Directory> generatedDir = project.layout.buildDirectory.dir(GENERATED_RESOURCES_PATH)

        TaskProvider<GenerateAutoConfigurationImportsTask> task = project.tasks.register(
                TASK_NAME, GenerateAutoConfigurationImportsTask) { GenerateAutoConfigurationImportsTask t ->
            t.group = 'build'
            t.description = 'Generates META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports ' +
                    'by scanning compiled classes for @AutoConfiguration'
            t.classesDirs.from(main.output.classesDirs)
            t.resourcesDirs.from(main.resources.srcDirs)
            t.outputDirectory.set(generatedDir)
        }

        main.output.dir(Collections.<String, Object> singletonMap('builtBy', task), generatedDir)
    }

}
