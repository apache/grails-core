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
package org.grails.gradle.plugin.core

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.regex.Pattern

import javax.inject.Inject

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import io.spring.gradle.dependencymanagement.DependencyManagementPlugin
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.apache.tools.ant.filters.EscapeUnicode
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.process.JavaForkOptions
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry

import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.plugin.ResolveMainClassName
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootArchive
import org.springframework.boot.gradle.tasks.run.BootRun

import grails.util.BuildSettings
import grails.util.Environment
import grails.util.GrailsNameUtils
import grails.util.Metadata
import org.grails.build.parsing.CommandLineParser
import org.grails.gradle.plugin.agent.AgentTasksEnhancer
import org.grails.gradle.plugin.commands.ApplicationContextCommandTask
import org.grails.gradle.plugin.commands.ApplicationContextScriptTask
import org.grails.gradle.plugin.model.GrailsClasspathToolingModelBuilder
import org.grails.gradle.plugin.run.FindMainClassTask
import org.grails.gradle.plugin.util.SourceSets
import org.grails.io.support.FactoriesLoaderSupport

/**
 * The main Grails gradle plugin implementation
 *
 * @since 3.0
 * @author Graeme Rocher
 */
@CompileStatic
class GrailsGradlePlugin extends GroovyPlugin {

    public static final String APPLICATION_CONTEXT_COMMAND_CLASS = 'grails.dev.commands.ApplicationCommand'

    protected static final List<String> CORE_GORM_LIBRARIES = ['async', 'core', 'simple', 'web', 'rest-client', 'gorm', 'gorm-validation', 'gorm-plugin-support', 'gorm-support', 'test-support', 'hibernate-core', 'gorm-test', 'rx', 'rx-plugin-support']
    // NOTE: mongodb, neo4j etc. should NOT be included here so they can be independently versioned
    protected static final List<String> CORE_GORM_PLUGINS = ['hibernate4', 'hibernate5']

    List<Class<Plugin>> basePluginClasses = [IntegrationTestGradlePlugin] as List<Class<Plugin>>
    List<String> excludedGrailsAppSourceDirs = ['migrations', 'assets']
    List<String> grailsAppResourceDirs = ['views', 'i18n', 'conf']
    private final ToolingModelBuilderRegistry registry

    @Inject
    GrailsGradlePlugin(ToolingModelBuilderRegistry registry) {
        this.registry = registry
    }

    void apply(Project project) {
        // reset the environment to ensure it is resolved again for each invocation
        Environment.reset()

        if (project.tasks.findByName('compileGroovy') == null) {
            super.apply(project)
        }

        excludeDependencies(project)

        configureProfile(project)

        registerGrailsExtension(project)

        applyDefaultPlugins(project)

        configureGroovy(project)

        configureMicronaut(project)

        registerToolingModelBuilder(project, registry)

        applyBasePlugins(project)

        registerFindMainClassTask(project)

        configureGrailsBuildSettings(project)

        configureFileWatch(project)

        String grailsVersion = resolveGrailsVersion(project)

        enableNative2Ascii(project, grailsVersion)

        configureAssetCompilation(project)

        configureConsoleTask(project)

        configureForkSettings(project, grailsVersion)

        configureGrailsSourceDirs(project)

        configureApplicationCommands(project)

        project.gradle.projectsEvaluated {
            createBuildPropertiesTask(project)
        }

        configureRunScript(project)

        configureRunCommand(project)

        configureGroovyCompiler(project)

        addGroovyCompilerScript('GrailsCore', project) {
            if (!project.extensions.findByType(GrailsExtension).importJavaTime) {
                return null
            }

            """
                withConfig(configuration) {
                    imports {
                        star 'java.time'
                    }
                }
            """.stripIndent(16)
        }
    }

