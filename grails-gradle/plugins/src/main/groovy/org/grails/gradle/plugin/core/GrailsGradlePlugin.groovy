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

import grails.util.BuildSettings
import grails.util.Environment
import grails.util.GrailsNameUtils
import grails.util.Metadata
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import io.spring.gradle.dependencymanagement.DependencyManagementPlugin
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.apache.grails.gradle.common.PropertyFileUtils
import org.apache.tools.ant.filters.EscapeUnicode
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.AttributeMatchingStrategy
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.process.JavaForkOptions
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.grails.build.parsing.CommandLineParser
import org.grails.gradle.plugin.commands.ApplicationContextCommandTask
import org.grails.gradle.plugin.commands.ApplicationContextScriptTask
import org.grails.gradle.plugin.exploded.ExplodedCompatibilityRule
import org.grails.gradle.plugin.exploded.ExplodedDisambiguationRule
import org.grails.gradle.plugin.exploded.GrailsExplodedPlugin
import org.grails.gradle.plugin.model.GrailsClasspathToolingModelBuilder
import org.grails.gradle.plugin.run.FindMainClassTask
import org.grails.gradle.plugin.util.SourceSets
import org.grails.io.support.FactoriesLoaderSupport
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.plugin.ResolveMainClassName
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootArchive
import org.springframework.boot.gradle.tasks.run.BootRun
import org.springframework.boot.loader.tools.LoaderImplementation

import javax.inject.Inject

/**
 * The main Grails gradle plugin implementation
 *
 * @since 3.0
 * @author Graeme Rocher
 */
@CompileStatic
class GrailsGradlePlugin extends GroovyPlugin {

    public static final String APPLICATION_CONTEXT_COMMAND_CLASS = 'grails.dev.commands.ApplicationCommand'

    List<Class<Plugin>> basePluginClasses = [IntegrationTestGradlePlugin] as List<Class<Plugin>>
    List<String> excludedGrailsAppSourceDirs = ['migrations', 'assets']
    List<String> grailsAppResourceDirs = ['views', 'i18n', 'conf', 'migrations']
    private final ToolingModelBuilderRegistry registry

    @Inject
    GrailsGradlePlugin(ToolingModelBuilderRegistry registry) {
        this.registry = registry
    }

