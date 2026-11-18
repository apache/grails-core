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

@CompileStatic
class AutoConfigurationImportsCompileInput {

    static final String PATH =
            'src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'
    private static final String INPUT_REGISTERED = 'grailsAutoConfigurationImportsInputRegistered'

    static void register(Project project, GroovyCompile task) {
        if (task.extensions.extraProperties.has(INPUT_REGISTERED)) {
            return
        }
        task.extensions.extraProperties.set(INPUT_REGISTERED, true)
        task.inputs.files(project.layout.projectDirectory.file(PATH))
                .withPropertyName('grailsAutoConfigurationImports')
                .withPathSensitivity(PathSensitivity.RELATIVE)
    }

}
