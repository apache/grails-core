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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import javax.inject.Inject

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec

import grails.util.BuildSettings
import grails.util.Environment
import org.grails.gradle.plugin.views.ViewCompileDestination
import org.grails.gradle.plugin.views.ViewCompileEnvironment
import org.grails.gradle.plugin.views.ViewCompileOptions

/**
 * Abstract Gradle task for compiling templates, using GroovyPageForkedCompiler
 * This Task is a Forked Java Task that is configurable with fork options provided
 * by {@link ViewCompileOptions}
 *
 * <p>Cacheable. What a page compiles to depends on the Groovy that compiles it, the Java it is
 * compiled for and the classes it is compiled against, and each of them is part of the key. The
 * compiler runs from {@link #getClasspath()}, which is fingerprinted in parts: the libraries, the
 * Groovy and the compiler among them, by content, as {@link #getLibraryClasspath()}, so a build that
 * resolves another Groovy is not handed this one's pages; the project's own classes by their ABI, as
 * {@link #getProjectClasses()}, since a page sees no more of them than a class compiled against
 * them does, with the files among them other than classes by content, as
 * {@link #getProjectResources()}; and the project's AST transformations by content, with the resources that register them,
 * as {@link #getAstTransformationClasses()}, since they run while the pages are compiled, as are all of
 * the project's classes when an {@code application.groovy} runs with them, as
 * {@link #getConfigurationClasses()}.
 * {@link #getJavaLauncher()} and the target compatibility say which Java. The Grails
 * environment whose configuration the pages are compiled with, {@link #getGrailsEnvironment()}, is
 * part of the key too. The pages are fingerprinted by their path under the source, so a checkout in
 * another directory reuses what this one compiled.</p>
 *
 * <p>Compiles every page, from an empty destination, each time it runs; see
 * {@link ViewCompileDestination}. The compiler's own economy, of leaving a page alone whose class is
 * newer than its source, would otherwise carry pages of another environment, classpath or
 * configuration, or pages that no longer exist, into the archive and the build cache.</p>
 *
 * @author David Estes
 * @since 4.0
 */
@CompileStatic
@CacheableTask
abstract class GroovyPageForkCompileTask extends AbstractCompile {

    @Input
    @Optional
    final Property<String> packageName

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final ConfigurableFileCollection grailsConfigurationPaths

    /**
     * The project's own compiled classes, as they are on {@link #getClasspath()}: normally the class
     * directories of the main source set. Fingerprinted by their ABI, as Gradle compares the classpath
     * of a Java compilation: a page compiles against the classes it names, their members and their
     * signatures, and not what their method bodies do, so a change to a method body or a private member
     * leaves the pages as they were, where a page is otherwise compiled again for every change to any
     * class of the project. AST transformations among them run their code while the pages are compiled,
     * and are an input by content as well, as {@link #getAstTransformationClasses()}, and so is all of
     * them when an {@code application.groovy} configures the pages, as
     * {@link #getConfigurationClasses()}.
     *
     * @since 9.0
     */
    @CompileClasspath
    final ConfigurableFileCollection projectClasses

    /**
     * The files of {@link #getProjectClasses()} other than classes, by content: the ABI they are
     * otherwise compared by leaves them out. The page compiler loads from them what registers an AST
     * transformation or an extension module, which a build or a plugin's {@code ast} source set can
     * copy into the classes directory; what Grails writes there,
     * such as {@code META-INF/grails.factories}, changes only with the classes it lists.
     *
     * @since 9.0
     */
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getProjectResources() {
        projectClasses.asFileTree.matching { PatternFilterable files -> files.exclude('**/*.class') }
    }

    /**
     * The project's AST transformations, which run while the pages are compiled, so what their method
     * bodies do is part of what a page compiles to: normally the whole output of the {@code ast} source
     * set, which a plugin's build copies into the main classes directory, among
     * {@link #getProjectClasses()}. Its resources as well as its classes, since they register the
     * transformations and the extension modules the pages are compiled with, and the ABI of
     * {@link #getProjectClasses()} leaves out everything but classes. Fingerprinted by content.
     *
     * @since 9.0
     */
    @Classpath
    final ConfigurableFileCollection astTransformationClasses

