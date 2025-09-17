package org.grails.gradle.plugin.exploded

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.language.jvm.tasks.ProcessResources

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

    private ObjectFactory objects

    @Inject
    GrailsExplodedPlugin(ObjectFactory objects) {
        this.objects = objects
    }

    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin(GrailsPluginGradlePlugin.PLUGIN_ID) {
            project.logger.info('Project {} will be exploded for bootRuns', project.name)

            GrailsExtension grails = project.extensions.findByType(GrailsExtension)
            Objects.requireNonNull(grails, 'GrailsExtension should be applied by the `grails-plugin` Gradle plugin.')

            if (grails.developmentRun) {
                project.logger.lifecycle('bootRun detected - exploding plugin {}', project.name)
                project.configurations.named('runtimeElements').configure { Configuration runtimeElements ->
                    runtimeElements.outgoing.variants.create(EXPLODED_VARIANT_NAME) { ConfigurationVariant v ->
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

                            def groovyCompileProvider = project.tasks.named('compileGroovy', GroovyCompile)
                            def processResourcesProvider = project.tasks.named('processResources', ProcessResources)

                            def groovyDir = groovyCompileProvider.get().destinationDirectory.get().asFile
                            def resourcesDir = processResourcesProvider.get().destinationDir

                            v.artifact(groovyDir) { ConfigurablePublishArtifact a ->
                                a.type = ArtifactTypeDefinition.DIRECTORY_TYPE
                                a.extension = ''
                                a.classifier = ''
                                a.builtBy(groovyCompileProvider, processResourcesProvider)
                            }
                            v.artifact(resourcesDir) { ConfigurablePublishArtifact a ->
                                a.type = ArtifactTypeDefinition.DIRECTORY_TYPE
                                a.extension = ''
                                a.classifier = ''
                                a.builtBy(groovyCompileProvider, processResourcesProvider)
                            }
                        }
                    }
                }
            }
        }
    }
}
