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
 * What the tag library index is wired into, and what a project declaring no tag libraries has to do
 * about it, which is nothing: the generator runs in a forked process against the project's own compile
 * classpath, so a project with no tag libraries to describe must not fork it at all.
 *
 * @since 8.0
 */
class TagLibraryIndexWiringFunctionalSpec extends GradleSpecification {

    def "the index written before compilation is used only to compile this project"() {
        given: 'it is read from source, so it cannot describe a tag library it cannot resolve yet'
        setupTestResourceProject('taglib-index-wiring')

        when:
        def result = executeTask('inspectTagLibraryIndexWiring')

        then: 'this project resolves a call to a tag it declares as it compiles'
        result.output.contains('COMPILE_SEES_PRE_INDEX=true')

        and: 'and a partial description reaches neither a page nor a project depending on this one'
        result.output.contains('PAGES_SEE_PRE_INDEX=false')
        result.output.contains('PRE_INDEX_IS_A_RESOURCE=false')
    }

    def "the index written after compilation is the one packaged and compiled against"() {
        given: 'by then every tag library resolves, whatever language its collaborators were written in'
        setupTestResourceProject('taglib-index-wiring')

        when:
        def result = executeTask('inspectTagLibraryIndexWiring')

        then:
        result.output.contains('PAGES_SEE_PACKAGED=true')
        result.output.contains('RUNTIME_SEES_PACKAGED=true')

        and: 'it waits for everything that writes the class output, not just the compile tasks'
        result.output.contains('PACKAGED_WAITS_FOR_CLASSES=true')
    }

    def "a test resolves tags against the same index as the application"() {
        given: 'a test source set builds its runtime classpath from the main output, not from the ' +
                'main runtime classpath, so it does not inherit the index by itself'
        setupTestResourceProject('taglib-index-wiring')

        when:
        def result = executeTask('inspectTagLibraryIndexWiring')

        then: 'otherwise a page rendered by a test would resolve against an index missing the ' +
                'application own tag libraries, which is where a problem would most likely be seen'
        result.output.contains('TEST_RUNTIME_SEES_PACKAGED=true')
    }

    def "the build stores and reuses a configuration cache entry"() {
        given: 'the index tasks read the grails extension through providers, which is only sound if ' +
                'those values are resolved when the entry is stored rather than at execution'
        setupTestResourceProject('taglib-index-wiring')

        when: 'stored'
        def stored = executeTask('classes', ['--configuration-cache'])

        then:
        assertTaskSuccess('generateTagLibraryIndex', stored)

        when: 'and reused, which is what fails if a Project was captured and serialised'
        def reused = executeTask('classes', ['--configuration-cache'])

        then:
        reused.output.contains('Reusing configuration cache')
    }

    def "a project with no tag libraries does not fork the generator"() {
        given: 'the generator is only on the compile classpath of a project that has tag libraries'
        setupTestResourceProject('taglib-index-wiring')

        when: 'both index tasks run; forking either would fail for want of the generator'
        def result = executeTask('classes')

        then:
        assertTaskSuccess('generateTagLibraryIndex', result)
    }

    def "the settings the build declares are not packaged"() {
        given: 'they say how this project compiles, so a project depending on it must not inherit them'
        def runner = setupTestResourceProject('taglib-index-wiring')

        when:
        def result = executeTask('jar')

        then:
        assertTaskSuccess('jar', result)

        and:
        File jar = new File(runner.projectDir, 'build/libs').listFiles().find { it.name.endsWith('.jar') }
        new java.util.zip.ZipFile(jar).withCloseable { zip ->
            zip.getEntry('META-INF/grails/taglibs/compile-settings.properties') == null
        }
    }

    def "the whole build wires together without a dependency cycle"() {
        given: 'the packaged index waits for classes, and nothing that classes waits for waits for it'
        setupTestResourceProject('taglib-index-wiring')

        when:
        def result = executeTask('build')

        then:
        assertTaskSuccess('packageTagLibraryIndex', result)
    }
}
