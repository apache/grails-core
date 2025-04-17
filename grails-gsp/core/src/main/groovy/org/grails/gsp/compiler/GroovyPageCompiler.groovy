/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp.compiler

import grails.config.ConfigMap
import org.apache.commons.logging.LogFactory
import org.apache.commons.logging.Log
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.grails.config.CodeGenConfig
import org.grails.gsp.GroovyPageMetaInfo
import org.grails.gsp.compiler.transform.GroovyPageInjectionOperation
import org.grails.taglib.encoder.OutputEncodingSettings
import groovy.transform.CompileStatic
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.CompletionService
import java.util.concurrent.Future

/**
 * Used to compile GSP files into a specified target directory.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
@CompileStatic
class GroovyPageCompiler {

    private static final Log LOG = LogFactory.getLog(GroovyPageCompiler)

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
                generatedGroovyPagesDirectory = new File(System.getProperty("java.io.tmpdir"),"gspcompile")
                generatedGroovyPagesDirectory.mkdirs()
            }
            if(configs) {
                CodeGenConfig codeGenConfig = new CodeGenConfig()
                codeGenConfig.classLoader = classLoader
                configMap = codeGenConfig
                for(path in configs) {
                    def f = new File(path)
                    if(f.exists()) {
                        if(f.name.endsWith('.yml')) {
                            codeGenConfig.loadYml(f)
                        }
                        else if(f.name.endsWith('.groovy')) {
                            codeGenConfig.loadGroovy(f)
                        }
                    }
                }
            }
            compilerConfig.setTargetDirectory(targetDir)
            compilerConfig.setSourceEncoding(encoding)
            ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*2)
            CompletionService completionService = new ExecutorCompletionService(threadPool);
            List<Future<Map>> futures = []
            try {
                Integer collationLevel = Runtime.getRuntime().availableProcessors()*2
                if(srcFiles.size() < collationLevel) {
                    collationLevel = 1
                }
                def collatedSrcFiles = srcFiles.collate(collationLevel)
                for(int index = 0; index < collatedSrcFiles.size(); index++) {        
                    def gspFiles = collatedSrcFiles[index]
                    
                    futures.add(completionService.submit({ ->
                        def results = [:]
                        for(int gspIndex = 0; gspIndex < gspFiles.size(); gspIndex++) {
                            File gsp = gspFiles[gspIndex]
                            try {
                                compileGSP(viewsDir, gsp, viewPrefix, packagePrefix, results)    
                            } catch(Exception ex) {
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
                    def completed = completionService.poll(100, TimeUnit.MILLISECONDS);
                    if (completed != null) {
                        Map results = completed.get() as Map //need this to throw exceptions on main thread it seems
                        compileGSPRegistry += results
                        --pending;
                    }
                }

                // write the view registry to a properties file (this is read by GroovyPagesTemplateEngine at runtime)
                File viewregistryFile = new File(targetDir, "gsp/views.properties")
                viewregistryFile.parentFile.mkdirs()
                Properties views = new Properties()
                if (viewregistryFile.exists()) {
                    // only changed files are added to the mapping, read the existing mapping file
                    viewregistryFile.withInputStream { stream ->
                        views.load(new InputStreamReader(stream, "UTF-8"));
                    }
                }
                views.putAll(compileGSPRegistry)
                viewregistryFile.withOutputStream { viewsOut ->
                    views.store(viewsOut, "Precompiled views for ${packagePrefix}")
                }
            } finally {
                // eventListener?.triggerEvent("StatusUpdate", "Shutting Down ThreadPool")
                threadPool.shutdown()
            }
        }
        return compileGSPRegistry
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
                packageDir += "/"
            }
            packageDir += generateJavaName(relPackagePath)
        }
        String className = generateJavaName(packageDir.replace('/','_'))
        className += generateJavaName(gspfile.name)
        // using default package because of GRAILS-5022
        packageDir = ''

        File classFile = new File(new File(targetDir, packageDir), "${className}.class")
        String packageName = packageDir.replace('/','.')
        String fullClassName
        if (packageName) {
            fullClassName = packageName + '.' + className
        }
        else {
            fullClassName = className
        }

        // compile check
        if (gspfile.exists() && (!classFile.exists() || gspfile.lastModified() > classFile.lastModified())) {
            File gspgroovyfile = new File(new File(generatedGroovyPagesDirectory, packageDir), className + ".groovy")
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
                File htmlDataFile = new File(new File(targetDir, packageDir),  className + GroovyPageMetaInfo.HTML_DATA_POSTFIX)
                htmlDataFile.parentFile.mkdirs()
                gpp.writeHtmlParts(htmlDataFile)
                // write linenumber mapping info to data file
                File lineNumbersDataFile = new File(new File(targetDir, packageDir),  className + GroovyPageMetaInfo.LINENUMBERS_DATA_POSTFIX)
                gpp.writeLineNumbers(lineNumbersDataFile)

                // register viewuri -> classname mapping
                compileGSPResults[viewuri] = fullClassName

                CompilationUnit unit = new CompilationUnit(compilerConfig, null, classLoader)
                unit.addPhaseOperation(operation, Phases.CANONICALIZATION)
                unit.addSource(gspgroovyfile.name,gsptarget.toString())
                // unit.addSource(gspgroovyfile)
                unit.compile()
            }
        }
        else {
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
            if (ch=='/') {
                nextMustBeStartChar = true
                sb.append(ch)
            }
            else {
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
