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
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class GroovydocEnhancerPluginSpec extends Specification {

    @TempDir
    File projectDir

    void 'groovydoc classpath includes runtime-only jars that Class.forName needs'() {
        given: 'a groovy project whose runtime-only jar is not on the compile classpath'
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.extensions.extraProperties.set('javaVersion', 21)
        project.pluginManager.apply('groovy')
        project.pluginManager.apply(GroovydocEnhancerPlugin)

        File compileOnlyJar = new File(projectDir, 'compile-only.jar')
        File runtimeOnlyJar = new File(projectDir, 'runtime-only.jar')
        compileOnlyJar.bytes = [] as byte[]
        runtimeOnlyJar.bytes = [] as byte[]
        project.dependencies.add('compileOnly', project.files(compileOnlyJar))
        project.dependencies.add('runtimeOnly', project.files(runtimeOnlyJar))

        when: 'the groovydoc task classpath is resolved'
        Groovydoc groovydoc = project.tasks.named('groovydoc', Groovydoc).get()
        Set<File> groovydocFiles = groovydoc.classpath.files

        then: 'runtime-only jars are visible to groovydoc alongside compile-only jars'
        groovydocFiles.any { it.name == runtimeOnlyJar.name }
        groovydocFiles.any { it.name == compileOnlyJar.name }
    }
}