    /**
     * {@link #getProjectClasses()} by content when a Groovy configuration is among
     * {@link #getGrailsConfigurationPaths()}, and nothing otherwise. The compiler runs an
     * {@code application.groovy} with the project's classes, and takes the codecs and the other
     * settings of the pages from what it computes, so with one a method body of the project can
     * decide what a page compiles to. A project configured by {@code application.yml} alone has its
     * pages compiled again only for what they can see.
     *
     * @since 9.0
     */
    @Classpath
    FileCollection getConfigurationClasses() {
        boolean groovyConfiguration = grailsConfigurationPaths.files.any { File file -> file.name.endsWith('.groovy') && file.isFile() }
        groovyConfiguration ? projectClasses : projectClasses.minus(projectClasses)
    }

    /**
     * {@link #getClasspath()} without {@link #getProjectClasses()}: the libraries the pages are
     * compiled against, and the Groovy and the compiler that compile them. Fingerprinted by content.
     *
     * @since 9.0
     */
    @Classpath
    final ConfigurableFileCollection libraryClasspath

    /**
     * The directory of the pages, which the compiler names them under. Internal rather than an input
     * directory: its pages are fingerprinted as part of {@link #getSource()}, and it need not exist -
     * a project whose only pages are generated, a plugin that scaffolds controllers and has no views
     * of its own, has none, and an input directory that does not exist fails validation. The pages
     * beneath it are an input all the same, as {@link #getAllPages()}, since the compiler finds them
     * by the directory.
     */
    @Internal
    final DirectoryProperty srcDir

    @Nested
    final ViewCompileOptions compileOptions

    @LocalState
    String tmpDirPath

    @Input
    @Optional
    final Property<String> serverpath

    /**
     * Whether the pages this task compiles are compiled statically.
     *
     * <p>Stated to the forked compiler as the {@code grails.views.gsp.compileStatic} system property,
     * which is the same setting {@code grails-app/conf/application.yml} carries and the same one the
     * running application reads, so that a page compiled here compiles the same way there.</p>
     */
    @Input
    final Property<Boolean> compileStatic

    /** Whether the pages this task compiles are held to the names they declare. See {@link #compileStatic}. */
    @Input
    final Property<Boolean> compileStaticStrict

    /**
     * Files that each name, one per line, pages of the source that are optional, as paths relative
     * to it. An optional page that does not compile is left out with a warning rather than failing
     * the build: a page generated from a template a dependency supplies is an optimisation, and
     * without it the page is produced when it is first rendered.
     */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    final ConfigurableFileCollection optionalPages

    /**
     * Directories of pages the build generated, compiled in the same compilation as the source, each
     * page named by its path under the directory holding it, as though it were in the source, where a
     * page at the same path takes precedence. Part of {@link #getSource()}, so a project whose only
     * pages are generated compiles them. One compilation rather than two, because each would write a
     * {@code gsp/views.properties} and an archive keeps only the first.
     */
    @Internal
    final ConfigurableFileCollection generatedViews

    /**
     * The Grails environment the pages are compiled for, by name.
     *
     * <p>A page is compiled against the configuration of one environment: the forked compiler merges
     * that environment's block of {@code application.yml} and {@code application.groovy} over the rest
     * before it reads the codecs and the other settings a page carries once compiled. The environment
     * is therefore part of what the pages are, and part of the cache key, so pages compiled for one
     * environment are not restored into a build for another.</p>
     *
     * <p>Defaults as {@link ViewCompileEnvironment#defaultFor} says: to an environment the fork
     * options of {@link #getCompileOptions()} already name, else to the one the build runs with, else
     * to {@code development}. Read as {@link #getStatedEnvironment()}, which is the input.</p>
     *
     * @since 7.0.18
     */
    @Internal
    final Property<String> grailsEnvironment

    private ExecOperations execOperations

    /** The build directory, which {@link #getDestinationDirectory()} has to be within. */
    private final DirectoryProperty buildDirectory

