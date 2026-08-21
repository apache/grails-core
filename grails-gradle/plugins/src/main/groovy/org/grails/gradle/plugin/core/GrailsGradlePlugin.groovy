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

import java.util.zip.ZipFile

import grails.util.BuildSettings
import grails.util.Environment
import grails.util.Metadata
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.grails.gradle.common.PropertyFileUtils
import org.apache.tools.ant.filters.EscapeUnicode
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.AttributeMatchingStrategy
import org.gradle.api.attributes.Category
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.process.JavaForkOptions
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.grails.gradle.plugin.bom.BomPropertyOverridesPlugin
import org.grails.gradle.plugin.commands.GrailsCliGradlePlugin
import org.grails.gradle.plugin.exploded.ExplodedCompatibilityRule
import org.grails.gradle.plugin.exploded.ExplodedDisambiguationRule
import org.grails.gradle.plugin.exploded.GrailsExplodedPlugin
import org.grails.gradle.plugin.i18n.GenerateI18nDescriptorTask
import org.apache.grails.gradle.plugin.aot.AotCacheExtension
import org.apache.grails.gradle.plugin.aot.ExtractApplicationTask
import org.apache.grails.gradle.plugin.aot.GenerateNativeMetadataTask
import org.apache.grails.gradle.plugin.aot.NativeMetadataExtension
import org.apache.grails.gradle.plugin.aot.TraceNativeMetadataTask
import org.apache.grails.gradle.plugin.aot.TrainAotCacheTask
import org.grails.gradle.plugin.model.GrailsClasspathToolingModelBuilder
import org.grails.gradle.plugin.run.FindMainClassTask
import org.grails.gradle.plugin.util.SourceSets
import org.springframework.boot.gradle.dsl.SpringBootExtension
import org.springframework.boot.gradle.plugin.ResolveMainClassName
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootArchive
import org.springframework.boot.gradle.tasks.run.BootRun
import javax.inject.Inject

/**
 * The main Grails gradle plugin implementation
 *
 * @since 3.0
 */
@CompileStatic
class GrailsGradlePlugin implements Plugin<Project> {

    private static final String NATIVE_IMAGE_PLUGIN = 'org.graalvm.buildtools.native'

    /** The plugin that compiles the pages, which are half of what a native image has to be told about. */
    private static final String GSP_PLUGIN = 'org.apache.grails.gradle.grails-gsp'

    private static final String SPRING_BOOT_PLUGIN = 'org.springframework.boot'

    private static final String ASSET_COMPILE_TASK = 'assetCompile'

    /** Where an executable jar reads its classpath from, and so where assets have to be to be found. */
    private static final String CLASSPATH_ASSETS_PATH = 'BOOT-INF/classes/assets'

    private static final int TRAINING_PORT = 18080

    /** Not the training port: a trace and a training run are both a started application. */
    private static final int TRACING_PORT = 18081

    private static final int TRAINING_START_TIMEOUT_SECONDS = 180

    private static final String CLI_PID_FILE_PROPERTY = 'grails.cli.pid.file'
    private static final String RUN_APP_PID_FILE_NAME = 'run-app.pid'

    List<Class<Plugin>> basePluginClasses = [IntegrationTestGradlePlugin] as List<Class<Plugin>>
    List<String> excludedGrailsAppSourceDirs = ['migrations', 'assets']
    List<String> grailsAppResourceDirs = ['views', 'i18n', 'conf', 'migrations']
    private final ToolingModelBuilderRegistry registry

    @Inject
    GrailsGradlePlugin(ToolingModelBuilderRegistry registry) {
        this.registry = registry
    }

    void apply(Project project) {

        project.pluginManager.apply(GroovyPlugin)

        // validate that only an app or a plugin is registered, and never both
        OnlyOneGrailsPlugin marker = (OnlyOneGrailsPlugin) project.getExtensions().findByName(OnlyOneGrailsPlugin.name)
        if (marker) {
            throw new GradleException("Project ${project.name} cannot be both a Grails application and a Grails plugin. Previously applied plugin: ${marker.pluginClassname}. Cannot apply: ${getClass().name}")
        }
        project.getExtensions().add(OnlyOneGrailsPlugin.name, new OnlyOneGrailsPlugin(pluginClassname: getClass().name))

        // reset the environment to ensure it is resolved again for each invocation
        Environment.reset()

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

        configureI18nDescriptor(project)
        configureNativeMetadata(project)
        configureAotCache(project)

        configureTemplateResources(project)

        configureAssetCompilation(project)

        // the CLI tier — the grailsCli configurations, command/console/shell/script tasks, and
        // cli companion discovery — lives in its own plugin
        project.pluginManager.apply(GrailsCliGradlePlugin)

        configureForkSettings(project, grailsVersion)

        configureBootRunPidFile(project)

        configureAheadOfTimeProcessing(project)

        configureNativeImage(project)

        configureJavaCompatibilityArgs(project)

        configureGrailsSourceDirs(project)

        project.gradle.projectsEvaluated {
            createBuildPropertiesTask(project)
        }

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

            // Publish the project base directory to the Groovy compiler's worker daemon JVM so the
            // GlobalGrailsClassInjectorTransformation AST transform can locate
            // src/main/resources/META-INF/grails.factories without relying on hardcoded
            // "build"/"target" output-directory names. Reuses the same CommandLineArgumentProvider
            // pattern as forked test/run tasks (see configureForkSettings).
            c.groovyOptions.forkOptions.jvmArgumentProviders.add(new GrailsAppBaseDirProvider(project.projectDir))

            // Publish the grails { compileStatic { controllers / services / tagLibs } } opt-ins to the
            // compiler worker JVM as system properties so CompileStaticArtefactInjector can stamp
            // @GrailsCompileStatic onto the matching artefacts. Read lazily (asArguments) so the value
            // reflects the user's grails { } block regardless of configuration ordering.
            GrailsExtension grailsExtension = project.extensions.findByType(GrailsExtension)
            if (grailsExtension != null) {
                c.groovyOptions.forkOptions.jvmArgumentProviders.add(new GrailsCompileStaticArtefactsProvider(grailsExtension.compileStatic))
            }
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

        // Configure indy and log status after evaluation so user's grails { } block has been applied
        GrailsExtension grailsExtension = project.extensions.findByType(GrailsExtension)
        project.afterEvaluate {
            boolean indyEnabled = grailsExtension.indy.getOrElse(false)
            Boolean preserveParameterNames = grailsExtension.preserveParameterNames.getOrNull()

            project.tasks.withType(GroovyCompile).configureEach { GroovyCompile c ->
                c.groovyOptions.optimizationOptions.indy = indyEnabled

                if (preserveParameterNames != null) {
                    project.logger.info('Grails: Configuring Groovy compilation to preserve parameter names: {}', preserveParameterNames)
                    c.groovyOptions.parameters = preserveParameterNames
                }
            }

            if (!indyEnabled) {
                project.logger.info('Grails: Groovy invokedynamic (indy) is disabled to improve performance (see issue #15293).')
                project.logger.info('        To enable invokedynamic: grails { indy = true } in build.gradle')
            }
        }
    }

