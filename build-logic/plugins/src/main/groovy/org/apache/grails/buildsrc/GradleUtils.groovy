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
import org.gradle.api.Project
import org.gradle.api.file.Directory

@CompileStatic
class GradleUtils {

    static Directory findRootGrailsCoreDir(Project project) {
        // .github / .git related directories are purged from source releases, so use the .asf.yaml as an indicator of
        // the parent directory
        findAsfRootDir(project.layout.projectDirectory)
    }

    static Directory findAsfRootDir(Directory currentDirectory) {
        def asfFile = currentDirectory.file('.asf.yaml').asFile
        asfFile.exists() ? currentDirectory : findAsfRootDir(currentDirectory.dir('../'))
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
