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
package org.grails.gradle.plugin.core

import grails.util.Environment
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.grails.gradle.plugin.util.SourceSets
import org.springframework.boot.gradle.tasks.bundling.BootJar

import javax.inject.Inject

/**
 * A Gradle plugin for Grails plugins
 *
 * @author Graeme Rocher
 * @since 3.0
 *
 */
@CompileStatic
class GrailsPluginGradlePlugin extends GrailsGradlePlugin {

    @Inject
    GrailsPluginGradlePlugin(ToolingModelBuilderRegistry registry) {
        super(registry)
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        checkForConfigurationClash(project)

        configureAstSources(project)

        addGroovyCompilerScript('GrailsPlugin', project) {
            """
                withConfig(configuration) {
                    inline(phase: 'CONVERSION') { source, context, classNode ->
                        classNode.putNodeMetaData('projectVersion', '${project.version}')
                        classNode.putNodeMetaData('projectName', '${project.name}')
                        classNode.putNodeMetaData('isPlugin', 'true')
                    }
                }
            """.stripIndent(16)
        }.configure { Task task ->
            task.inputs.property('version', project.provider { project.version.toString() })
            task.inputs.property('name', project.provider { project.name })
        }

        configureAssembleTask(project)

        configurePluginResources(project)

        configureJarTask(project)

        configureSourcesJarTask(project)

        configureExplodedDirConfiguration(project)
    }

    protected String getDefaultProfile() {
        'web-plugin'
    }

    /**
     * Configures an exploded configuration that can be used to build the classpath of the application from subprojects that are plugins without contructing a JAR file
     *
     * @param project The project instance
     */
    protected void configureExplodedDirConfiguration(Project project) {

        ConfigurationContainer allConfigurations = project.configurations

        def runtimeConfiguration = allConfigurations.findByName('runtimeClasspath')
        def explodedConfig = allConfigurations.create('exploded')
        explodedConfig.extendsFrom(runtimeConfiguration)
        if (Environment.isDevelopmentRun() && isExploded(project)) {
            runtimeConfiguration.artifacts.clear()
            // add the subproject classes as outputs
            TaskContainer allTasks = project.tasks

            GroovyCompile groovyCompile = (GroovyCompile) allTasks.findByName('compileGroovy')
            ProcessResources processResources = (ProcessResources) allTasks.findByName("processResources")

            runtimeConfiguration.artifacts.add(new ExplodedDir(groovyCompile.destinationDir, groovyCompile, processResources))
            explodedConfig.artifacts.add(new ExplodedDir(processResources.destinationDir, groovyCompile, processResources))
        }
    }

    @CompileDynamic
    private boolean isExploded(Project project) {
        Boolean.valueOf(project.properties.getOrDefault('exploded', 'false').toString())
    }

    @Override
    protected Task createBuildPropertiesTask(Project project) {
        // no-op
    }

    @CompileStatic
    protected void configureSourcesJarTask(Project project) {
        def taskContainer = project.tasks
        if (taskContainer.findByName('sourcesJar') == null) {
            def jarTask = taskContainer.create("sourcesJar", Jar)
            jarTask.archiveClassifier.set('sources')
            jarTask.from SourceSets.findMainSourceSet(project).allSource
        }
    }

    @Override
    protected void applySpringBootPlugin(Project project) {
        super.applySpringBootPlugin(project)
        project.tasks.withType(BootJar) { BootJar bootJar ->
            bootJar.enabled = false
        }
    }