    protected Closure<String> getGroovyCompilerScript(GroovyCompile compile, Project project) {
        GrailsExtension grails = project.extensions.findByType(GrailsExtension)

        // Everything below runs inside the returned closure, invoked from the task's doFirst:
        // the isClassOnClasspath probes resolve the compile classpath, which must not happen while
        // the task is being configured. A GroovyCompile task can be realized from inside an
        // in-flight resolution of compileClasspath (scheduling any task whose inputs include that
        // configuration realizes the compile task through the target-JVM attribute's provider
        // chain), and re-entering that resolution fails on Gradle 9.5+ with
        // 'Cannot observe dependencies before markAsObserved(String) has been called'.
        return { ->
            // Start with user-configured imports
            Set<String> starImports = new LinkedHashSet<>(grails.starImports)

            // Add java.time if enabled
            if (grails.importJavaTime) {
                starImports.add('java.time')
            }

            // Add Grails annotation packages and common validation annotations if enabled
            if (grails.importGrailsCommonAnnotations) {
                // Always add jakarta.validation.constraints
                starImports.add('jakarta.validation.constraints')

                // Check for grails.gorm.annotation.* classes on classpath
                if (isClassOnClasspath(compile.classpath, 'grails.gorm.annotation.CreatedDate')) {
                    starImports.add('grails.gorm.annotation')
                }

                // Check for grails.plugin.scaffolding.annotation.* classes on classpath
                if (isClassOnClasspath(compile.classpath, 'grails.plugin.scaffolding.annotation.Scaffold')) {
                    starImports.add('grails.plugin.scaffolding.annotation')
                }
            }

            // Return null if no imports are needed
            if (starImports.isEmpty()) {
                return null
            }

            // Build the import statements
            def importStatements = starImports.collect { pkg -> "                        star '$pkg'" }.join('\n')
            """withConfig(configuration) {
                    imports {
${importStatements}
                    }
                }
            """ as String
        }
    }

    /**
     * Check if a class exists on the given classpath.
     * This detects classes from any source: direct dependencies, transitive dependencies, or local jars.
     *
     * @param classpath The FileCollection representing the classpath to search
     * @param className The fully qualified class name to look for (e.g., 'grails.gorm.annotation.CreatedDate')
     * @return true if the class is found on the classpath
     */
    private static boolean isClassOnClasspath(FileCollection classpath, String className) {
        def classEntry = className.replace('.', '/') + '.class'
        classpath.files.any { f ->
            try {
                if (f.file && f.name.endsWith('.jar')) {
                    new ZipFile(f).withCloseable { zip ->
                        zip.getEntry(classEntry) != null
                    }
                } else if (f.directory) {
                    new File(f, classEntry).exists()
                } else {
                    false
                }
            } catch (Exception ignored) {
                false
            }
        }
    }

    protected void excludeDependencies(Project project) {
        // Perhaps change to check that if this is a Grails plugin, don't exclude?
        // Adding an exclusion to every dependency in a pom is very verbose and
        // greatly increases the size of the pom.
        // It would be nice to have documented in a comment why this global exclude is in here
        String slf4jPreventExclusion = project.findProperty('slf4jPreventExclusion')
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
                    String defaultProfileCoordinates = "org.apache.grails.profiles:${System.getProperty('grails.profile') ?: getDefaultProfile()}:${project.findProperty('grailsVersion') ?: BuildSettings.grailsVersion}" as String
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
        applyGrailsBom(project)
    }

