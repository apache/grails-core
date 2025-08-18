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
package org.grails.gradle.plugin.model

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.tooling.provider.model.ToolingModelBuilder

/**
 * Builds the GrailsClasspath instance that contains the URLs of the resolved dependencies
 */
@CompileStatic
class GrailsClasspathToolingModelBuilder implements ToolingModelBuilder {
    public static final String PROFILE_CONFIGURATION_NAME = "profile"

    @Override
    boolean canBuild(String modelName) {
        return modelName == GrailsClasspath.name
    }

    @Override
    Object buildAll(String modelName, Project project) {
        try {
            List<URL> runtimeDependencies = project.getConfigurations().getByName("testRuntimeClasspath").getResolvedConfiguration().getResolvedArtifacts().collect { ResolvedArtifact artifact ->
                artifact.getFile().toURI().toURL()
            }

            DefaultGrailsClasspath grailsClasspath = new DefaultGrailsClasspath(dependencies: runtimeDependencies)

            Configuration profileConfiguration = project.getConfigurations().getByName(PROFILE_CONFIGURATION_NAME)
            if (profileConfiguration != null) {
                grailsClasspath.profileDependencies = profileConfiguration.resolvedConfiguration.resolvedArtifacts.collect() { ResolvedArtifact artifact ->
                    artifact.file.toURI().toURL()
                }
            }
            return grailsClasspath
        } catch (ResolveException e) {
            new DefaultGrailsClasspath(error: e.message)
        }
    }
}
