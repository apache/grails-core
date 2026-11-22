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

import java.util.jar.JarFile

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class PublishPluginSpec extends Specification {

    @TempDir
    File tmp

    private File artifact(String name) {
        File f = new File(tmp, name)
        f.text = name
        f
    }

    private static PublishingExtension publishingOf(Project project) {
        project.pluginManager.apply('maven-publish')
        project.extensions.getByType(PublishingExtension)
    }

    void "collectChecksumInputFiles includes artifacts from every publication including the cli companion"() {
        given: "a project with a primary publication and an additional cli companion publication"
        Project project = ProjectBuilder.builder().withName('grails-core').build()
        PublishingExtension publishing = publishingOf(project)

        File mainJar = artifact('grails-core-1.0.jar')
        File cliJar = artifact('grails-core-cli-1.0.jar')
        File cliJavadoc = artifact('grails-core-cli-1.0-javadoc.jar')

        publishing.publications.create('maven', MavenPublication) { MavenPublication pub ->
            pub.artifact(mainJar)
        }
        publishing.publications.create('cli', MavenPublication) { MavenPublication pub ->
            pub.artifact(cliJar)
            pub.artifact(cliJavadoc) { it.classifier = 'javadoc' }
        }

        when:
        List<File> files = PublishPlugin.collectChecksumInputFiles(project, publishing)

        then: "both the primary and the cli companion artifacts are checksummed (the cli tier is not clobbered)"
        files.contains(mainJar)
        files.contains(cliJar)
        files.contains(cliJavadoc)
        files.size() == 3
    }

    void "collectChecksumInputFiles excludes the plugin metadata sidecar"() {
        given: "a publication carrying a jar alongside the plugin descriptor sidecar"
        Project project = ProjectBuilder.builder().withName('grails-core').build()
        PublishingExtension publishing = publishingOf(project)

        File jar = artifact('grails-core-1.0.jar')
        File pluginXml = artifact('grails-plugin.xml')

        publishing.publications.create('maven', MavenPublication) { MavenPublication pub ->
            pub.artifact(jar)
            pub.artifact(pluginXml)
        }

        when:
        List<File> files = PublishPlugin.collectChecksumInputFiles(project, publishing)

        then: "only the jar is checksummed; grails-plugin.xml is skipped"
        files == [jar]
    }

    void "collectChecksumInputFiles de-duplicates a file shared across publications"() {
        given: "two publications that both expose the same file"
        Project project = ProjectBuilder.builder().withName('grails-core').build()
        PublishingExtension publishing = publishingOf(project)

        File shared = artifact('grails-core-1.0.jar')

        publishing.publications.create('maven', MavenPublication) { MavenPublication pub ->
            pub.artifact(shared)
        }
        publishing.publications.create('cli', MavenPublication) { MavenPublication pub ->
            pub.artifact(shared)
        }

        when:
        List<File> files = PublishPlugin.collectChecksumInputFiles(project, publishing)

        then:
        files == [shared]
    }

    void "collectPublishedArtifactCoordinates maps every artifact file to the coordinate it is published under"() {
        given:
        Project project = ProjectBuilder.builder().withName('grails-core').build()
        PublishingExtension publishing = publishingOf(project)

        File mainJar = artifact('grails-core-1.0.jar')
        File sourcesJar = artifact('grails-core-1.0-sources.jar')
        File pluginXml = artifact('grails-plugin.xml')

        publishing.publications.create('maven', MavenPublication) { MavenPublication pub ->
            pub.groupId = 'org.apache.grails'
            pub.artifactId = 'grails-core'
            pub.version = '1.0'
            pub.artifact(mainJar)
            pub.artifact(sourcesJar) { it.classifier = 'sources' }
            pub.artifact(pluginXml)
        }

        when:
        Map<String, String> coordinates = PublishPlugin.collectPublishedArtifactCoordinates(publishing)

        then: 'classified artifacts carry their classifier and the plugin descriptor is left out'
        coordinates == [
                (mainJar.absolutePath)   : 'org.apache.grails:grails-core:1.0',
                (sourcesJar.absolutePath): 'org.apache.grails:grails-core:1.0:sources',
        ]
    }

    void "the jars and the published artifact list are built with the configuration cache, also when it is reused"() {
        given: 'a library inside a Grails checkout'
        new File(tmp, '.asf.yaml').text = ''
        writeFile('licenses/LICENSE-Apache-2.0.txt', 'Apache License')
        writeFile('grails-core/src/main/resources/META-INF/NOTICE', 'Apache Grails')
        writeFile('library/settings.gradle', "rootProject.name = 'library'")
        writeFile('library/gradle.properties', 'projectVersion=1.0.0-SNAPSHOT')
        writeFile('library/build.gradle', """
            plugins {
                id 'java-library'
                id 'org.apache.grails.gradle.grails-publish'
                id 'org.apache.grails.buildsrc.publish'
            }
            group = 'org.example'
            version = projectVersion
        """)
        writeFile('library/src/main/java/org/example/Library.java', 'package org.example; public class Library {}')

        when: 'the artifact list is saved from a clean build, storing and then reusing the configuration cache entry'
        BuildResult stored = runFromClean('savePublishedArtifacts')
        BuildResult reused = runFromClean('savePublishedArtifacts')

        then:
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')

        and: 'every built artifact is listed with its coordinate'
        new File(tmp, 'library/build/artifacts/library-1.0.0-SNAPSHOT.jar.txt').text == 'org.example:library:1.0.0-SNAPSHOT'
        new File(tmp, 'library/build/artifacts/library-1.0.0-SNAPSHOT-sources.jar.txt').text == 'org.example:library:1.0.0-SNAPSHOT:sources'

        and: 'the jar carries the fallback license and notice'
        new JarFile(new File(tmp, 'library/build/libs/library-1.0.0-SNAPSHOT.jar')).withCloseable { JarFile jar ->
            jar.getEntry('META-INF/LICENSE') != null && jar.getEntry('META-INF/NOTICE') != null
        }
    }

    void "a build that is asked to publish to the repository of the tests only publishes what a build resolves, and builds nothing else"() {
        given:
        library()

        when:
        BuildResult result = run('publishAllPublicationsToTestCaseMavenRepoRepository')
        File published = new File(tmp, 'build/local-maven/org/example/lib/1.0.0-SNAPSHOT')
        List<String> files = published.list().collect { String name -> name.replaceAll(/-\d{8}\.\d{6}-\d+/, '-SNAPSHOT') }

        then: 'the jar, the POM and the module metadata are there, and no sources or javadoc jar'
        files.contains('lib-1.0.0-SNAPSHOT.jar')
        files.contains('lib-1.0.0-SNAPSHOT.pom')
        files.contains('lib-1.0.0-SNAPSHOT.module')
        !files.any { String name -> name.contains('-sources') || name.contains('-javadoc') }

        and: 'the module metadata names no variant whose file is not there'
        String module = published.listFiles().find { File file -> file.name.endsWith('.module') }.text
        module.contains('"name": "apiElements"')
        module.contains('"name": "runtimeElements"')
        !module.contains('sourcesElements')
        !module.contains('javadocElements')

        and: 'neither the documentation, nor the tests, nor the checksums of a release were built'
        ranTasks(result).contains(':lib:jar')
        !ranTasks(result).any { String task -> task in [':lib:javadoc', ':lib:javadocJar', ':lib:sourcesJar', ':lib:compileTestJava', ':lib:testSourcesJar', ':lib:publishedChecksums', ':lib:savePublishedArtifacts'] }

        when: 'the same again, from the configuration cache'
        BuildResult reused = run('publishAllPublicationsToTestCaseMavenRepoRepository')

        then:
        reused.output.contains('Configuration cache entry reused')
        !ranTasks(reused).any { String task -> task in [':lib:javadocJar', ':lib:sourcesJar', ':lib:publishedChecksums'] }
    }

    void "a build that is asked for anything else publishes everything to the repository of the tests, as before"() {
        given:
        library()

        when:
        BuildResult result = run(tasks as String[])
        File published = new File(tmp, 'build/local-maven/org/example/lib/1.0.0-SNAPSHOT')
        List<String> files = published.list().collect { String name -> name.replaceAll(/-\d{8}\.\d{6}-\d+/, '-SNAPSHOT') }

        then:
        files.containsAll(['lib-1.0.0-SNAPSHOT.jar', 'lib-1.0.0-SNAPSHOT-sources.jar', 'lib-1.0.0-SNAPSHOT-javadoc.jar',
                           'lib-1.0.0-SNAPSHOT.pom', 'lib-1.0.0-SNAPSHOT.module'])
        published.listFiles().find { File file -> file.name.endsWith('.module') }.text.contains('sourcesElements')
        ranTasks(result).containsAll([':lib:javadocJar', ':lib:sourcesJar', ':lib:publishedChecksums', ':lib:savePublishedArtifacts'])

        where:
        tasks << [
                ['publishAllPublicationsToTestCaseMavenRepoRepository', 'assemble'],
                ['publishAllPublicationsToTestCaseMavenRepoRepository', '-PcompleteTestRepository=true'],
                ['publishAllPublicationsToTestCaseMavenRepoRepository', 'lib:tasks']
        ]
    }

    void "a publish to another place is complete, with its checksums, whatever is published for the tests"() {
        given:
        library()

        when:
        BuildResult result = run('publishToMavenLocal', "-Dmaven.repo.local=${new File(tmp, 'm2').absolutePath}")
        File published = new File(tmp, 'm2/org/example/lib/1.0.0-SNAPSHOT')

        then:
        published.list().toList().containsAll(['lib-1.0.0-SNAPSHOT.jar', 'lib-1.0.0-SNAPSHOT-sources.jar', 'lib-1.0.0-SNAPSHOT-javadoc.jar',
                                               'lib-1.0.0-SNAPSHOT.pom', 'lib-1.0.0-SNAPSHOT.module'])
        ranTasks(result).containsAll([':lib:javadocJar', ':lib:sourcesJar', ':lib:publishedChecksums', ':lib:savePublishedArtifacts'])
        new File(tmp, 'library/lib/build/checksums').list().any { String name -> name.endsWith('-sources.jar.sha512') }
    }

    void "a build that is included publishes to the repository of the tests what a build resolves, unless the command line names another of its tasks"() {
        given: 'a build that depends on the publish of the library for its tests, as the build of Forge does'
        library()
        writeFile('consumer/settings.gradle', """
            rootProject.name = 'consumer'
            includeBuild('../library') { name = 'framework' }
        """)
        writeFile('consumer/build.gradle', """
            tasks.register('specs') {
                dependsOn gradle.includedBuild('framework').task(':publishAllPublicationsToTestCaseMavenRepoRepository')
            }
        """)

        when:
        BuildResult result = run(new File(tmp, 'consumer'), 'specs')
        File published = new File(tmp, 'build/local-maven/org/example/lib/1.0.0-SNAPSHOT')

        then:
        published.list().any { String name -> name.endsWith('.jar') }
        !published.list().any { String name -> name.contains('-sources') || name.contains('-javadoc') }
        !ranTasks(result).any { String task -> task in [':framework:lib:javadocJar', ':framework:lib:sourcesJar', ':framework:lib:compileTestJava', ':framework:lib:publishedChecksums'] }

        when: 'the command line names a task of the included build that is no publish for the tests'
        new File(tmp, 'build/local-maven').deleteDir()
        result = run(new File(tmp, 'consumer'), 'specs', ':framework:lib:assemble')

        then:
        published.list().any { String name -> name.contains('-sources') }
        published.list().any { String name -> name.contains('-javadoc') }
        ranTasks(result).contains(':framework:lib:publishedChecksums')
    }

    void "a publish to another place that an including build depends on, without the command line naming it, fails and publishes nothing incomplete"() {
        given:
        library()
        writeFile('consumer/settings.gradle', """
            rootProject.name = 'consumer'
            includeBuild('../library') { name = 'framework' }
        """)
        writeFile('consumer/build.gradle', """
            tasks.register('install') {
                dependsOn gradle.includedBuild('framework').task(':lib:publishToMavenLocal')
            }
        """)
        File local = new File(tmp, 'm2')

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(new File(tmp, 'consumer'))
                .withArguments('install', "-Dmaven.repo.local=${local.absolutePath}", '--configuration-cache')
                .withPluginClasspath()
                .buildAndFail()

        then:
        result.output.contains('the publications are without their sources and javadoc jars')
        result.output.contains('-PcompleteTestRepository=true')
        !new File(local, 'org/example/lib').exists()

        when: 'with the property that asks for everything'
        result = run(new File(tmp, 'consumer'), 'install', "-Dmaven.repo.local=${local.absolutePath}", '-PcompleteTestRepository=true')

        then:
        new File(local, 'org/example/lib/1.0.0-SNAPSHOT').list().any { String name -> name.endsWith('-sources.jar') }
    }

    void "the tasks of a command line are a publish for the tests only when each of them is: #requested"() {
        expect:
        TestRepositoryPublish.isAllThatIsAsked(requested, ':') == own
        TestRepositoryPublish.isAllThatIsAsked(requested, ':grails-core') == included

        where:
        requested                                                                                  | own   | included
        ['publishAllPublicationsToTestCaseMavenRepoRepository']                                    | true  | true
        [':grails-core:publishMavenPublicationToTestCaseMavenRepoRepository']                      | true  | true
        ['publishAllPublicationsToTestCaseMavenRepoRepository', 'publishToMavenLocal']             | false | true
        ['publishAllPublicationsToTestCaseMavenRepoRepository', '--info']                          | false | true
        []                                                                                         | false | true
        ['build']                                                                                  | false | true
        [':grails-forge-cli:test', '--tests', 'org.example.SomeSpec']                              | false | true
        [':grails-core:publishAllPublicationsToTestCaseMavenRepoRepository', 'test']               | false | true
        ['test', ':grails-core:publishToMavenLocal']                                               | false | false
        ['test', ':grails-core:grails-bootstrap:groovydoc']                                        | false | false
        ['test', ':grails-gradle:grails-gradle-plugins:groovydoc']                                 | false | true
        [':grails-core-docs:test']                                                                 | false | true
        ['publishAllPublicationsToMavenRepository']                                                | false | true
        ['publishTestCaseMavenRepoRepository']                                                     | false | true
    }

    // A build of two projects, as the builds of Grails are: the project that is published, and above it the
    // one whose task of that name the Grails publish plugin has depend on the publish of every project.
    private void library() {
        new File(tmp, '.asf.yaml').text = ''
        writeFile('licenses/LICENSE-Apache-2.0.txt', 'Apache License')
        writeFile('grails-core/src/main/resources/META-INF/NOTICE', 'Apache Grails')
        writeFile('library/settings.gradle', "rootProject.name = 'library'\ninclude 'lib'")
        writeFile('library/gradle.properties', 'projectVersion=1.0.0-SNAPSHOT')
        writeFile('library/build.gradle', "tasks.register('publishAllPublicationsToTestCaseMavenRepoRepository')")
        writeFile('library/lib/build.gradle', """
            plugins {
                id 'java-library'
                id 'org.apache.grails.gradle.grails-publish'
                id 'org.apache.grails.buildsrc.publish'
            }
            group = 'org.example'
            version = projectVersion
        """)
        writeFile('library/lib/src/main/java/org/example/Library.java', 'package org.example; /** A library. */ public class Library {}')
        writeFile('library/lib/src/test/java/org/example/LibraryCheck.java', 'package org.example; public class LibraryCheck {}')
    }

    private BuildResult run(String... arguments) {
        return run(new File(tmp, 'library'), arguments)
    }

    private BuildResult run(File directory, String... arguments) {
        GradleRunner.create()
                .withProjectDir(directory)
                .withArguments(arguments.toList() + ['--configuration-cache', '--stacktrace'])
                .withPluginClasspath()
                .build()
    }

    // the tasks that did something: one that was skipped, as testSourcesJar is by its condition, built nothing
    private static List<String> ranTasks(BuildResult result) {
        return result.tasks.findAll { it.outcome.name() in ['SUCCESS', 'UP_TO_DATE', 'FROM_CACHE'] }*.path
    }

    private void writeFile(String path, String content) {
        File file = new File(tmp, path)
        file.parentFile.mkdirs()
        file.text = content
    }

    private BuildResult runFromClean(String task) {
        GradleRunner.create()
                .withProjectDir(new File(tmp, 'library'))
                .withArguments('clean', task, '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()
    }
}
