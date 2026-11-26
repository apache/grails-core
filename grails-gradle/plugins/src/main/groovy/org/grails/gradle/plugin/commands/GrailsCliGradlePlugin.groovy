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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.util.PatternFilterable

import grails.util.BuildSettings
import grails.util.Environment
import grails.util.GrailsNameUtils
import org.grails.gradle.plugin.core.GrailsExtension
import org.grails.gradle.plugin.core.GrailsGradlePlugin
import org.grails.gradle.plugin.core.TestPhaseNames
import org.grails.gradle.plugin.util.ClasspathUtils
import org.grails.gradle.plugin.util.SourceSets
import org.grails.io.support.FactoriesLoaderSupport
import org.grails.build.parsing.CommandLineParser

/**
 * Configures the CLI tier of a Grails application or plugin project: the {@code grailsCli}
 * configurations (with automatic discovery of companion {@code -cli} artifacts advertised by the
 * dependency graph), the per-command tasks, the generic {@code runCommand}/{@code runScript}
 * tasks, and the interactive {@code console}/{@code shell} tasks. Applied automatically by the
 * Grails Gradle plugin.
 *
 * @since 8.0
 */
@CompileStatic
class GrailsCliGradlePlugin implements Plugin<Project> {

    public static final String APPLICATION_CONTEXT_COMMAND_CLASS = 'org.apache.grails.core.cli.ApplicationCommand'

    /**
     * The task group the CLI command tasks are placed in. A group makes them visible in the
     * default {@code gradle tasks} listing (and therefore to shell tab-completion), matching the
     * commands offered by the interactive Grails shell.
     */
    public static final String GRAILS_COMMAND_GROUP = 'Grails'

    /**
     * The dependency bucket carrying CLI-only dependencies (Grails commands and their libraries):
     * compile-visible so `grails-app/commands` sources compile against the cli-only contract, on
     * the command-runner classpath, but never on `runtimeClasspath`, `bootRun`, or packaged
     * artifacts.
     */
    public static final String GRAILS_CLI_CONFIGURATION = 'grailsCli'

    /** Execution-only dependencies used by the command compatibility layer */
    public static final String GRAILS_CLI_LEGACY_CONFIGURATION = 'grailsCliLegacy'

    /** The resolvable view of {@link #GRAILS_CLI_CONFIGURATION} used by the command-runner tasks */
    public static final String GRAILS_CLI_CLASSPATH_CONFIGURATION = 'grailsCliClasspath'

    /**
     * Set this project property to {@code false} to stop the plugin from auto-provisioning the
     * CLI tier onto {@link #GRAILS_CLI_CONFIGURATION}; equivalent to
     * {@code grails { cliAutoProvision = false }}.
     */
    public static final String GRAILS_CLI_AUTO_PROVISION_PROPERTY = 'grailsCliAutoProvision'

    /**
     * Set this project property to {@code true} to auto-provision the Grails 7 command
     * compatibility bridge onto {@link #GRAILS_CLI_LEGACY_CONFIGURATION}; equivalent to
     * {@code grails { legacyCommandSupport = true }}. The bridge is off by default.
     */
    public static final String GRAILS_LEGACY_COMMAND_SUPPORT_PROPERTY = 'grailsLegacyCommandSupport'

    /** The internal probe configuration used to detect companion `-cli` modules */
    public static final String GRAILS_CLI_DETECT_CONFIGURATION = 'grailsCliDetect'

