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

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.stream.Stream

import grails.util.BuildSettings
import grails.util.GrailsNameUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ValueSourceSpec
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources

import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver

import org.grails.gradle.plugin.bom.BomPropertyOverridesExtension
import org.grails.gradle.plugin.bom.BomPropertyOverridesPlugin
import org.grails.gradle.plugin.commands.GrailsCliArtifactGradlePlugin
import org.grails.gradle.plugin.i18n.GenerateI18nDescriptorTask
import org.grails.gradle.plugin.run.FindMainClassTask
import org.grails.gradle.plugin.util.SourceSets

/**
 * A Gradle plugin for Grails plugins
 *
 * @author Graeme Rocher
 * @since 3.0
 *
 */
@CompileStatic
class GrailsPluginGradlePlugin extends GrailsGradlePlugin {

    public static final String PLUGIN_ID = 'org.apache.grails.gradle.grails-plugin'

    /** Build-dir root staged by {@code copyCommands}; laid out as the archive sees it. */
    protected static final String COMMANDS_STAGING_DIR = 'grails/plugin-commands'

    /** Build-dir root staged by {@code copyTemplates}; laid out as the archive sees it. */
    protected static final String TEMPLATES_STAGING_DIR = 'grails/plugin-templates'

    /** Build-dir root staged by {@code copyForgePack}; laid out as the archive sees it. */
    protected static final String FORGE_PACK_STAGING_DIR = 'grails/plugin-forge-pack'

    /** The directory of a plugin project that holds its Forge feature pack. */
    public static final String FORGE_PACK_SOURCE_DIR = 'src/main/forge'

    /** The descriptor of a Forge feature pack, in its source directory and in the jar. */
    public static final String FORGE_PACK_DESCRIPTOR = 'feature.yml'

    /** Where a Forge feature pack is in the jar of the plugin. */
    public static final String FORGE_PACK_JAR_DIR = 'META-INF/grails-forge'

    /** The manifest attribute that names the Forge feature pack a jar holds. */
    public static final String FORGE_PACK_MANIFEST_ATTRIBUTE = 'Grails-Forge-Pack'

    /** The configuration that holds Forge, which checks the feature pack of the plugin. */
    public static final String FORGE_PACK_TOOLING_CONFIGURATION = 'grailsForgePackTooling'

    /** The task that checks the Forge feature pack of the plugin. */
    public static final String FORGE_PACK_CHECK_TASK = 'checkForgePack'

    /** The task that tests the Forge feature pack of the plugin with generated applications. */
    public static final String FORGE_PACK_TEST_TASK = 'forgePackTest'

    /** Forge, without its version: what checks the feature pack of the plugin unless the build names another. */
    public static final String FORGE_PACK_TOOLING = 'org.apache.grails.forge:grails-forge-core'

