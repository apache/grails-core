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
package org.grails.forge.options

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.grails.forge.application.Project
import org.grails.forge.build.gradle.GradleDsl

@CompileStatic
enum BuildTool {

    GRADLE('build/libs', 'build.gradle', '-*-all.jar')

    public static final BuildTool DEFAULT_OPTION = BuildTool.GRADLE

    private final String jarDirectory
    private final String fileName
    private final String shadeJarPattern

    BuildTool(String jarDirectory, String fileName, String shadeJarPattern) {
        this.jarDirectory = jarDirectory
        this.fileName = fileName
        this.shadeJarPattern = shadeJarPattern
    }

    String getJarDirectory() {
        return jarDirectory
    }

    String getShadeJarDirectoryPattern(Project project) {
        Objects.requireNonNull(project, 'Project should not be null')
        return getJarDirectory() + '/' + project.getName() + shadeJarPattern
    }

    String getBuildFileName() {
        return fileName
    }

    @Override
    String toString() {
        return getName()
    }

    @Nonnull
    String getName() {
        return name().toLowerCase(Locale.ENGLISH)
    }

    boolean isGradle() {
        return this == GRADLE
    }

    Optional<GradleDsl> getGradleDsl() {
        if (isGradle()) {
            if (this == BuildTool.GRADLE) {
                return Optional.of(GradleDsl.GROOVY)
            }
        }
        return Optional.empty()
    }
}