    // TODO: this is copied from grails-common, but we should consider moving grails-common up to grails-gradle once
    // the publish plugin is moved out of grails-core
    static void makePropertiesFileReproducible(File factoriesFile) {
        String sourceDateEpoch = System.getenv('SOURCE_DATE_EPOCH')
        if (!sourceDateEpoch) {
            sourceDateEpoch = LocalDate.now(ZoneOffset.UTC)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toEpochSecond().toString()
        }

        Pattern timeRegex = Pattern.compile('^#(?:Sun|Mon|Tue|Wed|Thu|Fri|Sat)(?:,|\\\\s).*$')

        List<String> lines = factoriesFile.readLines(StandardCharsets.ISO_8859_1.name())

        boolean dateReplaced = false
        factoriesFile.withWriter { BufferedWriter writer ->
            lines.each { String line ->
                if (!dateReplaced && timeRegex.matcher(line).matches()) {
                    dateReplaced = true
                    writer.writeLine("# SOURCE_DATE_EPOCH = ${sourceDateEpoch}" as String)
                    return
                }

                writer.writeLine(line)
            }
        }
    }

    private static Provider<String> getMainClassProvider(Project project) {
        Provider<FindMainClassTask> findMainClassTask = project.tasks.named('findMainClass', FindMainClassTask)
        project.provider {
            File cacheFile = findMainClassTask.get().mainClassCacheFile.orNull?.asFile
            if (!cacheFile?.exists()) {
                return null
            }

            cacheFile?.text
        }
    }

    private void configureGroovyCompiler(Project project) {
        Provider<Directory> sourceConfigFiles = project.layout.buildDirectory.dir('groovyCompilerConfiguration')
        Provider<RegularFile> groovyCompilerConfigFile = project.layout.buildDirectory.file('grailsGroovyCompilerConfig.groovy')
        if (!project.tasks.findByName('configureGroovyCompiler')) {
            TaskProvider<Task> cleanGroovyConfigProvider = project.tasks.register('cleanGroovyCompilerConfig')
            cleanGroovyConfigProvider.configure { Task task ->
                task.group = 'build'
                task.doFirst {
                    sourceConfigFiles.get().asFile.deleteDir()
                    sourceConfigFiles.get().asFile.mkdirs()

                    File combinedFile = groovyCompilerConfigFile.get().asFile
                    if (!combinedFile.exists()) {
                        combinedFile.parentFile.mkdirs()
                        combinedFile.createNewFile()
                    }
                    combinedFile.write('// Placeholder for grails metadata and other configuration')
                }
            }
            // Merge the script at runtime so we don't suffer a performance penalty as part of every gradle task run
            TaskProvider<Task> configureTaskProvider = project.tasks.register('configureGroovyCompiler')
            configureTaskProvider.configure { Task task ->
                task.group = 'build'
                task.dependsOn('cleanGroovyCompilerConfig')

                // Gradle will cache the output based on the directory, so we must ensure it exists
                sourceConfigFiles.get().asFile.mkdirs()

                task.inputs.dir(sourceConfigFiles)
                task.outputs.file(groovyCompilerConfigFile)

                task.doLast {
                    List<String> scripts = sourceConfigFiles.get().asFile.listFiles({ File dir, String name ->
                        name.endsWithIgnoreCase('groovy')
                    } as FilenameFilter).collect { it.text }

                    GroovyCompile compileTask = project.tasks.named('compileGroovy', GroovyCompile).get()
                    if (compileTask.groovyOptions.configurationScript) {
                        scripts << compileTask.groovyOptions.configurationScript.text
                    }

                    String combinedScripts = scripts.findResults { it?.trim() }.join('\n').trim()
                    if (combinedScripts) {
                        File combinedFile = groovyCompilerConfigFile.get().asFile
                        combinedFile.parentFile.mkdirs()
                        combinedFile.write(combinedScripts)
                        compileTask.groovyOptions.configurationScript = combinedFile
                    }
                }
            }

            // Because the gradle plugin extends the groovy plugin, this will always exist at this point
            project.tasks.withType(GroovyCompile).configureEach {
                it.dependsOn(configureTaskProvider, cleanGroovyConfigProvider)
            }
        }
    }

