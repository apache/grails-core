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
package org.grails.gradle.plugin.commands

import java.util.concurrent.Callable
import java.util.jar.JarFile

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.attributes.Category
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.util.PatternFilterable

import grails.util.BuildSettings
import grails.util.Environment
import grails.util.GrailsNameUtils
import org.apache.grails.gradle.model.cli.ToolBuildNames
import org.apache.grails.gradle.model.cli.ToolDescriptor
import org.grails.gradle.plugin.core.GrailsExtension
import org.grails.gradle.plugin.core.GrailsGradlePlugin
import org.grails.gradle.plugin.core.TestPhaseNames
import org.grails.gradle.plugin.util.SourceSets
import org.grails.io.support.FactoriesLoaderSupport
import org.grails.build.parsing.CommandLineParser

/**
 * Configures the tools of a Grails application or plugin project, the consuming side: the source
 * sets of the tools ({@code src/cli} and {@code src/standalone}, see {@link ToolSourceSets}), the
 * scopes {@code grailsCli} and {@code grailsCliStandalone} (with automatic discovery of the
 * companions {@code -cli} and {@code -cli-standalone} advertised by the dependency graph), the
 * per-tool tasks, the task {@code runTool} and its deprecated other name {@code runCommand}, the
 * catalog of the tools and the record of the standalone tools, and the interactive
 * {@code console}/{@code shell} tasks. Applied automatically by the Grails Gradle plugin.
 *
 * @since 8.0
 */
@CompileStatic
class GrailsCliGradlePlugin implements Plugin<Project> {

    public static final String APPLICATION_CONTEXT_COMMAND_CLASS = ToolDescriptor.FACTORIES_KEY_COMMAND

    /**
     * The task group the tool tasks are placed in. A group makes them visible in the default
     * {@code gradle tasks} listing (and therefore to shell tab-completion).
     */
    public static final String GRAILS_COMMAND_GROUP = 'Grails'

    /**
     * The dependency bucket carrying the dependencies only the tools the build runs have, and the
     * {@code -cli} companions of plugins: on the class paths of {@code src/cli}, of the tests and of
     * the tool tasks, but never on the main compile or runtime class path, {@code bootRun}, or
     * packaged artifacts.
     */
    public static final String GRAILS_CLI_CONFIGURATION = ToolBuildNames.GRAILS_CLI_CONFIGURATION

    /** The resolvable view of {@link #GRAILS_CLI_CONFIGURATION} and {@link #GRAILS_CLI_STANDALONE_CONFIGURATION} the tool tasks run with */
    public static final String GRAILS_CLI_CLASSPATH_CONFIGURATION = ToolBuildNames.GRAILS_CLI_CLASSPATH_CONFIGURATION

    /**
     * The dependency bucket carrying the {@code -cli-standalone} companions of plugins and the
     * libraries only the tools that run as a process of their own have: on the class paths of
     * {@code src/standalone}, of {@code src/cli}, of the tests and of the tool tasks, and the only
     * dependencies a standalone tool is started with.
     */
    public static final String GRAILS_CLI_STANDALONE_CONFIGURATION = ToolBuildNames.GRAILS_CLI_STANDALONE_CONFIGURATION

    /** The resolvable view of {@link #GRAILS_CLI_STANDALONE_CONFIGURATION} alone */
    public static final String GRAILS_CLI_STANDALONE_CLASSPATH_CONFIGURATION = ToolBuildNames.GRAILS_CLI_STANDALONE_CLASSPATH_CONFIGURATION

    /**
     * Set this project property to {@code false} to stop the plugin from auto-provisioning the
     * tool API, the runner and the companions of plugins; equivalent to
     * {@code grails { cliAutoProvision = false }}.
     */
    public static final String GRAILS_CLI_AUTO_PROVISION_PROPERTY = 'grailsCliAutoProvision'

    /** The internal probe configuration used to detect the companions advertised by dependencies */
    public static final String GRAILS_CLI_DETECT_CONFIGURATION = 'grailsCliDetect'

    /**
     * The internal probe configuration used to detect the {@code -cli-standalone} companions advertised by
     * dependencies and by what {@code grailsCli} brings
     */
    public static final String GRAILS_CLI_STANDALONE_DETECT_CONFIGURATION = 'grailsCliStandaloneDetect'

    /**
     * The internal bucket that gives every class path of the tools the versions the project manages:
     * the platforms and the dependency constraints declared on the configurations of the main source
     * set, and none of their dependencies.
     */
    public static final String GRAILS_TOOL_VERSIONS_CONFIGURATION = 'grailsToolVersions'

    /** The resolvable configurations of the tools that have the versions of {@link #GRAILS_TOOL_VERSIONS_CONFIGURATION}. */
    public static final List<String> TOOL_CLASSPATH_CONFIGURATIONS = [
            ToolBuildNames.GRAILS_CLI_CLASSPATH_CONFIGURATION, ToolBuildNames.GRAILS_CLI_STANDALONE_CLASSPATH_CONFIGURATION,
            'cliCompileClasspath', 'cliRuntimeClasspath', 'standaloneCompileClasspath', 'standaloneRuntimeClasspath',
    ].asImmutable()

    /** What {@code runCommand}, the deprecated other name of {@code runTool}, says each time it runs. */
    public static final String RUN_COMMAND_DEPRECATED = 'runCommand is deprecated: use runTool'

    /** What {@code runScript}, which runs a script against the application context, says each time it runs. */
    public static final String RUN_SCRIPT_DEPRECATED = 'runScript is deprecated: write a tool and run it with runTool'

    /** What {@code runScriptCommand}, which runs a script command, says each time it runs. */
    public static final String SCRIPT_COMMANDS_DEPRECATED = 'Script commands are deprecated: write a tool and run it with runTool'

    /** The project property that turned on the bridge for the commands of Grails 7, which was removed. */
    @PackageScope
    static final String LEGACY_COMMAND_SUPPORT_PROPERTY = 'grailsLegacyCommandSupport'

    @Override
    void apply(Project project) {
        // self-sufficient when applied standalone: the auto-provisioning behavior is configured
        // through the `grails` extension, normally registered by the Grails Gradle plugin
        if (project.extensions.findByName('grails') == null) {
            project.extensions.create('grails', GrailsExtension, project)
        }

        refuseLegacyCommandSupport(project)

        configureGrailsCliConfiguration(project)

        configureToolSourceSets(project)

        configureConsoleTask(project)

        configureApplicationCommands(project)

        configureRunScript(project)

        configureRunTool(project)

        configureCommandCatalog(project)

        configureRunScriptCommand(project)
    }