    /**
     * The Java runtime the pages are compiled by.
     *
     * <p>Compilation is forked, and a forked process runs whatever JVM it is given rather than the
     * one the project asked for. Left to itself it inherits the JVM running Gradle, so a project
     * declaring a toolchain gets its pages compiled by a different Java than everything else it
     * builds -- which shows up as an {@code UnsupportedClassVersionError} at the moment a page is
     * first rendered, long after the build called itself successful.</p>
     *
     * <p>Nested rather than internal because the Java that did the compiling is part of what the
     * result is: pages built by one are not left standing when the build asks for another.</p>
     */
    @Nested
    abstract Property<JavaLauncher> getJavaLauncher()

    /**
     * Where the pages are compiled to. Emptied before each compilation, so a directory of the task's
     * own, within the build directory; see {@link ViewCompileDestination}.
     */
    @OutputDirectory
    final DirectoryProperty destinationDirectory

    @Inject
    GroovyPageForkCompileTask(ExecOperations execOperations, ObjectFactory objectFactory) {
        this.execOperations = execOperations
        packageName = objectFactory.property(String).convention(project.name ?: project.projectDir.canonicalFile.name)
        srcDir = objectFactory.directoryProperty()
        compileOptions = objectFactory.newInstance(ViewCompileOptions)
        serverpath = objectFactory.property(String)
        grailsEnvironment = objectFactory.property(String).convention(ViewCompileEnvironment.defaultFor(project.providers, compileOptions))
        // the input is what the compiler gets: a change to it once the input is read would be compiled but not keyed
        grailsEnvironment.finalizeValueOnRead()
        compileStatic = objectFactory.property(Boolean).convention(false)
        compileStaticStrict = objectFactory.property(Boolean).convention(false)
        optionalPages = objectFactory.fileCollection()
        generatedViews = objectFactory.fileCollection()
        grailsConfigurationPaths = objectFactory.fileCollection()
        projectClasses = objectFactory.fileCollection()
        astTransformationClasses = objectFactory.fileCollection()
        libraryClasspath = objectFactory.fileCollection()
        grailsConfigurationPaths.from(
                project.layout.projectDirectory.file('grails-app/conf/application.yml'),
                project.layout.projectDirectory.file('grails-app/conf/application.groovy')
        )
        buildDirectory = project.layout.buildDirectory
        destinationDirectory = objectFactory.directoryProperty().convention(buildDirectory.dir('gsp-classes/main'))
    }

    /**
     * The environment stated to the forked compiler, as the {@code grails.env} system property in
     * place of any the fork options name, so that nothing the compiler inherits decides:
     * {@link #getGrailsEnvironment()} by the environment's full name, which is what the pages are
     * compiled for and so what the cache key holds. A blank {@code grailsEnvironment} fails the build
     * here, before anything is compiled or removed.
     *
     * @since 7.0.18
     */
    @Input
    String getStatedEnvironment() {
        ViewCompileEnvironment.forCompiler(grailsEnvironment, path)
    }

    /** Gradle's file operations, which empty the destination; Gradle supplies them to the task it generates. */
    @Inject
    protected FileSystemOperations getFileSystemOperations() {
        throw new UnsupportedOperationException()
    }

    /**
     * The classpath the compiler runs from. Not an input as such: it is fingerprinted as
     * {@link #getLibraryClasspath()}, by content, and {@link #getProjectClasses()}, by ABI, with
     * {@link #getProjectResources()} by content. Overridden
     * only to say so, in place of the classpath input {@code AbstractCompile} declares it as.
     */
    @Override
    @Internal
    @SuppressWarnings('UnnecessaryOverridingMethod')
    FileCollection getClasspath() {
        super.getClasspath()
    }

    /**
     * Sets the classpath the compiler runs from, and {@link #getLibraryClasspath()} to all of it but
     * {@link #getProjectClasses()}, both read when the pages are compiled.
     */
    @Override
    void setClasspath(FileCollection configuration) {
        super.setClasspath(configuration)
        libraryClasspath.setFrom(configuration.minus(projectClasses))
    }

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSource() {
        return super.getSource().plus(generatedViews.asFileTree)
    }

    /**
     * Every page beneath {@link #getSrcDir()}, as the compiler reads them: it finds the pages by the
     * directory and compiles them all, so a page the task's own filters leave out of
     * {@link #getSource()} is compiled all the same, and is an input all the same. Fingerprinted by
     * path under the directory, so a checkout elsewhere reuses what this one compiled.
     *
     * @since 7.0.18
     */
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getAllPages() {
        srcDir.asFileTree.matching { PatternFilterable pages -> pages.include('**/*.' + fileExtension) }
    }

