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

import org.gradle.testkit.runner.TaskOutcome

/**
 * Covers the lifecycle guarantees of the Grails Groovy compiler configuration script wiring.
 *
 * <p>The combined script is produced by a generator task rather than by a {@code doFirst} on the
 * compile task, because Gradle finalizes task properties before task actions run. These tests pin
 * the three ordering properties that arrangement has to preserve.</p>
 *
 * @see GrailsGradlePlugin#getGroovyCompilerScript
 */
class GrailsGroovyCompilerConfigSpec extends GradleSpecification {

    def "a GroovyCompile registered after the project is evaluated still gets a generator"() {
        given: 'a source set registered from projectsEvaluated, after every afterEvaluate callback'
        setupTestResourceProject('compiler-config-late-source-set')

        when:
        def result = executeTask('inspectLate')

        then: 'the late compile task is wired, not only the ones present during evaluation'
        result.output.contains('LATE_GENERATOR_EXISTS=true')
        result.output.contains('MAIN_GENERATOR_EXISTS=true')
    }

    def "the generator is decoupled from the compile and runtime classpaths"() {
        given: 'an application with both an implementation and a runtimeOnly project dependency'
        setupTestResourceProject('compiler-config-classpath-decoupling')

        when:
        def result = executeTask(':app:inspectDecoupling')

        then: 'the script is built from configuration state, so no classpath enters the chain'
        result.output.contains('GENERATOR_DEPENDS_ON_COMPILE_CLASSPATH=false')
        result.output.contains('GENERATOR_DEPENDS_ON_RUNTIME_CLASSPATH=false')
        result.output.contains('GENERATOR_DECLARED_INPUT_FILES=0')

        and: 'its content is a declared input, so it is up-to-date checked rather than untracked'
        result.output.contains('GENERATOR_HAS_CONTENT_INPUT=true')
    }

    def "a configurationScript assigned from a later callback is folded in, not clobbered"() {
        given: 'a user assigning configurationScript from projectsEvaluated'
        setupTestResourceProject('compiler-config-late-assignment')

        when:
        def result = executeTask('inspectAssign')

        then: 'the combined script wins and carries the user script content'
        result.output.contains('FINAL_CONFIG_SCRIPT=grailsGroovyCompilerConfig-compileGroovy.groovy')
        result.output.contains('COMBINED_EXISTS=true')
        result.output.contains('COMBINED_CONTAINS_USER_IMPORT=true')
    }

    def "a GroovyCompile that no source set owns is wired like the source set ones"() {
        given: 'a GroovyCompile registered directly, with a configurationScript of its own'
        setupTestResourceProject('compiler-config-standalone-task')

        when:
        def result = executeTask('inspectStandalone')

        then: 'it compiles with the combined script, which carries the Grails imports and its own'
        result.output.contains('FINAL_CONFIG_SCRIPT=grailsGroovyCompilerConfig-compileCustomGroovy.groovy')
        result.output.contains('COMBINED_CONTAINS_GRAILS_IMPORT=true')
        result.output.contains('COMBINED_CONTAINS_USER_IMPORT=true')

        when: 'the compile task is scheduled with the configuration cache'
        def stored = executeTask('compileCustomGroovy', ['--configuration-cache'])
        def reused = executeTask('compileCustomGroovy', ['--configuration-cache'])

        then: 'its generator, registered while the graph is built, is part of the cached graph'
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')
        reused.task(':generateCompileCustomGroovyGrailsCompilerConfig')?.outcome == TaskOutcome.UP_TO_DATE
    }

    def "a configurationScript that does not exist fails the build instead of being dropped"() {
        given: 'a build whose configurationScript points at a file that is not there'
        def runner = setupTestResourceProject('compiler-config-missing-user-script')

        when:
        def result = runner.withArguments('compileGroovy', '--stacktrace').buildAndFail()

        then: 'the failure names the missing file rather than compiling without it'
        result.output.contains("property 'configurationScript' specifies file")
        result.output.contains("missing-config.groovy' which doesn't exist")
    }

    def "a plugin project declares the version and name it bakes into compiled classes as inputs"() {
        given: 'a Grails plugin project, whose script stamps projectVersion/projectName AST metadata'
        setupTestResourceProject('compiler-config-plugin-inputs')

        when:
        def result = executeTask('inspectPluginInputs')

        then: 'both are inputs of the compile task, so changing either recompiles'
        result.output.contains('HAS_VERSION_INPUT=true')
        result.output.contains('HAS_NAME_INPUT=true')
    }

    def "the generator waits for a task that produces the build's own config script"() {
        given: 'a build whose configurationScript is the output of another task'
        setupTestResourceProject('compiler-config-generated-user-script')

        when: 'the compile task graph is built'
        def result = executeTask('compileGroovy', ['--dry-run'])
        def order = result.output.readLines().findAll { it.startsWith(':') }
        int producer = order.findIndexOf { it.startsWith(':generateUserConfigScript') }
        int generator = order.findIndexOf { it.startsWith(':generateCompileGroovyGrailsCompilerConfig') }

        then: 'the producer is scheduled first, so its content is present when the script is combined'
        producer >= 0
        generator >= 0
        producer < generator
    }

    def "one generator per source set, tracked so only a real change regenerates"() {
        given: 'a project whose star imports come from a build property'
        setupTestResourceProject('compiler-config-incremental')

        when: 'the script is generated, generated again unchanged, then with different imports'
        def sourceSets = executeTask('inspectSourceSets')
        def first = executeTask('generateCompileGroovyGrailsCompilerConfig')
        def unchanged = executeTask('generateCompileGroovyGrailsCompilerConfig')
        def changed = executeTask('generateCompileGroovyGrailsCompilerConfig', ['-PextraImport=com.example.foo'])
        def outcome = { result ->
            result.tasks.find { it.path.endsWith(':generateCompileGroovyGrailsCompilerConfig') }?.outcome
        }

        then: 'every source set that compiles Groovy has its own generator'
        sourceSets.output.contains('generateCompileGroovyGrailsCompilerConfig')
        sourceSets.output.contains('generateCompileTestGroovyGrailsCompilerConfig')

        and: 'the script is a tracked input rather than regenerated unconditionally'
        outcome(first) == TaskOutcome.SUCCESS
        outcome(unchanged) == TaskOutcome.UP_TO_DATE
        outcome(changed) == TaskOutcome.SUCCESS
    }

    def "the wiring works with the configuration cache"() {
        given:
        setupTestResourceProject('compiler-config-incremental')

        when:
        def first = executeTask('generateCompileGroovyGrailsCompilerConfig', ['--configuration-cache'])
        def second = executeTask('generateCompileGroovyGrailsCompilerConfig', ['--configuration-cache'])

        then: 'the entry is stored and then reused, so nothing here defeats the cache'
        first.output.contains('Configuration cache entry stored')
        second.output.contains('Configuration cache entry reused')
    }

    def "opting in imports the Grails annotation packages and the build's own, and nothing else"() {
        given: 'a project opting in to the common annotations and adding one of its own'
        setupTestResourceProject('compiler-config-star-imports')

        when:
        def result = executeTask('inspectImports')

        then: 'the packages are imported whether or not they are on the classpath'
        result.output.contains("STAR_IMPORTS=[com.example.custom, grails.gorm.annotation, " +
                'grails.plugin.scaffolding.annotation, jakarta.validation.constraints]')

        and: 'java.time is not among them - Groovy 5 imports it by default'
        !result.output.contains("star 'java.time'")
    }
}
