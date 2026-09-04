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

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.HexFormat

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

@CompileStatic
class GradleUtils {

    static Directory findRootGrailsCoreDir(Project project) {
        // .github / .git related directories are purged from source releases, so use the .asf.yaml as an indicator of
        // the parent directory
        findAsfRootDir(project.layout.projectDirectory)
    }

    static Directory findAsfRootDir(Directory currentDirectory) {
        def asfFile = currentDirectory.file('.asf.yaml').asFile
        if (asfFile.exists()) {
            return currentDirectory
        }
        File parent = currentDirectory.asFile.parentFile
        parent && parent != currentDirectory.asFile ? findAsfRootDir(currentDirectory.dir('../')) : null
    }

    static Provider<Boolean> booleanProvider(Project project, String name, boolean defaultValue = false) {
        project.providers.gradleProperty(name)
                .map { it.trim().toBoolean() }
                .orElse(defaultValue)
    }

    static String projectPathKey(Project project) {
        HexFormat.of().formatHex(project.path.getBytes(StandardCharsets.UTF_8))
    }

    static String projectPathFromKey(String key) {
        new String(HexFormat.of().parseHex(key), StandardCharsets.UTF_8)
    }

    static String reportFileName(Project project, String taskName) {
        "${projectPathKey(project)}-${taskName}.xml"
    }

    static Provider<RegularFile> reportMarker(Project project, String tool, String taskName) {
        project.rootProject.layout.buildDirectory.file("reports/aggregation-markers/${tool}/${reportFileName(project, taskName)}.marker")
    }

    static void configureReportMarker(Task task, Directory rootDirectory, Provider<RegularFile> report,
            Provider<RegularFile> marker) {
        Path rootPath = rootDirectory.asFile.toPath().toAbsolutePath().normalize()
        task.outputs.file(marker)
        task.doFirst {
            Path reportPath = report.get().asFile.toPath().toAbsolutePath().normalize()
            Path relativeReportPath
            try {
                relativeReportPath = rootPath.relativize(reportPath)
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Cannot create a report marker for '${reportPath}' relative to '${rootPath}'", exception)
            }
            File reportFile = reportPath.toFile()
            reportFile.delete()
            File markerFile = marker.get().asFile
            markerFile.parentFile.mkdirs()
            markerFile.text = relativeReportPath.toString().replace(File.separator, '/')
        }
    }

    static <T> T lookupProperty(Project project, String name, T defaultValue = null) {
        T v = lookupPropertyByType(project, name, defaultValue?.class) as T
        return v == null ? defaultValue : v
    }

    static <T> T lookupPropertyByType(Project project, String name, Class<T> type) {
        // a cast exception will occur without this
        if (type && (type == Integer || type == int.class)) {
            def v = findProperty(project, name)
            return v == null ? null : Integer.valueOf(v as String) as T
        }
        if (type && (type == Boolean || type == boolean.class)) {
            def v = findProperty(project, name)
            return v == null ? null : (v as String).trim().toBoolean() as T
        }

        findProperty(project, name) as T
    }

    static Object findProperty(Project project, String name) {
        def property = project.findProperty(name)
        if (property != null) {
            return property
        }

        def ext = project.extensions.extraProperties
        if (ext.has(name)) {
            return ext.get(name)
        }

        null
    }
}