    /**
     * Applies a single Grails BOM as a Gradle platform and enables property-based
     * version overrides via the standalone
     * {@code org.apache.grails.gradle.bom-property-overrides} plugin.
     *
     * <p>This replaces the Spring Dependency Management plugin with two
     * orthogonal pieces:</p>
     * <ol>
     *   <li><strong>BOM import</strong>: the BOM selected by {@code grails.bom}
     *       (default {@code grails-bom}) is added as a Gradle {@code platform()}
     *       dependency - or an {@code enforcedPlatform()} for the Micronaut variants -
     *       on every declarable configuration, mirroring the global behaviour Spring
     *       DM provided via {@code configurations.all() + resolutionStrategy.eachDependency()}.
     *       Exactly one Grails BOM is ever applied; the BOMs are split by integration
     *       (default / hibernate5 / micronaut), so the plugin never layers two of them.</li>
     *   <li><strong>Property overrides</strong>: the BOM-agnostic
     *       {@link BomPropertyOverridesPlugin} reads the BOM's
     *       {@code <properties>} block and applies any project-level
     *       overrides via Gradle's
     *       {@code ResolutionStrategy.eachDependency()}.</li>
     * </ol>
     *
     * <p>Set {@code grails { bom = null }} (or the deprecated
     * {@code grails { springDependencyManagement = false }}) to suppress the automatic BOM
     * application entirely and declare the {@code platform()}/{@code enforcedPlatform()} by hand.</p>
     *
     * <p>Usage: to override a version managed by the Grails or Spring Boot BOM, set the
     * corresponding property in {@code gradle.properties} or {@code build.gradle}:</p>
     * <pre>
     * // gradle.properties
     * slf4j.version=1.7.36
     *
     * // or build.gradle
     * ext['slf4j.version'] = '1.7.36'
     * </pre>
     *
     * @see BomPropertyOverridesPlugin
     * @since 8.0
     */
    protected void applyGrailsBom(Project project) {
        // Ensure the developmentOnly configuration exists. Spring Boot's plugin
        // normally creates this, but using maybeCreate guarantees it is available
        // even if plugin ordering changes or Spring Boot is not applied. We do
        // this outside afterEvaluate so that other plugins applied during the
        // same configuration phase can rely on the configuration existing.
        project.configurations.maybeCreate('developmentOnly')

        // The BOM selection `grails { bom = ... }` is set in the user's build.gradle,
        // which runs AFTER plugin apply. We therefore wait until afterEvaluate to read
        // it and apply the BOM accordingly. By that point all declarable configurations
        // exist (java-base creates them during apply), so iterating them eagerly via
        // .each is sufficient - any plugin that adds a configuration later is responsible
        // for declaring its own BOM coordination if it needs it.
        project.afterEvaluate {
            def grailsExtension = project.extensions.findByType(GrailsExtension)
            def bomName = grailsExtension == null ? GrailsExtension.DEFAULT_BOM : grailsExtension.bom.getOrNull()
            if (!bomName) {
                project.logger.info(
                    'grails.bom is null; skipping automatic application of the Grails platform BOM and bom-property-overrides plugin for project {}',
                    project.path
                )
                return
            }

            // Exactly one Grails BOM may be applied. The BOMs are split by integration
            // (default / hibernate / micronaut), so a project must select a single variant.
            // If the build declares a Grails BOM by hand - for example a Micronaut application
            // declaring enforcedPlatform(grails-micronaut-bom), or an application generated by
            // Grails Forge / a profile that declares platform(grails-bom) directly - honor that
            // selection instead of the configured default, and fail fast if more than one distinct
            // Grails BOM is declared.
            def declaredBoms = declaredGrailsBoms(project)
            if (declaredBoms.size() > 1) {
                throw new GradleException(
                    "Project '${project.name}' declares more than one Grails BOM (${declaredBoms.join(', ')}). " +
                        'Exactly one Grails BOM may be applied; the BOMs are split by integration ' +
                        '(default / hibernate / micronaut), so a project must select a single variant.'
                )
            }
            def effectiveBom = declaredBoms.isEmpty() ? bomName : declaredBoms.first()

            def grailsVersion = (project.findProperty('grailsVersion') ?: BuildSettings.grailsVersion) as String
            def bomCoordinates = "org.apache.grails:${effectiveBom}:${grailsVersion}" as String

            // The Micronaut BOM variants must be applied as an enforcedPlatform: they layer
            // Micronaut-specific overrides (javaparser-core, etc.) on top of grails-base-bom,
            // and Micronaut's own platform would otherwise win those versions via conflict
            // resolution. All other Grails BOMs are applied as a regular platform.
            boolean enforced = effectiveBom in ENFORCED_PLATFORM_BOMS

            // Apply the single BOM to every declarable project configuration that does not already
            // declare a Grails BOM by hand, matching the behavior of the Spring Dependency
            // Management plugin which applied version constraints globally via configurations.all()
            // + resolutionStrategy.eachDependency(). Configurations that already declare the BOM
            // (e.g. 'implementation' in a generated app) are left untouched so a second BOM is
            // never layered on them, while sibling declarable configurations (such as 'console')
            // still receive BOM coverage. Non-declarable configurations (e.g. apiElements,
            // runtimeElements) inherit constraints through their parent configurations.
            // Tool/annotation-processor configurations are excluded because they hold independent
            // classpaths that already use their own platforms (e.g. Micronaut's annotation
            // processors import io.micronaut.platform:micronaut-platform). Adding a Grails BOM as a
            // second non-enforced platform on those configurations causes version conflict
            // resolution to upgrade transitives and break the tools/processors - unlike
            // resolutionStrategy hooks, platform() constraints participate in version conflict
            // resolution.
            project.configurations.each {
                if (it.canBeDeclared && !isExcludedFromBomPlatform(it.name) && !configurationHasGrailsBom(it)) {
                    def platformDependency = enforced ?
                            project.dependencies.enforcedPlatform(bomCoordinates) :
                            project.dependencies.platform(bomCoordinates)
                    project.dependencies.add(it.name, platformDependency)
                }
            }
        }

        // Delegate property-based version overrides to the bundled plugin. Auto-detect picks up
        // the platform()/enforcedPlatform() declaration (whether injected above or declared by
        // hand), plus any additional platform()/enforcedPlatform() the user declares. Users can
        // extend the override surface by declaring their own platforms - no extra configuration
        // is required here.
        //
        // Applied unconditionally, as its own top-level statement here - NOT nested inside the
        // afterEvaluate{} above, and NOT gated on grails.bom being set. Two things depend on that:
        //
        // 1. Ordering: BomPropertyOverridesPlugin.apply() registers its own project.afterEvaluate{}
        //    for the actual override-application logic. Gradle appends a newly-registered
        //    afterEvaluate listener to the END of the notification queue it's currently
        //    processing - so calling project.plugins.apply(BomPropertyOverridesPlugin) from INSIDE
        //    the afterEvaluate{} above (as this method used to) pushes that registration behind
        //    every afterEvaluate callback other plugins registered synchronously during apply() -
        //    including GrailsCliGradlePlugin's CLI companion probe (configureApplicationCommands),
        //    which eagerly resolves the api/implementation/runtimeOnly buckets via grailsCliDetect
        //    and locks them against further mutation. In a multi-project build, resolving one
        //    project's probe can force a dependency project to finish configuring mid-resolution,
        //    landing squarely in that window - causing BomManagedVersions.applyTo() to throw
        //    "Cannot mutate the dependencies of configuration ... after ... was resolved". Applying
        //    the plugin here, synchronously and before GrailsCliGradlePlugin is applied further
        //    down in this method, keeps both registrations in the same, race-free, apply()-time
        //    queue position. project.plugins.apply() is also idempotent, so this stays safe even
        //    if the build separately applies org.apache.grails.gradle.bom-property-overrides itself.
        //
        // 2. Unconditional application: whether grails.bom ends up null is only known once the
        //    afterEvaluate{} above runs, which - per point 1 - is too late to gate this call
        //    without reintroducing the same race. This is safe: when there's no BOM (grails.bom
        //    null and no platform() declared by hand), BomPropertyOverridesPlugin's own
        //    auto-detection finds no declared platform and applyOverrides() is a no-op - so
        //    opting out of the BOM still results in zero constraints being added, only
        //    project.plugins.findPlugin(...) now reports it as applied.
        project.plugins.apply(BomPropertyOverridesPlugin)
    }

