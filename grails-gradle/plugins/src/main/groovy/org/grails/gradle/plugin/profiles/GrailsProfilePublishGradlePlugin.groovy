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

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.bundling.Jar

import org.grails.gradle.plugin.publishing.GrailsPublishGradlePlugin

/**
 * A plugin for publishing profiles
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class GrailsProfilePublishGradlePlugin extends GrailsPublishGradlePlugin {

    private final ObjectFactory objectFactory

    @Inject
    GrailsProfilePublishGradlePlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        project.afterEvaluate { evaluated ->
            if (!project.plugins.hasPlugin(GrailsProfileGradlePlugin)) {
                throw new GradleException('Only profile projects can be published using the Grails Profile Publish Plugin. Apply the profile plugin first.')
            }

            evaluated.tasks.withType(GenerateMavenPom).each { generateMavenPomTask ->
                generateMavenPomTask.dependsOn(project.tasks.withType(Jar))
            }
        }
    }

    @Override
    protected Map<String, String> getDefaultExtraArtifact(Project project) {
        [source    : project.layout.buildDirectory.file('classes/profile/META-INF/grails-profile/profile.yml').get().asFile.toString(),
         classifier: defaultClassifier,
         extension : 'yml']
    }

    @Override
    protected String getDefaultClassifier() {
        'profile'
    }

    @Override
    protected void doAddArtefact(Project project, MavenPublication publication) {
        publication.from project.components.named('java').get()
    }
}
