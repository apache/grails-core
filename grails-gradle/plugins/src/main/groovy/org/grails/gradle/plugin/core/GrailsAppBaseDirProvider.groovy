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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic

import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

import grails.util.BuildSettings

/**
 * Provides the {@code -Dgrails.build.base.dir} system property to forked JVM tasks.
 *
 * The directory is marked {@link Internal} rather than {@code @InputDirectory} because
 * the project directory encompasses task output directories (e.g. {@code build/}).
 * Declaring it as {@code @InputDirectory} causes Gradle to report implicit dependency
 * violations between the consuming task (e.g. {@code test}) and every task that writes
 * into the project directory (e.g. {@code compileIntegrationTestGroovy}, {@code jar}).
 * The actual inputs that matter for caching (classpath, source sets) are already tracked
 * by their respective tasks.
 */
@CompileStatic
class GrailsAppBaseDirProvider implements CommandLineArgumentProvider {

    @Internal
    final File appBaseDir

    GrailsAppBaseDirProvider(File appBaseDir) {
        this.appBaseDir = appBaseDir
    }

    @Override
    Iterable<String> asArguments() {
        ["-D${BuildSettings.APP_BASE_DIR}=${appBaseDir.absolutePath}".toString()]
    }
}
