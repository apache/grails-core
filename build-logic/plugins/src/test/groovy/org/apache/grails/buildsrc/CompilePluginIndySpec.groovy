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
package org.apache.grails.buildsrc

import org.gradle.api.Project
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class CompilePluginIndySpec extends Specification {

    @TempDir
    File projectDir

    private Project projectWithGroovy(Map extraProperties = [:]) {
        File asf = new File(projectDir, '.asf.yaml')
        asf.text = 'github: { labels: [grails] }\n'
        File scriptDir = new File(projectDir, 'gradle')
        scriptDir.mkdirs()
        new File(scriptDir, 'groovy-compile-configscript.groovy').text = '// test fixture\n'
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        extraProperties.each { key, value ->
            project.extensions.extraProperties.set(key as String, value)
        }
        project.pluginManager.apply('groovy')
        project.pluginManager.apply(CompilePlugin)
        project
    }

    def "absent grailsIndy leaves compiler indy unset"() {
        when:
        Project project = projectWithGroovy()
        GroovyCompile compile = project.tasks.named('compileGroovy', GroovyCompile).get()

        then:
        compile.groovyOptions.optimizationOptions.indy == null
    }

    def "grailsIndy true sets compiler indy"() {
        when:
        Project project = projectWithGroovy(grailsIndy: 'true')
        GroovyCompile compile = project.tasks.named('compileGroovy', GroovyCompile).get()

        then:
        compile.groovyOptions.optimizationOptions.indy
    }

    def "grailsIndy false sets compiler indy off"() {
        when:
        Project project = projectWithGroovy(grailsIndy: 'false')
        GroovyCompile compile = project.tasks.named('compileGroovy', GroovyCompile).get()

        then:
        !compile.groovyOptions.optimizationOptions.indy
    }
}
