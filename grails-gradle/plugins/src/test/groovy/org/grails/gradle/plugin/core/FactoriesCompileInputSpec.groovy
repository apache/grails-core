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

import org.gradle.api.Project
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class FactoriesCompileInputSpec extends Specification {

    @TempDir
    File projectDir

    void 'the hand-authored factories files are compiler inputs, each registered once'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        GroovyCompile compileGroovy = project.tasks.register('compileGroovy', GroovyCompile).get()
        List<File> factoriesFiles = writeFactoriesFiles([FactoriesCompileInput.GRAILS_FACTORIES_PATH] + FactoriesCompileInput.CLI_FACTORIES_PATHS)

        when:
        2.times {
            FactoriesCompileInput.registerGrailsFactories(project, compileGroovy)
            FactoriesCompileInput.registerCliFactories(project, compileGroovy)
        }

        then:
        compileGroovy.inputs.files.files*.canonicalFile.findAll { it in factoriesFiles }.sort() == factoriesFiles.sort()
    }

    void 'a compilation that only registers commands is given only the files merged into grails-cli.factories'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        GroovyCompile compileCliGroovy = project.tasks.register('compileCliGroovy', GroovyCompile).get()
        List<File> cliFactoriesFiles = writeFactoriesFiles(FactoriesCompileInput.CLI_FACTORIES_PATHS)
        File grailsFactoriesFile = writeFactoriesFiles([FactoriesCompileInput.GRAILS_FACTORIES_PATH]).first()

        when:
        FactoriesCompileInput.registerCliFactories(project, compileCliGroovy)

        then:
        List<File> inputs = compileCliGroovy.inputs.files.files*.canonicalFile
        inputs.containsAll(cliFactoriesFiles)
        !inputs.contains(grailsFactoriesFile)
    }

    private List<File> writeFactoriesFiles(List<String> paths) {
        paths.collect { String path ->
            File file = new File(projectDir, path)
            file.parentFile.mkdirs()
            file.text = 'example.Type=example.Implementation\n'
            file.canonicalFile
        }
    }

}
