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

    def "a task that adds to the runtime classpath after compiling does not cycle through the generator"() {
        given: 'an application whose runtime classpath carries the output of a task that runs after classes'
        setupTestResourceProject('compiler-config-runtime-classpath-producer')

        when: 'the wiring is inspected'
        def result = executeTask('inspectRuntimeProducer')

        then: 'the producer is on the runtime classpath, and the generator does not depend on it'
        result.output.contains('RUNTIME_CLASSPATH_BUILT_BY_PRODUCER=true')
        result.output.contains('GENERATOR_DEPENDS_ON_RUNTIME_PRODUCER=false')
        result.output.contains('COMBINED_CONTAINS_GRAILS_IMPORT=true')

        when: 'the compile task and that producer are scheduled together'
        def graph = executeTask('compileGroovy', ['packageRuntimeExtra', '--dry-run'])

        then: 'the graph builds without a cycle, the generator ahead of the compile and the producer after'
        def order = graph.output.readLines().findAll { it.startsWith(':') }
        order.findIndexOf { it.startsWith(':generateCompileGroovyGrailsCompilerConfig') } <
                order.findIndexOf { it.startsWith(':compileGroovy') }
        order.findIndexOf { it.startsWith(':compileGroovy') } <
                order.findIndexOf { it.startsWith(':packageRuntimeExtra') }
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

    def "a generated user script is consumed during compilation and follows changes with #wiring wiring"() {
        given: 'a compiler script wired by provider or by plain file plus a compile dependency'
        setupTestResourceProject('compiler-config-generated-user-script-execution')

        when: 'a clean build compiles and runs source that needs both sets of imports'
        def first = executeTask('clean', ['verifyCompilation'] + scriptArgs)

        then:
        first.task(':generateUserConfigScript').outcome == TaskOutcome.SUCCESS
        first.task(':generateCompileGroovyGrailsCompilerConfig').outcome == TaskOutcome.SUCCESS
        first.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        first.output.contains('CONFIGURED_TYPE=java.nio.file.Path')
        first.output.contains('GRAILS_IMPORT=1')

        when: 'nothing changes'
        def unchanged = executeTask('verifyCompilation', scriptArgs)

        then:
        unchanged.task(':generateUserConfigScript').outcome == TaskOutcome.UP_TO_DATE
        unchanged.task(':generateCompileGroovyGrailsCompilerConfig').outcome == TaskOutcome.UP_TO_DATE
        unchanged.task(':compileGroovy').outcome == TaskOutcome.UP_TO_DATE

        when: 'the producer changes the imported type'
        def changed = executeTask('verifyCompilation', ['-PimportedType=java.net.URI'] + scriptArgs)

        then: 'the new script reaches the compiler rather than stale content being reused'
        changed.task(':generateUserConfigScript').outcome == TaskOutcome.SUCCESS
        changed.task(':generateCompileGroovyGrailsCompilerConfig').outcome == TaskOutcome.SUCCESS
        changed.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        changed.output.contains('CONFIGURED_TYPE=java.net.URI')
        changed.output.contains('GRAILS_IMPORT=1')

        where:
        wiring                 | scriptArgs
        'provider'             | []
        'plain'                | ['-PplainFile']
        'plain, ad-hoc producer' | ['-PplainFile', '-PadHocProducer']
    }

    def "a plain script file keeps its #producer producer ordered before script preparation"() {
        given: 'the legacy file assignment with a separate compileGroovy dependency'
        setupTestResourceProject('compiler-config-generated-user-script-execution')

        when: 'compilation is requested normally from a clean project'
        def natural = executeTask('verifyCompilation', ['-PplainFile'] + producerArgs)

        then: 'the producer runs before the combined script is read'
        natural.task(':generateUserConfigScript').outcome == TaskOutcome.SUCCESS
        natural.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        natural.output.contains('CONFIGURED_TYPE=java.nio.file.Path')
        natural.output.contains('GRAILS_IMPORT=1')

        when: 'script preparation is explicitly requested first after cleaning the outputs'
        def forced = executeTask('clean', ['generateCompileGroovyGrailsCompilerConfig',
                'verifyCompilation', '-PplainFile'] + producerArgs)

        then: 'the producer dependency still takes precedence over the requested task order'
        forced.task(':generateUserConfigScript').outcome == TaskOutcome.SUCCESS
        forced.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        forced.output.contains('CONFIGURED_TYPE=java.nio.file.Path')
        forced.output.contains('GRAILS_IMPORT=1')

        where: 'the producer declares the script as an @OutputFile property or with outputs.file'
        producer | producerArgs
        'typed'  | []
        'ad-hoc' | ['-PadHocProducer']
    }

    def "script preparation does not inherit unrelated compile dependencies with #wiring wiring"() {
        given: 'an additional compile dependency that itself depends on script preparation'
        setupTestResourceProject('compiler-config-generated-user-script-execution')

        when:
        def result = executeTask('verifyCompilation', ['-PadditionalDependency'] + scriptArgs)

        then: 'there is no cycle and the additional task still runs before compilation'
        result.task(':prepareCompilation').outcome == TaskOutcome.SUCCESS
        result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        result.tasks*.path.indexOf(':prepareCompilation') < result.tasks*.path.indexOf(':compileGroovy')
        result.output.contains('ADDITIONAL_COMPILE_DEPENDENCY=ran')
        result.output.contains('CONFIGURED_TYPE=java.nio.file.Path')
        result.output.contains('GRAILS_IMPORT=1')

        where:
        wiring     | scriptArgs
        'provider' | []
        'plain'    | ['-PplainFile']
    }

    def "a generated user script keeps its producer with the configuration cache and #wiring wiring"() {
        given:
        setupTestResourceProject('compiler-config-generated-user-script-execution')

        when: 'a cached graph is reused after cleaning all generated outputs'
        def stored = executeTask('clean', ['verifyCompilation', '--configuration-cache'] + scriptArgs)
        def reused = executeTask('clean', ['verifyCompilation', '--configuration-cache'] + scriptArgs)

        then: 'both builds compile using a newly produced script'
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')
        stored.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        reused.task(':generateUserConfigScript').outcome == TaskOutcome.SUCCESS
        reused.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        reused.output.contains('CONFIGURED_TYPE=java.nio.file.Path')
        reused.output.contains('GRAILS_IMPORT=1')

        where:
        wiring     | scriptArgs
        'provider' | []
        'plain'    | ['-PplainFile']
    }

    def "a build-cached user script is consumed during compilation with #wiring wiring"() {
        given: 'an isolated build cache and a compiler that always executes after cleaning'
        setupTestResourceProject('compiler-config-generated-user-script-execution')
        def args = ['verifyCompilation', '--build-cache', '-PuncachedCompilation'] + scriptArgs

        when: 'the producer populates the build cache'
        def first = executeTask('clean', args)

        then:
        first.task(':generateUserConfigScript').outcome == TaskOutcome.SUCCESS
        first.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        first.output.contains('CONFIGURED_TYPE=java.nio.file.Path')

        when: 'outputs are deleted and the producer is restored from the build cache'
        def restored = executeTask('clean', args)

        then: 'compilation actually executes using the restored script'
        restored.task(':generateUserConfigScript').outcome == TaskOutcome.FROM_CACHE
        restored.task(':generateCompileGroovyGrailsCompilerConfig').outcome == TaskOutcome.SUCCESS
        restored.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        restored.output.contains('CONFIGURED_TYPE=java.nio.file.Path')
        restored.output.contains('GRAILS_IMPORT=1')

        when: 'the producer input changes'
        def changed = executeTask('clean', args + ['-PimportedType=java.net.URI'])

        then: 'the old cache entry is not reused'
        changed.task(':generateUserConfigScript').outcome == TaskOutcome.SUCCESS
        changed.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        changed.output.contains('CONFIGURED_TYPE=java.net.URI')

        when: 'the original input is requested again after cleaning'
        def original = executeTask('clean', args)

        then: 'the original cached script replaces the changed script'
        original.task(':generateUserConfigScript').outcome == TaskOutcome.FROM_CACHE
        original.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        original.output.contains('CONFIGURED_TYPE=java.nio.file.Path')
        original.output.contains('GRAILS_IMPORT=1')

        where:
        wiring     | scriptArgs
        'provider' | []
        'plain'    | ['-PplainFile']
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
