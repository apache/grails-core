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
package org.grails.gradle.plugin.views.gsp

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import groovy.transform.CompileDynamic
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ValueSourceSpec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.War
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService

import org.grails.gradle.plugin.core.GrailsCompileStaticOptions
import org.grails.gradle.plugin.core.GrailsGradlePlugin
import org.grails.gradle.plugin.core.GrailsExtension
import org.grails.gradle.plugin.scaffolding.GenerateScaffoldedViewsTask
import org.grails.gradle.plugin.util.SourceSets

/**
 * A plugin that adds support for compiling Groovy Server Pages (GSP)
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GroovyPagePlugin implements Plugin<Project> {

    /**
     * The test source sets a Grails project may define, each of which renders pages.
     */
    private static final List<String> TEST_SOURCE_SET_NAMES = ['test', 'integrationTest']

    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('groovy') {
            configureProject(project)
        }
    }

    /**
     * Points the page compilers at {@code grails { compileStatic { gsp; strictGsp } }} once the
     * extension carrying it exists.
     *
     * <p>Wired when the plugin that registers the extension is applied rather than read through the
     * project later: this plugin is applied on its own as well as alongside that one, with no ordering
     * between them, and a provider that reaches the project to answer would put the project itself
     * behind a task input. What the tasks hold is the option object, which is what they are asking
     * about. Where the extension never appears, the values set below stand.</p>
     */
    private static void wireCompileStaticOptions(Project project, List<TaskProvider<GroovyPageForkCompileTask>> compilers) {
        project.plugins.withType(GrailsGradlePlugin) {
            GrailsCompileStaticOptions options = project.extensions.findByType(GrailsExtension)?.compileStatic
            if (options == null) {
                return
            }
            compilers.each { compiler ->
                compiler.configure {
                    it.compileStatic.set(options.gsp.orElse(false))
                    it.compileStaticStrict.set(options.strictGsp.orElse(false))
                }
            }
        }
    }

    /**
     * Whether compilation keeps parameter names, which decides whether a tag's attributes and body
     * parameters have to carry those names to be dispatchable. The index has to be generated under the
     * same setting the sources are compiled with, or it would describe a different set of tags.
     */
    @CompileDynamic
    private static Provider<Boolean> resolvePreserveParameterNames(Project project) {
        project.provider {
            Object grails = project.extensions.findByName('grails')
            Object preserve = grails?.hasProperty('preserveParameterNames') ? grails.preserveParameterNames : null
            if (preserve instanceof Provider) {
                return ((Provider) preserve).getOrElse(true) as Boolean
            }
            preserve == null ? Boolean.TRUE : (preserve as Boolean)
        }
    }

    /**
     * Whether the build declared that every tag library it uses is described at compile time, so that
     * a tag missing from the index is a mistake rather than something contributed later.
     */
    @CompileDynamic
    private static Provider<Boolean> resolveStrictTags(Project project) {
        project.provider {
            Object compileStatic = project.extensions.findByName('grails')?.compileStatic
            Object strict = compileStatic?.hasProperty('strictTags') ? compileStatic.strictTags : null
            strict instanceof Provider ? ((Provider) strict).getOrElse(false) as Boolean : Boolean.FALSE
        }
    }

    /**
     * Whether a tag call written without its namespace may be compiled into a direct invocation.
     */
    @CompileDynamic
    private static Provider<Boolean> resolveUnqualifiedTagCalls(Project project) {
        project.provider {
            Object compileStatic = project.extensions.findByName('grails')?.compileStatic
            Object unqualified = compileStatic?.hasProperty('unqualifiedTagCalls') ?
                    compileStatic.unqualifiedTagCalls : null
            unqualified instanceof Provider ?
                    ((Provider) unqualified).getOrElse(false) as Boolean : Boolean.FALSE
        }
    }

    /**
     * The namespaces the build declared as filled in while the application runs.
     */
    @CompileDynamic
    private static Provider<Set<String>> resolveDynamicTagNamespaces(Project project) {
        project.provider {
            Object compileStatic = project.extensions.findByName('grails')?.compileStatic
            Object namespaces = compileStatic?.hasProperty('dynamicTagNamespaces') ?
                    compileStatic.dynamicTagNamespaces : null
            namespaces instanceof Provider ?
                    (((Provider) namespaces).getOrElse([] as Set) as Set<String>) : ([] as Set<String>)
        }
    }

    /**
     * The encoding the project's Groovy sources are compiled with, which the generator has to read
     * them with. Falls back to the generator's own default when the project has not set one.
     */
    @CompileDynamic
    private static Provider<String> resolveCompileEncoding(Project project) {
        project.provider {
            Object compile = project.tasks.findByName('compileGroovy')
            (compile instanceof GroovyCompile) ? ((GroovyCompile) compile).options.encoding : null
        }
    }

    /**
     * The Groovy source roots of a source set, which is where a type this project declares is found.
     */
    @CompileDynamic
    private static Set<File> resolveGroovySourceRoots(SourceSet sourceSet) {
        Object groovy = sourceSet?.extensions?.findByName('groovy')
        groovy ? (groovy.srcDirs as Set<File>) : ([] as Set<File>)
    }

    /**
     * Puts the packaged index onto the runtime classpath of every test source set, so that a page
     * rendered by a test resolves its tags against the same index as the same page in production.
     */
    @CompileDynamic
    private static void addPackagedIndexToTestRuntime(Project project, FileCollection packagedTagLibIndex) {
        SourceSetContainer sourceSets = project.extensions.findByType(SourceSetContainer)
        if (sourceSets == null) {
            return
        }
        // Matched as they are created rather than looked up now. This runs on the groovy plugin being
        // applied, and integrationTest is registered by the Grails integration test support later, so
        // asking for it here would find nothing and skip it without saying so - which is the gap this
        // method exists to close.
        sourceSets.matching { SourceSet it -> it.name in TEST_SOURCE_SET_NAMES }
                .configureEach { SourceSet it ->
                    it.runtimeClasspath = it.runtimeClasspath.plus(packagedTagLibIndex)
                }
    }

    private void configureProject(Project project) {
        TaskContainer tasks = project.tasks

        SourceSet mainSourceSet = SourceSets.findMainSourceSet(project)
        SourceSetOutput output = mainSourceSet?.output
        FileCollection classesDirs = resolveClassesDirs(output, project)
        Provider<Directory> destDir = project.layout.buildDirectory.dir('gsp-classes/main')
        Provider<Directory> webappDestDir = project.layout.buildDirectory.dir('gsp-classes/webapp')
        output?.dir('gsp-classes')

        // The Java the rest of the project is built with, so that pages are built with it too.
        // Absent a toolchain this resolves to the JVM running Gradle, which is what compiling
        // pages fell back to before and remains the right answer when nothing else was asked for.
        JavaPluginExtension javaExtension = project.extensions.getByType(JavaPluginExtension)
        JavaToolchainService toolchains = project.extensions.getByType(JavaToolchainService)
        Provider<JavaLauncher> launcher = toolchains.launcherFor(javaExtension.toolchain)

        // The index is written twice, because the two things that read it need different guarantees.
        //
        // This one exists before this project is compiled, so that a call to a tag the project itself
        // declares can be resolved as it compiles. It is read from source, so it cannot describe
        // everything: a tag library referring to a type written in another language, or generated by
        // the build, is left out, and what was missed is recorded so that nothing in an incompletely
        // described namespace is reported as a misspelling. It is never packaged - a consumer must not
        // be given a partial description - and pages are not compiled against it either.
        // Everything both indexes must agree on is configured once, by type. Configuring the two
        // tasks separately would let them describe different sets of tag libraries, and the one that
        // is published is not the one this project compiles against - so they would diverge silently.
        // A project keeping tag libraries elsewhere adds them the same way.
        tasks.withType(GenerateTagLibraryIndexTask).configureEach { GenerateTagLibraryIndexTask index ->
            index.sourceDirectories.from(project.layout.projectDirectory.dir('grails-app/taglib'))
            index.parameterNamesRetained.set(resolvePreserveParameterNames(project))
            index.strictTags.set(resolveStrictTags(project))
            index.unqualifiedTagCalls.set(resolveUnqualifiedTagCalls(project))
            index.dynamicTagNamespaces.set(resolveDynamicTagNamespaces(project))
            index.javaLauncher.convention(launcher)
            // The generator reads the same sources the compiler will, so it has to decode them the
            // same way. Left to its own default it would read UTF-8 whatever the project compiles
            // with, and a tag or namespace containing a non-ASCII character would be misread - which
            // degrades to dynamic dispatch rather than to an error, so it would not be noticed.
            index.sourceEncoding.convention(resolveCompileEncoding(project))
        }

        // The settings live apart from the descriptors. The descriptors are published; the settings
        // say how this project is compiled and must reach no one else, and a directory on the runtime
        // classpath is copied wholesale into an executable archive, where excluding a file from an
        // archive task cannot reach it.
        Provider<Directory> settingsDir = project.layout.buildDirectory.dir('generated/grails-taglib-settings')
        Provider<Directory> tagLibIndexDir = project.layout.buildDirectory.dir('generated/grails-taglibs')
        def generateTagLibraryIndex = tasks.register('generateTagLibraryIndex', GenerateTagLibraryIndexTask) {
            it.destinationDirectory.set(tagLibIndexDir)
            it.settingsDirectory.set(settingsDir)
            it.generatorClasspath.from(project.configurations.named('compileClasspath'))
            // A tag library referring to a service, base class or trait of this project needs that
            // source to be read, not guessed, or it would be described wrongly or not at all.
            it.resolutionSourceRoots.from(project.provider { resolveGroovySourceRoots(mainSourceSet) })
        }
        FileCollection tagLibIndex = project.files(tagLibIndexDir).builtBy(generateTagLibraryIndex)

        // And this one is written again once the project has been compiled, with its own classes on
        // the classpath, where every tag library resolves whatever language it was written in. It is
        // the authoritative index: the one pages are compiled against, the one packaged, and the one a
        // project depending on this one reads. Every run replaces the directory, so a renamed or
        // deleted tag library cannot survive in it.
        Provider<Directory> packagedIndexDir =
                project.layout.buildDirectory.dir('generated/grails-taglibs-packaged')
        Provider<Directory> packagedSettingsDir =
                project.layout.buildDirectory.dir('generated/grails-taglib-settings-packaged')
        def packageTagLibraryIndex = tasks.register('packageTagLibraryIndex', GenerateTagLibraryIndexTask) {
            it.description = 'Regenerates the tag library index against the compiled project'
            it.destinationDirectory.set(packagedIndexDir)
            it.settingsDirectory.set(packagedSettingsDir)
            // The compiled classes, and a dependency on the task that gathers them, so this waits
            // for everything that writes into those directories rather than for the compile tasks
            // alone - the ast classes are copied in after compiling, for one.
            //
            // Deliberately the class directories and not the whole source set output. A view compiler
            // registers its own output directory into that output and runs after the classes task, so
            // it cannot declare the classes task as its producer without a cycle, and anything reading
            // the whole output is left consuming a directory nothing says it produced. Compiled views
            // are no use in resolving what a tag library declares anyway.
            it.generatorClasspath.from(project.configurations.named('compileClasspath'), classesDirs)
            it.dependsOn(tasks.named('classes'))
        }
        FileCollection packagedSettings =
                project.files(packagedSettingsDir).builtBy(packageTagLibraryIndex)
        FileCollection packagedTagLibIndex =
                project.files(packagedIndexDir).builtBy(packageTagLibraryIndex)

        // Pages resolve tag calls against the index and are compiled in a process of their own, so the
        // authoritative index has to be on their classpath.
        FileCollection allClasspath = project.getObjects().fileCollection().from(
                [
                        project.configurations.named('compileClasspath'),
                        classesDirs,
                        packagedTagLibIndex,
                        packagedSettings,
                        project.configurations.findByName('providedCompile') ?: null
                ].findAll { it }
        )

        // Carried into the artifact and onto the runtime classpath directly rather than through
        // processResources, which the classes task waits for - and this waits for the classes task.
        if (mainSourceSet != null) {
            mainSourceSet.runtimeClasspath = mainSourceSet.runtimeClasspath.plus(packagedTagLibIndex)
        }

        // A test renders pages too, and a test source set's runtime classpath is built from the main
        // source set's output rather than from its runtime classpath, so it does not inherit the line
        // above. Without this a page rendered from a test resolves its tags against an index missing
        // the application's own tag libraries - which is where a tag resolution problem would most
        // likely be noticed.
        addPackagedIndexToTestRuntime(project, packagedTagLibIndex)

        // Compiling this project's own controllers and tag libraries has to see the index too, or a
        // call to a tag the same project declares cannot be resolved. The directory joins the compile
        // classpath rather than the source set output, which would make the index wait for the
        // compilation it exists to precede.
        FileCollection tagLibSettings = project.files(settingsDir).builtBy(generateTagLibraryIndex)
        tasks.named('compileGroovy', GroovyCompile).configure { GroovyCompile compile ->
            compile.classpath = compile.classpath.plus(tagLibIndex).plus(tagLibSettings)
        }

        // The library artifact alone. A war or an executable archive is built from the runtime
        // classpath, which already carries the descriptors into the place that archive puts classes;
        // adding them here as well would put a second copy at the archive root, where nothing reads it
        // and where it would disagree with the first as soon as one was rebuilt. A plain jar is not
        // built from the runtime classpath, so it is the one that needs them added.
        tasks.named('jar', Jar).configure { Jar archive ->
            archive.from(packagedTagLibIndex)
        }

        // A scaffolded controller has no views of its own, so they are expanded from their
        // templates and compiled with the rest. They are staged together rather than compiled
        // separately, because a second compilation writes a second gsp/views.properties and the
        // archive tasks discard duplicates, losing the views one of them lists.
        //
        // Only a project that scaffolds pays for this. Staging copies the views, and pointing the
        // compilation at the copy would change what every other project compiles for no reason.
        Directory appViews = project.layout.projectDirectory.dir('grails-app/views')
        boolean scaffolds = scaffoldsAnyController(project).get()

        Directory viewsToCompile = appViews
        if (scaffolds) {
            Provider<Directory> stagedViews = project.layout.buildDirectory.dir('generated/views')
            def generateScaffoldedViews = tasks.register(
                    'generateScaffoldedViews', GenerateScaffoldedViewsTask) { GenerateScaffoldedViewsTask it ->
                it.group = BasePlugin.BUILD_GROUP
                it.description = 'Expands the views of scaffolded controllers so they can be precompiled'
                // the classes directory is written by more than one task, so the dependency is stated
                // against the compilation rather than inferred from the directory
                it.dependsOn(tasks.named('compileJava'))
                ['compileGroovy', 'copyAstClasses'].each { String name ->
                    if (project.tasks.findByName(name)) {
                        it.dependsOn(tasks.named(name))
                    }
                }
                it.classesDirs.from(classesDirs)
                it.templateClasspath.from(project.configurations.named('compileClasspath'))
                it.templateOverrides.from(
                        project.fileTree(project.layout.projectDirectory.dir('src/main/templates/scaffolding'))
                                .matching { PatternFilterable p -> p.include('*.gsp') })
                it.applicationViews.from(
                        project.fileTree(appViews)
                                .matching { PatternFilterable p -> p.include('**/*.gsp') })
                it.outputDirectory.set(project.layout.buildDirectory.dir('generated/scaffolded-views'))
            }

            tasks.register('stageGroovyPages', Sync) { Sync it ->
                it.description = 'Collects the application and scaffolded views for GSP compilation'
                it.into(stagedViews)
                it.from(appViews)
                it.from(generateScaffoldedViews)
                // the application's own page wins, matching how the view resolvers are ordered
                it.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
            viewsToCompile = stagedViews.get()
        }

        def compileGroovyPages = tasks.register('compileGroovyPages', GroovyPageForkCompileTask) {
            it.destinationDirectory.set(destDir)
            it.tmpDirPath = getTmpDirPath(project)
            // the setter takes a directory rather than a provider: it has to set both srcDir
            // and the SourceTask inputs, and setting srcDir alone compiles nothing
            it.source = viewsToCompile
            it.serverpath.set('/WEB-INF/grails-app/views/')
            it.classpath = allClasspath
            it.javaLauncher.convention(launcher)
            it.compileStatic.set(false)
            it.compileStaticStrict.set(false)
            if (scaffolds) {
                it.dependsOn(tasks.named('stageGroovyPages'))
            }
        }

        def compileWebappGroovyPages = tasks.register('compileWebappGroovyPages', GroovyPageForkCompileTask) {
            it.destinationDirectory.set(webappDestDir)
            it.source = project.layout.projectDirectory.dir('src/main/webapp')
            it.tmpDirPath = getTmpDirPath(project)
            it.serverpath.set('/')
            it.classpath = allClasspath
            it.javaLauncher.convention(launcher)
            it.compileStatic.set(false)
            it.compileStaticStrict.set(false)
        }

        wireCompileStaticOptions(project, [compileGroovyPages, compileWebappGroovyPages])

        compileGroovyPages.configure {
            it.dependsOn(
                    tasks.named('classes'),
                    compileWebappGroovyPages,
                    // Pages resolve tag calls against the index, so it has to be written first.
                    generateTagLibraryIndex
            )
        }

        tasks.withType(War).configureEach { War war ->
            war.dependsOn(compileGroovyPages)
            war.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            if (war.name == 'bootWar') {
                war.from(destDir) { CopySpec it ->
                    it.into('WEB-INF/classes')
                }
                war.from(webappDestDir) { CopySpec it ->
                    it.into('WEB-INF/classes')
                }
            } else if (war.name == 'war') {
                war.from(destDir)
                war.from(webappDestDir)
            }

            if (war.classpath) {
                war.classpath = war.classpath + project.files(destDir, webappDestDir)
            } else {
                war.classpath = project.files(destDir, webappDestDir)
            }
        }

        tasks.withType(Jar).configureEach { Jar jar ->
            jar.dependsOn(compileGroovyPages)
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            if (!(jar instanceof War)) {
                if (jar.name == 'bootJar') {
                    jar.from(destDir) { CopySpec it ->
                        it.into('BOOT-INF/classes')
                    }
                    jar.from(webappDestDir) { CopySpec it ->
                        it.into('BOOT-INF/classes')
                    }
                } else if (jar.name == 'jar') {
                    jar.from(destDir)
                    jar.from(webappDestDir)
                }
            }
        }
    }

    /**
     * Whether any controller in this project is scaffolded, which decides whether the views are
     * staged before they are compiled. The answer is needed while the build is being configured,
     * before anything has been compiled, so it is read from the sources.
     *
     * <p>Read through a {@link ValueSource} rather than by opening the files here. Gradle re-runs a
     * value source on every build and invalidates the configuration cache when its answer changes,
     * so a controller that becomes scaffolded rebuilds the graph that generates its views. Read
     * directly, the answer would be an undeclared input: settled once, cached, and wrong from then
     * on.</p>
     *
     * <p>The match is deliberately loose. {@link GenerateScaffoldedViewsTask} reads the real
     * annotation from the compiled class, so a false positive here costs a staging copy and a
     * generation task that writes nothing -- while a false negative costs a view that is missing
     * from the artifact, found by whoever opens that page.</p>
     */
    protected Provider<Boolean> scaffoldsAnyController(Project project) {
        project.providers.of(ScaffoldedControllers) { ValueSourceSpec<ScaffoldedControllers.Parameters> spec ->
            spec.parameters.controllers.from(
                    project.fileTree(project.layout.projectDirectory.dir('grails-app/controllers'))
                            .matching { PatternFilterable p -> p.include('**/*.groovy') })
        }
    }

    /** Reads the controller sources for the mark of a scaffolded one, as a tracked build input. */
    abstract static class ScaffoldedControllers implements ValueSource<Boolean, ScaffoldedControllers.Parameters> {

        interface Parameters extends ValueSourceParameters {

            ConfigurableFileCollection getControllers()

        }

        @Override
        Boolean obtain() {
            parameters.controllers.files.any { File controller -> controller.text.contains('@Scaffold') }
        }

    }

    protected FileCollection resolveClassesDirs(SourceSetOutput output, Project project) {
        output?.classesDirs ?: project.files(project.layout.buildDirectory.dir('classes/main'))
    }

    protected String getTmpDirPath(Project project) {
        File tmpdir = project.layout.buildDirectory.dir('gsptmp').get().asFile
        return tmpdir.absolutePath
    }

}
