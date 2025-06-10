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
package org.grails.gradle.plugin.profiles.tasks

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.grails.cli.profile.commands.script.GroovyScriptCommandTransform
import org.grails.gradle.plugin.profiles.GrailsProfileGradlePlugin
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer

import javax.inject.Inject

/**
 * Compiles the classes for a profile
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
@CacheableTask
class ProfileCompilerTask extends AbstractCompile {

    public static final String DEFAULT_COMPATIBILITY = JavaVersion.VERSION_17.majorVersion
    public static final String PROFILE_NAME = 'name'
    public static final String PROFILE_COMMANDS = 'commands'

    @Inject
    ProfileCompilerTask(ObjectFactory objectFactory, Project project) {
        group = BasePlugin.BUILD_GROUP
        sourceCompatibility = DEFAULT_COMPATIBILITY
        targetCompatibility = DEFAULT_COMPATIBILITY

        destinationDirectory = objectFactory.directoryProperty().convention(project.layout.buildDirectory.dir('classes/profile'))
        profileFile = objectFactory.fileProperty().convention(project.layout.buildDirectory.file('classes/profile/META-INF/grails-profile/profile.yml'))
        config = objectFactory.fileProperty()
        templatesDirectory = objectFactory.directoryProperty()
        skeletonDirectory = objectFactory.directoryProperty()
        commandsDirectory = objectFactory.directoryProperty()
    }

    @OutputDirectory
    final DirectoryProperty destinationDirectory

    @OutputFile
    final RegularFileProperty profileFile

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    final RegularFileProperty config

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    final DirectoryProperty templatesDirectory

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    final DirectoryProperty skeletonDirectory

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    final DirectoryProperty commandsDirectory

    // commands map to source property

    private Yaml createYamlHandler() {
        def options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(options), options)
    }

    @TaskAction
    void execute() {
        // Ensure output locations exist
        Directory destination = destinationDirectory.get()
        destination.asFile.mkdirs()

        RegularFile profileRegularFile = profileFile.get()
        profileRegularFile.asFile.parentFile.mkdirs()

        File configFile = this.config.getOrNull()?.asFile
        boolean profileYmlExists = configFile?.exists()

        Yaml yaml = createYamlHandler()
        LinkedHashMap<String, Object> profileData
        if (profileYmlExists) {
            profileData = (LinkedHashMap<String, Object>) configFile.withReader { BufferedReader r ->
                yaml.load(r)
            }
        } else {
            profileData = new LinkedHashMap<String, Object>()
        }
        profileData.put(PROFILE_NAME, project.findProperty('pomArtifactId') ?: project.name)

        if (!profileData.containsKey('extends')) {
            List<String> dependencies = []
            project.configurations.named(GrailsProfileGradlePlugin.PROFILE_API_CONFIGURATION).get().dependencies.all { Dependency d ->
                String profileName = d.name
                if (d instanceof DefaultProjectDependency) {
                    DefaultProjectDependency projectDependency = (DefaultProjectDependency) d
                    profileName = projectDependency.dependencyProject.findProperty('pomArtifactId') ?: profileName
                }

                dependencies.add("${d.group}:${profileName}:${d.version}".toString())
            }
            profileData.put('extends', dependencies.join(','))
        }

        List<File> groovySourceFiles = (commandsDirectory.getOrNull()?.asFileTree?.findAll { File f ->
            f.name.endsWith('.groovy')
        } ?: []) as List<File>
        List<File> ymlSourceFiles = (commandsDirectory.getOrNull()?.asFileTree?.findAll { File f ->
            f.name.endsWith('.yml')
        } ?: []) as List<File>

        Map<String, String> commandNames = [:]
        for (File f in groovySourceFiles) {
            def fn = f.name
            commandNames.put(fn - '.groovy', fn)
        }
        for (File f in ymlSourceFiles) {
            def fn = f.name
            commandNames.put(fn - '.yml', fn)
        }

        if (commandNames) {
            profileData.put(PROFILE_COMMANDS, commandNames)
        }

        if (profileYmlExists) {
            File parentDir = configFile.parentFile.canonicalFile
            File[] featureDirs = new File(parentDir, 'features').listFiles({ File f -> f.isDirectory() && !f.name.startsWith('.') } as FileFilter)
            if (featureDirs) {
                LinkedHashMap map = (LinkedHashMap) profileData.get('features')
                if (map == null) {
                    map = [:] as LinkedHashMap
                    profileData.put('features', map)
                }
                List featureNames = []
                for (f in featureDirs) {
                    featureNames.add f.name
                }
                if (featureNames) {
                    map.put('provided', featureNames.sort())
                }
                profileData.put('features', map)
            }
        }

        List<String> templates = []
        File templatesDir = templatesDirectory.getOrNull()?.asFile
        if (templatesDir?.exists()) {
            project.fileTree(templatesDir).visit { FileVisitDetails f ->
                if (!f.isDirectory() && !f.name.startsWith('.')) {
                    templates.add f.relativePath.pathString
                }
            }
        }

        if (templates) {
            profileData.put('templates', templates.sort())
        }

        profileRegularFile.asFile.withWriter { BufferedWriter w ->
            yaml.dump(profileData, w)
        }

        if (groovySourceFiles) {
            CompilerConfiguration configuration = new CompilerConfiguration()
            configuration.setScriptBaseClass('org.grails.cli.profile.commands.script.GroovyScriptCommand')
            configuration.setTargetDirectory(destination.getAsFile())
            configuration.setClasspath(classpath.asPath)

            def importCustomizer = new ImportCustomizer()
            importCustomizer.addStarImports('org.grails.cli.interactive.completers')
            importCustomizer.addStarImports('grails.util')
            importCustomizer.addStarImports('grails.codegen.model')
            configuration.addCompilationCustomizers(importCustomizer, new ASTTransformationCustomizer(new GroovyScriptCommandTransform()))

            for (File source in groovySourceFiles) {
                CompilationUnit compilationUnit = new CompilationUnit(configuration)
                configuration.compilationCustomizers.clear()
                configuration.compilationCustomizers.addAll(importCustomizer, new ASTTransformationCustomizer(new GroovyScriptCommandTransform()))
                compilationUnit.addSource(source)
                compilationUnit.compile()
            }
        }
    }
}
