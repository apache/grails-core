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

import grails.util.BuildSettings
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for {@link GrailsCompileStaticArtefactsProvider}, verifying that the
 * {@code grails { compileStaticControllers / compileStaticServices }} opt-ins are translated into the
 * expected {@code -D} system properties for the Groovy compiler worker JVM.
 *
 * @since 8.0
 */
class GrailsCompileStaticArtefactsProviderSpec extends Specification {

    private static GrailsExtension extension() {
        Project project = ProjectBuilder.builder().build()
        new GrailsExtension(project)
    }

    void 'no arguments are emitted when both opt-ins are disabled (the default)'() {
        expect:
        new GrailsCompileStaticArtefactsProvider(extension()).asArguments().toList() == []
    }

    void 'the controllers opt-in is published as a system property'() {
        given:
        GrailsExtension extension = extension()
        extension.compileStaticControllers = true

        expect:
        new GrailsCompileStaticArtefactsProvider(extension).asArguments().toList() ==
                ["-D${BuildSettings.COMPILE_STATIC_CONTROLLERS}=true".toString()]
    }

    void 'the services opt-in is published as a system property'() {
        given:
        GrailsExtension extension = extension()
        extension.compileStaticServices = true

        expect:
        new GrailsCompileStaticArtefactsProvider(extension).asArguments().toList() ==
                ["-D${BuildSettings.COMPILE_STATIC_SERVICES}=true".toString()]
    }

    void 'the taglibs opt-in is published as a system property'() {
        given:
        GrailsExtension extension = extension()
        extension.compileStaticTagLibs = true

        expect:
        new GrailsCompileStaticArtefactsProvider(extension).asArguments().toList() ==
                ["-D${BuildSettings.COMPILE_STATIC_TAGLIBS}=true".toString()]
    }

    void 'all opt-ins are published when enabled'() {
        given:
        GrailsExtension extension = extension()
        extension.compileStaticControllers = true
        extension.compileStaticServices = true
        extension.compileStaticTagLibs = true

        expect:
        new GrailsCompileStaticArtefactsProvider(extension).asArguments().toList() == [
                "-D${BuildSettings.COMPILE_STATIC_CONTROLLERS}=true".toString(),
                "-D${BuildSettings.COMPILE_STATIC_SERVICES}=true".toString(),
                "-D${BuildSettings.COMPILE_STATIC_TAGLIBS}=true".toString()
        ]
    }

    void 'the compileStaticArtefacts shortcut publishes all three opt-ins'() {
        given:
        GrailsExtension extension = extension()
        extension.compileStaticArtefacts = true

        expect:
        new GrailsCompileStaticArtefactsProvider(extension).asArguments().toList() == [
                "-D${BuildSettings.COMPILE_STATIC_CONTROLLERS}=true".toString(),
                "-D${BuildSettings.COMPILE_STATIC_SERVICES}=true".toString(),
                "-D${BuildSettings.COMPILE_STATIC_TAGLIBS}=true".toString()
        ]
    }

    void 'the compileStaticArtefacts shortcut combines with an individual opt-in without duplicates'() {
        given:
        GrailsExtension extension = extension()
        extension.compileStaticArtefacts = true
        extension.compileStaticControllers = true

        expect:
        new GrailsCompileStaticArtefactsProvider(extension).asArguments().toList() == [
                "-D${BuildSettings.COMPILE_STATIC_CONTROLLERS}=true".toString(),
                "-D${BuildSettings.COMPILE_STATIC_SERVICES}=true".toString(),
                "-D${BuildSettings.COMPILE_STATIC_TAGLIBS}=true".toString()
        ]
    }

    void 'the provider reads the extension lazily so it reflects values set after construction'() {
        given:
        GrailsExtension extension = extension()
        GrailsCompileStaticArtefactsProvider provider = new GrailsCompileStaticArtefactsProvider(extension)

        expect: 'nothing yet'
        provider.asArguments().toList() == []

        when: 'the opt-in is enabled after the provider was created'
        extension.compileStaticControllers = true

        then: 'the provider now publishes it'
        provider.asArguments().toList() == ["-D${BuildSettings.COMPILE_STATIC_CONTROLLERS}=true".toString()]
    }
}
