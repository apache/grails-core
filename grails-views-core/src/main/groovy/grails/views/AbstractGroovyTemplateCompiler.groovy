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

package grails.views

import java.util.concurrent.Callable
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import groovy.io.FileType
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.control.io.FileReaderSource

import grails.views.compiler.ViewsTransform
import grails.views.resolve.GenericGroovyTemplateResolver

/**
 * A generic compiler for Groovy templates that are compiled into classes in production
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class AbstractGroovyTemplateCompiler {

    @Delegate CompilerConfiguration configuration = new CompilerConfiguration()

    String packageName = ''
    File sourceDir
    ViewConfiguration viewConfiguration

    AbstractGroovyTemplateCompiler(ViewConfiguration configuration, File sourceDir) {
        this.viewConfiguration = configuration
        this.packageName = configuration.packageName
        this.sourceDir = sourceDir
        configureCompiler(this.configuration)
    }

    AbstractGroovyTemplateCompiler() {
    }

    protected CompilerConfiguration configureCompiler(CompilerConfiguration configuration) {
        configuration.compilationCustomizers.clear()

        ImportCustomizer importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports(viewConfiguration.packageImports)
        importCustomizer.addStaticStars(viewConfiguration.staticImports)

        configuration.addCompilationCustomizers(importCustomizer)
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(newViewsTransform()))
        return configuration
    }

    protected ViewsTransform newViewsTransform() {
        new ViewsTransform(viewConfiguration.extension)
    }

    void compile(List<File> sources) {

        // Mirror the GSP-side guard in GroovyPageCompiler: Groovy 6.0.0-SNAPSHOT
        // contains a thread-safety bug in org.codehaus.groovy.util.ListHashMap
        // reachable through AnnotationNode.isTargetAllowed ->
        // NodeMetaDataHandler.getNodeMetaData -> Map.computeIfAbsent on shared
        // annotation metadata when multiple template compiles concurrently
        // touch the same AST. Surfaces in CI as
        //   General error during instruction selection: Index N out of bounds
        //   java.lang.ArrayIndexOutOfBoundsException ... at ListHashMap.toMap
        // during :grails-test-examples-*:compileGsonViews. Default to a single
        // worker on Groovy 6 to dodge the race; preserve the historical
        // availableProcessors() * 2 default on Groovy 5 and earlier. Override
        // with -Dgrails.views.compiler.parallelism=N once Groovy 6 fixes this
        // (or 0 to use availableProcessors() * 2 explicitly).
        int parallelism = computeParallelism()
        ExecutorService threadPool = Executors.newFixedThreadPool(parallelism)
        CompletionService completionService = new ExecutorCompletionService(threadPool)

        try {
            Integer collationLevel = parallelism
            if (sources.size() < collationLevel) {
                collationLevel = 1
            }
            configuration.setClasspathList(classpath)
            String pathToSourceDir = sourceDir.canonicalPath
            def collatedSources = sources.collate(collationLevel)
            List<Future<Boolean>> futures = []
            for (int index = 0; index < collatedSources.size(); index++) {
                def sourceFiles = collatedSources[index]
                futures.add(completionService.submit({ ->
                    CompilerConfiguration configuration = new CompilerConfiguration(this.configuration)
                    for (int viewIndex = 0; viewIndex < sourceFiles.size(); viewIndex++) {
                        File source = sourceFiles[viewIndex]
                        configureCompiler(configuration)
                        CompilationUnit unit = new CompilationUnit(configuration)
                        String pathToSource = source.canonicalPath
                        String path = pathToSource - pathToSourceDir
                        String templateName = GenericGroovyTemplateResolver.resolveTemplateName(
                                packageName, path
                        )
                        unit.addSource(new SourceUnit(
                                templateName,
                                new FileReaderSource(source, configuration),
                                configuration,
                                unit.classLoader,
                                unit.errorCollector
                        ))
                        unit.compile()
                    }
                    return true
                } as Callable) as Future<Boolean>)
            }

            int pending = futures.size()

            while (pending > 0) {
                // Wait for up to 100ms to see if anything has completed.
                // The completed future is returned if one is found; otherwise null.
                // (Tune 100ms as desired)
                def completed = completionService.poll(100, TimeUnit.MILLISECONDS)
                if (completed != null) {
                    Boolean response = completed.get() as Boolean//need this to throw exceptions on main thread it seems
                    --pending
                }
            }
        }
        finally {
            threadPool.shutdown()
        }

    }

    void compile(File...sources) {
        compile(Arrays.asList(sources))
    }

    /**
     * Resolves the worker-thread count for parallel template compilation.
     * Honours -Dgrails.views.compiler.parallelism=N. A non-positive override
     * means "use availableProcessors() * 2" (the historical default). When the
     * property is unset we default to 1 on Groovy 6 (see the inline comment at
     * the call site for the ListHashMap thread-safety reasoning) and to
     * availableProcessors() * 2 on Groovy 5 and earlier.
     */
    private static int computeParallelism() {
        int cores = Runtime.getRuntime().availableProcessors()
        int defaultParallelism = isGroovy6OrLater() ? 1 : cores * 2

        String override = System.getProperty('grails.views.compiler.parallelism')
        if (override == null || override.isEmpty()) {
            return defaultParallelism
        }
        try {
            int requested = Integer.parseInt(override.trim())
            if (requested <= 0) {
                return cores * 2
            }
            return requested
        } catch (NumberFormatException ignore) {
            return defaultParallelism
        }
    }

    private static boolean isGroovy6OrLater() {
        String version = groovy.lang.GroovySystem.getVersion()
        if (version == null || version.isEmpty()) {
            return false
        }
        try {
            int dot = version.indexOf('.')
            int major = Integer.parseInt(dot >= 0 ? version.substring(0, dot) : version)
            return major >= 6
        } catch (NumberFormatException ignore) {
            return false
        }
    }

    static void run(String[] args, Class<? extends GenericViewConfiguration> configurationClass, Class<? extends AbstractGroovyTemplateCompiler> compilerClass) {
        if (args.length != 7) {
            System.err.println("Invalid arguments: [${args.join(',')}]")
            System.err.println("""
Usage: java -cp CLASSPATH ${compilerClass.name} [srcDir] [destDir] [targetCompatibility] [packageImports] [packageName] [configFile] [encoding]
""")
            System.exit(1)
        }
        File srcDir = new File(args[0])
        File destinationDir = new File(args[1])
        String targetCompatibility = args[2]
        String[] packageImports = args[3].trim().split(',')
        String packageName = args[4].trim()
        File configFile = new File(args[5])
        String encoding = new File(args[6])

        GenericViewConfiguration configuration = configurationClass.getDeclaredConstructor().newInstance()
        configuration.packageName = packageName
        configuration.encoding = encoding
        configuration.packageImports = packageImports

        configuration.readConfiguration(configFile)

        AbstractGroovyTemplateCompiler compiler = compilerClass.getDeclaredConstructor(ViewConfiguration, File).newInstance(configuration, srcDir)
        compiler.setTargetDirectory(destinationDir)
        compiler.setSourceEncoding(configuration.encoding)
        if (targetCompatibility != null) {
            compiler.setTargetBytecode(targetCompatibility)
        }

        String fileExtension = configuration.extension
        compiler.setDefaultScriptExtension(fileExtension)

        List<File> allFiles = []
        srcDir.eachFileRecurse(FileType.FILES) { File f ->
            if (f.name.endsWith(fileExtension)) {
                allFiles.add(f)
            }
        }
        compiler.compile(allFiles)
    }
}
