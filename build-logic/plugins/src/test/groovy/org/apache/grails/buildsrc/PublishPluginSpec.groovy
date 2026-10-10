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

    void "collectChecksumInputFiles excludes plugin metadata sidecars"() {
        given: "a publication carrying a jar alongside the plugin descriptor and profile sidecars"
        Project project = ProjectBuilder.builder().withName('grails-core').build()
        PublishingExtension publishing = publishingOf(project)

        File jar = artifact('grails-core-1.0.jar')
        File pluginXml = artifact('grails-plugin.xml')
        File profileYml = artifact('profile.yml')

        publishing.publications.create('maven', MavenPublication) { MavenPublication pub ->
            pub.artifact(jar)
            pub.artifact(pluginXml)
            pub.artifact(profileYml)
        }

        when:
        List<File> files = PublishPlugin.collectChecksumInputFiles(project, publishing)

        then: "only the jar is checksummed; grails-plugin.xml and profile.yml are skipped"
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
