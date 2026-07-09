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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for {@link GrailsCompileStaticOptions}, the nested {@code grails { compileStatic { } }}
 * configuration object. Verifies the lazy {@code Property<Boolean>} flags default to {@code false} and
 * support both the {@code property = value} DSL convenience and the {@code property.set(...)} API.
 *
 * @since 8.0
 */
class GrailsCompileStaticOptionsSpec extends Specification {

    private static GrailsCompileStaticOptions options() {
        Project project = ProjectBuilder.builder().build()
        project.objects.newInstance(GrailsCompileStaticOptions)
    }

    void 'all, controllers, services and tagLibs default to false'() {
        when:
        GrailsCompileStaticOptions compileStatic = options()

        then:
        !compileStatic.all.get()
        !compileStatic.controllers.get()
        !compileStatic.services.get()
        !compileStatic.tagLibs.get()
    }

    void 'each flag is an independent lazy property'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()

        when:
        compileStatic.controllers.set(true)

        then:
        compileStatic.controllers.get()
        !compileStatic.all.get()
        !compileStatic.services.get()
        !compileStatic.tagLibs.get()
    }

    void 'the property = value DSL convenience assigns the lazy property'() {
        given:
        GrailsCompileStaticOptions compileStatic = options()

        when:
        compileStatic.with {
            all = true
            controllers = true
            services = true
            tagLibs = true
        }

        then:
        compileStatic.all.get()
        compileStatic.controllers.get()
        compileStatic.services.get()
        compileStatic.tagLibs.get()
    }
}