    @Override
    void apply(Project project) {
        // self-sufficient when applied standalone: the auto-provisioning behavior is configured
        // through the `grails` extension, normally registered by the Grails Gradle plugin
        if (project.extensions.findByName('grails') == null) {
            project.extensions.create('grails', GrailsExtension, project)
        }

        configureGrailsCliConfiguration(project)

        configureConsoleTask(project)

        configureApplicationCommands(project)

        configureRunScript(project)

        configureRunCommand(project)

        configureCommandCatalog(project)

        configureRunScriptCommand(project)
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
                task.description = 'Runs a script command: src/main/scripts/<name>.groovy of the project, or META-INF/commands/<name>.groovy of a plugin'
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
     * Registers the task that writes the catalog of the application commands of the project (see
     * {@link GrailsCommandCatalogTask}). It reads the class path {@code runCommand} runs a command
     * with: the output of the project, {@code runtimeClasspath} and {@code grailsCliClasspath}.
     *
     * <p>The task finalizes {@code classes}: every build that compiles the project, whether it runs
     * the application, its tests or an archive, or an IDE builds it, leaves the catalog current, and
     * the command line has the commands of the application and its plugins by their names without a
     * step of its own. A finalizer adds nothing that {@code classes} waits for, and is up to date when
     * nothing it reads changed. The class paths are resolved leniently, since compiling the project
     * does not need them: one that does not resolve completely gives a catalog of what does, which says
     * what does not and is not complete, and the build goes on.</p>
     */
    protected void configureCommandCatalog(Project project) {
        project.pluginManager.withPlugin('java') {
            if (project.tasks.names.contains(GrailsCommandCatalogTask.TASK_NAME)) {
                project.logger.warn('Project {} already has a task named {}, so the Grails Gradle plugin does not register its own and ' +
                        'the catalog of the commands of the project is not written. Give the task another name.',
                        project.path, GrailsCommandCatalogTask.TASK_NAME)
                return
            }
            TaskProvider<GrailsCommandCatalogTask> catalog = project.tasks.register(GrailsCommandCatalogTask.TASK_NAME, GrailsCommandCatalogTask) { GrailsCommandCatalogTask task ->
                task.group = GRAILS_COMMAND_GROUP
                task.description = 'Writes the catalog of the application commands of the project, without loading or running them'
                SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
                // where the compiler and the resources of the project put registrations and descriptors: not the
                // whole output of the source set, which would have the catalog wait for compiled views as well
                FileCollection ownOutput = project.files(mainSourceSet.output.classesDirs,
                        project.tasks.named(mainSourceSet.processResourcesTaskName))
                task.applicationOutput.from(ownOutput)
                task.applicationRegistrations.from(ownOutput.asFileTree.matching { PatternFilterable patterns ->
                    patterns.include(GrailsCommandCatalogTask.REGISTRATION_PATTERNS)
                })
                for (String configurationName : [JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, GRAILS_CLI_CLASSPATH_CONFIGURATION]) {
                    // leniently: compiling the project does not need the class path its commands run with, and a build
                    // that compiles it does not fail because that does not resolve; the catalog lists what does, and
                    // says what does not (the configuration cache resolves it when it stores the task)
                    ArtifactCollection artifacts = project.configurations.getByName(configurationName).incoming
                            .artifactView({ ArtifactView.ViewConfiguration view -> view.lenient(true) } as Action<ArtifactView.ViewConfiguration>).artifacts
                    task.commandClasspath.from(artifacts.artifactFiles)
                    task.originsByPath.putAll(artifacts.resolvedArtifacts.map(new GrailsCommandCatalogTask.OriginsByPath()))
                    task.unresolved.put(configurationName, project.provider(new GrailsCommandCatalogTask.UnresolvedLines(artifacts)))
                }
                task.origins.set(task.originsByPath.map(new GrailsCommandCatalogTask.Origins()))
                // the names of the Test tasks there are when the task is about to run: none is created for them
                task.testPhases.set(project.provider({ TestPhaseNames.phases(project.tasks.withType(Test).names) } as Callable<Map<String, String>>))
                // in the directory of the project and not in its build directory, which the build can move: the
                // command line reads the catalog there without running the build to ask where that is
                task.catalogFile.convention(project.layout.projectDirectory.file(GrailsCommandCatalogTask.CATALOG_LOCATION))
                task.ignoreFile.convention(project.layout.projectDirectory.file(GrailsCommandCatalogTask.IGNORE_LOCATION))
                task.projectDirectory.convention(project.layout.projectDirectory)
            }
            project.tasks.named(JavaPlugin.CLASSES_TASK_NAME).configure { Task classes ->
                classes.finalizedBy(catalog)
            }
            configureStandaloneCommands(project, catalog)
        }
    }

    /**
     * Registers the task that writes down the class path of the commands that run as a process of
     * their own, without Gradle (see {@link GrailsStandaloneCommandsTask}): the output of the project
     * for the commands of the project itself, and {@code runtimeClasspath} and
     * {@code grailsCliClasspath} for all of them, which is what {@code runCommand} runs a command
     * with. The task runs after the one that writes the catalog, which says which commands those are,
     * so every build that compiles the project leaves the file current. The class paths are resolved
     * leniently, as they are for the catalog: one that does not resolve completely is not written
     * down, and the build goes on.
     */
    protected void configureStandaloneCommands(Project project, TaskProvider<GrailsCommandCatalogTask> catalog) {
        if (project.tasks.names.contains(GrailsStandaloneCommandsTask.TASK_NAME)) {
            project.logger.warn('Project {} already has a task named {}, so the Grails Gradle plugin does not register its own and ' +
                    'the class path of the commands that run without Gradle is not written down. Give the task another name.',
                    project.path, GrailsStandaloneCommandsTask.TASK_NAME)
            return
        }
        TaskProvider<GrailsStandaloneCommandsTask> standalone = project.tasks.register(GrailsStandaloneCommandsTask.TASK_NAME, GrailsStandaloneCommandsTask) { GrailsStandaloneCommandsTask task ->
            task.group = GRAILS_COMMAND_GROUP
            task.description = 'Writes down the class path of the commands that the grails command line runs without Gradle'
            SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
            task.catalogFile.set(catalog.flatMap { GrailsCommandCatalogTask catalogTask -> catalogTask.catalogFile })
            // the directories of the classes and of the resources, by the tasks that write them, and not the output of the
            // source set as a whole: that is built by classes, which this task runs after, as the task that writes the catalog does
            task.applicationClasspath.from(mainSourceSet.output.classesDirs, project.tasks.named(mainSourceSet.processResourcesTaskName))
            for (String configurationName : [JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, GRAILS_CLI_CLASSPATH_CONFIGURATION]) {
                ArtifactCollection artifacts = project.configurations.getByName(configurationName).incoming
                        .artifactView({ ArtifactView.ViewConfiguration view -> view.lenient(true) } as Action<ArtifactView.ViewConfiguration>).artifacts
                task.dependencyClasspath.from(artifacts.artifactFiles)
                task.projectOutputs.addAll(artifacts.resolvedArtifacts.map(new GrailsStandaloneCommandsTask.ProjectOutputs()))
                task.unresolved.put(configurationName, project.provider(new GrailsCommandCatalogTask.UnresolvedLines(artifacts)))
            }
            // the directories there are when the task is about to run, as whole paths: the command line compares the file with what is in them
            task.sourceDirectories.set(project.provider({
                mainSourceSet.allSource.srcDirs.collect { File directory -> directory.absolutePath }.sort()
            } as Callable<List<String>>))
            task.javaVersion.set(project.provider({
                Integer.valueOf(project.extensions.getByType(JavaPluginExtension).targetCompatibility.majorVersion)
            } as Callable<Integer>))
            task.standaloneCommandsFile.convention(project.layout.projectDirectory.file(GrailsStandaloneCommandsTask.LOCATION))
        }
        catalog.configure { GrailsCommandCatalogTask task ->
            task.finalizedBy(standalone)
        }
    }

    /**
     * Registers the `grailsCli` dependency bucket and its resolvable `grailsCliClasspath` view.
     * `grailsCli` carries the CLI tier — command companion artifacts (`<artifactId>-cli`) and the
     * libraries they need. It extends the compile classpaths (the same wiring `compileOnly` uses)
     * so `grails-app/commands` sources compile against the cli-only contract, while staying off
     * `runtimeClasspath` — and therefore out of `bootRun`, `bootJar`, and `bootWar`.
     */
    protected void configureGrailsCliConfiguration(Project project) {
        ConfigurationContainer configurations = project.configurations
        if (configurations.names.contains(GRAILS_CLI_CONFIGURATION)) {
            return
        }

        Configuration grailsCli = configurations.create(GRAILS_CLI_CONFIGURATION)
        grailsCli.canBeResolved = false
        grailsCli.canBeConsumed = false
        grailsCli.description = 'CLI-only dependencies (Grails commands and the libraries they need); compile-visible and on the command-runner classpath, but never on runtimeClasspath, bootRun, or packaged artifacts.'

        Configuration grailsCliLegacy = configurations.create(GRAILS_CLI_LEGACY_CONFIGURATION)
        grailsCliLegacy.canBeResolved = false
        grailsCliLegacy.canBeConsumed = false
        grailsCliLegacy.description = 'Execution-only compatibility dependencies used by the command runner; never compile-visible.'

        configurations.matching { Configuration it ->
            it.name in ['testRuntimeClasspath', 'integrationTestRuntimeClasspath']
        }.configureEach { Configuration it ->
            it.extendsFrom(grailsCliLegacy)
        }

        // compile visibility for grails-app/commands sources plus the TEST classpaths (tests
        // exercise commands inside the test JVM), while the main runtimeClasspath — and therefore
        // bootRun, bootJar, and bootWar — never sees the cli tier; matching configurations that
        // appear later (e.g. the integrationTest pair) are included as they are created
        configurations.matching { Configuration it ->
            it.name in ['compileClasspath', 'testCompileClasspath', 'testRuntimeClasspath',
                        'integrationTestCompileClasspath', 'integrationTestRuntimeClasspath']
        }.configureEach { Configuration it ->
            it.extendsFrom(grailsCli)
        }

        Configuration grailsCliClasspath = configurations.create(GRAILS_CLI_CLASSPATH_CONFIGURATION)
        grailsCliClasspath.extendsFrom(grailsCli, grailsCliLegacy)
        grailsCliClasspath.canBeResolved = true
        grailsCliClasspath.canBeConsumed = false
        grailsCliClasspath.description = 'Resolvable view of grailsCli used by the command-runner tasks.'

        Configuration grailsCliDetect = configurations.create(GRAILS_CLI_DETECT_CONFIGURATION)
        grailsCliDetect.canBeResolved = true
        grailsCliDetect.canBeConsumed = false
        grailsCliDetect.description = 'Internal probe used to discover companion -cli artifacts advertised by dependencies.'
        for (String bucket : ['api', 'implementation', 'runtimeOnly']) {
            configurations.matching { Configuration it -> it.name == bucket }.configureEach { Configuration it ->
                grailsCliDetect.extendsFrom(it)
            }
        }

        // computed when the configuration is first resolved, so every dependency (and the
        // extension configuration) declared by the build script is visible
        grailsCli.withDependencies { DependencySet dependencies ->
            autoProvisionCliDependencies(project, dependencies)
        }
        grailsCliLegacy.withDependencies { DependencySet dependencies ->
            autoProvisionLegacyCliDependencies(project, dependencies)
        }
    }

    /**
     * Auto-provisions the CLI tier onto {@code grailsCli}: the command contract and runner, plus
     * every companion {@code -cli} artifact advertised by a dependency of the application. A
     * module advertises its companion through the {@code Grails-Cli-Artifact} manifest attribute
     * of its runtime jar (stamped by the framework's cli-artifact build convention; third-party
     * plugins set it on their jar task). Discovery walks the full dependency graph (including
     * transitive plugins) through a lenient resolution of an internal probe configuration.
     * Disable with {@code grails { cliAutoProvision = false }}.
     */
    @CompileDynamic
    protected void autoProvisionCliDependencies(Project project, DependencySet dependencies) {
        GrailsExtension grails = project.extensions.getByType(GrailsExtension)
        if (!grails.cliAutoProvision.get()) {
            return
        }

        // command authoring and execution work out of the box: the command contract + the runner
        String grailsVersion = resolveGrailsVersion(project)
        dependencies.add(project.dependencies.create("org.apache.grails:grails-core-cli:${grailsVersion}"))
        dependencies.add(project.dependencies.create("org.apache.grails:grails-console:${grailsVersion}"))

        Configuration probe = project.configurations.getByName(GRAILS_CLI_DETECT_CONFIGURATION)
        def probeArtifacts = probe.incoming.artifactView { it.lenient(true) }.artifacts
        // lenient resolution never throws, but companions carried by an unresolvable dependency
        // are silently dropped along with their per-command tasks; surface that rather than
        // leaving the user to wonder why a command task is missing
        for (Throwable failure : probeArtifacts.failures) {
            project.logger.warn('Could not fully resolve CLI companion probe dependencies for project {}; some companion -cli artifacts (and their per-command Gradle tasks) may not be auto-provisioned. Cause: {}',
                    project.name, failure.message)
        }

        Map<String, Dependency> companions = [:]
        for (ResolvedArtifactResult artifact : probeArtifacts.artifacts) {
            Dependency companion = findAdvertisedCliArtifact(project, artifact)
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
                project.logger.info('Detected cli companion artifact {}, adding it to the {} configuration of project {}',
                        entry.key, GRAILS_CLI_CONFIGURATION, project.name)
                dependencies.add(companion)
            }
        }
    }

