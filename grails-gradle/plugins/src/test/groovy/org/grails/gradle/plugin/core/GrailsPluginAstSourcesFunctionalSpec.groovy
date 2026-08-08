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
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class GrailsPluginAstSourcesFunctionalSpec extends GradleSpecification {

    def "plugin AST sources preserve user and generated Groovy compiler configuration scripts"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-ast-sources')

        when:
        BuildResult result = executeTask('compileAstGroovy', ['copyAstClasses', 'compileGroovy', 'compileAdHocGroovy', 'compileLateGroovy'])

        then:
        assertTaskSuccess('generateGrailsGroovyCompilerConfigForCompileAstGroovy', result)
        assertTaskSuccess('generateGrailsGroovyCompilerConfigForCompileGroovy', result)
        assertTaskSuccess('generateGrailsGroovyCompilerConfigForCompileAdHocGroovy', result)
        assertTaskSuccess('generateGrailsGroovyCompilerConfigForCompileLateGroovy', result)
        assertTaskSuccess('compileAstGroovy', result)
        assertTaskSuccess('copyAstClasses', result)
        assertTaskSuccess('compileGroovy', result)
        assertTaskSuccess('compileAdHocGroovy', result)
        assertTaskSuccess('compileLateGroovy', result)
        new File(runner.projectDir, 'build/classes/groovy/main/example/AstSource.class').isFile()
        new File(runner.projectDir, 'build/classes/groovy/main/example/MainSource.class').isFile()
        new File(runner.projectDir, 'build/classes/groovy/ad-hoc/example/AdHocSource.class').isFile()
        new File(runner.projectDir, 'build/classes/groovy/late/example/LateSource.class').isFile()
        assertCombinedScript(runner, 'compileAstGroovy')
        assertCombinedScript(runner, 'compileGroovy')
        assertCombinedScript(runner, 'compileAdHocGroovy')
        assertCombinedScript(runner, 'compileLateGroovy')

        when:
        result = executeTask('compileAstGroovy', ['copyAstClasses', 'compileGroovy', 'compileAdHocGroovy', 'compileLateGroovy'])

        then:
        assertTaskOutcome('generateGrailsGroovyCompilerConfigForCompileAstGroovy', TaskOutcome.UP_TO_DATE, result)
        assertTaskOutcome('compileAstGroovy', TaskOutcome.UP_TO_DATE, result)
    }

    def "plugin AST sources restore compiler configuration generation and compilation from the build cache"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-ast-sources')

        when:
        executeTask('compileAstGroovy', ['--build-cache'])
        new File(runner.projectDir, 'build').deleteDir()
        BuildResult result = executeTask('compileAstGroovy', ['--build-cache'])

        then:
        assertTaskOutcome('generateGrailsGroovyCompilerConfigForCompileAstGroovy', TaskOutcome.FROM_CACHE, result)
        assertTaskOutcome('compileAstGroovy', TaskOutcome.FROM_CACHE, result)
    }

    private static void assertCombinedScript(GradleRunner runner, String compileTaskName) {
        File generatedScript = new File(runner.projectDir, "build/generated/grails-groovy-compiler/${compileTaskName}.groovy")
        assert generatedScript.isFile()
        assert generatedScript.text.contains("star 'java.time'")
        assert generatedScript.text.contains("star 'java.util.concurrent.atomic'")
        assert generatedScript.text.contains('projectVersion')
        assert generatedScript.text.contains('projectName')
        assert generatedScript.text.contains('isPlugin')
    }

    private static void assertTaskOutcome(String taskName, TaskOutcome expectedOutcome, BuildResult result) {
        def task = result.tasks.find { it.path.endsWith(":${taskName}") }
        assert task != null : "Task '${taskName}' not found in build result"
        assert task.outcome == expectedOutcome : "Task '${taskName}' outcome was ${task.outcome}"
    }
}
