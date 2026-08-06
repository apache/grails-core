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
package org.grails.gradle.plugin.views.gsp

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Functional tests for the classpath {@link GroovyPagePlugin} assembles for GSP compilation.
 *
 * <p>Uses Gradle TestKit to apply {@code org.apache.grails.gradle.grails-gsp} to a project and
 * assert what the {@code compileGroovyPages} and {@code compileWebappGroovyPages} tasks compile
 * against. The plugin no longer registers a {@code gspCompile} configuration: it was introduced
 * as the classpath for the original Ant-based GSP compiler, and once compilation moved to a
 * forked task its only remaining content was a hardcoded servlet API dependency, which the
 * compile classpath already supplies transitively.</p>
 *
 * @since 8.0
 */
class GroovyPagePluginFunctionalSpec extends GradleSpecification {

    def "plugin does not register a gspCompile configuration"() {
        given:
        setupTestResourceProject('gsp-compile-classpath')

        when:
        def result = executeTask('inspectGspCompileClasspath')

        then:
        result.output.contains('HAS_GSP_COMPILE_CONFIGURATION=false')
    }

    def "GSP compile tasks still resolve the compile classpath, provided dependencies and compiled classes"() {
        given:
        setupTestResourceProject('gsp-compile-classpath')

        when:
        def result = executeTask('inspectGspCompileClasspath')

        then: 'compileGroovyPages sees everything it needs to compile a GSP'
        result.output.contains('MAIN_HAS_COMPILE_CLASSPATH=true')
        result.output.contains('MAIN_HAS_PROVIDED_COMPILE=true')
        result.output.contains('MAIN_HAS_CLASSES_DIR=true')

        and: 'compileWebappGroovyPages resolves the same classpath'
        result.output.contains('WEBAPP_HAS_COMPILE_CLASSPATH=true')
        result.output.contains('WEBAPP_HAS_PROVIDED_COMPILE=true')
        result.output.contains('WEBAPP_HAS_CLASSES_DIR=true')
    }
}
