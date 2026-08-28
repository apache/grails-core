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

import spock.lang.Specification

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import grails.util.BuildSettings

/**
 * @since 8.0
 */
class GrailsGspCompileStaticProviderSpec extends Specification {

    private static GrailsCompileStaticOptions options() {
        Project project = ProjectBuilder.builder().build()
        project.objects.newInstance(GrailsCompileStaticOptions)
    }

    void 'nothing is published while the page opt-in is off, which is the default'() {
        expect:
        new GrailsGspCompileStaticProvider(options()).asArguments().toList() == []
    }

    void 'the page opt-in is published under the name the setting carries in configuration'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()
        compileStatic.gsp.set(true)

        expect:
        new GrailsGspCompileStaticProvider(compileStatic).asArguments().toList() == [
                '-Dgrails.views.gsp.compileStatic=true'
        ]

        and: 'which is the setting, not a name of its own'
        BuildSettings.COMPILE_STATIC_GSP == 'grails.views.gsp.compileStatic'
    }

    void 'the all shortcut does not turn pages on'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()
        compileStatic.all.set(true)

        expect:
        new GrailsGspCompileStaticProvider(compileStatic).asArguments().toList() == []
    }

    void 'pages can be turned on alongside the all shortcut'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()
        compileStatic.with {
            all = true
            gsp = true
        }

        expect:
        new GrailsGspCompileStaticProvider(compileStatic).asArguments().toList() == [
                "-D${BuildSettings.COMPILE_STATIC_GSP}=true".toString()
        ]
    }

    void 'the provider reads the option lazily so it reflects a value set after construction'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()
        GrailsGspCompileStaticProvider provider = new GrailsGspCompileStaticProvider(compileStatic)

        expect: 'nothing yet'
        provider.asArguments().toList() == []

        when:
        compileStatic.gsp.set(true)

        then:
        provider.asArguments().toList() == ["-D${BuildSettings.COMPILE_STATIC_GSP}=true".toString()]
    }

    void 'strictness is published alongside the page opt-in'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()
        compileStatic.with {
            gsp = true
            strictGsp = true
        }

        expect:
        new GrailsGspCompileStaticProvider(compileStatic).asArguments().toList() == [
                "-D${BuildSettings.COMPILE_STATIC_GSP}=true".toString(),
                "-D${BuildSettings.COMPILE_STATIC_GSP_STRICT}=true".toString()
        ]
    }

    void 'strictness on its own says nothing, because it says how a page compiled statically is read'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()
        compileStatic.strictGsp.set(true)

        expect:
        new GrailsGspCompileStaticProvider(compileStatic).asArguments().toList() == []
    }

    void 'the strictness input is false while pages are not compiled statically'() {
        given: 'strictness set, but nothing for it to apply to'
        GrailsCompileStaticOptions compileStatic = options()
        compileStatic.strictGsp.set(true)

        expect: 'the input reports what reaches the compiler, so toggling it here does not rerun the task'
        !new GrailsGspCompileStaticProvider(compileStatic).strict

        when: 'pages are compiled statically'
        compileStatic.gsp.set(true)

        then: 'it reports the strictness that now reaches them'
        new GrailsGspCompileStaticProvider(compileStatic).strict
    }

    void 'the page opt-in is not strict on its own'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()
        compileStatic.gsp.set(true)

        expect:
        new GrailsGspCompileStaticProvider(compileStatic).asArguments().toList() == [
                "-D${BuildSettings.COMPILE_STATIC_GSP}=true".toString()
        ]
    }

    void 'the artefact opt-ins are published separately and do not carry pages'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()
        compileStatic.gsp.set(true)

        expect: 'the artefact provider says nothing about pages'
        new GrailsCompileStaticArtefactsProvider(compileStatic).asArguments().toList() == []
    }
}
