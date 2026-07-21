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

import java.util.jar.JarFile

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import grails.util.Environment
import grails.util.GrailsNameUtils
import org.grails.gradle.plugin.core.GrailsExtension
import org.grails.gradle.plugin.core.GrailsGradlePlugin
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

    /** The resolvable view of {@link #GRAILS_CLI_CONFIGURATION} used by the command-runner tasks */
    public static final String GRAILS_CLI_CLASSPATH_CONFIGURATION = 'grailsCliClasspath'

    /**
     * Set this project property to {@code false} to stop the plugin from auto-provisioning the
     * CLI tier onto {@link #GRAILS_CLI_CONFIGURATION}; equivalent to
     * {@code grails { cliAutoProvision = false }}.
     */
    public static final String GRAILS_CLI_AUTO_PROVISION_PROPERTY = 'grailsCliAutoProvision'

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
        grailsCliClasspath.extendsFrom(grailsCli)
        grailsCliClasspath.canBeResolved = true
        grailsCliClasspath.canBeConsumed = false
        grailsCliClasspath.description = 'Resolvable view of grailsCli used by the command-runner tasks.'

        Configuration grailsCliDetect = configurations.create(GRAILS_CLI_DETECT_CONFIGURATION)
        grailsCliDetect.canBeResolved = true
        grailsCliDetect.canBeConsumed = false
        grailsCliDetect.visible = false
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
        dependencies.add(project.dependencies.create('org.apache.grails:grails-core-cli'))
        dependencies.add(project.dependencies.create('org.apache.grails:grails-console'))

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
            Dependency projectCompanion = findProjectCliCompanion(project, (ProjectComponentIdentifier) componentIdentifier)
            if (projectCompanion != null) {
                return projectCompanion
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
        project.dependencies.create(advertised)
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
        Project target = project.rootProject.findProject(componentIdentifier.projectPath)
        // only a project of the consuming build is addressable via findProject; a component from
        // another build of the composite shares the project-path namespace and would resolve to
        // the wrong project — the build tree path comparison rejects that collision
        if (target == null || target.buildTreePath != componentIdentifier.buildTreePath) {
            return null
        }
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
                        List<Object> args = []
                        def otherArgs = project.findProperty('args')
                        if (otherArgs) {
                            args.addAll(CommandLineParser.translateCommandline(otherArgs as String))
                        }

                        def appClassProvider = GrailsGradlePlugin.getMainClassProvider(project)

                        it.doFirst {
                            args << appClassProvider.get()
                            it.args(args)
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
                // unreadable jar — skip
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
                    it.args(appClass.get())
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
                    it.args(appClass.get())
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
                        args << appClassProvider.get()
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

                    List<Object> args = []
                    def otherArgs = project.findProperty('args')
                    if (otherArgs) {
                        args.addAll(CommandLineParser.translateCommandline(otherArgs as String))
                    }

                    def appClassProvider = GrailsGradlePlugin.getMainClassProvider(project)

                    it.doFirst {
                        args << appClassProvider.get()
                        it.args(args)
                    }

                }
            }
        }
    }

}