    protected TaskProvider<Task> addGroovyCompilerScript(String uniqueScriptName, Project project, Closure scriptGenerator) {
        String taskName = "configureGroovyCompiler${uniqueScriptName}" as String
        if (taskName in project.tasks.names) {
            return project.tasks.named(taskName)
        }

        TaskProvider<Task> configScriptTask = project.tasks.register(taskName)
        configScriptTask.configure { Task task ->
            task.group = 'build'

            Provider<RegularFile> targetConfigFile = project.layout.buildDirectory.file("groovyCompilerConfiguration/${uniqueScriptName}Config.groovy")
            task.outputs.file(targetConfigFile)
            task.inputs.files(project.configurations.named('runtimeClasspath'))
            task.dependsOn('cleanGroovyCompilerConfig')

            task.doLast {
                File file = targetConfigFile.get().asFile
                file.delete()

                String script = scriptGenerator.call(project)
                if (script) {
                    file.text = script
                }
            }
        }

        project.tasks.named('configureGroovyCompiler').configure { Task task ->
            task.dependsOn(configScriptTask)
        }

        return configScriptTask
    }

    protected void excludeDependencies(Project project) {
        // Perhaps change to check that if this is a Grails plugin, don't exclude?
        // Adding an exclusion to every dependency in a pom is very verbose and
        // greatly increases the size of the pom.
        // It would be nice to have documented in a comment why this global exclude is in here
        String slf4jPreventExclusion = project.properties['slf4jPreventExclusion']
        if (!slf4jPreventExclusion || slf4jPreventExclusion != 'true') {
            project.configurations.configureEach { Configuration configuration ->
                configuration.exclude group: 'org.slf4j', module: 'slf4j-simple'
            }
        }
    }

    protected void configureProfile(Project project) {
        if (!project.configurations.names.contains(GrailsClasspathToolingModelBuilder.PROFILE_CONFIGURATION_NAME)) {
            project.configurations.register(GrailsClasspathToolingModelBuilder.PROFILE_CONFIGURATION_NAME).configure { Configuration profileConfiguration ->
                profileConfiguration.description = "Configuration that allows for finding profile artifacts so commands, scripts, and other helpers can be found by the Grails Shell"
                profileConfiguration.canBeConsumed = false
                profileConfiguration.canBeResolved = true
                profileConfiguration.transitive = true

                profileConfiguration.defaultDependencies { DependencySet deps ->
                    String defaultProfileCoordinates = "org.apache.grails.profiles:${System.getProperty("grails.profile") ?: getDefaultProfile()}:${project.properties['grailsVersion'] ?: BuildSettings.grailsVersion}" as String
                    project.logger.info('No Grails profile is defined for project {}, defaulting to: {}', project.name, defaultProfileCoordinates)
                    deps.add(
                            project.dependencies.create(defaultProfileCoordinates)
                    )
                }

                profileConfiguration.resolutionStrategy.eachDependency { details ->
                    if (details.requested.group == 'org.apache.grails.profiles' && !details.requested.version) {
                        String grailsVersion = (project.findProperty('grailsVersion') ?: BuildSettings.grailsVersion) as String
                        project.logger.info('Dependency: {}:{} did not define a version, defaulting to grails version {}', details.requested.group, details.requested.name, grailsVersion)

                        details.useVersion(grailsVersion)
                        details.because("Grails Profile defined without a version, defaulting to configured Grails Version")
                    }
                }
            }
        }
    }

    protected void applyDefaultPlugins(Project project) {
        applySpringBootPlugin(project)

        project.afterEvaluate {
            GrailsExtension ge = project.extensions.getByType(GrailsExtension)
            if (ge.springDependencyManagement) {
                Plugin dependencyManagementPlugin = project.plugins.findPlugin(DependencyManagementPlugin)
                if (dependencyManagementPlugin == null) {
                    project.plugins.apply(DependencyManagementPlugin)
                }

                DependencyManagementExtension dme = project.extensions.findByType(DependencyManagementExtension)

                applyBomImport(dme, project)
            }
        }
    }

    protected void applySpringBootPlugin(Project project) {
        def springBoot = project.extensions.findByType(SpringBootExtension)
        if (!springBoot) {
            project.plugins.apply(SpringBootPlugin)
        }
    }

    @CompileDynamic
    private void applyBomImport(DependencyManagementExtension dme, project) {
        dme.imports({
            mavenBom("org.apache.grails:grails-bom:${project.properties['grailsVersion']}")
        })
        dme.setApplyMavenExclusions(false)
    }

    protected String getDefaultProfile() {
        'web'
    }

