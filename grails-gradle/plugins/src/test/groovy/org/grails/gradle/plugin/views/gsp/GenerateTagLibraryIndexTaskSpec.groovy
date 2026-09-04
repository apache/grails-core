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
package org.grails.gradle.plugin.views.gsp

import java.nio.file.Path

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import org.grails.gradle.plugin.core.GrailsExtension

/**
 * The index has to be generated before anything that resolves tag calls is compiled, and has to travel
 * with the artifact so that a project depending on this one can resolve its tags too. Both are
 * properties of how the task is wired rather than of what it writes.
 */
class GenerateTagLibraryIndexTaskSpec extends Specification {

    @TempDir
    Path projectDir

    Project project

    def setup() {
        // The task runs whether or not a project declares tag libraries of its own, because it also
        // records what the build declared about the tag libraries it uses. A tag library is present
        // here so that the ordinary case is what most of these check.
        File taglibDir = new File(projectDir.toFile(), 'grails-app/taglib/demo')
        taglibDir.mkdirs()
        new File(taglibDir, 'DemoTagLib.groovy').text = '''
            package demo
            class DemoTagLib {
                static namespace = 'demo'
                def hello(Map attrs) { }
            }
        '''
        project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build()
        project.pluginManager.apply('groovy')
        project.pluginManager.apply(GroovyPagePlugin)
    }

    void 'the task is registered'() {
        expect:
        project.tasks.findByName('generateTagLibraryIndex') instanceof GenerateTagLibraryIndexTask
    }

    void 'it reads the tag library source directory and writes into the build directory'() {
        given:
        GenerateTagLibraryIndexTask task = project.tasks.getByName('generateTagLibraryIndex') as GenerateTagLibraryIndexTask

        expect:
        task.sourceDirectories.files*.canonicalFile ==
                [new File(projectDir.toFile(), 'grails-app/taglib').canonicalFile]
        task.destinationDirectory.get().asFile.canonicalFile ==
                new File(projectDir.toFile(), 'build/generated/grails-taglibs').canonicalFile
    }

    void 'page compilation runs after the index exists'() {
        expect: 'pages resolve tag calls against the index, so it has to be written first'
        dependencyNames(project.tasks.getByName('compileGroovyPages')).contains('generateTagLibraryIndex')
    }

    void 'compiling this project sees the index it generates'() {
        given: 'otherwise a call to a tag this project declares could not be resolved as it compiles'
        Task compileGroovy = project.tasks.getByName('compileGroovy')

        expect:
        dependencyNames(compileGroovy).contains('generateTagLibraryIndex')

        and: 'the index is on the compile classpath, not merely produced alongside it'
        compileGroovy.classpath.files*.canonicalFile.contains(
                new File(projectDir.toFile(), 'build/generated/grails-taglibs').canonicalFile)
    }

    void 'the generator does not wait for this project to be compiled'() {
        given: 'it reads source, so requiring compiled output would invert the ordering it exists for'
        Task generate = project.tasks.getByName('generateTagLibraryIndex')

        expect:
        !dependencyNames(generate).contains('classes')
        !dependencyNames(generate).contains('compileGroovy')
    }

    void 'the index written before compilation is not packaged'() {
        given: 'read from source, it cannot describe a tag library it cannot resolve yet'
        SourceSet main = (project.extensions.getByType(SourceSetContainer)).getByName('main')

        expect: 'so a project depending on this one must not be given it'
        !main.resources.srcDirs*.canonicalFile.contains(
                new File(projectDir.toFile(), 'build/generated/grails-taglibs').canonicalFile)
    }

    void 'the index written after compilation is on the runtime classpath'() {
        given: 'a page compiled while the application runs resolves its tags against it'
        SourceSet main = (project.extensions.getByType(SourceSetContainer)).getByName('main')

        expect:
        main.runtimeClasspath.files*.canonicalFile.contains(
                new File(projectDir.toFile(), 'build/generated/grails-taglibs-packaged').canonicalFile)
    }

    void 'the index written after compilation waits for everything that writes the class output'() {
        given: 'not for the compile tasks alone, which others may write into that directory after'
        Task packageIndex = project.tasks.getByName('packageTagLibraryIndex')

        expect:
        dependencyNames(packageIndex).contains('classes')
    }

