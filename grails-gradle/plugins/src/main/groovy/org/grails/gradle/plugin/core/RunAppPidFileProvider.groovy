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

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

/**
 * Provides the {@code -Dgrails.cli.pid.file} system property to the forked {@code bootRun} JVM so
 * that the application writes its own PID to a known project local file. The CLI {@code stop-app}
 * command reads that file to locate and terminate the running application.
 *
 * <p>The PID file path is resolved lazily from a {@link Provider} when the argument list is built
 * at execution time, so the build directory is not forced during configuration and the value stays
 * configuration-cache safe.</p>
 */
@CompileStatic
class RunAppPidFileProvider implements CommandLineArgumentProvider {

    @Internal
    final String propertyName

    @Internal
    final Provider<RegularFile> pidFile

    RunAppPidFileProvider(String propertyName, Provider<RegularFile> pidFile) {
        this.propertyName = propertyName
        this.pidFile = pidFile
    }

    @Override
    Iterable<String> asArguments() {
        File file = pidFile.get().asFile
        file.parentFile?.mkdirs()
        ["-D${propertyName}=${file.absolutePath}".toString()]
    }
}
