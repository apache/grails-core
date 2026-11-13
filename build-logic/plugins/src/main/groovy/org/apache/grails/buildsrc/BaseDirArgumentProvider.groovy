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

import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

/**
 * Supplies {@code -Dbase.dir=<projectDir>} to a forked Groovy compiler so the
 * {@code GlobalGrailsClassInjectorTransformation} AST transform resolves the correct project
 * directory when generating {@code META-INF/grails.factories} / {@code grails-plugin.xml}.
 *
 * <p>This mirrors {@code GrailsAppBaseDirProvider} from the Grails Gradle plugins, which is not on
 * this build's classpath (build-logic is a separate, unpublished build). It is applied to EVERY
 * module of this multi-project build so that every Groovy compile requests its own {@code base.dir}.
 * That is what keeps the value correct: Gradle reuses a forked compiler daemon for a task whose
 * requested fork arguments the daemon already satisfies, so a compile that requests <em>no</em>
 * {@code base.dir} can be handed a daemon started for another module and inherit that module's
 * value. When every compile requests its own unique {@code base.dir}, daemons partition per project
 * and the value can never leak across modules.
 *
 * <p>The directory is {@link Internal} (not an {@code @InputDirectory}) because the project
 * directory contains the task's own output directories; the real cache-relevant inputs (sources,
 * classpath) are already tracked by the compile task.
 */
@CompileStatic
class BaseDirArgumentProvider implements CommandLineArgumentProvider {

    @Internal
    final File baseDir

    BaseDirArgumentProvider(File baseDir) {
        this.baseDir = baseDir
    }

    @Override
    Iterable<String> asArguments() {
        ["-Dbase.dir=${baseDir.absolutePath}".toString()]
    }
}