    /**
     * Grails BOM artifact names that must be applied as an {@code enforcedPlatform} rather than
     * a regular {@code platform}, because they layer Micronaut-specific overrides on top of
     * grails-base-bom that the Micronaut platform would otherwise override via conflict resolution.
     */
    private static final Set<String> ENFORCED_PLATFORM_BOMS = [
            'grails-micronaut-bom',
            'grails-hibernate5-micronaut-bom',
            'grails-hibernate7-micronaut-bom',
    ] as Set<String>

    /**
     * Known Grails BOM artifact names (group {@code org.apache.grails}). Used to detect whether a
     * project already declares a Grails BOM by hand so the plugin does not inject a second one,
     * preserving the guarantee that exactly one Grails BOM is applied.
     */
    private static final Set<String> GRAILS_BOM_NAMES = [
            'grails-bom',
            'grails-base-bom',
            'grails-hibernate5-bom',
            'grails-hibernate7-bom',
            'grails-neo4j-bom',
            'grails-micronaut-bom',
            'grails-hibernate5-micronaut-bom',
            'grails-hibernate7-micronaut-bom',
    ] as Set<String>

    /**
     * Returns the distinct known Grails BOM artifact names declared by hand as a {@code platform()}
     * or {@code enforcedPlatform()} on the project's declarable configurations.
     */
    private static Set<String> declaredGrailsBoms(Project project) {
        Set<String> names = new LinkedHashSet<>()
        for (Configuration configuration : project.configurations) {
            if (!configuration.canBeDeclared) {
                continue
            }
            for (Dependency dependency : configuration.dependencies) {
                if (isGrailsBomPlatform(dependency)) {
                    names.add(dependency.name)
                }
            }
        }
        names
    }

    /**
     * Returns whether the given configuration already declares a known Grails BOM as a
     * {@code platform()} / {@code enforcedPlatform()} by hand, so the plugin can avoid layering a
     * second BOM on top of it.
     */
    private static boolean configurationHasGrailsBom(Configuration configuration) {
        for (Dependency dependency : configuration.dependencies) {
            if (isGrailsBomPlatform(dependency)) {
                return true
            }
        }
        false
    }

