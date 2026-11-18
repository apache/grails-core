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
import org.gradle.testkit.runner.TaskOutcome

/**
 * Functional tests for the hand-authored factories files the Grails compiler merges into the ones it
 * generates, {@code META-INF/grails.factories} and {@code META-INF/grails-cli.factories}.
 *
 * <p>What the compiler writes depends on these files, so a change to one has to run the compiler again
 * even when no source changed, which a resource does not do on its own. It has to be a full
 * recompilation as well: an incremental one compiles nothing when no source changed, and the
 * generated file would keep what the previous version of the hand-authored file said.</p>
 *
 * @since 8.0.1
 * @see FactoriesCompileInput
 */
class FactoriesCompileInputFunctionalSpec extends GradleSpecification {

    void '#action #path recompiles the main classes in full'() {
        given:
        File projectDir = setupTestResourceProject('factories-compile-input').projectDir
        executeTask('compileGroovy')

        when: 'nothing changed'
        BuildResult unchanged = executeTask('compileGroovy')

        then:
        unchanged.task(':compileGroovy').outcome == TaskOutcome.UP_TO_DATE

        when:
        change.call(new File(projectDir, path))
        BuildResult changed = executeTask('compileGroovy', ['--info'])

        then:
        changed.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        changed.output.contains(fullRebuild(':compileGroovy'))

        where:
        action     | path                                               | change
        'editing'  | 'src/main/resources/META-INF/grails.factories'     | { File file -> file.text = 'grails.core.ArtefactHandler=example.OtherArtefactHandler\n' }
        'deleting' | 'src/main/resources/META-INF/grails.factories'     | { File file -> assert file.delete() }
        'adding'   | 'src/main/resources/META-INF/grails-cli.factories' | { File file -> file.text = 'org.apache.grails.core.cli.ApplicationCommand=example.Command\n' }
        'adding'   | 'src/cli/resources/META-INF/grails-cli.factories'  | { File file -> writeNew(file, 'org.apache.grails.core.cli.ApplicationCommand=example.Command\n') }
    }

    void 'a change to another resource does not recompile the main classes'() {
        given:
        File projectDir = setupTestResourceProject('factories-compile-input').projectDir
        executeTask('compileGroovy')

        when:
        new File(projectDir, 'src/main/resources/application.properties').text = 'example.name=changed\n'
        BuildResult changed = executeTask('compileGroovy')

        then:
        changed.task(':compileGroovy').outcome == TaskOutcome.UP_TO_DATE
    }

    void '#action #path recompiles the cli classes in full'() {
        given:
        File projectDir = setupTestResourceProject('factories-compile-input-cli').projectDir
        executeTask('compileCliGroovy')

        when: 'nothing changed'
        BuildResult unchanged = executeTask('compileCliGroovy')

        then:
        unchanged.task(':compileCliGroovy').outcome == TaskOutcome.UP_TO_DATE

        when:
        change.call(new File(projectDir, path))
        BuildResult changed = executeTask('compileCliGroovy', ['--info'])

        then:
        changed.task(':compileCliGroovy').outcome == TaskOutcome.SUCCESS
        changed.output.contains(fullRebuild(':compileCliGroovy'))

        where:
        action     | path                                               | change
        'editing'  | 'src/cli/resources/META-INF/grails-cli.factories'  | { File file -> file.text = 'org.apache.grails.core.cli.ApplicationCommand=example.OtherCommand\n' }
        'deleting' | 'src/cli/resources/META-INF/grails-cli.factories'  | { File file -> assert file.delete() }
        'adding'   | 'src/main/resources/META-INF/grails-cli.factories' | { File file -> writeNew(file, 'org.apache.grails.core.cli.ApplicationCommand=example.Command\n') }
    }

    private static String fullRebuild(String taskPath) {
        "The input changes require a full rebuild for incremental task '${taskPath}'."
    }

    private static void writeNew(File file, String text) {
        file.parentFile.mkdirs()
        file.text = text
    }
}