    @CompileDynamic
    protected Task createBuildPropertiesTask(Project project) {
        if (project.tasks.findByName('buildProperties') == null) {
            File resourcesDir = SourceSets.findMainSourceSet(project).output.resourcesDir
            File buildInfoFile = new File(resourcesDir, 'META-INF/grails.build.info')


            Task buildPropertiesTask = project.tasks.create('buildProperties')
            Map<String, Object> buildPropertiesContents = ['grails.env'            : Environment.isSystemSet() ? Environment.getCurrent().getName() : Environment.PRODUCTION.getName(),
                                                           'info.app.name'         : project.name,
                                                           'info.app.version'      : project.version instanceof Serializable ? project.version : project.version.toString(),
                                                           'info.app.grailsVersion': project.properties.get('grailsVersion')]

            buildPropertiesTask.inputs.properties(buildPropertiesContents)
            buildPropertiesTask.outputs.file(buildInfoFile)
            buildPropertiesTask.doLast {
                project.buildDir.mkdirs()
                ant.mkdir(dir: buildInfoFile.parentFile)
                ant.propertyfile(file: buildInfoFile) {
                    for (me in buildPropertiesTask.inputs.properties) {
                        entry key: me.key, value: me.value
                    }
                }
                makePropertiesFileReproducible(buildInfoFile)
            }

            TaskContainer tasks = project.tasks
            tasks.findByName('processResources')?.dependsOn(buildPropertiesTask)
        }
    }

    @CompileStatic
    protected void configureMicronaut(Project project) {
        final String micronautVersion = project.properties['micronautVersion']
        if (micronautVersion) {
            project.configurations.configureEach({ Configuration configuration ->
                configuration.resolutionStrategy.eachDependency({ DependencyResolveDetails details ->
                    String dependencyName = details.requested.name
                    String group = details.requested.group
                    if (group == 'io.micronaut' && dependencyName.startsWith('micronaut')) {
                        details.useVersion(micronautVersion)
                    }
                } as Action<DependencyResolveDetails>)
            } as Action<Configuration>)
        }
    }

