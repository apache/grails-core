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
package org.apache.grails.buildsrc

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class GrailsBomPropertyValidatorPluginSpec extends Specification {

    void "registers validateBomProperties on a java-platform project"() {
        given:
        Project project = ProjectBuilder.builder().withName('some-bom').build()

        when:
        project.pluginManager.apply('java-platform')
        project.pluginManager.apply(GrailsBomPropertyValidatorPlugin)

        then:
        project.tasks.names.contains(GrailsBomPropertyValidatorPlugin.VALIDATE_TASK_NAME)
    }

    void "registers nothing on a project that is not a BOM"() {
        given:
        Project project = ProjectBuilder.builder().withName('some-library').build()

        when:
        project.pluginManager.apply('java')
        project.pluginManager.apply(GrailsBomPropertyValidatorPlugin)

        then:
        !project.tasks.names.contains(GrailsBomPropertyValidatorPlugin.VALIDATE_TASK_NAME)
    }

    void "the dependency validator does not register the BOM validation"() {
        given:
        Project project = ProjectBuilder.builder().withName('some-bom').build()

        when:
        project.pluginManager.apply('java-platform')
        project.pluginManager.apply(GrailsDependencyValidatorPlugin)

        then:
        !project.tasks.names.contains(GrailsBomPropertyValidatorPlugin.VALIDATE_TASK_NAME)
    }
}
