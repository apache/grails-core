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
package org.grails.gradle.plugin.views

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * A project that applies the JSON views or the markup views plugin builds whether or not it has a
 * {@code grails-app/views} directory: a plugin that has no views yet has none, and neither has an
 * application whose last view was deleted. Without the directory, and with a directory that has no
 * file, the compile task has nothing to do; once the directory has a view, the task compiles.
 *
 * <p>The compiler is the stand-in of {@link AbstractViewCompileEnvironmentFunctionalSpec}, which
 * records the views it finds beneath the directory it is given.</p>
 */
class ViewsDirectoryFunctionalSpec extends GradleSpecification {

    static final List<Map<String, String>> KINDS = [
            [kind: 'JSON', project: 'gson-compile-environment', task: 'compileGsonViews', destination: 'build/gson-classes/main',
             standIn: 'grails/plugin/json/view/JsonViewCompiler.class', extension: 'gson', plugin: 'org.apache.grails.gradle.grails-gson'],
            [kind: 'markup', project: 'markup-compile-environment', task: 'compileGmlViews', destination: 'build/gml-classes/main',
             standIn: 'grails/plugin/markup/view/MarkupViewCompiler.class', extension: 'gml', plugin: 'org.apache.grails.gradle.grails-markup'],
    ]

    GradleRunner runner
    File checkout

    @Unroll
    def "a project with the #of.kind views plugin and no views directory builds, and its compile task has no source"() {
        given:
        project(of)

        when: 'the task that packages the project, which depends on the compile task'
        BuildResult result = executeTask('jar', ['--configuration-cache'])

        then:
        result.task(":${of.task}").outcome == TaskOutcome.NO_SOURCE
        result.task(':jar').outcome == TaskOutcome.SUCCESS
        !new File(checkout, 'grails-app/views').exists()
        !recorded(of).exists()

        where:
        of << KINDS
    }

    @Unroll
    def "a project with the #of.kind views plugin and an empty views directory builds, and its compile task has no source"() {
        given:
        project(of)
        new File(checkout, 'grails-app/views/book').mkdirs()

        when:
        BuildResult result = executeTask('jar', ['--configuration-cache'])

        then:
        result.task(":${of.task}").outcome == TaskOutcome.NO_SOURCE
        result.task(':jar').outcome == TaskOutcome.SUCCESS
        !recorded(of).exists()

        where:
        of << KINDS
    }

    @Unroll
    def "a project with the #of.kind views plugin and a view compiles the views of its directory"() {
        given:
        project(of)
        view(of, 'index')

        when:
        BuildResult result = executeTask('jar', ['--configuration-cache'])

        then:
        result.task(":${of.task}").outcome == TaskOutcome.SUCCESS
        compiled(of).source == new File(checkout, 'grails-app/views').canonicalPath
        compiled(of).pages.startsWith("book/index.${of.extension}:")

        when: 'nothing changes'
        BuildResult again = executeTask('jar', ['--configuration-cache'])

        then:
        again.task(":${of.task}").outcome == TaskOutcome.UP_TO_DATE

        where:
        of << KINDS
    }

    @Unroll
    def "the #of.kind views are compiled once the directory appears, and are gone from the build once it is deleted"() {
        given:
        project(of)

        when: 'there is no directory'
        BuildResult without = executeTask(of.task, ['--configuration-cache'])

        then:
        without.task(":${of.task}").outcome == TaskOutcome.NO_SOURCE

        when: 'the directory appears with a view'
        view(of, 'index')
        BuildResult with = executeTask(of.task, ['--configuration-cache'])

        then: 'the task is not up to date: the configuration that was stored without the directory is not reused'
        with.task(":${of.task}").outcome == TaskOutcome.SUCCESS
        !with.output.contains('Reusing configuration cache.')
        compiled(of).pages.startsWith("book/index.${of.extension}:")

        when: 'the directory is deleted again, as when the last view of an application is'
        new File(checkout, 'grails-app/views').deleteDir()
        BuildResult deleted = executeTask('jar', ['--configuration-cache'])

        then: 'the build goes on, and nothing of the views that were compiled is left to be packaged'
        // Gradle reports a task without source that removed what it had written before as one that worked
        deleted.task(":${of.task}").outcome == TaskOutcome.SUCCESS
        deleted.task(':jar').outcome == TaskOutcome.SUCCESS
        !recorded(of).exists()

        where:
        of << KINDS
    }

    @Unroll
    def "a build that documents the project and compiles its #of.kind views in one run is not refused, #views"() {
        given: 'a project with a class to document, whose documentation task reads the output of the main source set'
        project(of)
        new File(checkout, 'build.gradle') << """
            repositories { mavenCentral() }
            dependencies { implementation localGroovy() }
        """
        File source = new File(checkout, 'src/main/groovy/example/Book.groovy')
        source.parentFile.mkdirs()
        source.text = 'package example\n\nclass Book {\n    String title\n}\n'
        if (withView) {
            view(of, 'index')
        }

        when:
        BuildResult result = executeTask('groovydoc', ['javadoc', of.task])

        then: 'Gradle does not find a task that reads what another writes without an order between them'
        result.task(':groovydoc').outcome == TaskOutcome.SUCCESS
        result.task(":${of.task}").outcome == (withView ? TaskOutcome.SUCCESS : TaskOutcome.NO_SOURCE)
        !result.output.contains('without declaring an explicit or implicit dependency')

        where:
        [of, withView] << [KINDS, [true, false]].combinations()
        views = withView ? 'with a view' : 'without a views directory'
    }

    @Unroll
    def "the #of.kind compile task is not created by a build that does not need it"() {
        given:
        project(of)
        new File(checkout, 'build.gradle') << """
            tasks.configureEach { Task task ->
                if (task.name == '${of.task}') {
                    println 'CREATED ' + task.name
                }
            }
        """

        when:
        BuildResult result = executeTask('help')

        then:
        !result.output.contains("CREATED ${of.task}")

        where:
        of << KINDS
    }

    @Unroll
    def "a views path of the #of.kind compile task that is a file is still refused"() {
        given:
        project(of)
        new File(checkout, 'grails-app').mkdirs()
        new File(checkout, 'grails-app/views').text = 'not a directory'

        when:
        BuildResult result = runner.withArguments([of.task, '--stacktrace']).forwardOutput().buildAndFail()

        then:
        result.output.contains("The source for ${of.extension.toUpperCase()} compilation must be a single directory, but was ")

        where:
        of << KINDS
    }

    private void project(Map<String, String> of) {
        runner = setupTestResourceProject(of.project)
        checkout = runner.projectDir
        // the stand-in, under the real compiler's name, and the recorder it delegates to
        [of.standIn, 'org/grails/gradle/plugin/views/RecordingForkedCompiler.class'].each { String resource ->
            File compiler = new File(checkout, "compiler/${resource}")
            compiler.parentFile.mkdirs()
            compiler.bytes = getClass().classLoader.getResource(resource).bytes
        }
    }

    private File view(Map<String, String> of, String name) {
        File view = new File(checkout, "grails-app/views/book/${name}.${of.extension}")
        view.parentFile.mkdirs()
        view.text = 'title "The Stand"'
        view
    }

    private File recorded(Map<String, String> of) {
        new File(checkout, "${of.destination}/compiler.txt")
    }

    private Map<String, String> compiled(Map<String, String> of) {
        recorded(of).readLines().collectEntries { String line -> line.split('=', 2) as List }
    }
}