    @Override
    void setSource(Object source) {
        if (Directory.isAssignableFrom(source.class)) {
            this.srcDir.set(source as Directory)
        }
        else if (File.isAssignableFrom(source.class)) {
            this.srcDir.set(source as File)
            if (!srcDir.getAsFile().get().isDirectory()) {
                throw new IllegalArgumentException("The source for ${getFileExtension().toUpperCase()} compilation must be a single directory, but was $source")
            }
        }
        else if (DirectoryProperty.isAssignableFrom(source.class)) {
            this.srcDir.set(source as DirectoryProperty)
        }
        else {
            throw new RuntimeException("Unsupported source type: ${source.class.name}")
        }
        super.setSource(source)
    }

    @TaskAction
    void execute() {
        compile()
    }

    protected void compile() {
        String environment = statedEnvironment
        logger.info('Compiling the pages for the {} environment', environment)
        ViewCompileDestination.empty(fileSystemOperations, destinationDirectory.get().asFile, buildDirectory.get().asFile, classpath.files, path)
        ExecResult result = execOperations.javaexec(
                new Action<JavaExecSpec>() {
                    @Override
                    @CompileDynamic
                    void execute(JavaExecSpec javaExecSpec) {
                        javaExecSpec.executable = javaLauncher.get().executablePath.asFile.absolutePath
                        javaExecSpec.mainClass.set(getCompilerName())
                        javaExecSpec.setClasspath(getClasspath())

                        List<String> jvmArgs = ViewCompileEnvironment.withoutStated(compileOptions.forkOptions.jvmArgs)
                        if (jvmArgs) {
                            javaExecSpec.jvmArgs(jvmArgs)
                        }
                        javaExecSpec.setMaxHeapSize(compileOptions.forkOptions.memoryMaximumSize)
                        javaExecSpec.setMinHeapSize(compileOptions.forkOptions.memoryInitialSize)
                        javaExecSpec.systemProperty(Environment.KEY, environment)

                        if (!generatedViews.isEmpty()) {
                            javaExecSpec.systemProperty(BuildSettings.GENERATED_GSP_VIEW_DIRECTORIES,
                                    generatedViews.files*.absolutePath.join(File.pathSeparator))
                        }
                        if (!optionalPages.isEmpty()) {
                            javaExecSpec.systemProperty(BuildSettings.OPTIONAL_GSP_PAGES,
                                    optionalPages.files*.absolutePath.join(File.pathSeparator))
                        }
                        if (compileStatic.get()) {
                            javaExecSpec.systemProperty(BuildSettings.COMPILE_STATIC_GSP, 'true')
                            if (compileStaticStrict.get()) {
                                javaExecSpec.systemProperty(BuildSettings.COMPILE_STATIC_GSP_STRICT, 'true')
                            }
                        }

                        String configFiles = grailsConfigurationPaths.files.collect { it.canonicalPath }.join(',')

                        Path path = Paths.get(tmpDirPath)
                        File tmp = Files.exists(path) ? path.toFile() : Files.createDirectories(path).toFile()
                        List<String> arguments = [
                                srcDir.get().asFile.canonicalPath,
                                destinationDirectory.get().asFile.canonicalPath,
                                tmp.canonicalPath,
                                // What a page is compiled for follows what it is compiled by,
                                // unless the build has said otherwise for itself.
                                targetCompatibility ?: javaLauncher.get().metadata.languageVersion.toString(),
                                packageName.get() as String,
                                serverpath.getOrNull() as String,
                                configFiles,
                                compileOptions.encoding.get()
                        ]

                        prepareArguments(arguments)
                        javaExecSpec.args(arguments)
                    }

                }
        )
        result.assertNormalExitValue()

    }

    void prepareArguments(List<String> arguments) {
        // no-op
    }

    @Input
    protected String getCompilerName() {
        'org.grails.web.pages.GroovyPageForkedCompiler'
    }

    @Input
    String getFileExtension() {
        'gsp'
    }
}
