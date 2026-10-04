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

/**
 * Tests that {@link GrailsPluginGradlePlugin} declares {@code copyAstClasses} as a producer of the
 * main classes directories.
 *
 * <p>The AST classes are copied into the main classes directory. Tasks that read the main classes
 * directories without going through the {@code classes} lifecycle task, such as dependency analysis
 * tasks, must still run after the copy, otherwise Gradle reports an implicit dependency between the
 * two tasks and fails the build.</p>
 *
 * @see GrailsPluginGradlePlugin#configureAstSources
 */
class GrailsPluginGradlePluginAstClassesSpec extends GradleSpecification {

    def "main classes directories are built by copyAstClasses"() {
        given:
        setupTestResourceProject('plugin-ast-classes-dirs')

        when:
        def result = executeTask('inspectMainClassesDirs')

        then:
        def producers = (result.output =~ /MAIN_CLASSES_DIRS_BUILT_BY=(.*)/)[0][1].split(',') as List
        producers.contains('copyAstClasses')
        producers.contains('compileGroovy')
    }
}