    void 'compiling pages sees the authoritative index'() {
        given: 'a page must see every tag, including one only describable once compiled'
        Task compilePages = project.tasks.getByName('compileGroovyPages')

        expect:
        compilePages.classpath.files*.canonicalFile.contains(
                new File(projectDir.toFile(), 'build/generated/grails-taglibs-packaged').canonicalFile)

        and: 'and not the partial one written before compilation'
        !compilePages.classpath.files*.canonicalFile.contains(
                new File(projectDir.toFile(), 'build/generated/grails-taglibs').canonicalFile)
    }

    void 'the strictness and dynamic namespaces the build declares are task inputs'() {
        given: 'the settings are read when the task runs, so declaring them later still reaches it'
        GenerateTagLibraryIndexTask task = project.tasks.getByName('generateTagLibraryIndex') as GenerateTagLibraryIndexTask
        GrailsExtension grails = project.extensions.create('grails', GrailsExtension, project)

        when:
        grails.compileStatic.strictTags.set(true)
        grails.compileStatic.dynamicTagNamespaces.set(['legacy'] as Set)

        then: 'read from the build rather than from a system property, so a change recompiles'
        task.strictTags.get()
        task.dynamicTagNamespaces.get() == ['legacy'] as Set
    }

    void 'a project with no tag libraries of its own still records what the build declared'() {
        given: 'the settings apply to compiling the project, whether or not it declares tag libraries'
        File emptyDir = File.createTempDir('no-taglibs', '')
        Project empty = ProjectBuilder.builder().withProjectDir(emptyDir).build()
        empty.pluginManager.apply('groovy')
        empty.pluginManager.apply(GroovyPagePlugin)
        GrailsExtension grails = empty.extensions.create('grails', GrailsExtension, empty)
        grails.compileStatic.dynamicTagNamespaces.set(['legacy'] as Set)
        GenerateTagLibraryIndexTask task =
                empty.tasks.getByName('generateTagLibraryIndex') as GenerateTagLibraryIndexTask

        when:
        task.generate()

        then: 'written apart from the descriptors, so it can never travel with them'
        File settings = new File(emptyDir,
                'build/generated/grails-taglib-settings/META-INF/grails/taglibs/compile-settings.properties')
        settings.isFile()
        settings.text.contains('dynamicTagNamespaces=legacy')
        settings.text.contains('strictTags=false')

        cleanup:
        emptyDir.deleteDir()
    }

    void 'a build that declares nothing is left as permissive as before'() {
        given:
        GenerateTagLibraryIndexTask task = project.tasks.getByName('generateTagLibraryIndex') as GenerateTagLibraryIndexTask

        expect:
        !task.strictTags.get()
        task.dynamicTagNamespaces.get().isEmpty()
    }

    void 'the index is generated with the java the project is built with'() {
        given: 'it runs against the project compile classpath, so it needs the java that built it'
        GenerateTagLibraryIndexTask task = project.tasks.getByName('generateTagLibraryIndex') as GenerateTagLibraryIndexTask

        expect:
        task.javaLauncher.present
    }

    private static Set<String> dependencyNames(Task task) {
        task.taskDependencies.getDependencies(task)*.name as Set
    }

    void 'further tag library source directories can be added'() {
        given: 'a project keeping tag libraries outside grails-app/taglib as well'
        GenerateTagLibraryIndexTask task = project.tasks.getByName('generateTagLibraryIndex') as GenerateTagLibraryIndexTask
        File extra = new File(projectDir.toFile(), 'src/main/groovy')

        when:
        task.sourceDirectories.from(extra)

        then: 'both are scanned, so tags declared in either resolve in the same compilation'
        task.sourceDirectories.files*.canonicalFile.contains(extra.canonicalFile)
        task.sourceDirectories.files.size() == 2
    }

    void 'the task declares its inputs and outputs so it can be skipped and cached'() {
        given:
        Task task = project.tasks.getByName('generateTagLibraryIndex')

        expect: 'a declared output directory, without which stale entries could never be detected'
        !task.outputs.files.isEmpty()

        and: 'declared inputs, so an unchanged source set does not regenerate'
        !task.inputs.files.isEmpty()

        and: 'and it is cacheable'
        task.class.superclass.isAnnotationPresent(org.gradle.api.tasks.CacheableTask) ||
                task.class.isAnnotationPresent(org.gradle.api.tasks.CacheableTask)
    }
}
