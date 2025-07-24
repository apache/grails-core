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
package org.grails.cli.gradle.cache

import java.util.concurrent.Callable

import groovy.transform.CompileStatic

import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

import grails.util.BuildSettings
import org.grails.cli.gradle.GradleUtil
import org.grails.cli.profile.ProjectContext

/**
 * Utility class for performing cached operations that retrieve data from Gradle. Since these operations are expensive we want to cache the data to avoid unnecessarily calling Gradle
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
abstract class CachedGradleOperation<T> implements Callable<T> {

    protected String fileName
    protected ProjectContext projectContext

    CachedGradleOperation(ProjectContext projectContext, String fileName) {
        this.fileName = fileName
        this.projectContext = projectContext
    }

    abstract T readFromCached(File f)

    abstract void writeToCache(PrintWriter writer, T data)

    abstract T readFromGradle(ProjectConnection connection)

    @Override
    T call() throws Exception {
        def depsFile = new File(BuildSettings.TARGET_DIR, fileName)
        try {
            if(depsFile.exists() && depsFile.lastModified() > new File(projectContext.baseDir, "build.gradle").lastModified()) {
                T cached = readFromCached(depsFile)
                if(cached) {
                    return cached
                }

            }
        } catch (Throwable e) {
            throw e
        }

        try {
            ProjectConnection projectConnection = GradleUtil.openGradleConnection(projectContext.baseDir)
            try {
                updateStatusMessage()
                def data = readFromGradle(projectConnection)
                storeData(data)
                return data
            } finally {
                projectConnection.close()
            }
        } finally {
            DefaultGradleConnector.close()
            ConnectorServices.reset()
        }
    }

    void updateStatusMessage() {
        // no-op
    }

    protected void storeData(T data) {
        try {
            def depsFile = new File(BuildSettings.TARGET_DIR, fileName)
            depsFile.withPrintWriter { PrintWriter writer ->
                writeToCache(writer, data)
            }
        } catch (Throwable e) {
            // ignore
        }
    }
}
