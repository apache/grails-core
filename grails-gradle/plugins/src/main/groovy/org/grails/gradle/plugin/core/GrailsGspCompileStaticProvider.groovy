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

import grails.util.BuildSettings

/**
 * Publishes the {@code grails { compileStatic { gsp } }} opt-in to a forked JVM as the
 * {@link BuildSettings#COMPILE_STATIC_GSP} system property.
 *
 * <p>Applied to the JVM that compiles pages ahead of time and to the JVM that runs the application,
 * which compiles a page again when it changes. Both read the setting under the name it carries in
 * configuration, so a page compiles the same way whichever compiled it.</p>
 *
 * <p>The lazy {@link GrailsCompileStaticOptions} property is read in {@link #asArguments} (when the
 * task runs, not when it is configured). The effective value is also exposed as an {@link Input}
 * getter so that toggling the flag invalidates the task.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsGspCompileStaticProvider implements CommandLineArgumentProvider {

    private final GrailsCompileStaticOptions compileStatic

    GrailsGspCompileStaticProvider(GrailsCompileStaticOptions compileStatic) {
        this.compileStatic = compileStatic
    }

    @Input
    boolean isCompileStaticGsp() {
        compileStatic.gsp.getOrElse(false)
    }

    /**
     * Strictness only reaches the compiler where pages are compiled statically, so it is reported as
     * false while that is off. Reporting the raw flag would make toggling it invalidate the task
     * without changing anything it produces.
     */
    @Input
    boolean isStrict() {
        isCompileStaticGsp() && compileStatic.strictGsp.getOrElse(false)
    }

    @Override
    Iterable<String> asArguments() {
        if (!isCompileStaticGsp()) {
            // Strictness says how a page is read where it is compiled statically, so on its own it
            // has nothing to say.
            return []
        }
        List<String> args = ["-D${BuildSettings.COMPILE_STATIC_GSP}=true".toString()]
        if (isStrict()) {
            args.add("-D${BuildSettings.COMPILE_STATIC_GSP_STRICT}=true".toString())
        }
        args
    }
}
