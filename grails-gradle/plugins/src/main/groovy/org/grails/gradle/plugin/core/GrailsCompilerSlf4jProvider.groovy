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

import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/**
 * Keeps SLF4J from reporting on itself in the Groovy compiler's worker JVM of a Grails project: the
 * system property {@code slf4j.internal.verbosity} is {@code ERROR} there.
 *
 * <p>The AST transformations of Grails make a logger of SLF4J when they are loaded from the class path
 * of a compilation. The class path of {@code src/standalone} has the API of SLF4J, from the tool API,
 * and no binding, as a standalone tool runs without the application, and so may any other class path
 * that does not have the libraries of the application. SLF4J then said on the standard error, once for
 * every compilation, that it had no provider and logged nothing, which the build showed with the
 * compilation and the {@code grails} command with the build that refreshes a standalone tool. Nothing
 * is logged during a compilation that anybody reads, so nothing is lost; SLF4J still reports its
 * errors.</p>
 *
 * <p>No binding is put on the class path instead: one beside the binding the class path of an
 * application has would have SLF4J report two, and a dependency would have to be resolved for every
 * compilation. Every compilation of the project gets the same property, so the compilations of a
 * project share their worker JVMs as they did.</p>
 *
 * @since 9.0
 */
@CompileStatic
class GrailsCompilerSlf4jProvider implements CommandLineArgumentProvider {

    /** The system property SLF4J reads the level of its own reports from. */
    static final String VERBOSITY_PROPERTY = 'slf4j.internal.verbosity'

    /** The level of the reports of SLF4J that are said: errors only. */
    static final String VERBOSITY = 'ERROR'

    /** The level, an input of the compilation, as every argument of its worker JVM is. */
    @Input
    String getVerbosity() {
        VERBOSITY
    }

    @Override
    Iterable<String> asArguments() {
        ["-D${VERBOSITY_PROPERTY}=${VERBOSITY}".toString()]
    }
}
