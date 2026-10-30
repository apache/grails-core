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

import org.gradle.testkit.runner.BuildResult

/**
 * Functional tests for the entries of a generated factories file whose class is gone.
 *
 * <p>The Grails compiler writes a registration as it compiles the class, so it can drop one only while
 * it is compiling. When the only change is a deleted source that nothing depends on, incremental
 * compilation deletes the class file and compiles nothing, and the entry naming the class would
 * survive into what the build packages: a registration of a class that cannot be loaded.</p>
 *
 * <p>The fixture opts into incremental Groovy compilation ({@code options.incremental = true}). The
 * default compilation recompiles everything and cleans the destination directory first, so the
 * compiler writes the generated file again from the classes that remain and leaves nothing to
 * prune.</p>
 *
 * @since 8.0.1
 * @see PruneGeneratedFactories
 */
class PruneGeneratedFactoriesFunctionalSpec extends GradleSpecification {

    private static final String GENERATED = 'build/classes/groovy/main/META-INF/grails.factories'

    void 'a deleted class is dropped from the generated factories file when nothing is recompiled'() {
        given:
        File projectDir = setupTestResourceProject('factories-prune').projectDir
        executeTask('compileGroovy')

        expect: 'the compilation registered both handlers beside the hand-authored entry, and Plain under nothing'
        registered(projectDir) == ['example.Handler': ['elsewhere.JarHandler', 'example.GoneHandler', 'example.KeepHandler']]

        when:
        assert new File(projectDir, 'src/main/groovy/example/GoneHandler.groovy').delete()
        BuildResult deleted = executeTask('compileGroovy', ['--info'])

        then: 'the compiler did not run, so nothing it writes could have dropped the entry'
        deleted.output.contains('None of the classes needs to be compiled')

        and: 'the class is gone from the file, and the hand-authored entry, naming no class here, is kept'
        registered(projectDir) == ['example.Handler': ['elsewhere.JarHandler', 'example.KeepHandler']]
    }

    void 'the generated factories file is removed when its last entry is'() {
        given: 'a project whose one registration is GoneHandler, with Plain the only other source'
        File projectDir = setupTestResourceProject('factories-prune').projectDir
        assert new File(projectDir, 'src/main/resources/META-INF/grails.factories').delete()
        assert new File(projectDir, 'src/main/groovy/example/KeepHandler.groovy').delete()
        executeTask('compileGroovy')

        expect:
        registered(projectDir) == ['example.Handler': ['example.GoneHandler']]

        when:
        assert new File(projectDir, 'src/main/groovy/example/GoneHandler.groovy').delete()
        BuildResult deleted = executeTask('compileGroovy', ['--info'])

        then: 'Plain kept the compilation from having no source, which skips it and removes every output, and it compiled nothing'
        deleted.output.contains('None of the classes needs to be compiled')
        new File(projectDir, 'build/classes/groovy/main/example/Plain.class').isFile()

        and: 'so it is the prune that removed the file, rather than leave one without an entry'
        !new File(projectDir, GENERATED).exists()
    }

    /** Every registration of the generated file, by type, so a class registered under no type is seen to be */
    private static Map<String, List<String>> registered(File projectDir) {
        Properties properties = new Properties()
        new File(projectDir, GENERATED).withInputStream { properties.load(it) }
        properties.stringPropertyNames().sort().collectEntries { String type ->
            [(type): properties.getProperty(type).tokenize(',').sort()]
        }
    }
}
