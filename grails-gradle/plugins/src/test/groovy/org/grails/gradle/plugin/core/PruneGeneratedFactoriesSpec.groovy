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

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class PruneGeneratedFactoriesSpec extends Specification {

    private static final String HAND_AUTHORED = 'src/main/resources/META-INF/grails.factories'

    @TempDir
    File projectDir

    Project project
    GroovyCompile compileGroovy
    File classesDirectory
    File generated

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        classesDirectory = new File(projectDir, 'build/classes/groovy/main')
        compileGroovy = project.tasks.register('compileGroovy', GroovyCompile) { GroovyCompile task ->
            task.destinationDirectory.set(classesDirectory)
        }.get()
        generated = new File(classesDirectory, PruneGeneratedFactories.GRAILS_FACTORIES_LOCATION)
    }

    void 'an entry whose class is gone is dropped, and the others are kept'() {
        given:
        compiled('example.KeepHandler', 'example.Outer$NestedHandler', 'example.KeepInjector')
        write(generated, 'grails.core.ArtefactHandler=example.GoneHandler,example.KeepHandler,example.Outer$NestedHandler\n' +
                'grails.compiler.traits.TraitInjector=example.KeepInjector\n')

        when:
        prune()

        then:
        load(generated) == [
                'grails.core.ArtefactHandler'          : 'example.KeepHandler,example.Outer$NestedHandler',
                'grails.compiler.traits.TraitInjector': 'example.KeepInjector',
        ]
        !generated.text.contains('#Mon') && generated.readLines().first() == '#Grails Factories File'
    }

    void 'an entry the hand-authored file declares is kept although no class of this project backs it'() {
        given:
        write(new File(projectDir, HAND_AUTHORED), 'grails.core.ArtefactHandler=elsewhere.JarHandler\n')
        write(generated, 'grails.core.ArtefactHandler=elsewhere.JarHandler,example.GoneHandler\n')

        when:
        prune()

        then:
        load(generated) == ['grails.core.ArtefactHandler': 'elsewhere.JarHandler']
    }

    void 'a type left with no entries is removed, and the file once none are left'() {
        given:
        compiled('example.KeepHandler')
        write(generated, 'grails.core.ArtefactHandler=example.KeepHandler\ngrails.compiler.traits.TraitInjector=example.GoneInjector\n')

        when:
        prune()

        then:
        load(generated) == ['grails.core.ArtefactHandler': 'example.KeepHandler']

        when:
        new File(classesDirectory, 'example/KeepHandler.class').delete()
        prune()

        then:
        !generated.exists()
    }

    void 'a file with nothing to drop is not rewritten'() {
        given:
        compiled('example.KeepHandler')
        String text = 'grails.core.ArtefactHandler=example.KeepHandler\n'
        write(generated, text)

        when:
        prune()

        then:
        generated.text == text
    }

    void 'registering twice adds the action once'() {
        given:
        int before = compileGroovy.actions.size()

        when:
        2.times {
            PruneGeneratedFactories.register(project, compileGroovy, PruneGeneratedFactories.GRAILS_FACTORIES_LOCATION, [HAND_AUTHORED])
        }

        then:
        compileGroovy.actions.size() == before + 1
    }

    private void prune() {
        Action<Task> action = new PruneGeneratedFactories(PruneGeneratedFactories.GRAILS_FACTORIES_LOCATION,
                [new File(projectDir, HAND_AUTHORED)])
        action.execute(compileGroovy)
    }

    private void compiled(String... classNames) {
        classNames.each { String className ->
            write(new File(classesDirectory, "${className.replace('.', '/')}.class"), '')
        }
    }

    private static void write(File file, String text) {
        file.parentFile.mkdirs()
        file.text = text
    }

    private static Map<String, String> load(File file) {
        Properties properties = new Properties()
        file.withInputStream { properties.load(it) }
        properties as Map<String, String>
    }

}