    private static final Pattern FORGE_PACK_NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)

    private static final int FORGE_PACK_NAME_LENGTH = 200

    private static final int FORGE_PACK_DESCRIPTOR_LENGTH = 256 * 1024

    @Override
    protected String grailsArtifactType() {
        GenerateI18nDescriptorTask.TYPE_PLUGIN
    }

    /**
     * The Grails plugin name, derived from the {@code *GrailsPlugin.groovy} descriptor exactly as
     * {@link GrailsNameUtils#getPluginName} does at runtime, so
     * {@code SpringSecurityCoreGrailsPlugin} yields {@code spring-security-core}.
     *
     * <p>Recording the runtime name rather than the Gradle project name is what lets the i18n
     * descriptor be matched against the plugins the application actually discovers, and it is the
     * namespace a plugin's message bundle base names must sit within.</p>
     *
     * <p>Resolved lazily: the descriptor is a source file, so the search must not happen while the
     * project is still being configured.</p>
     */
    @Override
    protected Provider<String> grailsArtifactName(Project project) {
        project.provider {
            SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
            File descriptor = mainSourceSet?.allSource?.matching { PatternFilterable pattern ->
                pattern.include('**/*GrailsPlugin.groovy')
            }?.files?.sort { File file -> file.name }?.find()
            descriptor ? GrailsNameUtils.getPluginName(descriptor.name) : project.name
        }
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        project.pluginManager.apply('java-library')

        // every Grails plugin publishes the companions of its tools, each when its folder has sources; applying
        // the id org.apache.grails.gradle.grails-plugin-cli in the build script as well changes nothing
        project.pluginManager.apply(GrailsCliArtifactGradlePlugin)

        checkForConfigurationClash(project)
        configureAstSources(project)
        configureAssembleTask(project)
        configurePluginResources(project)
        configureJarTask(project)
        configureForgePack(project)
        configureForgePackCheck(project)
        configureForgePackTest(project)
        configureSourcesJarTask(project)
    }

    @Override
    protected Closure<String> getGroovyCompilerScript(GroovyCompile compile, Project project) {
        def versionProvider = project.provider { project.version.toString() }
        compile.inputs.property('version', versionProvider)

        def projectNameProvider = project.provider { project.name }
        compile.inputs.property('name', projectNameProvider)

        Closure<String> parent = super.getGroovyCompilerScript(compile, project)
        return { ->
            """${parent?.call() ?: ''}

            withConfig(configuration) {
                inline(phase: 'CONVERSION') { source, context, classNode ->
                    classNode.putNodeMetaData('projectVersion', '${versionProvider.get()}')
                    classNode.putNodeMetaData('projectName', '${projectNameProvider.get()}')
                    classNode.putNodeMetaData('isPlugin', 'true')
                }
            }
            """ as String
        }
    }

    @Override
    protected void createBuildPropertiesTask(Project project) {
        // no-op
    }

    @CompileStatic
    protected void configureSourcesJarTask(Project project) {
        if (!project.tasks.names.contains('sourcesJar')) {
            project.logger.info('A sourcesJar task was not found, creating one.', project.name)
            project.tasks.register('sourcesJar', Jar).configure { Jar jarTask ->
                jarTask.archiveClassifier.set('sources')
                jarTask.from(SourceSets.findMainSourceSet(project).allSource)
            }
        }
    }

    @Override
    protected void applySpringBootPlugin(Project project) {
        super.applySpringBootPlugin(project)
        project.tasks.withType(BootJar).configureEach { BootJar bootJar ->
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
            it.from(sourceSets.ast.output)
            it.into(project.layout.buildDirectory.dir('classes/groovy/main'))
        }
        // The AST classes are copied into the main classes directory, so every task that reads the main
        // classes directories (not only the ones wired through 'classes') must run after the copy.
        (sourceSets.main.output.classesDirs as ConfigurableFileCollection).builtBy(copyAstClasses)

        project.tasks.named('findMainClass', FindMainClassTask).configure {
            it.dependsOn(copyAstClasses)
        }

        def taskContainer = project.tasks
        taskContainer.named('classes').configure { it.dependsOn(copyAstClasses) }

        taskContainer.withType(JavaExec).configureEach {
            it.classpath += sourceSets.ast.output
        }

        // configureEach, not whenTaskAdded: the eager hook realizes every registered task at this point,
        // processResources among them, and the resource processing then reads the grails extension
        // before the build script has configured it (native2ascii, for one)
        taskContainer.configureEach { Task task ->
            if (task.name == 'compileWebappGroovyPages') {
                task.dependsOn(copyAstClasses)
            }
        }

        project.afterEvaluate {
            Task sourcesJarTask = taskContainer.findByName('sourcesJar')
            if (sourcesJarTask) {
                project.rootProject.logger.info('Found sources jar task')
                sourcesJarTask.configure {
                    project.rootProject.logger.info('Including ast in sources jar')
                    from(sourceSets.ast.allSource)
                }
            } else {
                project.rootProject.logger.info('No sources jar task found')
            }

            Task javadocTask = taskContainer.findByName('javadoc')
            if (javadocTask) {
                javadocTask.configure {
                    source += sourceSets.ast.allJava
                }
            } else {
                project.rootProject.logger.info('Warning - a javadocTask was not found, so the ast source will not be included in the javadoc task')
            }

            Task groovydocTask = taskContainer.findByName('groovydoc')
            if (groovydocTask) {
                if (taskContainer.findByName('javadocJar') == null) {
                    taskContainer.create('javadocJar', Jar) {
                        archiveClassifier.set('javadoc')
                        from(groovydocTask.outputs)
                        outputs.cacheIf { true }
                    }.dependsOn(javadocTask)
                }

                groovydocTask.configure {
                    source += sourceSets.ast.allJava
                }
            } else {
                project.rootProject.logger.info('Warning - a groovydocTask was not found, so the ast source will not be included in the groovydoc task')
            }
        }
    }

    protected void configureAssembleTask(Project project) {
        // Assemble task in Grails Plugins should only produce a plain jar
        project.tasks.named('assemble').configure { Task assembleTask ->
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
        project.tasks.named('jar', Jar).configure { Jar jarTask ->
            jarTask.enabled = true
            jarTask.archiveClassifier.set('') // Remove '-plain' suffix from jar file name
            jarTask.exclude(
                    'application.groovy',
                    'application.yml',
                    'logback.groovy',
                    'logback.xml',
                    'logback-spring.xml',
                    // Plugins must not ship spring/resources.groovy (use doWithSpring instead),
                    // but it must remain in build/resources/main/ so it is on the integration
                    // test classpath for plugin modules that test their own resources.groovy.
                    'spring/resources.groovy'
            )
        }
    }

    /**
     * Packages the Forge feature pack of the plugin, when it has one: the {@code feature.yml} and the
     * {@code templates} directory under {@code src/main/forge} go into {@code META-INF/grails-forge}
     * of the jar, and the manifest of the jar names the pack, so that a tool can tell a jar holds one
     * without reading the descriptor. A plugin without that directory is built as before. The build
     * fails for a pack Forge could not read from the jar: one without a descriptor, with a link, or with
     * a file Gradle leaves out of every copy, such as {@code .gitignore}.
     *
     * <p>The pack is staged in a directory of its own, attached to the output of the main source set
     * like the templates of the plugin are, so the GSP templates of a pack are not dropped by the
     * pattern that keeps views out of {@code processResources}.</p>
     */
    protected void configureForgePack(Project project) {
        ProjectLayout layout = project.layout
        Directory source = layout.projectDirectory.dir(FORGE_PACK_SOURCE_DIR)

        File sourceDirectory = source.asFile
        TaskProvider<Copy> copyForgePack = project.tasks.register('copyForgePack', Copy) { Copy copy ->
            copy.from(source) { CopySpec spec ->
                spec.include(FORGE_PACK_DESCRIPTOR, 'templates/**')
            }
            copy.into(layout.buildDirectory.dir("${FORGE_PACK_STAGING_DIR}/${FORGE_PACK_JAR_DIR}"))
            copy.includeEmptyDirs = false
            // Gradle leaves some files out of every copy (.gitignore, backup files and the like) and does
            // not look at them, or at what is a link, as inputs either, so it would take the task for up to
            // date when one is added. The pack is small: it is staged and checked every time
            copy.outputs.upToDateWhen { false }
            regenerateStagingTree(copy)
            copy.doFirst { Task task ->
                checkForgePackSource(sourceDirectory)
            }
            copy.doLast { Task task ->
                List<String> missing = forgePackFiles(sourceDirectory) - forgePackFiles(((Copy) task).destinationDir)
                if (missing) {
                    throw new GradleException("The feature pack in ${FORGE_PACK_SOURCE_DIR} has files Gradle leaves out of a jar, " +
                            "so it cannot name them: ${missing.join(', ')}")
                }
            }
        }

        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).output
                .dir([builtBy: copyForgePack], layout.buildDirectory.dir(FORGE_PACK_STAGING_DIR))

        // read as an input of the configuration: a pack that is added or renamed is seen by the next build
        Provider<String> descriptor = project.providers.fileContents(source.file(FORGE_PACK_DESCRIPTOR)).asText
        project.tasks.named('jar', Jar).configure { Jar jarTask ->
            if (descriptor.isPresent()) {
                jarTask.manifest.attributes([(FORGE_PACK_MANIFEST_ATTRIBUTE): forgePackName(descriptor.get())])
            }
        }
    }

    /**
     * Checks the Forge feature pack of the plugin, when it has one, as a part of {@code check}: the
     * task {@code checkForgePack} has Forge read the pack the way it reads one when an application is
     * created, and compile and render every template. A plugin without {@code src/main/forge} is built
     * as before, and resolves nothing for it.
     *
     * <p>Forge is resolved from {@code grailsForgePackTooling}, a configuration of this task alone that
     * nothing of the plugin is published or compiled with, and runs in a JVM of its own. The
     * configuration has the Forge of the Grails version of the project unless the build declares
     * another.</p>
     *
     * <p>The jar is built without the check: {@code jar} and {@code assemble} need no Forge to be
     * resolved, as they need no test to pass, and neither does {@code publishToMavenLocal}.
     * {@code check}, and so {@code build}, is what fails for a pack Forge could not use, and so does
     * publishing the plugin to a repository, which does not otherwise depend on {@code check}.</p>
     */
    protected void configureForgePackCheck(Project project) {
        ProjectLayout layout = project.layout
        ProviderFactory providers = project.providers
        Directory source = layout.projectDirectory.dir(FORGE_PACK_SOURCE_DIR)
        DependencyHandler dependencyHandler = project.dependencies
        ObjectFactory objects = project.objects

        Provider<String> presence = providers.of(ForgePackStateValueSource) { ValueSourceSpec<ForgePackStateValueSource.Parameters> spec ->
            spec.parameters.packDirectory.set(source)
            spec.parameters.digest.set(false)
        }
        project.configurations.register(FORGE_PACK_TOOLING_CONFIGURATION) { Configuration configuration ->
            configuration.description = 'Forge, which checks the feature pack of the plugin. It is not a dependency of the plugin.'
            configuration.canBeConsumed = false
            configuration.canBeResolved = true
            configuration.attributes { AttributeContainer attributes ->
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
            }
            configuration.defaultDependencies { DependencySet dependencies ->
                // read here as an input of the configuration: a pack that is added later is seen, and a
                // plugin without a pack has nothing to resolve
                if (presence.get() != ForgePackStateValueSource.ABSENT) {
                    String grailsVersion = (project.findProperty('grailsVersion') ?: BuildSettings.grailsVersion) as String
                    dependencies.add(dependencyHandler.create("${FORGE_PACK_TOOLING}:${grailsVersion}".toString()))
                }
            }
        }

        // the overrides of BOM properties of the project are not for the class path of Forge either
        project.pluginManager.withPlugin(BomPropertyOverridesPlugin.PLUGIN_ID) {
            project.extensions.getByType(BomPropertyOverridesExtension).excludeConfiguration(FORGE_PACK_TOOLING_CONFIGURATION)
        }
        TaskProvider<CheckForgePackTask> checkForgePack = project.tasks.register(FORGE_PACK_CHECK_TASK, CheckForgePackTask) { CheckForgePackTask task ->
            task.group = LifecycleBasePlugin.VERIFICATION_GROUP
            task.description = "Checks the Forge feature pack in ${FORGE_PACK_SOURCE_DIR} with Forge."
            configureForgePackTool(project, task)
            task.markerFile.set(layout.buildDirectory.file('grails/forge-pack-check/checked'))
        }
        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { Task check ->
            check.dependsOn(checkForgePack)
        }
        // a pack nobody checked is not published for others to find out. Installing into the local
        // repository of a developer stays without it, as the jar does
        project.pluginManager.withPlugin('maven-publish') {
            project.tasks.withType(PublishToMavenRepository).configureEach { PublishToMavenRepository publish ->
                publish.dependsOn(checkForgePack)
            }
        }
    }

    /**
     * Gives a task that runs Forge on the feature pack of the plugin what it runs it on and with: the
     * pack, the class path of {@code grailsForgePackTooling} and what of it could not be had, and the
     * Java of the toolchain. A plugin without {@code src/main/forge} skips the task.
     */
    private static void configureForgePackTool(Project project, ForgePackToolTask task) {
        ProviderFactory providers = project.providers
        Directory source = project.layout.projectDirectory.dir(FORGE_PACK_SOURCE_DIR)
        File sourceDirectory = source.asFile
        NamedDomainObjectProvider<Configuration> tooling = project.configurations.named(FORGE_PACK_TOOLING_CONFIGURATION)
        Provider<String> lookAgain = providers.of(NeverTheSameValueSource) { ValueSourceSpec<ValueSourceParameters.None> spec -> }
        task.packDirectory.set(source)
        // not read while the build is configured, so it is read again by every build
        task.packState.set(providers.of(ForgePackStateValueSource) { ValueSourceSpec<ForgePackStateValueSource.Parameters> spec ->
            spec.parameters.packDirectory.set(source)
            spec.parameters.digest.set(true)
        })
        // what cannot be resolved or fetched is left out here and fails the task, which can say what it is for
        task.toolingClasspath.from(tooling.map { Configuration configuration ->
            configuration.incoming.artifactView { ArtifactView.ViewConfiguration view -> view.lenient(true) }.files
        })
        task.toolingProblems.set(tooling.map { Configuration configuration ->
            ForgeToolingProblems problems = ForgeToolingProblems.of(configuration)
            if (!problems.empty) {
                // kept with the configuration cache, this would be what the next build is told too,
                // although the repository has it by then: that build is to look again
                lookAgain.get()
            }
            problems
        })
        task.javaLauncher.set(project.extensions.getByType(JavaToolchainService)
                .launcherFor(project.extensions.getByType(JavaPluginExtension).toolchain))
        task.onlyIf("there is a feature pack in ${FORGE_PACK_SOURCE_DIR}") { Task checked ->
            Files.exists(sourceDirectory.toPath(), LinkOption.NOFOLLOW_LINKS)
        }
    }

    /**
     * Tests the Forge feature pack of the plugin, when it has one and when it is asked for: the task
     * {@code forgePackTest} has Forge generate applications with the feature of the pack, and builds
     * each with its own Gradle wrapper. No other task depends on it, since it builds applications,
     * which takes minutes and the repositories Grails is in. What is generated and how it is built is
     * said in the {@code grails { forgePackTest { } }} block. The Maven publications of the project are
     * given to the applications in a repository of the task, before every other one, as this build has
     * just built them: see {@link ForgePackPublications}.
     */
    protected void configureForgePackTest(Project project) {
        ProjectLayout layout = project.layout
        GrailsForgePackTestOptions options = project.extensions.getByType(GrailsExtension).forgePackTest
        RepositoryHandler declared = project.repositories
        // the repositories of the build script, when the build names no others: read when the task is
        // about to run, after every build script has declared its own
        Provider<ForgePackFixtureRepositories> ofBuildScript = project.provider {
            ForgePackFixtureRepositories.of(declared.withType(MavenArtifactRepository))
        }
        Provider<Boolean> named = project.provider { options.repositories.isPresent() }
        Provider<List<String>> entries = named.flatMap { Boolean given ->
            given ? options.repositories : ofBuildScript.map { ForgePackFixtureRepositories repositories -> repositories.used }
        }
        // a build that is offline builds its fixtures offline: part of what a configuration is stored for
        boolean offlineBuild = project.gradle.startParameter.offline
        String gradleUserHome = project.gradle.gradleUserHomeDir.absolutePath
        project.tasks.register(FORGE_PACK_TEST_TASK, ForgePackTestTask) { ForgePackTestTask task ->
            task.group = LifecycleBasePlugin.VERIFICATION_GROUP
            task.description = "Generates applications with the Forge feature pack in ${FORGE_PACK_SOURCE_DIR} and builds them. " +
                    'Not a part of check.'
            configureForgePackTool(project, task)
            task.fixtures.set(project.provider { options.fixtureLines() })
            task.buildTasks.set(options.tasks)
            task.buildArguments.set(options.arguments)
            task.repositories.set(options.mavenLocal.zip(entries) { Boolean mavenLocal, List<String> repositories ->
                (mavenLocal ? [ForgePackFixtureRepositories.MAVEN_LOCAL] + repositories : repositories).toUnique()
            })
            task.repositoriesDigest.set(task.repositories.map { List<String> repositories -> ForgePackFixtureRepositories.digest(repositories) })
            task.repositoriesLeftOut.set(named.flatMap { Boolean given ->
                given ? project.provider { Collections.<String>emptyList() } :
                        ofBuildScript.map { ForgePackFixtureRepositories repositories -> repositories.leftOut }
            })
            task.offline.convention(options.offline.map { Boolean offline -> offline || offlineBuild })
            task.fixtureTimeout.set(options.fixtureTimeout)
            task.gradleUserHome.set(gradleUserHome)
            task.fixturesDirectory.set(layout.buildDirectory.dir('forge-pack-fixtures'))
            task.reportDirectory.set(layout.buildDirectory.dir('forge-pack-test'))
            task.publicationRepository.set(layout.buildDirectory.dir('forge-pack-repository'))
            task.publicationLayout.convention(Collections.<String, String>emptyMap())
            task.publicationPaths.set(task.publicationLayout.map { Map<String, String> files -> new ArrayList<String>(files.keySet()) })
            task.publicationFiles.from(task.publicationLayout.map { Map<String, String> files -> files.values() })
            // what the applications get of the plugin is what this build has just built: the publications are
            // built before the task and are its inputs, so a plugin that was built again is tested again
            project.pluginManager.withPlugin('maven-publish') {
                PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
                TaskContainer tasks = project.tasks
                task.publicationLayout.set(project.provider { ForgePackPublications.layout(publishing, tasks) })
                task.publicationFiles.builtBy(project.provider { ForgePackPublications.builtBy(publishing, tasks) })
            }
        }
    }

    /**
     * The files of a pack below a directory, by their path from it: the descriptor and what is under
     * {@code templates}. Links are not followed.
     */
    private static List<String> forgePackFiles(File directory) {
        List<String> files = []
        Path root = directory.toPath()
        Path descriptor = root.resolve(FORGE_PACK_DESCRIPTOR)
        if (Files.isRegularFile(descriptor, LinkOption.NOFOLLOW_LINKS)) {
            files << FORGE_PACK_DESCRIPTOR
        }
        Path templates = root.resolve('templates')
        if (Files.isDirectory(templates, LinkOption.NOFOLLOW_LINKS)) {
            Files.walk(templates).withCloseable { Stream<Path> paths ->
                paths.filter { Path path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) }
                        .forEach { Path path -> files << root.relativize(path).join('/') }
            }
        }
        return files.toSorted()
    }

    /**
     * A directory for a pack holds a descriptor, and nothing of it is a link, see {@link ForgePackLinks}.
     */
    private static void checkForgePackSource(File directory) {
        ForgePackLinks.refuseLinkedDirectory(directory)
        if (!Files.isRegularFile(directory.toPath().resolve(FORGE_PACK_DESCRIPTOR), LinkOption.NOFOLLOW_LINKS)) {
            throw new GradleException("${FORGE_PACK_SOURCE_DIR} has files of a feature pack and no ${FORGE_PACK_DESCRIPTOR} that is a file")
        }
        ForgePackLinks.refuseLinkedTemplates(directory)
    }

    /**
     * The name a descriptor of a Forge feature pack gives the pack. Forge checks the whole of a
     * descriptor when the pack is used; only the name is read here, for the manifest.
     */
    protected static String forgePackName(String descriptor) {
        if (descriptor.length() > FORGE_PACK_DESCRIPTOR_LENGTH) {
            throw new GradleException("${FORGE_PACK_SOURCE_DIR}/${FORGE_PACK_DESCRIPTOR} has more than the ${FORGE_PACK_DESCRIPTOR_LENGTH} " +
                    'characters a descriptor can have')
        }
        Object name = null
        try {
            LoaderOptions options = new LoaderOptions()
            options.allowDuplicateKeys = false
            options.maxAliasesForCollections = 0
            // every value as it is written, as Forge reads a name: yes and on are names, not true
            Resolver asWritten = new Resolver() {

                @Override
                protected void addImplicitResolvers() {
                }
            }
            Object root = new Yaml(new SafeConstructor(options), new Representer(new DumperOptions()), new DumperOptions(), options, asWritten)
                    .load(descriptor.startsWith('\uFEFF') ? descriptor.substring(1) : descriptor)
            name = root instanceof Map ? ((Map) root).get('name') : null
            if (name instanceof String) {
                name = ((String) name).replaceAll(/[\r\n]+$/, '')
            }
        } catch (YAMLException e) {
            throw new GradleException("${FORGE_PACK_SOURCE_DIR}/${FORGE_PACK_DESCRIPTOR} cannot be read as YAML: " +
                    (e.message ?: e.class.simpleName).readLines().first())
        }
        if (name instanceof String && name.length() <= FORGE_PACK_NAME_LENGTH && FORGE_PACK_NAME.matcher((String) name).matches() &&
                !(name in ['true', 'false', 'null'])) {
            return (String) name
        }
        throw new GradleException("${FORGE_PACK_SOURCE_DIR}/${FORGE_PACK_DESCRIPTOR} does not name the feature pack: 'name' has to be " +
                'text of lowercase letters, digits and single hyphens that starts with a letter')
    }

    /**
     * A plugin's {@code src/main/templates} are always packaged by {@code copyTemplates}, so the
     * base wiring that folds them into {@code processResources} must not also apply. Routing them
     * through {@code processResources} would subject them to the {@code **}{@code /*.gsp} exclusion
     * that keeps compiled views out of {@code build/resources/main}, silently dropping GSP
     * templates such as the ones {@code generate-views} and {@code s2ui-override} render from.
     */
    @Override
    protected void configureTemplateResources(Project project) {
    }

    /**
     * Packages plugin templates into the runtime jar and routes command scripts into the companion
     * {@code -cli} jar.
     *
     * <p>{@code src/main/scripts} is staged as {@code META-INF/commands} on the cli source set, so
     * Groovy/YAML/JSON command resources ship only on {@code grailsCliClasspath} and stay out of
     * {@code runtimeClasspath}, {@code bootJar}, and {@code bootWar}: every Grails plugin publishes a
     * {@code -cli} companion when it has a script ({@link GrailsCliArtifactGradlePlugin}). Templates
     * always stay on the runtime jar.</p>
     *
     * <p>{@code copyCommands} and {@code copyTemplates} stay {@link Copy} tasks - build scripts
     * configure them through {@code tasks.named('copyTemplates', Copy)} - and each owns one
     * directory that nothing else writes, laid out exactly as the archive sees it. The staging
     * tree is wiped before it is regenerated, so a script or template whose source was deleted
     * stops being packaged; a plain {@code Copy} only ever adds. The wipe runs as a task action,
     * so it happens only when the task actually executes and up-to-date checks are untouched.</p>
     *
     * <p>Those directories are attached to a source set output instead of being copied again by
     * {@code process*Resources}. That is what keeps GSP templates intact: {@code grails-app/views}
     * is a resource directory whose {@code *.gsp} files are excluded from {@code processResources},
     * and copy patterns set on a task apply to every spec composed into it - so anything routed
     * through {@code processResources} would lose the GSP templates that {@code generate-views} and
     * {@code s2ui-override} render from.</p>
     */
    protected void configurePluginResources(Project project) {
        ProjectLayout layout = project.layout

        TaskProvider<Copy> copyCommands = project.tasks.register('copyCommands', Copy) { Copy copy ->
            copy.from(layout.projectDirectory.dir('src/main/scripts'))
            copy.into(layout.buildDirectory.dir("${COMMANDS_STAGING_DIR}/META-INF/commands"))
            regenerateStagingTree(copy)
        }

        TaskProvider<Copy> copyTemplates = project.tasks.register('copyTemplates', Copy) { Copy copy ->
            copy.from(layout.projectDirectory.dir('src/main/templates'))
            copy.into(layout.buildDirectory.dir("${TEMPLATES_STAGING_DIR}/META-INF/templates"))
            regenerateStagingTree(copy)
        }

        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        mainSourceSet.output.dir([builtBy: copyTemplates], layout.buildDirectory.dir(TEMPLATES_STAGING_DIR))

        project.tasks.named('processResources', ProcessResources).configure { ProcessResources task ->
            // grails-app/views is a resource directory, but a plugin's views are compiled rather
            // than packaged. Templates carrying the same GSPs are staged above, out of reach of
            // this pattern.
            task.exclude('**/*.gsp')
        }

        routeCommandResources(project, copyCommands)
    }

    /**
     * Clears a copy task's own destination before it runs, so the tree it produces is exactly the
     * tree its sources describe. Registered as a task action rather than a separate clean task:
     * Gradle settles up-to-dateness before any action runs, so an unchanged build still skips the
     * task instead of being forced to re-copy.
     */
    private static void regenerateStagingTree(Copy copy) {
        copy.doFirst { Task task ->
            ((Copy) task).destinationDir.deleteDir()
        }
    }

    /**
     * Sends {@code src/main/scripts} to the cli source set, whose companion every Grails plugin with a
     * script publishes.
     */
    private void routeCommandResources(Project project, TaskProvider<Copy> copyCommands) {
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        sourceSets.getByName(GrailsCliArtifactGradlePlugin.CLI_SOURCE_SET_NAME).output.dir([builtBy: copyCommands],
                project.layout.buildDirectory.dir(COMMANDS_STAGING_DIR))
    }

    protected void checkForConfigurationClash(Project project) {
        File yamlConfig = new File(project.projectDir, 'grails-app/conf/plugin.yml')
        File groovyConfig = new File(project.projectDir, 'grails-app/conf/plugin.groovy')
        if (yamlConfig.exists() && groovyConfig.exists()) {
            throw new RuntimeException('A plugin may define a plugin.yml or a plugin.groovy, but not both')
        }
    }
}
