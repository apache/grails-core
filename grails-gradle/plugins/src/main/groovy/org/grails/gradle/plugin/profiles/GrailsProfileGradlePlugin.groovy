/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.profiles

import groovy.transform.CompileStatic
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.file.SyncSpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.grails.gradle.plugin.profiles.tasks.ProfileCompilerTask

import javax.inject.Inject
import java.nio.file.Files

import static org.gradle.api.plugins.BasePlugin.BUILD_GROUP

/**
 * A plugin that is capable of compiling a Grails profile into a JAR file for distribution
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class GrailsProfileGradlePlugin implements Plugin<Project> {

    static final String USAGE_PROFILE_NAME = 'profile-runtime'
    static final String COMPONENT_NAME = 'profile'
    static final String RUNTIME_API_CONFIGURATION = 'profileRuntimeApi'
    static final String RUNTIME_ONLY_CONFIGURATION = 'profileRuntimeOnly'

    private final SoftwareComponentFactory softwareComponentFactory
    private final ObjectFactory objectFactory

    @Inject
    GrailsProfileGradlePlugin(ObjectFactory objectFactory, SoftwareComponentFactory softwareComponentFactory) {
        this.objectFactory = objectFactory
        this.softwareComponentFactory = softwareComponentFactory
    }

    @Override
    void apply(Project project) {
        project.pluginManager.apply(BasePlugin)

        Usage profileUsage = objectFactory.named(Usage, USAGE_PROFILE_NAME)

        project.dependencies.attributesSchema {
            it.attribute(Usage.USAGE_ATTRIBUTE) {
                it.compatibilityRules.add(JavaRuntimeCompatibility)
            }
        }

        AdhocComponentWithVariants profileComponent = softwareComponentFactory.adhoc(COMPONENT_NAME)
        project.components.add(profileComponent)

        NamedDomainObjectProvider<Configuration> runtimeApiConfiguration = project.configurations.register(RUNTIME_API_CONFIGURATION)
        runtimeApiConfiguration.configure { Configuration it ->
            it.description = 'Dependencies exported transitively to other profile projects'
            it.canBeConsumed = false
            it.canBeResolved = false
            it.attributes {
                it.attribute(Usage.USAGE_ATTRIBUTE, profileUsage)
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements, LibraryElements.CLASSES))
                it.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category, Category.LIBRARY))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling, Bundling.EXTERNAL))
            }
            profileComponent.addVariantsFromConfiguration(it) {
                it.mapToMavenScope('compile')
            }
        }

        NamedDomainObjectProvider<Configuration> runtimeOnlyConfiguration = project.configurations.register(RUNTIME_ONLY_CONFIGURATION)
        runtimeOnlyConfiguration.configure { Configuration it ->
            it.description = 'Dependencies required to compile a profile project'
            it.canBeConsumed = true
            it.canBeResolved = true
            it.extendsFrom(runtimeApiConfiguration.get())
            it.attributes {
                it.attribute(Usage.USAGE_ATTRIBUTE, profileUsage)
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements, LibraryElements.JAR))
                it.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category, Category.LIBRARY))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling, Bundling.EXTERNAL))
            }
            profileComponent.addVariantsFromConfiguration(it) {
                it.mapToMavenScope('runtime')
            }
        }

        TaskProvider<Task> processProfileResourcesTask = project.tasks.register('processProfileResources')
        processProfileResourcesTask.configure { Task task ->
            task.group = 'build'
            task.inputs.dir(project.provider {
                def directory = project.layout.projectDirectory.dir('commands')
                directory.asFile.exists() ? directory : null
            }).optional().skipWhenEmpty()
            task.inputs.dir(project.provider {
                def directory = project.layout.projectDirectory.dir('templates')
                directory.asFile.exists() ? directory : null
            }).optional().skipWhenEmpty()
            task.inputs.dir(project.provider {
                def directory = project.layout.projectDirectory.dir('features')
                directory.asFile.exists() ? directory : null
            }).optional().skipWhenEmpty()
            task.inputs.dir(project.provider {
                def directory = project.layout.projectDirectory.dir('skeleton')
                directory.asFile.exists() ? directory : null
            }).optional().skipWhenEmpty()

            task.doLast {
                project.sync { SyncSpec sync ->
                    sync.from(project.layout.projectDirectory.dir('commands')) { CopySpec s ->
                        s.exclude('*.groovy')
                        s.into('commands')
                    }

                    sync.from(project.layout.projectDirectory.dir('templates')) { CopySpec s ->
                        s.into('templates')
                    }

                    sync.from(project.layout.projectDirectory.dir('features')) { CopySpec s ->
                        s.into('features')
                    }

                    sync.from(project.layout.projectDirectory.dir('skeleton')) { CopySpec s ->
                        s.into('skeleton')
                    }

                    sync.into(project.layout.buildDirectory.dir('resources/profile/META-INF/grails-profile'))
                }
            }
        }

        TaskProvider<ProfileCompilerTask> compileTask = project.tasks.register('compileProfile', ProfileCompilerTask)
        compileTask.configure { ProfileCompilerTask it ->
            it.destinationDirectory.set project.layout.buildDirectory.dir('classes/profile')
            it.config.set project.layout.projectDirectory.file('profile.yml')
            // The profile task serves 2 purposes, it compiles the groovy files & it generates the profile.yml
            // for this reason the source must be set to include all possible files
            it.source project.provider {
                def commandsDirectory = project.layout.projectDirectory.dir('commands')
                def templatesDirectory = project.layout.projectDirectory.dir('templates')
                def skeletonDirectory = project.layout.projectDirectory.dir('skeleton')

                List<Directory> dirs = []
                if (commandsDirectory.asFile.exists()) {
                    dirs << commandsDirectory
                }
                if (templatesDirectory.asFile.exists()) {
                    dirs << templatesDirectory
                }
                if (skeletonDirectory.asFile.exists()) {
                    dirs << skeletonDirectory
                }
                project.files(dirs)
            }
            it.templatesDirectory.set project.provider {
                def templatesDirectory = project.layout.projectDirectory.dir('templates')
                if (templatesDirectory.asFile.exists()) {
                    return templatesDirectory
                }
                return null
            }
            it.skeletonDirectory.set project.provider {
                def skeletonDirectory = project.layout.projectDirectory.dir('skeleton')
                if (skeletonDirectory.asFile.exists()) {
                    return skeletonDirectory
                }
                return null
            }
            it.commandsDirectory.set project.provider {
                def commandsDirectory = project.layout.projectDirectory.dir('commands')
                if (commandsDirectory.asFile.exists()) {
                    return commandsDirectory
                }
                return null
            }
            it.classpath = runtimeOnlyConfiguration.get()
        }

        TaskProvider<Jar> jarTask = project.tasks.register('profileJar', Jar)
        jarTask.configure { Jar jar ->
            jar.group = BUILD_GROUP
            jar.dependsOn(processProfileResourcesTask, compileTask, project.tasks.named('jar', Jar).orNull)

            jar.from(project.files(project.layout.buildDirectory.dir('resources/profile'), project.layout.buildDirectory.dir('classes/profile')))
            jar.destinationDirectory.set(project.layout.buildDirectory.dir('libs'))
            jar.description = 'Assembles a jar archive containing the profile classes.'

//            ArchivePublishArtifact jarArtifact = new ArchivePublishArtifact(jar)
//            project.artifacts.add(CONFIGURATION_NAME, jarArtifact)
        }

        TaskProvider<Jar> sourcesJarTask = project.tasks.register('sourcesProfileJar', Jar)
        sourcesJarTask.configure { Jar jar ->
            jar.from(project.layout.projectDirectory.dir('commands'))
            if (project.file('profile.yml').exists()) {
                jar.from(project.file('profile.yml'))
            }
            jar.from(project.layout.projectDirectory.dir('templates')) { CopySpec spec ->
                spec.into('templates')
            }
            jar.from(project.layout.projectDirectory.dir('skeleton')) { CopySpec spec ->
                spec.into('skeleton')
            }
            jar.archiveClassifier.set('sources')
            jar.destinationDirectory.set(new File(project.layout.buildDirectory.asFile.get(), 'libs'))
            jar.description = 'Assembles a jar archive containing the profile sources.'
            jar.group = BUILD_GROUP
        }

        TaskProvider<Jar> javadocJarTask = project.tasks.register('javadocProfileJar', Jar)
        javadocJarTask.configure { Jar jar ->
            final File tempReadmeForJavadoc = Files.createTempFile('README', 'txt').toFile()
            // https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources
            tempReadmeForJavadoc << 'Profiles are templates and do not have javadoc.'
            jar.from(tempReadmeForJavadoc)
            jar.archiveClassifier.set('javadoc')
            jar.destinationDirectory.set(new File(project.layout.buildDirectory.asFile.get(), 'libs'))
            jar.description = 'Assembles a jar archive containing the profile javadoc.'
            jar.group = BUILD_GROUP
        }

        project.tasks.named('assemble').configure { Task it ->
            it.dependsOn(jarTask)
        }
    }
}

/**
 * I'm not sure why a separate configuration was originally created for the profiles, but maybe they are combined
 * into a single gradle project.  For compatibility purposes, treat java-runtime as a usable substitute
 */
class JavaRuntimeCompatibility implements AttributeCompatibilityRule<Usage> {
    @Override
    void execute(CompatibilityCheckDetails<Usage> d) {
        if (d.consumerValue.name == GrailsProfileGradlePlugin.USAGE_PROFILE_NAME && d.producerValue.name == Usage.JAVA_RUNTIME) {
            d.compatible()
        }
    }
}