    protected void autoProvisionLegacyCliDependencies(Project project, DependencySet dependencies) {
        GrailsExtension grails = project.extensions.getByType(GrailsExtension)
        // Modern CLI auto-provisioning is the master switch; legacyCommandSupport is the finer
        // opt-in for only the Grails 7 application-command bridge (off by default).
        if (grails.cliAutoProvision.get() && grails.legacyCommandSupport.get()) {
            String grailsVersion = resolveGrailsVersion(project)
            dependencies.add(project.dependencies.create(
                    "org.apache.grails:grails-core-cli-legacy:${grailsVersion}"))
        }
    }

    protected static String resolveGrailsVersion(Project project) {
        (project.findProperty('grailsVersion') ?: BuildSettings.grailsVersion) as String
    }

    /**
     * Returns the {@link Dependency} on the companion cli artifact advertised by the given
     * resolved artifact, or {@code null} if it advertises none.
     *
     * <p>A companion produced by a project of the <em>current</em> build is bound as a project
     * dependency requesting the project's {@code cli} feature capability, so it resolves against
     * the sibling project's not-yet-published variant instead of a repository (the multi-project
     * plugin-development workflow). Everything else — external modules and projects of an included
     * (composite) build — is bound to the plain {@code group:artifactId} module coordinate read
     * from the resolved jar's {@code Grails-Cli-Artifact} manifest attribute.</p>
     */
    @CompileDynamic
    protected Dependency findAdvertisedCliArtifact(Project project, ResolvedArtifactResult artifact) {
        def componentIdentifier = artifact.id.componentIdentifier
        if (componentIdentifier instanceof ProjectComponentIdentifier) {
            if (isProjectOfThisBuild(project, (ProjectComponentIdentifier) componentIdentifier)) {
                // the project's own cliArtifactId decides, so its jar - which this build has usually not built
                // yet, and whose absence the configuration cache would record as an input - is never read
                return findProjectCliCompanion(project, (ProjectComponentIdentifier) componentIdentifier)
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
            advertised = jarFile.manifest?.mainAttributes?.getValue(GrailsCliArtifactGradlePlugin.CLI_ARTIFACT_MANIFEST_ATTRIBUTE)
        }
        catch (IOException ignored) {
            return null
        }
        if (!advertised) {
            return null
        }
        if (advertised.tokenize(':').size() != 2) {
            project.logger.warn('Ignoring malformed {} value [{}] found in the dependencies of project {}',
                    GrailsCliArtifactGradlePlugin.CLI_ARTIFACT_MANIFEST_ATTRIBUTE, advertised, project.name)
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
     * Binds a companion advertised by a project of the current build as a project dependency on
     * its {@code cli} feature capability, or returns {@code null} when the component is not a
     * resolvable project of the current build (an included-build project, or a coincidental
     * same-named project). The companion coordinate comes from the project's {@code cliArtifactId}
     * property exported by the cli-artifact convention, so the (possibly not-yet-built) jar is
     * never read.
     */
    @CompileDynamic
    protected Dependency findProjectCliCompanion(Project project, ProjectComponentIdentifier componentIdentifier) {
        if (!isProjectOfThisBuild(project, componentIdentifier)) {
            return null
        }
        Project target = project.rootProject.findProject(componentIdentifier.projectPath)
        def cliArtifactId = target.findProperty('cliArtifactId')
        if (!cliArtifactId) {
            // the extra property is exported in the producer's afterEvaluate; the cliArtifact
            // extension (and its convention) exists from plugin-apply time, so consult it
            // directly when discovery runs before the producer has been fully evaluated
            cliArtifactId = target.extensions.findByName('cliArtifact')?.artifactId?.getOrNull()
        }
        if (!cliArtifactId) {
            return null
        }
        String capabilityCoordinate = "${target.group}:${cliArtifactId}" as String
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

    @CompileDynamic
    protected void configureApplicationCommands(Project project) {
        def applicationContextCommands = FactoriesLoaderSupport.loadFactoryNames(APPLICATION_CONTEXT_COMMAND_CLASS,
                FactoriesLoaderSupport.classLoader, FactoriesLoaderSupport.CLI_FACTORIES_RESOURCE_LOCATION)
        project.afterEvaluate {
            FileCollection fileCollection = ClasspathUtils.buildClasspath(project, project.configurations.runtimeClasspath, project.configurations.grailsCliClasspath)
            // commands come from the buildscript classloader (legacy placement) and from the
            // project's cli tier, so auto-provisioned command artifacts register their tasks
            // without any buildscript classpath entry
            Set<String> commandClassNames = new LinkedHashSet<String>()
            if (applicationContextCommands) {
                commandClassNames.addAll(applicationContextCommands as List)
            }
            GrailsExtension grails = project.extensions.getByType(GrailsExtension)
            if (grails.cliAutoProvision.get()) {
                commandClassNames.addAll(loadCommandNamesFromCliClasspath(project))
            }
            // Legacy Grails 7 application commands intentionally do not get named Gradle tasks.
            // Configuration-time runtimeClasspath scans were nondeterministic in multi-project
            // builds. Those commands remain available through the generic runCommand task and the
            // shell adapter once grails-core-cli-legacy is on grailsCliClasspath.
            for (ctxCommand in commandClassNames) {
                String taskName = GrailsNameUtils.getLogicalPropertyName(ctxCommand, 'Command')
                String commandName = GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(ctxCommand, 'Command'))
                if (!project.tasks.names.contains(taskName)) {
                    project.tasks.register(taskName, ApplicationContextCommandTask).configure {
                        it.group = GRAILS_COMMAND_GROUP
                        it.description = "Runs the Grails ${commandName} command"
                        it.classpath = fileCollection
                        it.command = commandName
                        it.systemProperty(Environment.KEY, System.getProperty(Environment.KEY, Environment.DEVELOPMENT.getName()))
                        List<String> args = []
                        def otherArgs = project.findProperty('args')
                        if (otherArgs) {
                            args.addAll(CommandLineParser.translateCommandline(otherArgs as String))
                        }

                        def appClassProvider = GrailsGradlePlugin.getMainClassProvider(project)

                        it.doFirst {
                            // -Pargs split as it always was, the arguments of --arg as they are, or the file of --args-file
                            it.args(it.runnerArguments(args, GrailsCliGradlePlugin.requireMainClass(appClassProvider, it.name)))
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads command class names from the {@code META-INF/grails-cli.factories} files of the
     * resolved {@code grailsCliClasspath} jars, so per-command Gradle tasks (e.g. {@code dbmUpdate})
     * are registered for auto-provisioned cli artifacts. Resolution is lenient and jars that are
     * not built yet (project dependencies within a composite) are skipped — the generic
     * {@code runCommand} task can always execute those commands regardless.
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
                String factoryNames = properties.getProperty(APPLICATION_CONTEXT_COMMAND_CLASS)
                if (factoryNames) {
                    names.addAll(factoryNames.split(',').toList()*.trim())
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

    @CompileDynamic
    protected void configureRunScript(Project project) {
        if (!project.tasks.names.contains('runScript')) {
            def runTask = project.tasks.register('runScript', ApplicationContextScriptTask)
            project.afterEvaluate {
                runTask.configure {
                    it.group = GRAILS_COMMAND_GROUP
                    it.description = 'Executes a Grails script'
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

    @CompileDynamic
    protected void configureRunCommand(Project project) {
        if (!project.tasks.names.contains('runCommand')) {
            def runTask = project.tasks.register('runCommand', ApplicationContextCommandTask)
            project.afterEvaluate {
                runTask.configure {
                    it.group = GRAILS_COMMAND_GROUP
                    it.description = 'Runs a Grails command against the application context'
                    SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
                    it.classpath = mainSourceSet.runtimeClasspath + project.configurations.getByName(GRAILS_CLI_CLASSPATH_CONFIGURATION)
                    it.systemProperty(Environment.KEY, System.getProperty(Environment.KEY, Environment.DEVELOPMENT.getName()))

                    // devtools' automatic restart mechanism uses a specialized classloader setup, which can interfere
                    // with Grails' plugin management and bean wiring when running CLI commands via Gradle
                    it.systemProperty('spring.devtools.restart.enabled', 'false')

                    List<String> args = []
                    def otherArgs = project.findProperty('args')
                    if (otherArgs) {
                        args.addAll(CommandLineParser.translateCommandline(otherArgs as String))
                    }

                    def appClassProvider = GrailsGradlePlugin.getMainClassProvider(project)

                    it.doFirst {
                        // -Pargs split as it always was, the arguments of --arg as they are, or the file of --args-file
                        it.args(it.runnerArguments(args, GrailsCliGradlePlugin.requireMainClass(appClassProvider, it.name)))
                    }

                }
            }
        }
    }

}
