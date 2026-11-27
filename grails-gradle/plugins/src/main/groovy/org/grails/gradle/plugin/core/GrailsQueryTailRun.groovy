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

import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Names the query-tail run of one build: every test JVM the build forks with query-tail capture enabled is
 * given the name, so that each capture a test JVM opens, one datastore after another, and every test task
 * of the build, adds to the one run rather than starting another and truncating the file.
 *
 * <p>The run is a {@link Supplier} of its name because a project that loads the plugin with a class loader of
 * its own is handed the run another project registered, an instance of the other loader's class, which it can
 * read only through a type both loaders share.</p>
 *
 * @since 9.0
 */
@CompileStatic
abstract class GrailsQueryTailRun implements BuildService<BuildServiceParameters.None>, Supplier<String> {

    /** The name of the run, the same for every task of the build and another for the next build. */
    final String id = UUID.randomUUID().toString()

    /**
     * @return the name of the run
     */
    @Override
    String get() {
        id
    }
}
