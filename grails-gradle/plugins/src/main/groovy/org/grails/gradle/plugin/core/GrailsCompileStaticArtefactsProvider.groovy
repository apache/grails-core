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
 * Publishes the nested {@code grails { compileStatic { controllers / services / tagLibs } }} opt-ins
 * (see {@link BuildSettings#COMPILE_STATIC_CONTROLLERS}, {@link BuildSettings#COMPILE_STATIC_SERVICES}
 * and {@link BuildSettings#COMPILE_STATIC_TAGLIBS}) to the Groovy compiler's worker JVM as system
 * properties so the {@code CompileStaticArtefactInjector} AST transform can stamp
 * {@code @GrailsCompileStatic} onto the matching artefacts. The {@code compileStatic { all }} shortcut
 * is folded into all three.
 *
 * <p>The lazy {@link GrailsCompileStaticOptions} properties are read in {@link #asArguments} (at compile
 * time, not configuration time). The effective values are also exposed as {@link Input} getters so
 * toggling any flag invalidates the compile task and triggers recompilation.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsCompileStaticArtefactsProvider implements CommandLineArgumentProvider {

    private final GrailsCompileStaticOptions compileStatic

    GrailsCompileStaticArtefactsProvider(GrailsCompileStaticOptions compileStatic) {
        this.compileStatic = compileStatic
    }

    // The effective values fold in the compileStatic.all shortcut so that toggling it both emits the
    // flags and invalidates the compile task (it is the @Input getters that Gradle snapshots).

    @Input
    boolean isCompileStaticControllers() {
        compileStatic.all.getOrElse(false) || compileStatic.controllers.getOrElse(false)
    }

    @Input
    boolean isCompileStaticServices() {
        compileStatic.all.getOrElse(false) || compileStatic.services.getOrElse(false)
    }

    @Input
    boolean isCompileStaticTagLibs() {
        compileStatic.all.getOrElse(false) || compileStatic.tagLibs.getOrElse(false)
    }

    @Override
    Iterable<String> asArguments() {
        List<String> args = []
        if (isCompileStaticControllers()) {
            args.add("-D${BuildSettings.COMPILE_STATIC_CONTROLLERS}=true".toString())
        }
        if (isCompileStaticServices()) {
            args.add("-D${BuildSettings.COMPILE_STATIC_SERVICES}=true".toString())
        }
        if (isCompileStaticTagLibs()) {
            args.add("-D${BuildSettings.COMPILE_STATIC_TAGLIBS}=true".toString())
        }
        args
    }
}