    @CompileDynamic
    protected void configureAstSources(Project project) {
        SourceSetContainer sourceSets = SourceSets.findSourceSets(project)
        project.sourceSets {
            ast {
                groovy {
                    compileClasspath += project.configurations.compileClasspath
                }
            }
            main {
                compileClasspath += sourceSets.ast.output
            }
            test {
                compileClasspath += sourceSets.ast.output
            }
        }

        def copyAstClasses = project.tasks.register('copyAstClasses', Copy) {
            it.from sourceSets.ast.output
            it.into project.layout.buildDirectory.dir("classes/groovy/main")
        }

        def taskContainer = project.tasks
        taskContainer.named('classes').configure { it.dependsOn(copyAstClasses) }

        taskContainer.withType(JavaExec).configureEach {
            it.classpath += sourceSets.ast.output
        }

        project.afterEvaluate {
            try {
                taskContainer.getByName('compileWebappGroovyPages').dependsOn(copyAstClasses)
            }
            catch (ignored) {
            }

            Task sourcesJarTask = taskContainer.findByName('sourcesJar')
            if (sourcesJarTask) {
                project.rootProject.logger.lifecycle("Found sources jar task")
                sourcesJarTask.configure {
                    project.rootProject.logger.lifecycle("Including ast in sources jar")
                    from sourceSets.ast.allSource
                }
            } else {
                project.rootProject.logger.lifecycle("No sources jar task found")
            }

            Task javadocTask = taskContainer.findByName('javadoc')
            if (javadocTask) {
                javadocTask.configure {
                    source += sourceSets.ast.allJava
                }
            } else {
                project.rootProject.logger.lifecycle("Warning - a javadocTask was not found, so the ast source will not be included in the javadoc task")
            }

            Task groovydocTask = taskContainer.findByName('groovydoc')
            if (groovydocTask) {
                if (taskContainer.findByName('javadocJar') == null) {
                    taskContainer.create("javadocJar", Jar).configure {
                        archiveClassifier.set('javadoc')
                        from groovydocTask.outputs
                    }.dependsOn(javadocTask)
                }

                groovydocTask.configure {
                    source += sourceSets.ast.allJava
                }
            } else {
                project.rootProject.logger.lifecycle("Warning - a groovydocTask was not found, so the ast source will not be included in the groovydoc task")
            }
        }
    }

    protected void configureAssembleTask(Project project) {
        // Assemble task in Grails Plugins should only produce a plain jar
        project.tasks.named('assemble') { Task assembleTask ->
            def disabledTasks = [
                    'bootDistTar',
                    'bootDistZip',
                    'bootJar',
                    'bootStartScripts',
                    'bootWar',
                    'bootWarMainClassName',
                    'distTar',
                    'distZip',
                    'startScripts',
                    'war'
            ]
            disabledTasks.each { String disabledTaskName ->
                project.tasks.findByName(disabledTaskName)?.enabled = false
            }
            // By default the assemble task does not create a plain jar
            assembleTask.dependsOn('jar')
        }
    }

    protected void configureJarTask(Project project) {
        project.tasks.named('jar', Jar) { Jar jarTask ->
            jarTask.enabled = true
            jarTask.archiveClassifier.set('') // Remove '-plain' suffix from jar file name
            jarTask.exclude(
                    'application.groovy',
                    'application.yml',
                    'logback.groovy',
                    'logback.xml',
                    'logback-spring.xml'
            )
        }
    }

    @CompileDynamic
    protected void configurePluginResources(Project project) {
        project.afterEvaluate() {
            ProcessResources processResources = (ProcessResources) project.tasks.getByName('processResources')

            def processResourcesDependencies = []

            processResourcesDependencies << project.task(type: Copy, "copyCommands") {
                from "${project.projectDir}/src/main/scripts"
                into "${processResources.destinationDir}/META-INF/commands"
            }

            processResourcesDependencies << project.task(type: Copy, "copyTemplates") {
                from "${project.projectDir}/src/main/templates"
                into "${processResources.destinationDir}/META-INF/templates"
            }
            processResources.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            processResources.dependsOn(*processResourcesDependencies)
            project.processResources {
                exclude "spring/resources.groovy"
                exclude "**/*.gsp"
            }
        }
    }

    protected void checkForConfigurationClash(Project project) {
        File yamlConfig = new File(project.projectDir, "grails-app/conf/plugin.yml")
        File groovyConfig = new File(project.projectDir, "grails-app/conf/plugin.groovy")
        if (yamlConfig.exists() && groovyConfig.exists()) {
            throw new RuntimeException("A plugin may define a plugin.yml or a plugin.groovy, but not both")
        }
    }

    static class ExplodedDir implements PublishArtifact {
        final String extension = ""
        final String type = "dir"
        final Date date = new Date()

        final File file
        final TaskDependency buildDependencies

        ExplodedDir(File file, Object... tasks) {
            this.file = file
            this.buildDependencies = new DefaultTaskDependency().add(tasks)
        }

        @Override
        String getName() {
            file.name
        }

        @Override
        String getClassifier() {
            ""
        }
    }
}