    void apply(Project project) {
        // validate that only an app or a plugin is registered, and never both
        OnlyOneGrailsPlugin marker = (OnlyOneGrailsPlugin) project.getExtensions().findByName(OnlyOneGrailsPlugin.name)
        if (marker) {
            throw new GradleException("Project ${project.name} cannot be both a Grails application and a Grails plugin. Previously applied plugin: ${marker.pluginClassname}. Cannot apply: ${getClass().name}")
        }
        project.getExtensions().add(OnlyOneGrailsPlugin.name, new OnlyOneGrailsPlugin(pluginClassname: getClass().name))

        // reset the environment to ensure it is resolved again for each invocation
        Environment.reset()

        if (!project.tasks.names.contains('compileGroovy')) {
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

        String grailsVersion = resolveGrailsVersion(project)

        enableNative2Ascii(project, grailsVersion)

        configureAssetCompilation(project)

        configureConsoleTask(project)

        configureForkSettings(project, grailsVersion)

        configureJavaCompatibilityArgs(project)

        configureGrailsSourceDirs(project)

        configureApplicationCommands(project)

        project.gradle.projectsEvaluated {
            createBuildPropertiesTask(project)
        }

        configureRunScript(project)

        configureRunCommand(project)

        configureGroovyCompiler(project)

        configureMatchingExplodedRules(project)
    }

    private void configureMatchingExplodedRules(Project project) {
        /**
         * the exploded plugin may or may not be configured for the given project, these rules ensure tasks that are considered "development"
         * running tasks (like bootRun) will prefer the exploded variant of a plugin if it is available but still match the non-exploded variant if not.
         */
        project.dependencies.attributesSchema { schema ->
            schema.attribute(GrailsExplodedPlugin.EXPLODED_ATTRIBUTE).with { AttributeMatchingStrategy details ->
                details.compatibilityRules.add(ExplodedCompatibilityRule)
                details.disambiguationRules.add(ExplodedDisambiguationRule)
            }
        }
    }

    protected static Provider<String> getMainClassProvider(Project project) {
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
        project.tasks.withType(GroovyCompile).configureEach { GroovyCompile c ->
            // Use a task-specific config file to avoid overlapping outputs when multiple
            // GroovyCompile tasks exist in the same project (e.g. compileGroovy, compileTestGroovy).
            Provider<RegularFile> groovyCompilerConfigFile = project.layout.buildDirectory.file("grailsGroovyCompilerConfig-${c.name}.groovy")
            c.outputs.file(groovyCompilerConfigFile)

            Closure<String> userScriptGenerator = getGroovyCompilerScript(c, project)
            c.doFirst {
                // This isn't ideal - we're performing configuration at execution time, but the alternative would be having
                // to maintain a clean / configuration task and then gradle would want to cache those tasks.  Since the inputs
                // to those tasks would effectively be the runtimeClasspath, dependency problems can arise if another task
                // changes the runtimeClasspath. To prevent having to add those tasks into the dependency chain, use doFirst
                File combinedFile = groovyCompilerConfigFile.get().asFile
                if (!combinedFile.exists()) {
                    combinedFile.parentFile.mkdirs()
                    combinedFile.createNewFile()
                }

                String configuredScript = null
                if (c.groovyOptions.configurationScript) {
                    configuredScript = c.groovyOptions.configurationScript.text?.trim() ?: null
                }
                String grailsScript = userScriptGenerator?.call()

                String combinedScripts = """
                    // Grails groovy compilation configuration to ensure ASTs are applied correctly
                    
                    ${grailsScript?.trim() ?: ''}

                    ${configuredScript?.trim() ?: ''}
                """
                combinedFile.write(combinedScripts)
                c.groovyOptions.configurationScript = combinedFile
            }
        }
    }

    protected Closure<String> getGroovyCompilerScript(GroovyCompile compile, Project project) {
        GrailsExtension grails = project.extensions.findByType(GrailsExtension)
        if (!grails.importJavaTime) {
            return null
        }

        return { ->
            '''withConfig(configuration) {
                    imports {
                        star 'java.time'
                    }
                }
            '''
        }
    }

    protected void excludeDependencies(Project project) {
        // Perhaps change to check that if this is a Grails plugin, don't exclude?
        // Adding an exclusion to every dependency in a pom is very verbose and
        // greatly increases the size of the pom.
        // It would be nice to have documented in a comment why this global exclude is in here
        String slf4jPreventExclusion = project.properties['slf4jPreventExclusion']
        if (!slf4jPreventExclusion || slf4jPreventExclusion != 'true') {
            project.configurations.configureEach { Configuration configuration ->
                configuration.exclude(group: 'org.slf4j', module: 'slf4j-simple')
            }
        }
    }

    protected void configureProfile(Project project) {
        if (!project.configurations.names.contains(GrailsClasspathToolingModelBuilder.PROFILE_CONFIGURATION_NAME)) {
            project.configurations.register(GrailsClasspathToolingModelBuilder.PROFILE_CONFIGURATION_NAME).configure { Configuration profileConfiguration ->
                profileConfiguration.description = 'Configuration that allows for finding profile artifacts so commands, scripts, and other helpers can be found by the Grails Shell'
                profileConfiguration.canBeConsumed = false
                profileConfiguration.canBeResolved = true
                profileConfiguration.transitive = true

                profileConfiguration.defaultDependencies { DependencySet deps ->
                    String defaultProfileCoordinates = "org.apache.grails.profiles:${System.getProperty('grails.profile') ?: getDefaultProfile()}:${project.properties['grailsVersion'] ?: BuildSettings.grailsVersion}" as String
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
                        details.because('Grails Profile defined without a version, defaulting to configured Grails Version')
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
            Map<String, Object> buildPropertiesContents = [
                'grails.env': Environment.isSystemSet() ? Environment.getCurrent().getName() : Environment.PRODUCTION.getName(),
                'info.app.name': project.name,
                'info.app.version': project.version instanceof Serializable ? project.version : project.version.toString(),
                'info.app.grailsVersion': project.properties.get('grailsVersion')
            ]

            buildPropertiesTask.inputs.properties(buildPropertiesContents)
            buildPropertiesTask.outputs.file(buildInfoFile)
            buildPropertiesTask.doLast {
                project.buildDir.mkdirs()
                ant.mkdir(dir: buildInfoFile.parentFile)
                ant.propertyfile(file: buildInfoFile) {
                    for (me in buildPropertiesTask.inputs.properties) {
                        entry(key: me.key, value: me.value)
                    }
                }
                PropertyFileUtils.makePropertiesFileReproducible(buildInfoFile)
            }

            TaskContainer tasks = project.tasks
            tasks.findByName('processResources')?.dependsOn(buildPropertiesTask)
        }
    }

    @CompileStatic
    protected void configureMicronaut(Project project) {
        project.afterEvaluate {
            boolean micronautEnabled = project.getConfigurations().getByName('implementation').getDependencies().findAll { Dependency dep -> dep.group == 'org.apache.grails' && dep.name == 'grails-micronaut' } as boolean
            if (!micronautEnabled) {
                return
            }

            GrailsExtension ge = project.extensions.getByType(GrailsExtension)
            if (!ge.micronautAutoSetup) {
                return
            }

            project.logger.lifecycle('Micronaut Support Detected for {}', project.name)

            // Validate that grails-micronaut-bom is applied as enforcedPlatform. The BOM is now the
            // single source of truth for the Micronaut platform version: applying it as
            // enforcedPlatform pins io.micronaut.platform:micronaut-platform with a strict
            // constraint that no transitive can override.
            validateMicronautBom(project)

            project.logger.info('Configuring CLASSIC boot loader for Micronaut compatibility in {}', project.name)
            project.tasks.withType(BootArchive).configureEach {
                it.loaderImplementation.convention(LoaderImplementation.CLASSIC)
            }

        }
    }

    /**
     * Validates that grails-micronaut-bom is applied as an enforcedPlatform when micronaut is used.
     * The grails-micronaut-bom layers Micronaut-specific overrides (e.g. javaparser-core) on top
     * of grails-bom; without enforcedPlatform, Micronaut's platform would override these versions
     * via Gradle's conflict resolution. Regular Grails projects (without Micronaut) should continue
     * to use the spring-managed versions via plain platform(:grails-bom).
     */
    @CompileStatic
    protected static void validateMicronautBom(Project project) {
        Configuration implConfig = project.configurations.findByName('implementation')
        if (implConfig == null) {
            return
        }

        for (Dependency dep : implConfig.dependencies) {
            if (dep.name == 'grails-micronaut-bom' && dep instanceof ModuleDependency) {
                Object categoryAttr = ((ModuleDependency) dep).attributes.getAttribute(
                        org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
                )
                if (categoryAttr != null && categoryAttr.toString() == org.gradle.api.attributes.Category.ENFORCED_PLATFORM) {
                    return // correctly configured
                }
            }
        }

        throw new GradleException(
                "Project '${project.name}' uses Micronaut but does not apply grails-micronaut-bom as an enforcedPlatform. " +
                        "Micronaut's platform declares higher versions of javaparser-core and other libraries that would " +
                        'override the grails-bom versions via conflict resolution. Change to:\n\n' +
                        '    implementation enforcedPlatform(project(\':grails-micronaut-bom\'))\n'
        )
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
        (['src/main/resources'] + grailsAppResourceDirs.collect { 'grails-app/' + it })
                .collect { project.file(it) }
                .sort { it.name } // sort for build reproducibility
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

        grailsSourceDirs
                .tap { add(project.file('src/main/groovy')) }
                .sort { it.name } // sort for build reproducibility
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

    protected <T extends JavaForkOptions & DefaultTask> void configureForkSettings(Project project, String grailsVersion) {
        def systemPropertyConfigurer = { String defaultGrailsEnv, T task ->
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

            task.systemProperty(Metadata.APPLICATION_NAME, project.name)
            task.systemProperty(Metadata.APPLICATION_VERSION, (project.version instanceof Serializable ? project.version : project.version.toString()))
            task.systemProperty(Metadata.APPLICATION_GRAILS_VERSION, grailsVersion)
            // Use a CommandLineArgumentProvider so that the absolute project directory path
            // is normalized for build cache relocatability (PathSensitivity.RELATIVE).
            task.jvmArgumentProviders.add(new GrailsAppBaseDirProvider(project.projectDir))
            task.systemProperty(BuildSettings.PROJECT_TARGET_DIR, project.layout.buildDirectory.get().asFile.name)
            task.systemProperty(Environment.KEY, defaultGrailsEnv)
            task.systemProperty(Environment.FULL_STACKTRACE, System.getProperty(Environment.FULL_STACKTRACE) ?: '')
            if (task.minHeapSize == null) {
                task.minHeapSize = '768m'
            }
            if (task.maxHeapSize == null) {
                task.maxHeapSize = '768m'
            }

            // Copy GRAILS_FORK_OPTS into the fork. Or use GRAILS_OPTS if no fork options provided
            // This allows run-app etc. to run using appropriate settings and allows users to provided
            // different FORK JVM options to the build options.
            def envMap = System.getenv()
            String opts = envMap.GRAILS_FORK_OPTS ?: envMap.GRAILS_OPTS
            if (opts) {
                task.jvmArgs(opts.split(' '))
            }
        }

        TaskContainer tasks = project.tasks

        String grailsEnvSystemProperty = System.getProperty(Environment.KEY)
        tasks.withType(Test).configureEach(systemPropertyConfigurer.curry(grailsEnvSystemProperty ?: Environment.TEST.getName()))
        tasks.withType(JavaExec).configureEach(systemPropertyConfigurer.curry(grailsEnvSystemProperty ?: Environment.DEVELOPMENT.getName()))

        configureToolchainForForkTasks(project)
    }

    /**
     * Configures {@link JavaExec} tasks to inherit the project's Java toolchain.
     *
     * <p>Gradle's {@code JavaPlugin} already sets toolchain conventions on
     * {@code JavaCompile}, {@code Javadoc}, and {@code Test} tasks, but does
     * <strong>not</strong> set them on {@code JavaExec} tasks. This means forked
     * JVM processes (dbm-* migration tasks, console, shell, and application
     * context commands) use the JDK running Gradle instead of the project's
     * configured toolchain. When the project targets a different JDK version
     * than the one running Gradle, this causes {@code UnsupportedClassVersionError}
     * or silent runtime failures.</p>
     *
     * <p>This method only acts when the user has explicitly configured a toolchain
     * via {@code java.toolchain.languageVersion}. When no toolchain is configured,
     * behavior is unchanged - tasks use the JDK running Gradle as before.</p>
     *
     * <p>Uses {@code convention()} so that individual tasks can still override
     * the launcher via {@code javaLauncher.set(...)} if needed.</p>
     *
     * @param project the Gradle project
     * @since 7.0.8
     */
    protected void configureToolchainForForkTasks(Project project) {
        project.plugins.withId('java') {
            project.tasks.withType(JavaExec).configureEach { JavaExec task ->
                def javaExtension = project.extensions.findByType(JavaPluginExtension)
                if (javaExtension?.toolchain?.languageVersion?.isPresent()) {
                    def toolchainService = project.extensions.getByType(JavaToolchainService)
                    def launcher = toolchainService.launcherFor(javaExtension.toolchain)
                    task.javaLauncher.convention(launcher)
                }
            }
        }
    }

    /**
     * Configures JVM arguments required for compatibility with Java 23+.
     *
     * <p>Java 24 introduced restrictions on native access ({@code JEP 472}) that cause
     * warnings from libraries such as hawtjni (used by JLine) and Netty that call
     * {@code System.loadLibrary} or declare native methods. The
     * {@code --enable-native-access=ALL-UNNAMED} flag suppresses these warnings and
     * will become mandatory in a future JDK release when the default changes to deny.</p>
     *
     * <p>Java 23 began terminal deprecation of {@code sun.misc.Unsafe} memory-access
     * methods ({@code JEP 471/498}). Netty 4.1.x uses {@code Unsafe.allocateMemory}
     * for off-heap buffers. The {@code --sun-misc-unsafe-memory-access=allow} flag
     * suppresses the resulting warnings until Netty migrates to {@code MemorySegment}
     * APIs (Netty 4.2+).</p>
     *
     * <p>Both flags are only added when the target JVM version (from the configured
     * toolchain, or the JVM running Gradle if no toolchain is set) is high enough to
     * recognize them, avoiding {@code Unrecognized option} errors on older JDKs.</p>
     *
     * @param project the Gradle project
     * @see <a href="https://github.com/apache/grails-core/issues/15216">#15216 - Java 25 native access warnings</a>
     * @see <a href="https://github.com/apache/grails-core/issues/15343">#15343 - sun.misc.Unsafe deprecation warnings</a>
     * @since 7.0.8
     */
    protected void configureJavaCompatibilityArgs(Project project) {
        project.plugins.withId('java') {
            project.tasks.withType(Test).configureEach { Test task ->
                applyCompatArgs(project, task, task.name)
            }
            project.tasks.withType(JavaExec).configureEach { JavaExec task ->
                applyCompatArgs(project, task, task.name)
            }
        }
    }

    private void applyCompatArgs(Project project, JavaForkOptions task, String taskName) {
        int targetVersion = resolveTargetJavaVersion(project)

        if (targetVersion >= 24) {
            task.jvmArgs('--enable-native-access=ALL-UNNAMED')
            project.logger.info("Grails: adding --enable-native-access=ALL-UNNAMED to ${taskName} for Java ${targetVersion} compatibility")
        }

        if (targetVersion >= 23) {
            task.jvmArgs('--sun-misc-unsafe-memory-access=allow')
            project.logger.info("Grails: adding --sun-misc-unsafe-memory-access=allow to ${taskName} for Java ${targetVersion} compatibility")
        }
    }

    /**
     * Resolves the Java version that forked tasks will use. Checks the project's
     * toolchain configuration first, falling back to the JVM running Gradle.
     *
     * @param project the Gradle project
     * @return the major Java version number (e.g. 17, 21, 24, 25)
     */
    private int resolveTargetJavaVersion(Project project) {
        JavaPluginExtension javaExtension = project.extensions.findByType(JavaPluginExtension)
        if (javaExtension?.toolchain?.languageVersion?.isPresent()) {
            return javaExtension.toolchain.languageVersion.get().asInt()
        }
        return JavaVersion.current().majorVersion.toInteger()
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
                        throw new GradleException(/If overriding the mainClass, the property 'mainClass' and the springboot.mainClass must be set to the same value/)
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
                it.systemProperty('spring.output.ansi.console-available', 'true')
            }

            project.tasks.withType(ResolveMainClassName).configureEach {
                it.dependsOn(findMainClassTask)
                it.configuredMainClassName.convention(GrailsGradlePlugin.getMainClassProvider(project))
            }
        } else if (!FindMainClassTask.isAssignableFrom(existingTask.class)) {
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
                'info.app.name': project.name,
                'info.app.version': project.version?.toString(),
                'info.app.grailsVersion': grailsVersion
            ]

            task.from(project.relativePath('src/main/templates')) { spec ->
                spec.into('META-INF/templates')
            }

            if (!native2ascii) {
                task.from(sourceSet.resources) { spec ->
                    spec.include('**/*.properties')
                    spec.filter(ReplaceTokens, tokens: replaceTokens)
                }
            } else if (!grailsExt.native2asciiAnt) {
                task.from(sourceSet.resources) { spec ->
                    spec.include('**/*.properties')
                    spec.filter(ReplaceTokens, tokens: replaceTokens)
                    spec.filter(EscapeUnicode)
                }
            }

            task.from(sourceSet.resources) { spec ->
                spec.filter(ReplaceTokens, tokens: replaceTokens)
                spec.include('**/*.groovy')
                spec.include('**/*.yml')
                spec.include('**/*.xml')
            }

            task.from(sourceSet.resources) { spec ->
                spec.exclude('**/*.properties')
                spec.exclude('**/*.groovy')
                spec.exclude('**/*.yml')
                spec.exclude('**/*.xml')
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
                    SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
                    it.classpath = mainSourceSet.runtimeClasspath + project.configurations.getByName('console')
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

    protected FileCollection buildClasspath(Project project, String... configurationNames) {
        buildClasspath(
                project,
                configurationNames.collect {
                    project.configurations.named(it).getOrNull()
                }.findAll(/* remove nulls */) as Configuration[]
        )
    }

    @CompileStatic
    private static final class OnlyOneGrailsPlugin {
        String pluginClassname
    }
}
