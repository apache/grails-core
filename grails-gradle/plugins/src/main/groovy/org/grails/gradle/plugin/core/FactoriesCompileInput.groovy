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

import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.compile.GroovyCompile

/**
 * Declares the hand-authored factories files the Grails compiler merges into the ones it generates as
 * inputs of a Groovy compilation. A resource does not invalidate a compilation on its own, so a change
 * to one of these files left the generated file as it was until a source changed as well. As an input
 * that is not a source, a change to one recompiles in full, which writes the generated file again.
 *
 * @since 8.0.1
 */
@CompileStatic
class FactoriesCompileInput {

    /** Merged into the generated {@code META-INF/grails.factories} */
    static final String GRAILS_FACTORIES_PATH = 'src/main/resources/META-INF/grails.factories'

    /** Merged into the generated {@code META-INF/grails-cli.factories} */
    static final List<String> CLI_FACTORIES_PATHS = [
            'src/main/resources/META-INF/grails-cli.factories',
            'src/cli/resources/META-INF/grails-cli.factories',
    ].asImmutable()

    private static final String GRAILS_FACTORIES_REGISTERED = 'grailsFactoriesInputRegistered'
    private static final String CLI_FACTORIES_REGISTERED = 'grailsCliFactoriesInputRegistered'

    /**
     * For a compilation of project sources, where artefact handlers and trait injectors are registered.
     */
    static void registerGrailsFactories(Project project, GroovyCompile task) {
        if (task.extensions.extraProperties.has(GRAILS_FACTORIES_REGISTERED)) {
            return
        }
        task.extensions.extraProperties.set(GRAILS_FACTORIES_REGISTERED, true)
        task.inputs.files(project.layout.projectDirectory.file(GRAILS_FACTORIES_PATH))
                .withPropertyName('grailsFactories')
                .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    /**
     * For a compilation that can register commands: project sources, and the sources of a cli tier.
     */
    static void registerCliFactories(Project project, GroovyCompile task) {
        if (task.extensions.extraProperties.has(CLI_FACTORIES_REGISTERED)) {
            return
        }
        task.extensions.extraProperties.set(CLI_FACTORIES_REGISTERED, true)
        task.inputs.files(CLI_FACTORIES_PATHS.collect { String path -> project.layout.projectDirectory.file(path) })
                .withPropertyName('grailsCliFactories')
                .withPathSensitivity(PathSensitivity.RELATIVE)
    }

}