    /**
     * Fails the build that still asks for the bridge that ran the commands of Grails 7 with the
     * project property {@code grailsLegacyCommandSupport}: the bridge was removed, and a build that
     * asked for it would otherwise go on without the commands it expects.
     */
    protected void refuseLegacyCommandSupport(Project project) {
        Object asked = project.findProperty(LEGACY_COMMAND_SUPPORT_PROPERTY)
        if (asked != null && Boolean.parseBoolean(asked.toString())) {
            throw new GradleException(GrailsExtension.LEGACY_COMMAND_SUPPORT_REMOVED)
        }
    }

    /**
     * Registers the task that runs a script command of the project or of one of its plugins (see
     * {@link ScriptCommandTask}). Its class path is what the project depends on at run time and
     * {@code grailsCliClasspath}, without the output of the project: a script is run without
     * compiling the project it is run in, which it may be about to generate code for. The projects
     * that one depends on are on its run time class path and are built.
     */
    protected void configureRunScriptCommand(Project project) {
        project.pluginManager.withPlugin('java') {
            if (project.tasks.names.contains(ScriptCommandTask.TASK_NAME)) {
                project.logger.warn('Project {} already has a task named {}, so the Grails Gradle plugin does not register its own and ' +
                        'the script commands of the project are not run by it. Give the task another name.',
                        project.path, ScriptCommandTask.TASK_NAME)
                return
            }
            project.tasks.register(ScriptCommandTask.TASK_NAME, ScriptCommandTask) { ScriptCommandTask task ->
                task.group = GRAILS_COMMAND_GROUP
                task.description = 'Runs a script command: src/main/scripts/<name>.groovy of the project, or META-INF/commands/<name>.groovy of a plugin. ' +
                        'Script commands are deprecated: write a tool and run it with runTool'
                task.doFirst(new WarnOfDeprecation(SCRIPT_COMMANDS_DEPRECATED))
                task.classpath = project.files(
                        project.configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME),
                        project.configurations.named(GRAILS_CLI_CLASSPATH_CONFIGURATION))
                // the directory of the project, for a script of a project that is not the one the build was started in
                task.systemProperty('base.dir', project.layout.projectDirectory.asFile.absolutePath)
                task.standardInput = System.in
            }
        }
    }

    /**
     * Registers the task that writes the catalog of the tools of the project (see
     * {@link GrailsToolCatalogTask}). It reads the output of the three source sets of the project,
     * each with the target its tools have to have, and the class paths the tools of plugins come
     * from: {@code runtimeClasspath} and {@code grailsCliClasspath}.
     *
     * <p>The task finalizes {@code classes} and {@code cliClasses}: every build that compiles the
     * project, whether it runs the application, its tests or an archive, or an IDE builds it, leaves
     * the catalog current, and the command line has the tools of the application and its plugins by
     * their names without a step of its own. It does not finalize {@code standaloneClasses}: the
     * catalog reads the output of the application, and the record of the standalone tools, which is
     * refreshed by compiling {@code src/standalone} alone, has to be written while the application
     * does not compile. A finalizer adds nothing that the task it finalizes waits for, and is up to
     * date when nothing it reads changed. The class paths are resolved leniently, since compiling the
     * project does not need them: one that does not resolve completely gives a catalog of what does,
     * which says what does not and is not complete, and the build goes on.</p>
     */
    protected void configureCommandCatalog(Project project) {
        project.pluginManager.withPlugin('java') {
            if (project.tasks.names.contains(GrailsToolCatalogTask.TASK_NAME)) {
                project.logger.warn('Project {} already has a task named {}, so the Grails Gradle plugin does not register its own and ' +
                        'the catalog of the tools of the project is not written. Give the task another name.',
                        project.path, GrailsToolCatalogTask.TASK_NAME)
                return
            }
            SourceSet cli = ToolSourceSets.cli(project)
            TaskProvider<GrailsToolCatalogTask> catalog = project.tasks.register(GrailsToolCatalogTask.TASK_NAME, GrailsToolCatalogTask) { GrailsToolCatalogTask task ->
                task.group = GRAILS_COMMAND_GROUP
                task.description = 'Writes the catalog of the tools of the project and its plugins, without loading or running them'
                SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
                FileCollection mainOutput = registrationsOutput(project, mainSourceSet)
                FileCollection cliOutput = registrationsOutput(project, ToolSourceSets.cli(project))
                FileCollection standaloneOutput = registrationsOutput(project, ToolSourceSets.standalone(project))
                task.applicationOutput.from(mainOutput)
                task.cliOutput.from(cliOutput)
                task.standaloneOutput.from(standaloneOutput)
                task.applicationRegistrations.from(project.files(mainOutput, cliOutput, standaloneOutput).asFileTree.matching { PatternFilterable patterns ->
                    patterns.include(GrailsToolCatalogTask.REGISTRATION_PATTERNS)
                })
                for (String configurationName : [JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, GRAILS_CLI_CLASSPATH_CONFIGURATION]) {
                    // leniently: compiling the project does not need the class path its tools run with, and a build
                    // that compiles it does not fail because that does not resolve; the catalog lists what does, and
                    // says what does not (the configuration cache resolves it when it stores the task)
                    ArtifactCollection artifacts = project.configurations.getByName(configurationName).incoming
                            .artifactView({ ArtifactView.ViewConfiguration view -> view.lenient(true) } as Action<ArtifactView.ViewConfiguration>).artifacts
                    task.commandClasspath.from(artifacts.artifactFiles)
                    task.originsByPath.putAll(artifacts.resolvedArtifacts.map(new GrailsToolCatalogTask.OriginsByPath()))
                    task.unresolved.put(configurationName, project.provider(new GrailsToolCatalogTask.UnresolvedLines(artifacts)))
                }
                task.origins.set(task.originsByPath.map(new GrailsToolCatalogTask.Origins()))
                // the names of the Test tasks there are when the task is about to run: none is created for them
                task.testPhases.set(project.provider({ TestPhaseNames.phases(project.tasks.withType(Test).names) } as Callable<Map<String, String>>))
                // in the directory of the project and not in its build directory, which the build can move: the
                // command line reads the catalog there without running the build to ask where that is
                task.catalogFile.convention(project.layout.projectDirectory.file(GrailsToolCatalogTask.CATALOG_LOCATION))
                task.ignoreFile.convention(project.layout.projectDirectory.file(GrailsToolCatalogTask.IGNORE_LOCATION))
                task.projectDirectory.convention(project.layout.projectDirectory)
            }
            for (String finalized : [JavaPlugin.CLASSES_TASK_NAME, cli.classesTaskName]) {
                project.tasks.named(finalized).configure { Task classes ->
                    classes.finalizedBy(catalog)
                }
            }
            configureStandaloneTools(project, catalog)
        }
    }

    // where the compiler and the resources of a source set put registrations and descriptors: not the whole output
    // of the source set, which would have the catalog wait for compiled views as well
    private static FileCollection registrationsOutput(Project project, SourceSet sourceSet) {
        project.files(sourceSet.output.classesDirs, project.tasks.named(sourceSet.processResourcesTaskName))
    }

    /**
     * Registers the task that writes down the class path of the tools that run as a process of
     * their own, without Gradle (see {@link GrailsStandaloneToolsTask}): the output of
     * {@code src/standalone} for the tools of the project itself, and the run time class path of that
     * source set, which is {@code grailsCliStandalone} and, in a plugin, the libraries of
     * {@code standaloneImplementation}. Nothing of the main source set, of {@code src/cli} or of
     * {@code grailsCli} is in it, and the task depends on nothing but the compilation of
     * {@code src/standalone}: it is written while the application does not compile.
     *
     * <p>The task finalizes the task that writes the catalog and {@code standaloneClasses}, so every
     * build that compiles the project leaves the file current. The class path is resolved leniently,
     * as it is for the catalog: one that does not resolve completely is not written down, and the
     * build goes on.</p>
     */
    protected void configureStandaloneTools(Project project, TaskProvider<GrailsToolCatalogTask> catalog) {
        if (project.tasks.names.contains(GrailsStandaloneToolsTask.TASK_NAME)) {
            project.logger.warn('Project {} already has a task named {}, so the Grails Gradle plugin does not register its own and ' +
                    'the class path of the tools that run without Gradle is not written down. Give the task another name.',
                    project.path, GrailsStandaloneToolsTask.TASK_NAME)
            return
        }
        SourceSet standalone = ToolSourceSets.standalone(project)
        TaskProvider<GrailsStandaloneToolsTask> standaloneTools = project.tasks.register(GrailsStandaloneToolsTask.TASK_NAME, GrailsStandaloneToolsTask) { GrailsStandaloneToolsTask task ->
            task.group = GRAILS_COMMAND_GROUP
            task.description = 'Writes down the class path of the tools that the grails command line runs without Gradle'
            // the directories of the classes and of the resources, by the tasks that write them, and not the output of the
            // source set as a whole
            task.standaloneClasspath.from(registrationsOutput(project, standalone))
            String configurationName = standalone.runtimeClasspathConfigurationName
            ArtifactCollection artifacts = project.configurations.getByName(configurationName).incoming
                    .artifactView({ ArtifactView.ViewConfiguration view -> view.lenient(true) } as Action<ArtifactView.ViewConfiguration>).artifacts
            task.dependencyClasspath.from(artifacts.artifactFiles)
            task.originsByPath.putAll(artifacts.resolvedArtifacts.map(new GrailsToolCatalogTask.OriginsByPath()))
            task.projectOutputs.addAll(artifacts.resolvedArtifacts.map(new GrailsStandaloneToolsTask.ProjectOutputs()))
            task.unresolved.put(configurationName, project.provider(new GrailsToolCatalogTask.UnresolvedLines(artifacts)))
            // the directories there are when the task is about to run, as whole paths: the command line compares the file with what is in them
            task.sourceDirectories.set(project.provider({
                standalone.allSource.srcDirs.collect { File directory -> directory.absolutePath }.sort()
            } as Callable<List<String>>))
            task.javaVersion.set(project.provider({
                Integer.valueOf(project.extensions.getByType(JavaPluginExtension).targetCompatibility.majorVersion)
            } as Callable<Integer>))
            task.projectDirectory.convention(project.layout.projectDirectory)
            task.standaloneToolsFile.convention(project.layout.projectDirectory.file(GrailsStandaloneToolsTask.LOCATION))
        }
        catalog.configure { GrailsToolCatalogTask task ->
            task.finalizedBy(standaloneTools)
        }
        project.tasks.named(standalone.classesTaskName).configure { Task classes ->
            classes.finalizedBy(standaloneTools)
        }
    }

    /**
     * Registers the dependency buckets {@code grailsCli} and {@code grailsCliStandalone} and their
     * resolvable views {@code grailsCliClasspath} (both) and {@code grailsCliStandaloneClasspath}
     * (the second alone). {@code grailsCli} carries the companions {@code -cli} of plugins and what
     * only the tools the build runs need; {@code grailsCliStandalone} the companions
     * {@code -cli-standalone} and what only the tools that run as a process of their own need. Both
     * are on the class paths of the tests and of {@code src/cli}, and {@code grailsCliStandalone}
     * alone on that of {@code src/standalone}. Neither is on the main compile class path or on
     * {@code runtimeClasspath}, and therefore in {@code bootRun}, {@code bootJar} or {@code bootWar}.
     * Every class path of the tools has the platforms and dependency constraints of the main
     * configurations ({@link #declareVersionsOfMain}), so their dependencies are declared without a
     * version, as those of the application are.
     */
    protected void configureGrailsCliConfiguration(Project project) {
        ConfigurationContainer configurations = project.configurations
        if (configurations.names.contains(GRAILS_CLI_CONFIGURATION)) {
            return
        }

        Configuration grailsCli = configurations.create(GRAILS_CLI_CONFIGURATION)
        grailsCli.canBeResolved = false
        grailsCli.canBeConsumed = false
        grailsCli.description = 'The -cli companions of plugins and the dependencies only the tools the build runs need: on the class paths of src/cli, ' +
                'of the tests and of the tool tasks, but never on the main class paths, bootRun, or packaged artifacts.'

        Configuration grailsCliStandalone = configurations.create(GRAILS_CLI_STANDALONE_CONFIGURATION)
        grailsCliStandalone.canBeResolved = false
        grailsCliStandalone.canBeConsumed = false
        grailsCliStandalone.description = 'The -cli-standalone companions of plugins and the dependencies only the tools that run as a process of their own need: ' +
                'on the class paths of src/standalone, src/cli, the tests and the tool tasks, but never on the main class paths, bootRun, or packaged artifacts.'

        // the tests exercise tools inside the test JVM; matching configurations that appear later (e.g. the
        // integrationTest pair) are included as they are created
        configurations.matching { Configuration it ->
            it.name in ['testCompileClasspath', 'testRuntimeClasspath', 'integrationTestCompileClasspath', 'integrationTestRuntimeClasspath',
                        'cliCompileClasspath', 'cliRuntimeClasspath']
        }.configureEach { Configuration it ->
            it.extendsFrom(grailsCli, grailsCliStandalone)
        }
        configurations.matching { Configuration it ->
            it.name in ['standaloneCompileClasspath', 'standaloneRuntimeClasspath']
        }.configureEach { Configuration it ->
            it.extendsFrom(grailsCliStandalone)
        }

        Configuration grailsCliClasspath = configurations.create(GRAILS_CLI_CLASSPATH_CONFIGURATION)
        grailsCliClasspath.extendsFrom(grailsCli, grailsCliStandalone)
        grailsCliClasspath.canBeResolved = true
        grailsCliClasspath.canBeConsumed = false
        grailsCliClasspath.description = 'Resolvable view of grailsCli and grailsCliStandalone the tool tasks run with.'

        Configuration grailsCliStandaloneClasspath = configurations.create(GRAILS_CLI_STANDALONE_CLASSPATH_CONFIGURATION)
        grailsCliStandaloneClasspath.extendsFrom(grailsCliStandalone)
        grailsCliStandaloneClasspath.canBeResolved = true
        grailsCliStandaloneClasspath.canBeConsumed = false
        grailsCliStandaloneClasspath.description = 'Resolvable view of grailsCliStandalone alone: the dependencies a standalone tool is started with.'

        // a dependency of a tool is written without a version, as every other dependency of the project is: the class
        // paths of the tools have the platforms and constraints of the main configurations, but not their dependencies
        Configuration toolVersions = configurations.create(GRAILS_TOOL_VERSIONS_CONFIGURATION)
        toolVersions.canBeResolved = false
        toolVersions.canBeConsumed = false
        toolVersions.description = 'Internal: the platforms and dependency constraints of the main configurations, without their dependencies, ' +
                'for the class paths of the tools.'
        toolVersions.withDependencies { DependencySet dependencies ->
            declareVersionsOfMain(project, toolVersions, dependencies)
        }
        configurations.matching { Configuration it -> it.name in TOOL_CLASSPATH_CONFIGURATIONS }.configureEach { Configuration it ->
            it.extendsFrom(toolVersions)
        }

        Configuration grailsCliDetect = configurations.create(GRAILS_CLI_DETECT_CONFIGURATION)
        grailsCliDetect.canBeResolved = true
        grailsCliDetect.canBeConsumed = false
        grailsCliDetect.description = 'Internal probe used to discover the companions advertised by dependencies.'
        // the -cli-standalone companions are also those of the plugins grailsCli brings: a build that names the -cli of a
        // plugin gets the plugin through it, and so its standalone tools on the class path standalone tools are started with
        Configuration grailsCliStandaloneDetect = configurations.create(GRAILS_CLI_STANDALONE_DETECT_CONFIGURATION)
        grailsCliStandaloneDetect.canBeResolved = true
        grailsCliStandaloneDetect.canBeConsumed = false
        grailsCliStandaloneDetect.description = 'Internal probe used to discover the -cli-standalone companions advertised by dependencies and by what grailsCli brings.'
        grailsCliStandaloneDetect.extendsFrom(grailsCli)
        for (String bucket : ['api', 'implementation', 'runtimeOnly']) {
            configurations.matching { Configuration it -> it.name == bucket }.configureEach { Configuration it ->
                grailsCliDetect.extendsFrom(it)
                grailsCliStandaloneDetect.extendsFrom(it)
            }
        }

        // computed when the configuration is first resolved, so every dependency (and the
        // extension configuration) declared by the build script is visible
        grailsCli.withDependencies { DependencySet dependencies ->
            autoProvisionCliDependencies(project, dependencies)
        }
        grailsCliStandalone.withDependencies { DependencySet dependencies ->
            autoProvisionStandaloneDependencies(project, dependencies)
        }
    }

    /**
     * Declares on {@link #GRAILS_TOOL_VERSIONS_CONFIGURATION}, when a class path of the tools is first
     * resolved, the versions the project manages: a copy of every platform (and enforced platform)
     * declared on a configuration the main compile or run time class path is made of, and the
     * dependency constraints declared there. The dependencies of those configurations themselves are
     * not declared: a standalone tool is started without the dependencies of the application.
     *
     * @param project the project
     * @param toolVersions the bucket the class paths of the tools extend
     * @param dependencies the dependencies of the bucket
     */
    protected static void declareVersionsOfMain(Project project, Configuration toolVersions, DependencySet dependencies) {
        SourceSetContainer sourceSets = project.extensions.findByType(SourceSetContainer)
        SourceSet main = sourceSets?.findByName(SourceSet.MAIN_SOURCE_SET_NAME)
        if (main == null) {
            return
        }
        Set<Configuration> declaredOnMain = new LinkedHashSet<Configuration>()
        for (String classpath : [main.compileClasspathConfigurationName, main.runtimeClasspathConfigurationName]) {
            Configuration configuration = project.configurations.findByName(classpath)
            if (configuration != null) {
                declaredOnMain.addAll(configuration.hierarchy)
            }
        }
        for (Configuration configuration : declaredOnMain) {
            if (!configuration.canBeDeclared || configuration.is(toolVersions)) {
                continue
            }
            for (Dependency dependency : configuration.dependencies) {
                if (isPlatform(dependency)) {
                    dependencies.add(dependency.copy())
                }
            }
            toolVersions.dependencyConstraints.addAll(configuration.dependencyConstraints)
        }
    }

    /** Whether the dependency is a platform, declared with {@code platform()} or {@code enforcedPlatform()}. */
    private static boolean isPlatform(Dependency dependency) {
        if (!(dependency instanceof ModuleDependency)) {
            return false
        }
        Category category = ((ModuleDependency) dependency).attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)
        category != null && (category.name == Category.REGULAR_PLATFORM || category.name == Category.ENFORCED_PLATFORM)
    }

    /**
     * Gives every project the source sets of its tools ({@link ToolSourceSets}) and their class paths.
     *
     * <p>In an application, {@code src/cli} is compiled against the output and the compile class path
     * of the main source set and the output of {@code src/standalone}, and runs with the run time class
     * path of the main source set; the tests and the integration tests have the output of both source
     * sets, so a test can make a tool as it could when tools were in the main source set. In a plugin,
     * which applies the plugin that publishes its companions ({@link GrailsCliArtifactGradlePlugin}),
     * the companion {@code -cli} depends on the plugin and on its own {@code -cli-standalone} instead,
     * and its tests of tools are in the phase {@code testCli}. The difference is made by a file
     * collection that is empty when the publishing plugin is applied, so nothing has to wait until the
     * build script was evaluated.</p>
     */
    protected void configureToolSourceSets(Project project) {
        project.pluginManager.withPlugin('java') {
            ToolSourceSets.create(project)
            SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
            SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            SourceSet cli = sourceSets.getByName(ToolSourceSets.CLI)
            SourceSet standalone = sourceSets.getByName(ToolSourceSets.STANDALONE)
            PublishingSide publishing = new PublishingSide()
            project.pluginManager.withPlugin(GrailsCliArtifactGradlePlugin.PLUGIN_ID) {
                publishing.applied = true
            }
            cli.compileClasspath = cli.compileClasspath + project.files(new OfApplication(publishing, main, standalone, false))
            cli.runtimeClasspath = cli.runtimeClasspath + project.files(new OfApplication(publishing, main, standalone, true))
            FileCollection toolClasses = project.files(new ToolClassesOfApplication(publishing, cli, standalone))
            project.configurations.matching { Configuration configuration ->
                configuration.name in [JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, 'integrationTestImplementation']
            }.configureEach { Configuration configuration ->
                project.dependencies.add(configuration.name, toolClasses)
            }
        }
    }

    /**
     * Whether the plugin that publishes the companions of a Grails plugin is applied to the project:
     * set when it is applied, read when a class path of the tools is resolved.
     */
    @PackageScope
    static class PublishingSide {

        boolean applied
    }

    /**
     * What the source set {@code src/cli} of an application has of the application: the output and
     * the compile (or run time) class path of the main source set, and the output of
     * {@code src/standalone}. Nothing in a plugin.
     */
    @PackageScope
    static class OfApplication implements Callable<Object> {

        private final PublishingSide publishing
        private final SourceSet main
        private final SourceSet standalone
        private final boolean runtime

        OfApplication(PublishingSide publishing, SourceSet main, SourceSet standalone, boolean runtime) {
            this.publishing = publishing
            this.main = main
            this.standalone = standalone
            this.runtime = runtime
        }

        @Override
        Object call() {
            if (publishing.applied) {
                return Collections.emptyList()
            }
            runtime ? [main.output, main.runtimeClasspath, standalone.output, standalone.runtimeClasspath] :
                    [main.output, main.compileClasspath, standalone.output]
        }
    }

    /** The output of the source sets of the tools of an application, for its tests. Nothing in a plugin. */
    @PackageScope
    static class ToolClassesOfApplication implements Callable<Object> {

        private final PublishingSide publishing
        private final SourceSet cli
        private final SourceSet standalone

        ToolClassesOfApplication(PublishingSide publishing, SourceSet cli, SourceSet standalone) {
            this.publishing = publishing
            this.cli = cli
            this.standalone = standalone
        }

        @Override
        Object call() {
            publishing.applied ? Collections.emptyList() : [cli.output, standalone.output]
        }
    }

    /**
     * The class path the build runs a tool with, in {@code runTool}, {@code runCommand} and the task
     * of each tool: the run time class paths of the main source set and of the two source sets of
     * tools, and {@code grailsCliClasspath}, without Spring Boot devtools, whose restarting class
     * loader is not for a tool.
     *
     * @param project a project with the {@code java} plugin
     * @return the class path
     */
    static FileCollection toolClasspath(Project project) {
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        project.files(sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).runtimeClasspath,
                ToolSourceSets.cli(project).runtimeClasspath,
                ToolSourceSets.standalone(project).runtimeClasspath,
                project.configurations.named(GRAILS_CLI_CLASSPATH_CONFIGURATION)).filter(new WithoutDevtools())
    }

    /** Leaves out the jar of Spring Boot devtools. */
    @PackageScope
    static class WithoutDevtools implements Spec<File>, Serializable {

        private static final long serialVersionUID = 1L

        @Override
        boolean isSatisfiedBy(File file) {
            !file.name.startsWith('spring-boot-devtools')
        }
    }

    /**
     * Auto-provisions {@code grailsCli}: the tool API and the runner, plus every companion
     * {@code -cli} advertised by a dependency of the project. A plugin advertises its companion
     * through the {@code Grails-Cli-Artifact} manifest attribute of its runtime jar. Discovery walks
     * the full dependency graph (including transitive plugins) through a lenient resolution of an
     * internal probe configuration. Disable with {@code grails { cliAutoProvision = false }}.
     */
    protected void autoProvisionCliDependencies(Project project, DependencySet dependencies) {
        autoProvision(project, dependencies, ['org.apache.grails:grails-core-cli', 'org.apache.grails:grails-console'],
                ToolBuildNames.CLI_ARTIFACT_MANIFEST_ATTRIBUTE, ToolBuildNames.CLI_ARTIFACT_ID_PROPERTY, GRAILS_CLI_CONFIGURATION,
                GRAILS_CLI_DETECT_CONFIGURATION, true)
    }

    /**
     * Auto-provisions {@code grailsCliStandalone}: the tool API, plus every companion
     * {@code -cli-standalone} advertised through the {@code Grails-Cli-Standalone-Artifact} manifest
     * attribute of the runtime jar of a plugin the project depends on, or of a plugin {@code grailsCli}
     * brings, as the {@code -cli} of a plugin brings the plugin. Disabled with
     * {@link #autoProvisionCliDependencies}.
     */
    protected void autoProvisionStandaloneDependencies(Project project, DependencySet dependencies) {
        autoProvision(project, dependencies, ['org.apache.grails:grails-core-cli'],
                ToolBuildNames.CLI_STANDALONE_ARTIFACT_MANIFEST_ATTRIBUTE, ToolBuildNames.CLI_STANDALONE_ARTIFACT_ID_PROPERTY,
                GRAILS_CLI_STANDALONE_CONFIGURATION, GRAILS_CLI_STANDALONE_DETECT_CONFIGURATION, false)
    }

    @CompileDynamic
    private void autoProvision(Project project, DependencySet dependencies, List<String> provided, String attribute, String artifactIdProperty,
                               String configurationName, String probeName, boolean warnOfFailures) {
        GrailsExtension grails = project.extensions.getByType(GrailsExtension)
        if (!grails.cliAutoProvision.get()) {
            return
        }

        // tool authoring and execution work out of the box
        String grailsVersion = resolveGrailsVersion(project)
        for (String module : provided) {
            dependencies.add(project.dependencies.create("${module}:${grailsVersion}"))
        }

        Configuration probe = project.configurations.getByName(probeName)
        def probeArtifacts = probe.incoming.artifactView { it.lenient(true) }.artifacts
        // lenient resolution never throws, but companions carried by an unresolvable dependency
        // are silently dropped along with their per-tool tasks; surface that rather than
        // leaving the user to wonder why a tool task is missing
        if (warnOfFailures) {
            for (Throwable failure : probeArtifacts.failures) {
                project.logger.warn('Could not fully resolve CLI companion probe dependencies for project {}; some companions (and their per-tool Gradle tasks) may not be auto-provisioned. Cause: {}',
                        project.name, failure.message)
            }
        }

        Map<String, Dependency> companions = [:]
        for (ResolvedArtifactResult artifact : probeArtifacts.artifacts) {
            Dependency companion = findAdvertisedCompanion(project, artifact, attribute, artifactIdProperty)
            if (companion != null) {
                companions.putIfAbsent("${companion.group}:${companion.name}" as String, companion)
            }
        }

        for (Map.Entry<String, Dependency> entry : companions) {
            Dependency companion = entry.value
            boolean alreadyDeclared = dependencies.any { Dependency existing ->
                existing.group == companion.group && existing.name == companion.name
            }
            if (!alreadyDeclared) {
                project.logger.info('Detected companion {}, adding it to the {} configuration of project {}',
                        entry.key, configurationName, project.name)
                dependencies.add(companion)
            }
        }
    }

    protected static String resolveGrailsVersion(Project project) {
        (project.findProperty('grailsVersion') ?: BuildSettings.grailsVersion) as String
    }

    /**
     * Returns the {@link Dependency} on the companion cli artifact advertised by the given
     * resolved artifact, or {@code null} if it advertises none.
     *
     * @see #findAdvertisedCompanion
     */
    protected Dependency findAdvertisedCliArtifact(Project project, ResolvedArtifactResult artifact) {
        findAdvertisedCompanion(project, artifact, ToolBuildNames.CLI_ARTIFACT_MANIFEST_ATTRIBUTE, ToolBuildNames.CLI_ARTIFACT_ID_PROPERTY)
    }

    /**
     * Returns the {@link Dependency} on a companion advertised by the given resolved artifact, or
     * {@code null} if it advertises none.
     *
     * <p>A companion produced by a project of the <em>current</em> build is bound as a project
     * dependency requesting the capability of the companion, so it resolves against the sibling
     * project's not-yet-published variant instead of a repository (the multi-project
     * plugin-development workflow). Everything else — external modules and projects of an included
     * (composite) build — is bound to the plain {@code group:artifactId} module coordinate read
     * from the manifest attribute of the resolved jar.</p>
     *
     * @param attribute the manifest attribute that names the companion
     * @param artifactIdProperty the extra property of a project of this build that names its companion
     */
    @CompileDynamic
    protected Dependency findAdvertisedCompanion(Project project, ResolvedArtifactResult artifact, String attribute, String artifactIdProperty) {
        def componentIdentifier = artifact.id.componentIdentifier
        if (componentIdentifier instanceof ProjectComponentIdentifier) {
            if (isProjectOfThisBuild(project, (ProjectComponentIdentifier) componentIdentifier)) {
                // the project's own companion property decides, so its jar - which this build has usually not built
                // yet, and whose absence the configuration cache would record as an input - is never read
                return findProjectCompanion(project, (ProjectComponentIdentifier) componentIdentifier, artifactIdProperty)
            }
            // a project of an included build (or a same-named project in the wrong build) is not
            // addressable through findProject; fall through to the advertised module coordinate
        }

        File file = artifact.file
        if (file == null || !file.isFile() || !file.name.endsWith('.jar')) {
            return null
        }
        String advertised
        try (JarFile jarFile = new JarFile(file)) {
            advertised = jarFile.manifest?.mainAttributes?.getValue(attribute)
        }
        catch (IOException ignored) {
            return null
        }
        if (!advertised) {
            return null
        }
        if (advertised.tokenize(':').size() != 2) {
            project.logger.warn('Ignoring malformed {} value [{}] found in the dependencies of project {}',
                    attribute, advertised, project.name)
            return null
        }
        // Manifests advertise group:artifact only. Prefer the producer module's own version so
        // third-party plugins (versioned independently of Grails) resolve their matching companion.
        // Fall back to the current Grails version for framework modules / unversioned components.
        String companionVersion = resolveAdvertisedCompanionVersion(artifact, advertised, project)
        project.dependencies.create("${advertised}:${companionVersion}")
    }

    protected static String resolveAdvertisedCompanionVersion(
            ResolvedArtifactResult artifact, String advertised, Project project) {
        def componentIdentifier = artifact.id.componentIdentifier
        if (componentIdentifier instanceof ModuleComponentIdentifier) {
            String moduleVersion = ((ModuleComponentIdentifier) componentIdentifier).version
            if (moduleVersion) {
                return moduleVersion
            }
        }
        for (ResolvedComponentResult component : project.configurations
                .getByName(GRAILS_CLI_DETECT_CONFIGURATION).incoming.resolutionResult.allComponents) {
            if (component.id == componentIdentifier) {
                String componentVersion = component.moduleVersion?.version
                if (componentVersion && componentVersion != Project.DEFAULT_VERSION) {
                    return componentVersion
                }
                break
            }
        }
        resolveGrailsVersion(project)
    }

    /**
     * Whether the component is a project of the consuming build. Only those are addressable via findProject: a
     * component from another build of the composite shares the project-path namespace and would resolve to the
     * wrong project, which the build tree path comparison rejects.
     */
    @CompileDynamic
    protected static boolean isProjectOfThisBuild(Project project, ProjectComponentIdentifier componentIdentifier) {
        Project target = project.rootProject.findProject(componentIdentifier.projectPath)
        target != null && target.buildTreePath == componentIdentifier.buildTreePath
    }

    /**
     * Binds the {@code -cli} companion of a project of the current build.
     *
     * @see #findProjectCompanion
     */
    protected Dependency findProjectCliCompanion(Project project, ProjectComponentIdentifier componentIdentifier) {
        findProjectCompanion(project, componentIdentifier, ToolBuildNames.CLI_ARTIFACT_ID_PROPERTY)
    }

    /**
     * Binds a companion advertised by a project of the current build as a project dependency on
     * the capability of the companion, or returns {@code null} when the component is not a
     * resolvable project of the current build (an included-build project, or a coincidental
     * same-named project), or when the project has no such companion. The companion coordinate comes
     * from the extra property the plugin that publishes the companions sets on the project
     * ({@code cliArtifactId}, {@code cliStandaloneArtifactId}) only when it has the companion, so the
     * (possibly not-yet-built) jar is never read.
     */
    @CompileDynamic
    protected Dependency findProjectCompanion(Project project, ProjectComponentIdentifier componentIdentifier, String artifactIdProperty) {
        if (!isProjectOfThisBuild(project, componentIdentifier)) {
            return null
        }
        Project target = project.rootProject.findProject(componentIdentifier.projectPath)
        def artifactId = target.findProperty(artifactIdProperty)
        if (!artifactId) {
            return null
        }
        String capabilityCoordinate = "${target.group}:${artifactId}" as String
        Dependency dependency = project.dependencies.project(path: componentIdentifier.projectPath)
        dependency.capabilities { it.requireCapability(capabilityCoordinate) }
        dependency
    }

    /**
     * Returns the application main class for a task that runs against the application, or fails the task with
     * an explanation when the project has none, as is the case for a plugin without an {@code Application} class.
     *
     * <p>Task actions call it qualified with the class name: with the configuration cache, an action's closure
     * does not keep its owner, so an unqualified call would be looked up on the task instead.</p>
     *
     * @param mainClass the main class found by the {@code findMainClass} task
     * @param taskName the name of the task that requires the main class
     * @return the name of the main class
     */
    @PackageScope
    static String requireMainClass(Provider<String> mainClass, String taskName) {
        def mainClassName = mainClass.orNull
        if (!mainClassName) {
            throw new GradleException("The '${taskName}' task requires an application class with a main method, but none was found. " +
                    "Add an Application class, or set 'springBoot.mainClass' if the project already has one.")
        }
        mainClassName
    }

    /**
     * Registers a task for each tool registered by a jar of {@code grailsCliClasspath} (or of the class
     * path of the build script), named after the name of the tool: {@code dbm-update} is run by the
     * task {@code dbmUpdate}. The name of a tool is the one its class name gives
     * ({@link ToolDescriptor#conventionName}).
     */
    @CompileDynamic
    protected void configureApplicationCommands(Project project) {
        // a tool is registered under the key of the tool contract, and by a companion of Grails 8 under the key of the command contract
        List<String> applicationContextCommands = []
        for (String key : ToolDescriptor.FACTORIES_KEYS) {
            String[] registered = FactoriesLoaderSupport.loadFactoryNames(key,
                    FactoriesLoaderSupport.classLoader, FactoriesLoaderSupport.CLI_FACTORIES_RESOURCE_LOCATION)
            if (registered) {
                applicationContextCommands.addAll(registered as List<String>)
            }
        }
        project.afterEvaluate {
            if (!project.pluginManager.hasPlugin('java')) {
                return
            }
            FileCollection fileCollection = toolClasspath(project)
            // tools come from the buildscript classloader (legacy placement) and from the
            // project's tool tier, so auto-provisioned companions register their tasks
            // without any buildscript classpath entry
            Set<String> commandClassNames = new LinkedHashSet<String>()
            if (applicationContextCommands) {
                commandClassNames.addAll(applicationContextCommands as List)
            }
            GrailsExtension grails = project.extensions.getByType(GrailsExtension)
            if (grails.cliAutoProvision.get()) {
                commandClassNames.addAll(loadCommandNamesFromCliClasspath(project))
            }
            for (ctxCommand in commandClassNames) {
                // the name a tool has by its class name, as the tool works it out: CreateReportTool and
                // CreateReportCommand are both create-report, and the task is createReport
                String commandName = ToolDescriptor.conventionName(ctxCommand)
                if (!ToolDescriptor.isCommandName(commandName)) {
                    continue
                }
                String taskName = GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(commandName)
                if (!project.tasks.names.contains(taskName)) {
                    project.tasks.register(taskName, ApplicationContextCommandTask).configure {
                        it.group = GRAILS_COMMAND_GROUP
                        it.description = "Runs the tool ${commandName}"
                        it.classpath = fileCollection
                        it.command = commandName
                        it.systemProperty(Environment.KEY, System.getProperty(Environment.KEY, Environment.DEVELOPMENT.getName()))
                        it.doFirst(new RunnerArguments(propertyArguments(project), GrailsGradlePlugin.getMainClassProvider(project)))
                    }
                }
            }
        }
    }

    /**
     * Loads tool class names from the {@code META-INF/grails-cli.factories} files of the
     * resolved {@code grailsCliClasspath} jars, so per-tool Gradle tasks (e.g. {@code dbmUpdate})
     * are registered for auto-provisioned companions. Resolution is lenient and jars that are
     * not built yet (project dependencies within a composite) are skipped — the generic
     * {@code runTool} task can always execute those tools regardless.
     */
    @CompileDynamic
    protected Collection<String> loadCommandNamesFromCliClasspath(Project project) {
        Set<String> names = new LinkedHashSet<String>()
        Configuration cliClasspath = project.configurations.findByName(GRAILS_CLI_CLASSPATH_CONFIGURATION)
        if (cliClasspath == null) {
            return names
        }
        for (File file : cliClasspath.incoming.artifactView { it.lenient(true) }.files) {
            if (!file.isFile() || !file.name.endsWith('.jar')) {
                continue
            }
            try (JarFile jarFile = new JarFile(file)) {
                def entry = jarFile.getEntry(FactoriesLoaderSupport.CLI_FACTORIES_RESOURCE_LOCATION)
                if (entry == null) {
                    continue
                }
                Properties properties = new Properties()
                jarFile.getInputStream(entry).withCloseable { input ->
                    properties.load(input)
                }
                for (String key : ToolDescriptor.FACTORIES_KEYS) {
                    String factoryNames = properties.getProperty(key)
                    if (factoryNames) {
                        names.addAll(factoryNames.split(',').toList()*.trim())
                    }
                }
            }
            catch (IOException ignored) {
                // unreadable jar - skip
            }
        }
        names
    }

    protected void configureConsoleTask(Project project) {
        TaskContainer tasks = project.tasks
        if (!project.configurations.names.contains('console')) {
            if (!tasks.names.contains('findMainClass')) {
                project.logger.info('Project {} does not contain the findMainClass task so the console & shell tasks will not be created.', project.name)
                return
            }

            NamedDomainObjectProvider<Configuration> consoleConfiguration = project.configurations.register('console')
            createConsoleTask(project, tasks, consoleConfiguration)
            createShellTask(project, tasks, consoleConfiguration)
        }
    }

    @CompileDynamic
    protected TaskProvider<JavaExec> createConsoleTask(Project project, TaskContainer tasks, NamedDomainObjectProvider<Configuration> configuration) {
        def consoleTask = tasks.register('console', JavaExec)
        project.afterEvaluate {
            consoleTask.configure {
                it.group = GRAILS_COMMAND_GROUP
                it.description = 'Runs the interactive Grails Swing console'
                it.dependsOn(tasks.named('classes'), tasks.named('findMainClass'))
                // grails-console arrives through the auto-provisioned grailsCli tier; the
                // `console` configuration remains for additional console-only dependencies
                it.classpath = project.sourceSets.main.runtimeClasspath + configuration.get() +
                        project.configurations.getByName(GRAILS_CLI_CLASSPATH_CONFIGURATION)
                it.mainClass.set('grails.ui.console.GrailsSwingConsole')

                def appClass = GrailsGradlePlugin.getMainClassProvider(project)

                it.doFirst {
                    it.args(GrailsCliGradlePlugin.requireMainClass(appClass, it.name))
                }
            }
        }
        consoleTask
    }

    @CompileDynamic
    protected TaskProvider<JavaExec> createShellTask(Project project, TaskContainer tasks, NamedDomainObjectProvider<Configuration> configuration) {
        def shellTask = tasks.register('shell', JavaExec)
        project.afterEvaluate {
            shellTask.configure {
                it.group = GRAILS_COMMAND_GROUP
                it.description = 'Runs the interactive Grails shell'
                it.dependsOn(tasks.named('classes'), tasks.named('findMainClass'))
                // grails-console arrives through the auto-provisioned grailsCli tier; the
                // `console` configuration remains for additional console-only dependencies
                it.classpath = project.sourceSets.main.runtimeClasspath + configuration.get() +
                        project.configurations.getByName(GRAILS_CLI_CLASSPATH_CONFIGURATION)
                it.mainClass.set('grails.ui.shell.GrailsShell')
                it.standardInput = System.in

                def appClass = GrailsGradlePlugin.getMainClassProvider(project)

                it.doFirst {
                    it.args(GrailsCliGradlePlugin.requireMainClass(appClass, it.name))
                }
            }
        }
        shellTask
    }

    /**
     * Registers {@code runScript}, which runs a Groovy script file against the application context, as in
     * Grails 7 and 8. It is deprecated: it runs as before, after a warning that says to write a tool.
     */
    @CompileDynamic
    protected void configureRunScript(Project project) {
        if (!project.tasks.names.contains('runScript')) {
            def runTask = project.tasks.register('runScript', ApplicationContextScriptTask)
            project.afterEvaluate {
                runTask.configure {
                    it.group = GRAILS_COMMAND_GROUP
                    it.description = 'Deprecated: executes a Grails script against the application context; write a tool and run it with runTool'
                    it.doFirst(new WarnOfDeprecation(RUN_SCRIPT_DEPRECATED))
                    SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
                    it.classpath = mainSourceSet.runtimeClasspath + project.configurations.getByName(GRAILS_CLI_CLASSPATH_CONFIGURATION)
                    it.systemProperty(Environment.KEY, System.getProperty(Environment.KEY, Environment.DEVELOPMENT.getName()))

                    // devtools' automatic restart mechanism uses a specialized classloader setup, which can interfere
                    // with Grails' plugin management and bean wiring when running CLI scripts via Gradle
                    it.systemProperty('spring.devtools.restart.enabled', 'false')

                    List<Object> args = []
                    def otherArgs = project.findProperty('args')
                    if (otherArgs) {
                        args.addAll(CommandLineParser.translateCommandline(otherArgs as String))
                    }

                    def appClassProvider = GrailsGradlePlugin.getMainClassProvider(project)

                    it.doFirst {
                        args << GrailsCliGradlePlugin.requireMainClass(appClassProvider, it.name)
                        it.args(args)
                    }
                }
            }
        }
    }

    /**
     * Registers {@code runTool}, which runs a tool of the project or of one of its plugins by its name,
     * the first argument, and {@code runCommand}, its deprecated other name, which does the same after a
     * warning. Both run with {@link #toolClasspath}.
     */
    protected void configureRunTool(Project project) {
        project.pluginManager.withPlugin('java') {
            registerRunTool(project, ToolBuildNames.RUN_TOOL_TASK,
                    'Runs a tool of the project or of one of its plugins: the first argument is the name of the tool (--arg=<name>)', null)
            registerRunTool(project, ToolBuildNames.RUN_COMMAND_TASK, "Deprecated: use ${ToolBuildNames.RUN_TOOL_TASK}".toString(),
                    new WarnOfDeprecation(RUN_COMMAND_DEPRECATED))
        }
    }

    @CompileDynamic
    private void registerRunTool(Project project, String taskName, String description, Action<Task> warning) {
        if (project.tasks.names.contains(taskName)) {
            return
        }
        project.tasks.register(taskName, ApplicationContextCommandTask) { ApplicationContextCommandTask task ->
            task.group = GRAILS_COMMAND_GROUP
            task.description = description
            task.classpath = toolClasspath(project)
            task.systemProperty(Environment.KEY, System.getProperty(Environment.KEY, Environment.DEVELOPMENT.getName()))
            // devtools' automatic restart mechanism uses a specialized classloader setup, which can interfere
            // with Grails' plugin management and bean wiring when running tools via Gradle
            task.systemProperty('spring.devtools.restart.enabled', 'false')
            if (warning != null) {
                task.doFirst(warning)
            }
            task.doFirst(new RunnerArguments(propertyArguments(project), GrailsGradlePlugin.getMainClassProvider(project)))
        }
    }

    // the arguments of the project property args, split as they always were
    private static List<String> propertyArguments(Project project) {
        List<String> args = []
        Object otherArgs = project.findProperty('args')
        if (otherArgs) {
            args.addAll(CommandLineParser.translateCommandline(otherArgs as String))
        }
        args
    }

    /**
     * Gives the runner of a tool its arguments when the task starts: those of {@code -Pargs} split as they
     * always were, those of {@code --arg} as they are, or the file of {@code --args-file}, and the class of the
     * application. A class of its own and not a closure, which the configuration cache would have to store
     * with what it encloses.
     */
    @PackageScope
    static class RunnerArguments implements Action<Task> {

        private final List<String> propertyArguments
        private final Provider<String> mainClass

        RunnerArguments(List<String> propertyArguments, Provider<String> mainClass) {
            this.propertyArguments = propertyArguments
            this.mainClass = mainClass
        }

        @Override
        void execute(Task task) {
            ApplicationContextCommandTask runner = (ApplicationContextCommandTask) task
            runner.args(runner.runnerArguments(propertyArguments, requireMainClass(mainClass, runner.name)))
        }
    }

    /**
     * Says, once each time a deprecated task runs, that it is deprecated and what to do instead. A class of
     * its own and not a closure, which the configuration cache would have to store with what it encloses.
     */
    @PackageScope
    static class WarnOfDeprecation implements Action<Task>, Serializable {

        private static final long serialVersionUID = 1L

        private final String message

        WarnOfDeprecation(String message) {
            this.message = message
        }

        @Override
        void execute(Task task) {
            task.logger.warn(message)
        }
    }
}
