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
package org.grails.gradle.plugin.profiles

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.bundling.Jar
import org.grails.gradle.plugin.publishing.GrailsPublishGradlePlugin

import java.nio.file.Files

import static org.gradle.api.plugins.BasePlugin.BUILD_GROUP

/**
 * A plugin for publishing profiles
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class GrailsProfilePublishGradlePlugin extends GrailsPublishGradlePlugin {

    @Override
    void apply(Project project) {
        super.apply(project)
        final File tempReadmeForJavadoc = Files.createTempFile('README', 'txt').toFile()
        tempReadmeForJavadoc << 'https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources'
        project.tasks.register('javadocProfileJar', Jar, { Jar jar ->
            jar.from(tempReadmeForJavadoc)
            jar.archiveClassifier.set('javadoc')
            jar.destinationDirectory.set(new File(project.layout.buildDirectory.getAsFile().get(), 'libs'))
            jar.setDescription('Assembles a jar archive containing the profile javadoc.')
            jar.setGroup(BUILD_GROUP)
        })

        project.afterEvaluate { evaluated ->
            evaluated.tasks.withType(GenerateMavenPom).each { generateMavenPomTask ->
                generateMavenPomTask.dependsOn(project.tasks.withType(Jar))
            }
        }
    }

    @Override
    protected Map<String, String> getDefaultExtraArtifact(Project project) {
        [source    : "${project.buildDir}/classes/profile/META-INF/grails-profile/profile.yml".toString(),
         classifier: defaultClassifier,
         extension : 'yml']
    }

    @Override
    protected String getDefaultClassifier() {
        'profile'
    }

    @Override
    protected void doAddArtefact(Project project, MavenPublication publication) {
        publication.artifact(project.tasks.findByName('profileJar'))
        publication.artifact(project.tasks.findByName('sourcesProfileJar'))
        publication.artifact(project.tasks.findByName('javadocProfileJar'))

        publication.pom(new Action<MavenPom>() {
            @Override
            void execute(MavenPom mavenPom) {
                mavenPom.withXml(new Action<XmlProvider>() {
                    @Override
                    void execute(XmlProvider xml) {
                        Node dependenciesNode = xml.asNode().appendNode('dependencies')

                        DependencySet dependencySet = project.configurations[GrailsProfileGradlePlugin.RUNTIME_CONFIGURATION].allDependencies

                        for (Dependency dependency : dependencySet) {
                            Node dependencyNode = dependenciesNode.appendNode('dependency')
                            dependencyNode.appendNode('groupId', dependency.group)
                            dependencyNode.appendNode('artifactId', dependency.name)
                            dependencyNode.appendNode('version', dependency.version)
                            dependencyNode.appendNode('scope', GrailsProfileGradlePlugin.RUNTIME_CONFIGURATION)
                        }
                    }
                })
            }
        })
    }

    @Override
    protected validateProjectPublishable(Project project) {
        // no-op
    }
}
