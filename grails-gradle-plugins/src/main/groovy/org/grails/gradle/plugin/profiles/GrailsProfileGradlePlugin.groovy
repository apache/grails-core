/*
 * Copyright 2015-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gradle.plugin.profiles

import grails.io.IOUtils
import grails.util.BuildSettings
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.grails.cli.profile.commands.script.GroovyScriptCommand
import org.grails.gradle.plugin.profiles.tasks.ProfileCompilerTask

import static org.gradle.api.plugins.BasePlugin.BUILD_GROUP

/**
 * A plugin that is capable of compiling a Grails profile into a JAR file for distribution
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class GrailsProfileGradlePlugin implements Plugin<Project> {

    static final String CONFIGURATION_NAME = 'grails'

    public static final String RUNTIME_CONFIGURATION = "profileRuntimeOnly"

    @Override
    void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class)
        project.configurations.create(CONFIGURATION_NAME)
        def profileConfiguration = project.configurations.create(RUNTIME_CONFIGURATION)

        profileConfiguration.resolutionStrategy.eachDependency {
            DependencyResolveDetails details = (DependencyResolveDetails)it
            def requested = details.requested
            def group = requested.group
            def version = requested.version

            if(!group || !version) {
                group = group ?: "org.grails.profiles"
                version = version ?: BuildSettings.grailsVersion

                details.useTarget(group: group, name: requested.name,version:version)
            }
        }

        def profileYml = project.file("profile.yml")

        def commandsDir = project.file("commands")
        def resourcesDir = new File(project.layout.buildDirectory.getAsFile().get(), "resources/profile")
        def templatesDir = project.file("templates")
        def skeletonsDir = project.file("skeleton")
        def featuresDir = project.file("features")

        def spec1 = project.copySpec { CopySpec spec ->
            spec.from(commandsDir)
            spec.exclude("*.groovy")
            spec.into("commands")
        }
        def spec2 = project.copySpec { CopySpec spec ->
            spec.from(templatesDir)
            spec.into("templates")
        }
        def spec4 = project.copySpec { CopySpec spec ->
            spec.from(featuresDir)
            spec.into("features")
        }
        def spec3 = project.copySpec { CopySpec spec ->
            spec.from(skeletonsDir)
            spec.into("skeleton")
        }

        def processProfileResourcesTask = project.tasks.register("processProfileResources", Copy, { Copy c ->
            c.group = "build"
            c.with(spec1, spec2, spec3, spec4)
            c.into(new File(resourcesDir, "/META-INF/grails-profile"))
        })

        def classsesDir = new File(project.layout.buildDirectory.getAsFile().get(), "classes/profile")
        def compileProfileTask = project.tasks.register("compileProfile", ProfileCompilerTask, { ProfileCompilerTask task ->
            task.group = "build"
            task.profileDestinationDir = classsesDir
            task.source = commandsDir
            task.config = profileYml
            if(templatesDir.exists()) {
                task.templatesDir = templatesDir
            }
            task.classpath = project.configurations.getByName(RUNTIME_CONFIGURATION) + project.files(IOUtils.findJarFile(GroovyScriptCommand))
        })

        def profileJarTask = project.tasks.register("profileJar", Jar, { Jar jar ->
            jar.dependsOn(processProfileResourcesTask, compileProfileTask)

            def jarTask = project.tasks.findByName("jar")
            if(jarTask){
                jar.dependsOn(jarTask)
            }

            jar.from(resourcesDir)
            jar.from(classsesDir)
            jar.destinationDirectory.set(new File(project.layout.buildDirectory.getAsFile().get(), "libs"))
            jar.setDescription("Assembles a jar archive containing the profile classes.")
            jar.setGroup(BUILD_GROUP)

//            ArchivePublishArtifact jarArtifact = new ArchivePublishArtifact(jar)
//            project.artifacts.add(CONFIGURATION_NAME, jarArtifact)
        })

        project.tasks.register("sourcesProfileJar", Jar, { Jar jar ->
            jar.from(commandsDir)
            if(profileYml.exists()) {
                jar.from(profileYml)
            }
            jar.from(templatesDir) { CopySpec spec ->
                spec.into("templates")
            }
            jar.from(skeletonsDir) { CopySpec spec ->
                spec.into("skeleton")
            }
            jar.archiveClassifier.set("sources")
            jar.destinationDirectory.set(new File(project.layout.buildDirectory.getAsFile().get(), "libs"))
            jar.setDescription("Assembles a jar archive containing the profile sources.")
            jar.setGroup(BUILD_GROUP)

        })
        project.tasks.findByName("assemble").dependsOn profileJarTask
    }
}
