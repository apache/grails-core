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

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A package of a module is in one of its jars only: the runtime jar, {@code -cli} or
 * {@code -cli-standalone}.
 */
class CompanionPackagesTaskSpec extends Specification {

    @TempDir
    File tmp

    def "jars that each have packages of their own pass, and the report lists the packages of each"() {
        given:
        CompanionPackagesTask task = task([
                'my-plugin-1.0.jar'               : ['org/example/MyPlugin.class', 'org/example/web/Page.class', 'META-INF/MANIFEST.MF'],
                'my-plugin-cli-1.0.jar'           : ['org/example/cli/ReportTool.class', 'META-INF/grails-cli.factories'],
                'my-plugin-cli-standalone-1.0.jar': ['org/example/cli/standalone/CopyTool.class', 'META-INF/templates/org/example/Copy.groovy'],
        ])

        when:
        task.check()

        then:
        task.report.get().asFile.text == 'my-plugin-1.0.jar: org.example org.example.web\n' +
                'my-plugin-cli-1.0.jar: org.example.cli\n' +
                'my-plugin-cli-standalone-1.0.jar: org.example.cli.standalone\n'
    }

    @Unroll
    def "a package in #where fails the build, naming the package and the jars"() {
        given:
        CompanionPackagesTask task = task([
                'my-plugin-1.0.jar'               : ['org/example/MyPlugin.class'],
                'my-plugin-cli-1.0.jar'           : ["${cli}/ReportTool.class".toString()],
                'my-plugin-cli-standalone-1.0.jar': ["${standalone}/CopyTool.class".toString()],
        ])

        when:
        task.check()

        then:
        GradleException e = thrown()
        e.message.startsWith("A package of :my-plugin is in two of its jars: ${split}. A package is in one jar only")

        where:
        where                         | cli                   | standalone            || split
        'both companions'             | 'org/example/cli'     | 'org/example/cli'     || 'org.example.cli is in my-plugin-cli-1.0.jar and in my-plugin-cli-standalone-1.0.jar'
        'a companion and the plugin'  | 'org/example'         | 'org/example/files'   || 'org.example is in my-plugin-1.0.jar and in my-plugin-cli-1.0.jar'
        'the standalone companion and the plugin' | 'org/example/cli' | 'org/example' || 'org.example is in my-plugin-1.0.jar and in my-plugin-cli-standalone-1.0.jar'
    }

    def "what is not a class of a package does not count: resources, META-INF, the unnamed package, and a jar that was not built"() {
        given:
        CompanionPackagesTask task = task([
                'my-plugin-1.0.jar'    : ['org/example/MyPlugin.class', 'META-INF/templates/Copy.class', 'module-info.class', 'Loose.class',
                                          'org/example/cli/template.txt'],
                'my-plugin-cli-1.0.jar': ['org/example/cli/ReportTool.class', 'META-INF/versions/21/org/example/cli/ReportTool.class',
                                          'META-INF/templates/Copy.class', 'module-info.class', 'Loose.class'],
        ])
        task.jars.from(new File(tmp, 'my-plugin-cli-standalone-1.0.jar'))

        when:
        task.check()

        then:
        task.report.get().asFile.text == 'my-plugin-1.0.jar: org.example\nmy-plugin-cli-1.0.jar: org.example.cli\n'
    }

    def "a class of a multi-release jar is in the package of its path below the release"() {
        expect:
        CompanionPackagesTask.packages(jar('multi.jar', ['META-INF/versions/21/org/example/Tool.class', 'org/other/Tool.class'])) ==
                ['org.example', 'org.other'] as Set
    }

    private CompanionPackagesTask task(Map<String, List<String>> jars) {
        Project root = ProjectBuilder.builder().withProjectDir(new File(tmp, 'build-root')).build()
        Project project = ProjectBuilder.builder().withParent(root).withProjectDir(new File(tmp, 'build-root/my-plugin')).withName('my-plugin').build()
        CompanionPackagesTask task = project.tasks.register(CompanionPackagesTask.TASK_NAME, CompanionPackagesTask).get()
        task.projectPath.set(project.path)
        jars.each { String name, List<String> entries -> task.jars.from(jar(name, entries)) }
        task.report.set(new File(tmp, 'report/packages.txt'))
        task
    }

    private File jar(String name, List<String> entries) {
        File jar = new File(tmp, name)
        jar.withOutputStream { OutputStream output ->
            new JarOutputStream(output).withCloseable { JarOutputStream archive ->
                for (String entry : entries) {
                    archive.putNextEntry(new JarEntry(entry))
                    archive.write(1)
                    archive.closeEntry()
                }
            }
        }
        jar
    }
}
