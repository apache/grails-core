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
package org.grails.gsp.compiler

import java.util.concurrent.Callable
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.springframework.core.CollectionFactory
import grails.config.ConfigMap
import org.apache.grails.gradle.common.PropertyFileUtils
import org.grails.config.CodeGenConfig
import org.grails.gsp.GroovyPageMetaInfo
import org.grails.gsp.compiler.transform.GroovyPageInjectionOperation
import org.grails.taglib.encoder.OutputEncodingSettings
import org.grails.gsp.GroovyPage

/**
 * Used to compile GSP files into a specified target directory. The compiler creates 3 files per page.
 * Firstly, it generates a {@link GroovyPage} derived class which is then compiled to a .class file.
 * It also will generate a "_html.data" and a "_linenumbers.data" file which contain the static HTML parts of the page.
 * These are read at runtime by the {@link org.grails.gsp.GroovyPagesTemplateEngine} class.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
@CompileStatic
class GroovyPageCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyPageCompiler)

    private Map compileGSPRegistry = [:]
    private Object mutexObject = new Object()
    File generatedGroovyPagesDirectory
    File targetDir
    CompilerConfiguration compilerConfig = new CompilerConfiguration()
    GroovyPageInjectionOperation operation = new GroovyPageInjectionOperation()
    GroovyClassLoader classLoader = new GroovyClassLoader(Thread.currentThread().contextClassLoader, compilerConfig)

    List<File> srcFiles = []
    File viewsDir
    String viewPrefix = '/'
    String packagePrefix = 'default'
    String encoding = 'UTF-8'
    String expressionCodec = OutputEncodingSettings.getDefaultValue(OutputEncodingSettings.EXPRESSION_CODEC_NAME)
    String[] configs = []
    ConfigMap configMap
    ExecutorService threadPool

    void setCompilerConfig(CompilerConfiguration c) {
        compilerConfig = c
        classLoader = new GroovyClassLoader(Thread.currentThread().contextClassLoader, compilerConfig)
    }

    void setCleanCompilerConfig(CompilerConfiguration c) {
        compilerConfig = c
        classLoader = new GroovyClassLoader(System.classLoader, compilerConfig)
    }

    /**
     * Compiles the given GSP pages and returns a Map of URI to classname mappings
     */
    Map compile() {
        if (srcFiles && targetDir && viewsDir) {
            if (!generatedGroovyPagesDirectory) {
                generatedGroovyPagesDirectory = new File(System.getProperty('java.io.tmpdir'), 'gspcompile')
                generatedGroovyPagesDirectory.mkdirs()
            }
            if (configs) {
                CodeGenConfig codeGenConfig = new CodeGenConfig()
                codeGenConfig.classLoader = classLoader
                configMap = codeGenConfig
                for (path in configs) {
                    def f = new File(path)
                    if (f.exists()) {
                        if (f.name.endsWith('.yml')) {
                            codeGenConfig.loadYml(f)
                        } else if (f.name.endsWith('.groovy')) {
                            codeGenConfig.loadGroovy(f)
                        }
                    }
                }
            }
            compilerConfig.setTargetDirectory(targetDir)
            compilerConfig.setSourceEncoding(encoding)
            // GSP compilation parallelism is intentionally configurable via the
            // grails.gsp.compiler.parallelism system property. The default is
            // 1 (serial) under Groovy 6 because Groovy 6.0.0-SNAPSHOT contains
            // a thread-safety bug in org.codehaus.groovy.util.ListHashMap that
            // surfaces during AnnotationNode.isTargetAllowed -> NodeMetaDataHandler
            // .getNodeMetaData -> Map.computeIfAbsent on shared annotation
            // metadata (e.g. @Inject, @CompileStatic) when multiple GSPs are
            // compiled concurrently. The symptom is "General error during
            // instruction selection: Index N out of bounds for length N" with
            // an ArrayIndexOutOfBoundsException in ListHashMap.toMap. Falling
            // back to a single thread eliminates the race at a small cost in
            // wall-clock time. Override with -Dgrails.gsp.compiler.parallelism=N
            // (or 0 to use availableProcessors*2) once Groovy 6 fixes this.
            int parallelism = computeGspCompilerParallelism()
            ExecutorService threadPool = Executors.newFixedThreadPool(parallelism)
            CompletionService completionService = new ExecutorCompletionService(threadPool)
            List<Future<Map>> futures = []
            try {
                Integer collationLevel = parallelism
                if (srcFiles.size() < collationLevel) {
                    collationLevel = 1
                }
                def collatedSrcFiles = srcFiles.collate(collationLevel)
                for (int index = 0; index < collatedSrcFiles.size(); index++) {
                    def gspFiles = collatedSrcFiles[index]

                    futures.add(completionService.submit({ ->
                        def results = [:]
                        for (int gspIndex = 0; gspIndex < gspFiles.size(); gspIndex++) {
                            File gsp = gspFiles[gspIndex]
                            try {
                                compileGSP(viewsDir, gsp, viewPrefix, packagePrefix, results)
                            } catch (Exception ex) {
                                LOG.error("Error Compiling GSP File: ${gsp.name} - ${ex.message}")
                                throw ex
                            }
                        }
                        return results
                    } as Callable) as Future<Map>)
                }

                int pending = futures.size()

                while (pending > 0) {
                    // Wait for up to 100ms to see if anything has completed.
                    // The completed future is returned if one is found; otherwise null.
                    // (Tune 100ms as desired)
                    def completed = completionService.poll(100, TimeUnit.MILLISECONDS)
                    if (completed != null) {
                        Map results = completed.get() as Map //need this to throw exceptions on main thread it seems
                        compileGSPRegistry += results
                        --pending
                    }
                }

                // write the view registry to a properties file (this is read by GroovyPagesTemplateEngine at runtime)
                File viewregistryFile = new File(targetDir, 'gsp/views.properties')
                viewregistryFile.parentFile.mkdirs()
                // Use SortedProperties to ensure a consistent order of entries for reproducible builds
                Properties views = CollectionFactory.createSortedProperties(false)
                if (viewregistryFile.exists()) {
                    // only changed files are added to the mapping, read the existing mapping file
                    viewregistryFile.withInputStream { stream ->
                        views.load(new InputStreamReader(stream, 'UTF-8'))
                    }
                }
                views.putAll(compileGSPRegistry)
                viewregistryFile.withOutputStream { viewsOut ->
                    views.store(viewsOut, "Precompiled views for ${packagePrefix}")
                }
                PropertyFileUtils.makePropertiesFileReproducible(viewregistryFile)
            } finally {
                // eventListener?.triggerEvent("StatusUpdate", "Shutting Down ThreadPool")
                threadPool.shutdown()
            }
        }
        return compileGSPRegistry
    }

    /**
     * Resolves the worker-thread count for parallel GSP compilation.
     *
     * Honours -Dgrails.gsp.compiler.parallelism=N. A value of 0 (or any
     * non-positive number) means "use availableProcessors() * 2" (the
     * historical Grails default). When the property is unset we default
     * to 1 on Groovy 6 (see the inline comment at the call site for why)
     * and to availableProcessors() * 2 on Groovy 5 and earlier.
     */
    private static int computeGspCompilerParallelism() {
        int cores = Runtime.getRuntime().availableProcessors()
        int defaultParallelism = isGroovy6OrLater() ? 1 : cores * 2

        String override = System.getProperty('grails.gsp.compiler.parallelism')
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

    /**
     * Compiles an individual GSP file
     *
     * @param viewsDir The base directory that contains the GSP view
     * @param gspfile The actual GSP file reference
     * @param viewPrefix The prefix to use for the path to the view
     * @param packagePrefix The package prefix to use which allows scoping for different applications and plugins
     *
     */
    protected Map compileGSP(File viewsDir, File gspfile, String viewPrefix, String packagePrefix, Map compileGSPResults) {
        String relPath = relativePath(viewsDir, gspfile)
        String viewuri = viewPrefix + relPath

        String relPackagePath = relativePath(viewsDir, gspfile.getParentFile())

        String packageDir = "gsp/${packagePrefix}"
        if (relPackagePath.length() > 0) {
            if (!packageDir.endsWith('/')) {
                packageDir += '/'
            }
            packageDir += generateJavaName(relPackagePath)
        }
        String className = generateJavaName(packageDir.replace('/', '_'))
        className += generateJavaName(gspfile.name)
        // using default package because of GRAILS-5022
        packageDir = ''

        File classFile = new File(new File(targetDir, packageDir), "${className}.class")
        String packageName = packageDir.replace('/', '.')
        String fullClassName
        if (packageName) {
            fullClassName = packageName + '.' + className
        } else {
            fullClassName = className
        }

        // compile check
        if (gspfile.exists() && (!classFile.exists() || gspfile.lastModified() > classFile.lastModified())) {
            File gspgroovyfile = new File(new File(generatedGroovyPagesDirectory, packageDir), className + '.groovy')
            // gspgroovyfile.getParentFile().mkdirs()

            gspfile.withInputStream { InputStream gspinput ->
                GroovyPageParser gpp = new GroovyPageParser(viewuri - '.gsp', viewuri, gspfile.absolutePath, gspinput, encoding, expressionCodec, configMap)
                gpp.packageName = packageName
                gpp.className = className
                gpp.lastModified = gspfile.lastModified()
                StringWriter gsptarget = new StringWriter()
                gpp.generateGsp(gsptarget)
                gsptarget.flush()
                // write static html parts to data file (read from classpath at runtime)
                File htmlDataFile = new File(new File(targetDir, packageDir), className + GroovyPageMetaInfo.HTML_DATA_POSTFIX)
                htmlDataFile.parentFile.mkdirs()
                gpp.writeHtmlParts(htmlDataFile)
                // write linenumber mapping info to data file
                File lineNumbersDataFile = new File(new File(targetDir, packageDir), className + GroovyPageMetaInfo.LINENUMBERS_DATA_POSTFIX)
                gpp.writeLineNumbers(lineNumbersDataFile)

                // register viewuri -> classname mapping
                compileGSPResults[viewuri] = fullClassName

                CompilationUnit unit = new CompilationUnit(compilerConfig, null, classLoader)
                unit.addPhaseOperation(operation, Phases.CANONICALIZATION)
                unit.addSource(gspgroovyfile.name, gsptarget.toString())
                unit.compile()
            }
        } else {
            compileGSPResults[viewuri] = fullClassName
        }

        return compileGSPResults

    }

    // find out the relative path from relbase to file
    protected String relativePath(File relbase, File file) {
        List<String> pathParts = []
        File currentFile = file
        while (currentFile != null && currentFile != relbase) {
            pathParts += currentFile.name
            currentFile = currentFile.parentFile
        }
        pathParts.reverse().join('/')
    }

    protected generateJavaName(String str) {
        StringBuilder sb = new StringBuilder()
        int i = 0
        boolean nextMustBeStartChar = true
        char ch
        while (i < str.length()) {
            ch = str.charAt(i++)
            if (ch == '/') {
                nextMustBeStartChar = true
                sb.append(ch)
            } else {
                // package or class name cannot start with a number
                if (nextMustBeStartChar && !Character.isJavaIdentifierStart(ch)) {
                    sb.append('_')
                }
                nextMustBeStartChar = false
                sb.append(Character.isJavaIdentifierPart(ch) ? ch : '_')
            }
        }
        sb.toString()
    }
}
