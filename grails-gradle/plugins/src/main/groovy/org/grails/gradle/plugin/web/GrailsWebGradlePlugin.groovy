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
package org.grails.gradle.plugin.web

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.Project
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import grails.util.Environment
import org.grails.gradle.plugin.commands.ApplicationContextCommandTask
import org.grails.gradle.plugin.core.GrailsGradlePlugin

/**
 * Adds web specific extensions
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GrailsWebGradlePlugin extends GrailsGradlePlugin {

    private static final String URL_MAPPINGS_REPORT = 'urlMappingsReport'

    @Inject
    GrailsWebGradlePlugin(ToolingModelBuilderRegistry registry) {
        super(registry)
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        // The task could theoretically already be registered by
        // GrailsGradlePlugin in configureApplicationCommands()
        // if grails-web-urlmappings is on the build classpath
        if (!project.tasks.names.contains(URL_MAPPINGS_REPORT))  {
            registerUrlMappingsTask(project)
        }
    }

    private void registerUrlMappingsTask(Project project) {
        project.tasks.register(URL_MAPPINGS_REPORT, ApplicationContextCommandTask) { task ->
            task.classpath = buildClasspath(
                    project,
                    'runtimeClasspath', 'console'
            )
            task.systemProperty(
                    Environment.KEY,
                    System.getProperty(
                            Environment.KEY,
                            Environment.DEVELOPMENT.name
                    )
            )
            def appClassProvider = GrailsGradlePlugin.getMainClassProvider(project)
            task.argumentProviders.add({
                ['url-mappings-report', appClassProvider.get()]
            } as CommandLineArgumentProvider)
        }
    }
}