    /**
     * Returns whether the dependency is a known Grails BOM declared with platform semantics, i.e. a
     * {@code platform()} or {@code enforcedPlatform()} declaration (carrying the {@code Category}
     * attribute). A plain {@code org.apache.grails:*-bom} dependency does not import constraints and
     * is therefore not treated as a declared BOM.
     */
    private static boolean isGrailsBomPlatform(Dependency dependency) {
        if (dependency.group != 'org.apache.grails' || !GRAILS_BOM_NAMES.contains(dependency.name)) {
            return false
        }
        if (!(dependency instanceof ModuleDependency)) {
            return false
        }
        Category category = ((ModuleDependency) dependency).attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)
        category != null && (category.name == Category.REGULAR_PLATFORM || category.name == Category.ENFORCED_PLATFORM)
    }

    private static boolean isExcludedFromBomPlatform(String name) {
        name == 'checkstyle' || name == 'codenarc' || name == 'pmd' ||
                name == 'spotbugs' || name == 'spotbugsPlugins' ||
                name == 'annotationProcessor' || name.endsWith('AnnotationProcessor')
    }

    protected void applySpringBootPlugin(Project project) {
        def springBoot = project.extensions.findByType(SpringBootExtension)
        if (!springBoot) {
            project.plugins.apply(SpringBootPlugin)
        }
    }

    protected String getDefaultProfile() {
        'web'
    }

    @CompileDynamic
    protected void createBuildPropertiesTask(Project project) {
        if (project.tasks.findByName('buildProperties') == null) {
            File resourcesDir = SourceSets.findMainSourceSet(project).output.resourcesDir
            File buildInfoFile = new File(resourcesDir, 'META-INF/grails.build.info')

            Map<String, Object> buildPropertiesContents = [
                    'grails.env': Environment.isSystemSet() ? Environment.getCurrent().getName() : Environment.PRODUCTION.getName(),
                    'info.app.name': project.name,
                    'info.app.version': project.version instanceof Serializable ? project.version : project.version.toString(),
                    'info.app.grailsVersion': project.findProperty('grailsVersion')
            ]

            // Capture build directory at configuration time to avoid Task.project access at execution time
            def buildDir = project.layout.buildDirectory.asFile.get()

            TaskProvider<Task> buildPropertiesTask = project.tasks.register('buildProperties') { Task task ->
                task.inputs.properties(buildPropertiesContents)
                task.outputs.file(buildInfoFile)

                task.doLast {
                    buildDir.mkdirs()
                    buildInfoFile.parentFile.mkdirs()
                    Properties props = new Properties()
                    task.inputs.properties.each { key, value ->
                        props.setProperty(key as String, value as String)
                    }
                    buildInfoFile.withOutputStream { out ->
                        props.store(out, null)
                    }
                    PropertyFileUtils.makePropertiesFileReproducible(buildInfoFile)
                }
            }

            TaskContainer tasks = project.tasks
            tasks.findByName('processResources')?.dependsOn(buildPropertiesTask)
        }
    }

    @CompileStatic
    protected void configureMicronaut(Project project) {
        project.afterEvaluate {
            boolean micronautEnabled = project.getConfigurations().getByName('runtimeClasspath').getAllDependencies().any { Dependency dep -> dep.group == 'org.apache.grails' && dep.name == 'grails-micronaut' }
            if (!micronautEnabled) {
                return
            }

            GrailsExtension ge = project.extensions.getByType(GrailsExtension)
            if (!ge.micronautAutoSetup) {
                return
            }

            project.logger.lifecycle('Micronaut Support Detected for {}', project.name)

            // Validate that a Micronaut-compatible Grails BOM is applied as enforcedPlatform. The
            // selected BOM is the single source of truth for the Micronaut platform version:
            // applying it as enforcedPlatform pins io.micronaut.platform:micronaut-platform with a
            // strict constraint that no transitive can override.
            validateMicronautBom(project)

        }
    }

    /**
     * Validates that a Micronaut-compatible BOM is applied as an enforcedPlatform when micronaut is used.
     * The grails-micronaut-bom (and its hibernate-specific variants) layers Micronaut-specific overrides
     * (e.g. javaparser-core) on top of grails-base-bom; without enforcedPlatform, Micronaut's platform would
     * override these versions via Gradle's conflict resolution. Regular Grails projects (without Micronaut)
     * should continue to use the spring-managed versions via plain platform(:grails-bom).
     */
    @CompileStatic
    protected static void validateMicronautBom(Project project) {
        Configuration implConfig = project.configurations.findByName('implementation')
        if (implConfig == null) {
            return
        }

        // Exactly one Grails BOM is ever applied (the BOMs are split by integration:
        // default / hibernate / micronaut). A Micronaut project selects the Micronaut
        // variant either by setting grails { bom = 'grails-micronaut-bom' } (auto-applied
        // as an enforcedPlatform by applyGrailsBom) or by opting out via grails { bom = null }
        // and declaring enforcedPlatform(grails-micronaut-bom) by hand. Either way the
        // Micronaut BOM must be an enforcedPlatform so the Micronaut platform cannot override
        // its versions via conflict resolution. We scan the Micronaut BOM declarations on the
        // 'implementation' configuration and accept it as valid when at least one is an
        // enforcedPlatform.
        Set<String> validMicronautBoms = [
                'grails-micronaut-bom',
                'grails-hibernate5-micronaut-bom',
                'grails-hibernate7-micronaut-bom',
        ] as Set<String>

        for (Dependency dep : implConfig.dependencies) {
            if (dep.name in validMicronautBoms && dep instanceof ModuleDependency) {
                Object categoryAttr = ((ModuleDependency) dep).attributes.getAttribute(
                        org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
                )
                if (categoryAttr != null && categoryAttr.toString() == org.gradle.api.attributes.Category.ENFORCED_PLATFORM) {
                    return // correctly configured
                }
            }
        }

        throw new GradleException(
                "Project '${project.name}' uses Micronaut but does not apply a Micronaut BOM as an enforcedPlatform. " +
                        "Micronaut's platform declares higher versions of javaparser-core and other libraries that would " +
                        'override the grails-bom versions via conflict resolution. Change to one of:\n\n' +
                        '    implementation enforcedPlatform("org.apache.grails:grails-micronaut-bom:$grailsVersion")\n' +
                        '    implementation enforcedPlatform("org.apache.grails:grails-hibernate5-micronaut-bom:$grailsVersion")\n' +
                        '    implementation enforcedPlatform("org.apache.grails:grails-hibernate7-micronaut-bom:$grailsVersion")\n'
        )
    }

    @CompileStatic
    protected void configureGroovy(Project project) {
        final String groovyVersion = project.findProperty('groovy.version')
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
            project.extensions.create('grails', GrailsExtension, project)
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
        configureAssetsOnTheClasspath(project)
    }

    /**
     * Packages the compiled assets where an executable jar can read them.
     *
     * <p>The asset pipeline plugin puts them at the root of whatever archive is built, which is
     * where a war serves its web content from and is therefore right for a war. An executable jar
     * has no web content: it serves assets by reading them off the classpath, and its classpath is
     * {@code BOOT-INF/classes} -- so the same assets, at the same place, in a jar rather than a war,
     * are packaged but unreachable, and every asset a page asks for is a 404 while the page itself
     * renders. Adding them under the classpath directory is what makes them found.</p>
     *
     * <p>Only for {@code bootJar}. A war already serves them from the root, and putting them on its
     * classpath as well would ship the same bytes twice.</p>
     */
    private void configureAssetsOnTheClasspath(Project project) {
        project.pluginManager.withPlugin(SPRING_BOOT_PLUGIN) {
            // Asked for by name when the archive needs it, rather than matched out of the task
            // container in advance.
            //
            // A matching {} collection is live. Handed to project.files() it became part of
            // bootJar's input files, so the container was reachable from the archive's state -- and
            // resolving those inputs ran the predicate against every task registered, realizing all
            // of them to find the one. Asking the names costs nothing and realizes nothing; only
            // the task that is actually there is then looked up, and the file collection it returns
            // carries the dependency on it.
            //
            // Still by name rather than by the plugin that registers it: the pipeline's plugin id
            // has changed once already and the task name has not, and an application is free to
            // register the task itself.
            FileCollection compiledAssets = project.files(project.provider {
                project.tasks.names.contains(ASSET_COMPILE_TASK)
                        ? project.tasks.named(ASSET_COMPILE_TASK).get().outputs.files
                        : project.files()
            })
            project.tasks.named('bootJar', AbstractCopyTask).configure { AbstractCopyTask task ->
                task.from(compiledAssets) { CopySpec spec ->
                    spec.into(CLASSPATH_ASSETS_PATH)
                }
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
     * Generates the application's bean definitions for the environment it will be run in.
     *
     * <p>Generation reads the definitions to write them out as code, and an application declares
     * different ones in different environments -- development declares reloadable beans, which
     * cannot be expressed as generated code. Left at the default the definitions written out are
     * development's, and an application built from them is not the application that was asked
     * for.</p>
     *
     * <p>Nothing here runs unless the application applies Spring Boot's AOT plugin, which is what
     * asks for generated definitions in the first place.</p>
     */
    protected void configureAheadOfTimeProcessing(Project project) {
        project.pluginManager.withPlugin('org.springframework.boot.aot') {
            project.tasks.named('processAot', JavaExec) { JavaExec task ->
                task.systemProperty(Environment.KEY, Environment.PRODUCTION.name)
            }
        }
    }

    /**
     * What a Grails application needs of a native image that an image cannot work out for itself.
     *
     * <p>Applications resolve calls through invokedynamic here, because the classic call site
     * defines a class as it runs and an image has no way to define one. The framework has to be
     * built the same way. This is a convention, so an application that has said otherwise keeps
     * what it said.</p>
     *
     * <p>Nothing here runs unless the application applies GraalVM's plugin, which is what asks for
     * an image in the first place. An application that never builds one is untouched.</p>
     */
    protected void configureNativeImage(Project project) {
        project.pluginManager.withPlugin('org.graalvm.buildtools.native') {
            GrailsExtension grailsExt = project.extensions.getByType(GrailsExtension)
            grailsExt.indy.convention(true)
        }
        configureNativeMetadataTrace(project)
    }

    /**
     * Records the reflection a running application does, which is the half of an image's metadata
     * that reading the build output cannot supply.
     *
     * <p>{@code generateNativeMetadata} writes down the application's own artefacts without running
     * anything. What it cannot see is the framework reflecting along a request path -- a controller
     * method reached through Groovy's dispatch, a conversion asked for while binding a form -- and an
     * image built without those starts, serves its home page, and fails on the first request that
     * needs one.</p>
     *
     * <p>Run deliberately rather than as part of a build: it starts the application, and what it
     * writes belongs in the sources beside the code, where the next person can see which paths an
     * image was built to cover.</p>
     */
    protected void configureNativeMetadataTrace(Project project) {
        NativeMetadataExtension extension = ((ExtensionAware) project.extensions.getByName('grails'))
                .extensions.create('nativeMetadata', NativeMetadataExtension)
        extension.paths.convention(['/'])
        extension.forms.convention([])
        extension.jvmArguments.convention(['-Dgrails.env=production'])
        extension.port.convention(TRACING_PORT)
        extension.startTimeoutSeconds.convention(TRAINING_START_TIMEOUT_SECONDS)
        extension.outputDirectory.convention(project.layout.projectDirectory
                .dir('src/native/resources/META-INF/native-image'))

        project.pluginManager.withPlugin(SPRING_BOOT_PLUGIN) {
            TaskProvider<?> bootJar = project.tasks.named('bootJar')
            Provider<JavaLauncher> launcher = trainingLauncher(project)

            project.tasks.register('traceNativeMetadata', TraceNativeMetadataTask) { TraceNativeMetadataTask task ->
                task.group = BasePlugin.BUILD_GROUP
                task.description = 'Runs the application under the tracing agent and records the reflection it does'
                task.dependsOn(bootJar)
                task.archiveFile.set(archiveFileOf(bootJar))
                // The project's toolchain unless told otherwise, which for a project that builds an
                // image is the GraalVM the image is built with -- and the agent is only there.
                task.javaExecutable.set(extension.javaExecutable
                        .orElse(launcher.map { JavaLauncher java -> java.executablePath.asFile.absolutePath }))
                task.jvmArguments.set(extension.jvmArguments)
                task.paths.set(extension.paths)
                task.forms.set(extension.forms)
                task.port.set(extension.port)
                task.startTimeoutSeconds.set(extension.startTimeoutSeconds)
                task.outputDirectory.set(extension.outputDirectory)
            }
        }
    }

    protected void configureBootRunPidFile(Project project) {
        // Producer side of the run-app PID contract: the forked app writes its PID to 'run-app.pid'
        // under the Gradle build directory. The CLI stop-app command resolves the PID file
        // independently of Gradle, via BuildSettings.TARGET_DIR (the conventional <projectDir>/build).
        // These two locations coincide only for the DEFAULT Gradle build directory; because this uses
        // project.layout.buildDirectory, customizing it (layout.buildDirectory) would write the PID
        // file where stop-app does not look, so that customization is not supported for stop-app.
        Provider<RegularFile> pidFile = project.layout.buildDirectory.file(RUN_APP_PID_FILE_NAME)
        project.pluginManager.withPlugin('org.springframework.boot') {
            project.tasks.withType(BootRun).configureEach { BootRun task ->
                // The path is resolved lazily at execution time via a CommandLineArgumentProvider
                // (see GrailsAppBaseDirProvider) so it stays configuration-cache safe and does not
                // force the build directory provider during configuration. The forked application
                // reads this location so stop-app can locate and terminate it.
                task.jvmArgumentProviders.add(new RunAppPidFileProvider(CLI_PID_FILE_PROPERTY, pidFile))

                // Report a deliberate stop as a successful build (see BootRunExitCodeVerifier).
                task.ignoreExitValue = true
                task.doLast(new BootRunExitCodeVerifier())
            }
        }
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
                // Tell Spring Boot's AnsiOutput a console is available under bootRun (System.console()
                // is null there, so DETECT mode would otherwise emit no colors). This is set on every
                // OS on purpose: it is not a force-on. AnsiOutput's DETECT mode still gates Windows out
                // internally (return !OS_NAME.contains("win")), so legacy Windows consoles never receive
                // raw ANSI escapes, while macOS/Linux and modern terminals get colored bootRun output.
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
     * Packages {@code src/main/templates} into the main resources as {@code META-INF/templates}.
     *
     * <p>Grails plugins override this: they stage templates through the {@code copyTemplates} task
     * into a directory of their own so that a plugin's templates are not subject to the resource
     * filters {@code processResources} applies to {@code grails-app} resource directories.</p>
     */
    protected void configureTemplateResources(Project project) {
        SourceSet sourceSet = SourceSets.findMainSourceSet(project)
        project.tasks.named(sourceSet.processResourcesTaskName, ProcessResources).configure { ProcessResources task ->
            task.from(project.layout.projectDirectory.dir('src/main/templates')) { CopySpec spec ->
                spec.into('META-INF/templates')
            }
        }
    }

    /**
     * Records the message bundles this artifact ships into {@code META-INF/grails/i18n.properties}.
     *
     * <p>Spring Boot's message source is configured with an explicit base-name list, which nothing at
     * runtime can discover without scanning the classpath. Writing the answer here lets the runtime
     * read it back through an exact-name resource lookup instead — no {@code classpath*:*.properties}
     * scan, and therefore no wildcard resource metadata needed for a native image.</p>
     *
     * <p>The task reads the bundle <em>sources</em>, not the processed resources: {@code native2ascii}
     * and {@code EscapeUnicode} rewrite bundle contents but never file names, so there is no need to
     * order this after them and no risk of a {@code processResources} dependency cycle.</p>
     */
    protected void configureI18nDescriptor(Project project) {
        SourceSet sourceSet = SourceSets.findMainSourceSet(project)
        GrailsExtension grailsExt = project.extensions.getByType(GrailsExtension)

        TaskProvider<GenerateI18nDescriptorTask> descriptorTask = project.tasks.register(
                'generateI18nDescriptor', GenerateI18nDescriptorTask) { GenerateI18nDescriptorTask task ->
            task.group = BasePlugin.BUILD_GROUP
            task.description = 'Records the message bundle base names and locales this artifact contributes'
            task.bundleDirectory.set(project.layout.projectDirectory.dir('grails-app/i18n'))
            task.artifactType.set(grailsArtifactType())
            task.artifactName.set(grailsArtifactName(project))
            task.artifactVersion.set(project.provider { project.version?.toString() })
            task.declaredBasenames.set(grailsExt.i18n.basenames)
            task.outputDirectory.set(project.layout.buildDirectory.dir('generated-resources/grails-i18n'))
        }

        project.tasks.named(sourceSet.processResourcesTaskName, ProcessResources).configure { ProcessResources task ->
            task.from(descriptorTask)
        }
    }

    /**
     * Wires up the cache the JDK can write for an application, so the next start reads what a
     * training run worked out rather than working it out again.
     *
     * <p>Three steps, because the cache is only usable against the layout it was trained on: the
     * archive is extracted, the extracted application is run and asked for its pages, and what the
     * run recorded is left beside it. An application asks for this with
     * {@code grails.aotCache.enabled}, and says which of its pages matter.</p>
     */
    protected void configureAotCache(Project project) {
        AotCacheExtension extension = ((ExtensionAware) project.extensions.getByName('grails'))
                .extensions.create('aotCache', AotCacheExtension)
        extension.enabled.convention(false)
        extension.paths.convention([])
        extension.jvmArguments.convention(['-Dspring.aot.enabled=true', '-Dgrails.env=production'])
        extension.port.convention(TRAINING_PORT)
        extension.startTimeoutSeconds.convention(TRAINING_START_TIMEOUT_SECONDS)

        project.pluginManager.withPlugin(SPRING_BOOT_PLUGIN) {
            TaskProvider<?> bootJar = project.tasks.named('bootJar')
            Provider<Directory> application = project.layout.buildDirectory.dir('aot-cache/application')
            Provider<JavaLauncher> launcher = trainingLauncher(project)

            TaskProvider<ExtractApplicationTask> extract = project.tasks.register(
                    'extractAotCacheApplication', ExtractApplicationTask) { ExtractApplicationTask task ->
                task.group = BasePlugin.BUILD_GROUP
                task.description = 'Extracts the application, which is the form the cache is read against'
                task.onlyIf { extension.enabled.get() }
                task.archiveFile.set(archiveFileOf(bootJar))
                // The launcher rather than its path: this task is cacheable, and a path is where a
                // JDK happens to live on the machine that ran the build. Set as the provider it is,
                // so the toolchain is not resolved -- and a JDK not provisioned -- by every build
                // that merely reads this project.
                task.javaLauncher.set(launcher)
                task.destination.set(application)
            }

            project.tasks.register('trainAotCache', TrainAotCacheTask) { TrainAotCacheTask task ->
                task.group = BasePlugin.BUILD_GROUP
                task.description = 'Runs the application once so the JDK can write down what starting it needs'
                task.onlyIf { extension.enabled.get() }
                task.dependsOn(extract)
                task.applicationDirectory.set(application)
                task.archiveFileName.set(archiveFileOf(bootJar).map { RegularFile file -> file.asFile.name })
                // Beside the extracted application rather than inside it. Inside, the cache and its
                // metadata land in the directory this task declares as its input, so writing them
                // changes that input and the task can never be up to date -- every build would run
                // the application again to record what the last one already recorded.
                Provider<Directory> beside = project.layout.buildDirectory.dir('aot-cache')
                // Read here rather than inside the closure below. A provider is queried when the
                // property is finalized or the task runs, so a closure that reaches through
                // project carries the project into the task's state -- which the configuration
                // cache refuses. The name is a string by then, which it does not mind.
                String cacheName = project.name
                task.cacheFile.set(beside.map { Directory dir -> dir.file("${cacheName}.aot") })
                task.metadataFile.set(beside.map { Directory dir -> dir.file('aot-cache.properties') })
                task.javaExecutable.set(launcher.map { JavaLauncher java -> java.executablePath.asFile.absolutePath })
                // Recorded from the JDK that will run the training, not the one running the build.
                // A cache is read only by the JDK build that wrote it, and this is what a deployment
                // checks that against -- so naming the wrong one is worse than naming none.
                task.javaVersion.set(launcher.map { JavaLauncher java -> java.metadata.jvmVersion })
                task.javaVendor.set(launcher.map { JavaLauncher java -> java.metadata.vendor })
                task.jvmArguments.set(extension.jvmArguments)
                task.paths.set(extension.paths)
                task.port.set(extension.port)
                task.startTimeoutSeconds.set(extension.startTimeoutSeconds)
            }
        }
    }

    /**
     * The archive a task should consume, as something to be resolved when it runs.
     *
     * <p>A provider rather than a file: asking the task for its archive resolves the task, and a
     * task resolved while the build is configured is one every build pays for whether or not it
     * asked for an archive.</p>
     */
    private static Provider<RegularFile> archiveFileOf(TaskProvider<?> bootJar) {
        bootJar.flatMap { Task task -> (Provider<RegularFile>) task.property('archiveFile') }
    }

    /**
     * The JDK the cache will be trained on, which is the project's toolchain where it declares one.
     *
     * <p>A cache is read only by the JDK build that wrote it, so training has to happen on the JDK
     * the application is compiled for rather than whichever one happens to be running Gradle. A
     * project on the Java 21 baseline with a Java 25 toolchain compiles with 25 and would otherwise
     * have trained with 21, where the cache options do not exist -- and the failure it reported was
     * that the training run ended before it started serving.</p>
     *
     * <p>Where no toolchain is declared this resolves to the JDK running the build, which is also
     * the one that compiled the application.</p>
     */
    private static Provider<JavaLauncher> trainingLauncher(Project project) {
        JavaToolchainService toolchains = project.extensions.getByType(JavaToolchainService)
        JavaPluginExtension java = project.extensions.getByType(JavaPluginExtension)
        toolchains.launcherFor(java.toolchain)
    }

    /**
     * Records the application's own classes and pages so a native image keeps them usable. The build
     * output already names both, so an application does not have to be traced to be buildable.
     */
    protected void configureNativeMetadata(Project project) {
        // Only where an image is actually being built. The metadata is read by nothing else, and
        // generating it means reading the compiled classes and the pages compiled into every
        // dependency -- so wiring it into processResources unconditionally made a build that only
        // wanted to write a resource compile its sources and resolve its whole runtime classpath
        // first, which a project that had declared no repositories could not do.
        project.pluginManager.withPlugin(NATIVE_IMAGE_PLUGIN) {
            configureNativeMetadataTask(project)
        }
    }

    private void configureNativeMetadataTask(Project project) {
        SourceSet sourceSet = SourceSets.findMainSourceSet(project)

        TaskProvider<GenerateNativeMetadataTask> metadataTask = project.tasks.register(
                'generateNativeMetadata', GenerateNativeMetadataTask) { GenerateNativeMetadataTask task ->
            task.group = BasePlugin.BUILD_GROUP
            task.description = 'Records the application classes and pages a native image must keep'
            // The classes directory is written by more than one task, so the dependency has to be
            // stated. Against the compilations rather than the classes task, because what reads
            // this task's output has to run after the pages are compiled, and compiling the pages
            // runs the classes task -- see where the output is consumed, below.
            task.dependsOn(project.tasks.named(sourceSet.compileJavaTaskName))
            // Asked of the names rather than of the tasks: findByName creates the task in order to
            // answer whether it exists, so a build pays for one it may not have depended on.
            ['compileGroovy', 'copyAstClasses'].each { String name ->
                if (project.tasks.names.contains(name)) {
                    task.dependsOn(project.tasks.named(name))
                }
            }
            task.classesDirs.from(sourceSet.output.classesDirs)
            task.pageClassesDirs.from(project.layout.buildDirectory.dir('gsp-classes/main'))
            // Leniently: the pages compiled into dependencies are worth finding, but not at the
            // price of making every build that writes a resource resolve the whole runtime
            // classpath first. A dependency that cannot be resolved contributes no pages rather
            // than failing a build that never asked for a native image.
            task.pageClasspath.from(project.configurations.named('runtimeClasspath').map { conf ->
                conf.incoming.artifactView { view -> view.lenient(true) }.files
            })
            task.outputDirectory.set(project.layout.buildDirectory.dir('generated-resources/grails-native'))
        }

        // The pages are half of what this records, and they are compiled by compileGroovyPages.
        project.pluginManager.withPlugin(GSP_PLUGIN) {
            metadataTask.configure { GenerateNativeMetadataTask task ->
                task.dependsOn(project.tasks.named('compileGroovyPages'))
            }
        }

        // Consumed after the classes exist rather than through processResources.
        //
        // processResources looks like the natural home for a generated resource, and it was the
        // home until it turned out to invert the order this task needs: compileGroovyPages depends
        // on classes, classes runs processResources, and processResources consuming this task put
        // it ahead of all three. The pages were therefore compiled after the task that reads them
        // had already run -- so the page half of the metadata was empty on a clean build and stale
        // on any other, and an image built from it was missing every page. Nothing failed; the
        // pages were simply not found at runtime.
        //
        // No ordering satisfies both directions, so the output goes to what runs after the pages
        // are compiled: the archive, and the image being built from it. Adding it to the source
        // set output instead would put the cycle back, because the classes task builds those.
        project.tasks.withType(Jar).configureEach { Jar jar ->
            if (jar.name == 'bootJar') {
                jar.from(metadataTask) { CopySpec spec -> spec.into('BOOT-INF/classes') }
            }
        }
        addToNativeImageClasspath(project, metadataTask)
    }

    /**
     * Puts the recorded metadata on the classpath the image is built from.
     *
     * <p>Dynamically, because the plugin that defines the extension is not on this plugin's compile
     * classpath -- and it is only asked for inside {@code withPlugin}, so an application that never
     * builds an image never reaches it.</p>
     */
    @CompileDynamic
    private static void addToNativeImageClasspath(Project project,
                                                  TaskProvider<GenerateNativeMetadataTask> metadataTask) {
        def graalvm = project.extensions.findByName('graalvmNative')
        graalvm?.binaries?.configureEach { binary ->
            binary.classpath(metadataTask)
        }
    }

    /**
     * The {@code artifact.type} recorded in the i18n descriptor. Applications and plugins are
     * distinguished so that message precedence can put the application's own bundles ahead of every
     * plugin's, which classloader enumeration order can never express.
     */
    protected String grailsArtifactType() {
        GenerateI18nDescriptorTask.TYPE_APPLICATION
    }

    /**
     * The {@code artifact.name} recorded in the i18n descriptor. For an application this is only
     * diagnostic; {@link GrailsPluginGradlePlugin} overrides it with the Grails plugin name, which
     * both namespaces the plugin's base names and links the descriptor to the plugin the runtime
     * discovers.
     */
    protected Provider<String> grailsArtifactName(Project project) {
        project.provider { project.name }
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

            // Filter parameters are not part of Gradle's up-to-date checks (gradle/gradle#1191),
            // so the token values must be declared as inputs — otherwise bumping grailsVersion or
            // the app version leaves processResources UP-TO-DATE and stale substituted values
            // (e.g. info.app.grailsVersion in application.yml) are repackaged into every build.
            task.inputs.properties(replaceTokens)

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
            it.inputs.dir(src)
            it.outputs.dir(dest)

            def antBuilder = it.ant

            it.doLast {
                antBuilder.native2ascii(src: src, dest: dest,
                        includes: '**/*.properties', encoding: 'UTF-8')
            }
        }

        native2asciiTask
    }

    @CompileStatic
    private static final class OnlyOneGrailsPlugin {

        String pluginClassname
    }
}
