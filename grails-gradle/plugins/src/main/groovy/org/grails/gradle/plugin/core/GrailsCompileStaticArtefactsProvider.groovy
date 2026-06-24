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
 * Publishes the {@code grails { compileStaticControllers }} / {@code grails { compileStaticServices }} /
 * {@code grails { compileStaticTagLibs }} opt-ins (see {@link BuildSettings#COMPILE_STATIC_CONTROLLERS},
 * {@link BuildSettings#COMPILE_STATIC_SERVICES} and {@link BuildSettings#COMPILE_STATIC_TAGLIBS}) to the
 * Groovy compiler's worker JVM as system properties so the {@code CompileStaticArtefactInjector} AST
 * transform can stamp {@code @GrailsCompileStatic} onto the matching artefacts. The
 * {@code grails { compileStaticArtefacts }} shortcut is folded into all three.
 *
 * <p>The flags are exposed as {@link Input} so toggling them invalidates the compile task and
 * triggers recompilation.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsCompileStaticArtefactsProvider implements CommandLineArgumentProvider {

    private final GrailsExtension grails

    GrailsCompileStaticArtefactsProvider(GrailsExtension grails) {
        this.grails = grails
    }

    // The effective values fold in the compileStaticArtefacts shortcut so that toggling it both emits the
    // flags and invalidates the compile task (it is the @Input getters that Gradle snapshots).

    @Input
    boolean isCompileStaticControllers() {
        grails.compileStaticArtefacts || grails.compileStaticControllers
    }

    @Input
    boolean isCompileStaticServices() {
        grails.compileStaticArtefacts || grails.compileStaticServices
    }

    @Input
    boolean isCompileStaticTagLibs() {
        grails.compileStaticArtefacts || grails.compileStaticTagLibs
    }

    @Override
    Iterable<String> asArguments() {
        List<String> args = []
        if (compileStaticControllers) {
            args.add("-D${BuildSettings.COMPILE_STATIC_CONTROLLERS}=true".toString())
        }
        if (compileStaticServices) {
            args.add("-D${BuildSettings.COMPILE_STATIC_SERVICES}=true".toString())
        }
        if (compileStaticTagLibs) {
            args.add("-D${BuildSettings.COMPILE_STATIC_TAGLIBS}=true".toString())
        }
        args
    }
}