    @CompileStatic
    protected void configureGroovy(Project project) {
        final String groovyVersion = project.properties['groovy.version']
        if (groovyVersion) {
            project.logger.lifecycle('Warning: groovy.version is defined, Grails Gradle Plugin will force all groovy dependencies to version {}.', groovyVersion)
            project.configurations.configureEach { Configuration configuration ->
                configuration.resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                    String group = details.requested.group
                    if (group == 'org.apache.groovy') {
                        details.useVersion(groovyVersion)
                    }
                }
            }
        }
    }

    @CompileStatic
    protected void registerToolingModelBuilder(Project project, ToolingModelBuilderRegistry registry) {
        registry.register(new GrailsClasspathToolingModelBuilder())
    }

    @CompileStatic
    protected void applyBasePlugins(Project project) {
        for (Class<Plugin> cls in basePluginClasses) {
            project.plugins.apply(cls)
        }
    }

    protected GrailsExtension registerGrailsExtension(Project project) {
        if (project.extensions.findByName('grails') == null) {
            project.extensions.add('grails', new GrailsExtension(project))
        }
    }

    @CompileStatic
    protected void configureFileWatch(Project project) {
        def environment = Environment.getCurrent()
        enableFileWatch(environment, project)
    }

    @CompileStatic
    protected String configureGrailsBuildSettings(Project project) {
        System.setProperty(BuildSettings.APP_BASE_DIR, project.projectDir.absolutePath)
    }

    @CompileDynamic
    protected void configureApplicationCommands(Project project) {
        def applicationContextCommands = FactoriesLoaderSupport.loadFactoryNames(APPLICATION_CONTEXT_COMMAND_CLASS)
        project.afterEvaluate {
            FileCollection fileCollection = buildClasspath(project, project.configurations.runtimeClasspath, project.configurations.console)
            for (ctxCommand in applicationContextCommands) {
                String taskName = GrailsNameUtils.getLogicalPropertyName(ctxCommand, 'Command')
                String commandName = GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(ctxCommand, 'Command'))
                if (!project.tasks.names.contains(taskName)) {
                    project.tasks.register(taskName, ApplicationContextCommandTask).configure {
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

    @CompileDynamic
    protected void configureGrailsSourceDirs(Project project) {
        project.sourceSets {
            main {
                groovy {
                    srcDirs = resolveGrailsSourceDirs(project)
                }
                resources {
                    srcDirs = resolveGrailsResourceDirs(project)
                }
            }
        }
    }

    @CompileStatic
    protected List<File> resolveGrailsResourceDirs(Project project) {
        List<File> grailsResourceDirs = [project.file('src/main/resources')]
        for (String f in grailsAppResourceDirs) {
            grailsResourceDirs.add(project.file("grails-app/${f}"))
        }
        grailsResourceDirs
    }

    @CompileStatic
    protected List<File> resolveGrailsSourceDirs(Project project) {
        List<File> grailsSourceDirs = []
        File grailsApp = project.file('grails-app')
        if (grailsApp.exists()) {
            grailsApp.eachDir { File subdir ->
                if (isGrailsSourceDirectory(subdir)) {
                    grailsSourceDirs.add(subdir)
                }
            }
        }
        grailsSourceDirs.add(project.file('src/main/groovy'))
        grailsSourceDirs
    }

    @CompileStatic
    protected boolean isGrailsSourceDirectory(File subdir) {
        def dirName = subdir.name
        !subdir.hidden && !dirName.startsWith('.') && !excludedGrailsAppSourceDirs.contains(dirName) && !grailsAppResourceDirs.contains(dirName)
    }

    protected String resolveGrailsVersion(Project project) {
        def grailsVersion = project.property('grailsVersion')

        if (!grailsVersion) {
            def grailsCoreDep = project.configurations.getByName('compileClasspath').dependencies.find { Dependency d -> d.name == 'grails-core' }
            grailsVersion = grailsCoreDep.version
        }
        grailsVersion
    }

    @CompileDynamic
    protected void configureAssetCompilation(Project project) {
        if (project.extensions.findByName('assets')) {
            project.assets {
                assetsPath = project.layout.projectDirectory.dir('grails-app/assets')
            }
            project.tasks.named('assetCompile').configure {
                it.destinationDirectory = project.layout.buildDirectory.dir('assetCompile/assets')
            }
        }
    }

    protected void configureForkSettings(Project project, String grailsVersion) {

        def systemPropertyConfigurer = { String defaultGrailsEnv, JavaForkOptions task ->
            def map = System.properties.findAll { entry ->
                entry.key?.toString()?.startsWith('grails.')
            }
            for (key in map.keySet()) {
                def value = map.get(key)
                if (value) {
                    def sysPropName = key.toString().substring(7)
                    task.systemProperty(sysPropName, value.toString())
                }
            }

            task.systemProperty Metadata.APPLICATION_NAME, project.name
            task.systemProperty Metadata.APPLICATION_VERSION, (project.version instanceof Serializable ? project.version : project.version.toString())
            task.systemProperty Metadata.APPLICATION_GRAILS_VERSION, grailsVersion
            task.systemProperty Environment.KEY, defaultGrailsEnv
            task.systemProperty Environment.FULL_STACKTRACE, System.getProperty(Environment.FULL_STACKTRACE) ?: ''
            if (task.minHeapSize == null) {
                task.minHeapSize = '768m'
            }
            if (task.maxHeapSize == null) {
                task.maxHeapSize = '768m'
            }
            task.jvmArgs '-XX:+TieredCompilation', '-XX:TieredStopAtLevel=1', '-XX:CICompilerCount=3'

            // Copy GRAILS_FORK_OPTS into the fork. Or use GRAILS_OPTS if no fork options provided
            // This allows run-app etc. to run using appropriate settings and allows users to provided
            // different FORK JVM options to the build options.
            def envMap = System.getenv()
            String opts = envMap.GRAILS_FORK_OPTS ?: envMap.GRAILS_OPTS
            if (opts) {
                task.jvmArgs opts.split(' ')
            }
        }

        TaskContainer tasks = project.tasks

        String grailsEnvSystemProperty = System.getProperty(Environment.KEY)
        tasks.withType(Test).each systemPropertyConfigurer.curry(grailsEnvSystemProperty ?: Environment.TEST.getName())
        tasks.withType(JavaExec).each systemPropertyConfigurer.curry(grailsEnvSystemProperty ?: Environment.DEVELOPMENT.getName())
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
                it.dependsOn(tasks.named('classes'), tasks.named('findMainClass'))
                it.classpath = project.sourceSets.main.runtimeClasspath + configuration.get()
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
                it.dependsOn(tasks.named('classes'), tasks.named('findMainClass'))
                it.classpath = project.sourceSets.main.runtimeClasspath + configuration.get()
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
    protected void enableFileWatch(Environment environment, Project project) {
        if (environment.isReloadEnabled()) {
            project.configurations {
                agent
            }
            project.afterEvaluate(new AgentTasksEnhancer())
        }
    }

    @CompileDynamic
    protected void registerFindMainClassTask(Project project) {
        TaskContainer taskContainer = project.tasks

        def existingTask = taskContainer.findByName('findMainClass')
        if (existingTask == null) {
            def mainClassFileContainer = project.layout.buildDirectory.file('resolvedMainClassName')
            TaskProvider<FindMainClassTask> findMainClassTask = project.tasks.register('findMainClass', FindMainClassTask)
            findMainClassTask.configure {
                it.dependsOn(project.tasks.named('compileGroovy', GroovyCompile), project.tasks.named('classes'))
                it.mustRunAfter(project.tasks.named('classes'))
                it.mainClassCacheFile.set(mainClassFileContainer)
                it.outputs.upToDateWhen {
                    mainClassFileContainer.orNull?.asFile?.exists()
                }
            }

            project.afterEvaluate {
                // Support overrides - via mainClass property
                def propertyMainClassName = project.findProperty('mainClass')
                if (propertyMainClassName) {
                    findMainClassTask.configure {
                        it.mainClassName.set(propertyMainClassName)
                    }
                }

                // Support overrides - via mainClass springboot extension
                def springBootExtension = project.extensions.getByType(SpringBootExtension)
                String springBootMainClassName = springBootExtension.mainClass.getOrNull()
                if (springBootMainClassName) {
                    findMainClassTask.configure {
                        it.mainClassName.set(springBootMainClassName)
                    }
                }

                if (springBootMainClassName && propertyMainClassName) {
                    if (springBootMainClassName != propertyMainClassName) {
                        throw new GradleException("If overriding the mainClass, the property 'mainClass' and the springboot.mainClass must be set to the same value")
                    }
                }

                def extraProperties = project.extensions.getByType(ExtraPropertiesExtension)
                def overriddenMainClass = propertyMainClassName ?: springBootMainClassName
                if (!overriddenMainClass) {
                    // the findMainClass task needs to set these values
                    extraProperties.set('mainClassName', project.provider {
                        File cacheFile = findMainClassTask.get().mainClassCacheFile.orNull?.asFile
                        if (!cacheFile?.exists()) {
                            return null
                        }

                        cacheFile?.text
                    })

                    springBootExtension.mainClass.set(project.provider {
                        File cacheFile = findMainClassTask.get().mainClassCacheFile.orNull?.asFile
                        if (!cacheFile?.exists()) {
                            return null
                        }

                        cacheFile?.text
                    })
                } else {
                    // we need to set the overridden value on both
                    extraProperties.set('mainClass', overriddenMainClass)
                    springBootExtension.mainClass.set(overriddenMainClass)
                }
            }

            project.tasks.withType(BootArchive).configureEach { BootArchive bootTask ->
                bootTask.dependsOn(findMainClassTask)
                bootTask.mainClass.convention(GrailsGradlePlugin.getMainClassProvider(project))
            }

            project.tasks.withType(BootRun).configureEach { BootRun it ->
                it.dependsOn(findMainClassTask)
                it.mainClass.convention(GrailsGradlePlugin.getMainClassProvider(project))
            }

            project.tasks.withType(ResolveMainClassName).configureEach {
                it.dependsOn(findMainClassTask)
                it.configuredMainClassName.convention(GrailsGradlePlugin.getMainClassProvider(project))
            }
        } else if (!FindMainClassTask.class.isAssignableFrom(existingTask.class)) {
            project.logger.warn('Grails Projects typically register a findMainClass task to force the MainClass resolution for Spring Boot. This task already exists so this will not occur.')
        }
    }

    /**
     * Enables native2ascii processing of resource bundles
     **/
    @CompileDynamic
    protected void enableNative2Ascii(Project project, String grailsVersion) {
        SourceSet sourceSet = SourceSets.findMainSourceSet(project)

        TaskContainer tasks = project.tasks
        tasks.named(sourceSet.processResourcesTaskName).configure { AbstractCopyTask task ->
            GrailsExtension grailsExt = project.extensions.getByType(GrailsExtension)
            boolean native2ascii = grailsExt.native2ascii
            task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE)
            if (native2ascii && grailsExt.native2asciiAnt && !tasks.findByName('native2ascii')) {
                File destinationDir = ((ProcessResources) task).destinationDir
                TaskProvider<Task> native2asciiTask = createNative2AsciiTask(tasks, project.file('grails-app/i18n'), destinationDir)
                task.configure {
                    it.dependsOn(native2asciiTask)
                }
            }

            Map<String, String> replaceTokens = [
                    'info.app.name'         : project.name,
                    'info.app.version'      : project.version?.toString(),
                    'info.app.grailsVersion': grailsVersion
            ]

            task.from(project.relativePath('src/main/templates')) { spec ->
                spec.into('META-INF/templates')
            }

            if (!native2ascii) {
                task.from(sourceSet.resources) { spec ->
                    spec.include '**/*.properties'
                    spec.filter(ReplaceTokens, tokens: replaceTokens)
                }
            } else if (!grailsExt.native2asciiAnt) {
                task.from(sourceSet.resources) { spec ->
                    spec.include '**/*.properties'
                    spec.filter(ReplaceTokens, tokens: replaceTokens)
                    spec.filter(EscapeUnicode)
                }
            }

            task.from(sourceSet.resources) { spec ->
                spec.filter(ReplaceTokens, tokens: replaceTokens)
                spec.include '**/*.groovy'
                spec.include '**/*.yml'
                spec.include '**/*.xml'
            }

            task.from(sourceSet.resources) { spec ->
                spec.exclude '**/*.properties'
                spec.exclude '**/*.groovy'
                spec.exclude '**/*.yml'
                spec.exclude '**/*.xml'
            }
        }
    }

    @CompileDynamic
    protected TaskProvider<Task> createNative2AsciiTask(TaskContainer tasks, src, dest) {
        TaskProvider<Task> native2asciiTask = tasks.register('native2ascii').configure {
            it.doLast {
                it.ant.native2ascii(src: src, dest: dest,
                        includes: '**/*.properties', encoding: 'UTF-8')
            }
            it.inputs.dir(src)
            it.outputs.dir(dest)
        }

        native2asciiTask
    }

    @CompileDynamic
    protected void configureRunScript(Project project) {
        if (!project.tasks.names.contains('runScript')) {
            def runTask = project.tasks.register('runScript', ApplicationContextScriptTask)
            project.afterEvaluate {
                runTask.configure {
                    SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
                    it.classpath = mainSourceSet.runtimeClasspath + project.configurations.getByName('console')
                    it.systemProperty(Environment.KEY, System.getProperty(Environment.KEY, Environment.DEVELOPMENT.getName()))

                    // devtools' automatic restart mechanism uses a specialized classloader setup, which can interfere
                    // with Grails' plugin management and bean wiring when running CLI scripts via Gradle
                    it.systemProperty 'spring.devtools.restart.enabled', 'false'

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
                    SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
                    it.classpath = mainSourceSet.runtimeClasspath + project.configurations.getByName('console')
                    it.systemProperty(Environment.KEY, System.getProperty(Environment.KEY, Environment.DEVELOPMENT.getName()))

                    // devtools' automatic restart mechanism uses a specialized classloader setup, which can interfere
                    // with Grails' plugin management and bean wiring when running CLI commands via Gradle
                    it.systemProperty 'spring.devtools.restart.enabled', 'false'

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

    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        output?.classesDirs ?: project.files(project.layout.buildDirectory.dir('classes/main'))
    }

    protected FileCollection buildClasspath(Project project, Configuration... configurations) {
        SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
        SourceSetOutput output = mainSourceSet?.output
        FileCollection mainFiles = resolveClassesDirs(output, project)
        FileCollection fileCollection = project.files(project.layout.buildDirectory.dir('resources/main'), project.layout.buildDirectory.dir('gsp-classes')) + mainFiles
        configurations.each {
            fileCollection = fileCollection + it.filter({ File file -> !file.name.startsWith('spring-boot-devtools') })
        }
        fileCollection
    }
}
