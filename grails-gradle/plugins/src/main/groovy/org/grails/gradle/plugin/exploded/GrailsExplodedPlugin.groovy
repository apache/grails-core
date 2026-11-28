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
package org.grails.gradle.plugin.exploded

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

import org.grails.gradle.plugin.core.GrailsExtension
import org.grails.gradle.plugin.core.GrailsPluginGradlePlugin

/**
 * Configures a Grails Plugin so that its classes and resources are exploded when running bootRun from a dependent Grails Application
 */
@CompileStatic
class GrailsExplodedPlugin implements Plugin<Project> {

    public static final String PLUGIN_ID = 'org.apache.grails.gradle.grails-exploded'
    public static final String EXPLODED_VARIANT_NAME = 'exploded'
    public static final Attribute EXPLODED_ATTRIBUTE = Attribute.of('grails.plugin.exploded', Boolean)

    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin(GrailsPluginGradlePlugin.PLUGIN_ID) {
            project.logger.info('Project {} will be exploded for bootRuns', project.name)

            GrailsExtension grails = project.extensions.findByType(GrailsExtension)
            Objects.requireNonNull(grails, 'GrailsExtension should be applied by the `grails-plugin` Gradle plugin.')

            if (grails.developmentRun) {
                project.logger.lifecycle('bootRun detected - exploding plugin {}', project.name)
                project.configurations.named('runtimeElements').configure { Configuration runtimeElements ->
                    runtimeElements.outgoing.variants.register(EXPLODED_VARIANT_NAME) { ConfigurationVariant v ->
                        v.attributes { AttributeContainer ac ->
                            // copy values from runtimeElements
                            runtimeElements.attributes.keySet().each { Attribute<?> key ->
                                Attribute<Object> typedKey = (Attribute<Object>) key
                                Object value = runtimeElements.attributes.getAttribute(typedKey)
                                if (value != null) {
                                    ac.attribute(typedKey, value)
                                }
                            }

                            // add our additional attribute
                            ac.attribute(EXPLODED_ATTRIBUTE, true)
                        }
                        def output = project.extensions.getByType(JavaPluginExtension)
                                .sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).get().output
                        output.each { File dir ->
                            v.artifact(dir) { ConfigurablePublishArtifact artifact ->
                                artifact.type = ArtifactTypeDefinition.DIRECTORY_TYPE
                                artifact.extension = ''
                                artifact.classifier = ''
                                artifact.builtBy(output)
                            }
                        }
                    }
                }
            }
        }
    }
}
