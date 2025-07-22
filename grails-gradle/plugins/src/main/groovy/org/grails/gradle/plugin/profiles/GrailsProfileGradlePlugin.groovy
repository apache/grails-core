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
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.file.SyncSpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.grails.gradle.plugin.profiles.tasks.ProfileCompilerTask

import javax.inject.Inject

import static org.gradle.api.plugins.BasePlugin.BUILD_GROUP

/**
 * A plugin that is capable of compiling a Grails profile into a JAR file for distribution
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class GrailsProfileGradlePlugin implements Plugin<Project> {

    // to be used to extend profiles
    static final String PROFILE_API_CONFIGURATION = 'profileRuntimeApi'

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
        project.pluginManager.apply(GroovyPlugin)
        project.pluginManager.apply(JavaLibraryPlugin)

        NamedDomainObjectProvider<Configuration> runtimeOnlyConfiguration = project.configurations.register(PROFILE_API_CONFIGURATION)
        runtimeOnlyConfiguration.configure { Configuration config ->
            config.description = 'Profiles to inherit for this profile project'
            config.canBeConsumed = true
            config.canBeResolved = true

            config.attributes {
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            }
        }

        project.configurations.named("apiElements")
                .configure { it.extendsFrom(runtimeOnlyConfiguration.get()) }

        project.configurations.named("runtimeElements")
                .configure { it.extendsFrom(runtimeOnlyConfiguration.get()) }

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
            Map<String, String> artifactIdMappings = [:]
            project.rootProject.subprojects.each { p ->
                project.evaluationDependsOn(p.path)
                String artifactId = p.findProperty('pomArtifactId')
                if(artifactId) {
                    artifactIdMappings[p.name] = artifactId
                }
            }
            it.projectArtifactIds.set(artifactIdMappings)

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
            it.classpath = project.files(runtimeOnlyConfiguration.get(), project.configurations.named('runtimeClasspath').get())
        }

        project.tasks.named('classes').configure {
            it.dependsOn(compileTask)
        }

        TaskProvider<Jar> jarTask = project.tasks.named('jar', Jar)
        jarTask.configure { Jar jar ->
            jar.dependsOn(processProfileResourcesTask, compileTask)

            jar.from(project.files(project.layout.buildDirectory.dir('resources/profile'), project.layout.buildDirectory.dir('classes/profile')))
            jar.reproducibleFileOrder = true
            jar.preserveFileTimestamps = false
            // to avoid platform specific defaults, set the permissions consistently
            jar.filePermissions { permissions ->
                permissions.unix(0644)
            }
            jar.dirPermissions { permissions ->
                permissions.unix(0755)
            }
        }

        TaskProvider<Jar> sourcesJarTask = project.tasks.register('sourcesJar', Jar)
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
            jar.reproducibleFileOrder = true
            jar.preserveFileTimestamps = false
            // to avoid platform specific defaults, set the permissions consistently
            jar.filePermissions { permissions ->
                permissions.unix(0644)
            }
            jar.dirPermissions { permissions ->
                permissions.unix(0755)
            }
        }

        def profileReadme = project.layout.buildDirectory.file('profile.txt')
        TaskProvider<Task> readmeGeneration = project.tasks.register('profileReadmeGeneration') { Task task ->
            task.group = BUILD_GROUP
            task.description = 'Generates a README file for profile javadoc.'
            task.outputs.file(profileReadme)
            task.doLast {
                def readmeFile = profileReadme.get().asFile
                if (!readmeFile.exists()) {
                    readmeFile.text = "Profiles are templates and do not have javadoc."
                }
            }
        }

        project.tasks.named('javadoc').configure {
            it.enabled = false // will replace the java doc
        }

        TaskProvider<Jar> javadocJarTask = project.tasks.register('javadocJar', Jar)
        javadocJarTask.configure { Jar jar ->
            jar.reproducibleFileOrder = true
            jar.preserveFileTimestamps = false
            // to avoid platform specific defaults, set the permissions consistently
            jar.filePermissions { permissions ->
                permissions.unix(0644)
            }
            jar.dirPermissions { permissions ->
                permissions.unix(0755)
            }

            jar.dependsOn(readmeGeneration)
            // https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources
            jar.from(profileReadme) { CopySpec spec ->
                spec.rename { fileName -> 'README.txt' }
            }
            jar.archiveClassifier.set('javadoc')
            jar.destinationDirectory.set(new File(project.layout.buildDirectory.asFile.get(), 'libs'))
            jar.description = 'Assembles a jar archive containing the profile javadoc.'
            jar.group = BUILD_GROUP
        }
    }
}
