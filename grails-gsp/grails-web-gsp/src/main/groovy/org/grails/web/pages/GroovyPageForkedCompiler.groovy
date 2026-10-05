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
package org.grails.web.pages

import groovy.io.FileType
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilerConfiguration

import grails.util.BuildSettings

import org.grails.gsp.compiler.GroovyPageCompiler

/**
 * A Forked Compiler for use (typically by Gradle)
 *
 * @author David Estes
 */

@CompileStatic
class GroovyPageForkedCompiler {

    private static final String fileExtension = '.gsp'

    @Delegate
    CompilerConfiguration configuration = new CompilerConfiguration()

    String packageName = ''
    File sourceDir
    File destDir
    File tmpdir
    String serverpath
    String encoding
    String targetCompatibility
    String[] configs

    GroovyPageForkedCompiler(File sourceDir, File destDir, File tmpdir) {
        this.tmpdir = tmpdir
        this.destDir = destDir
        this.sourceDir = sourceDir

    }

    GroovyPageCompiler createPageCompiler() {
        GroovyPageCompiler compiler = new GroovyPageCompiler()
        CompilerConfiguration config = new CompilerConfiguration()

        if (configs) {
            String[] configPaths = extractValidConfigPaths(configs)
            compiler.setConfigs(configPaths)
        }
        if (classpath) {
            config.classpath = classpath.toString()
        }
        if (targetCompatibility) {
            config.setTargetBytecode(targetCompatibility)
        }

        compiler.compilerConfig = config
        compiler.targetDir = destDir
        compiler.viewsDir = sourceDir

        if (tmpdir) {
            compiler.generatedGroovyPagesDirectory = tmpdir
        }
        if (packageName) {
            compiler.packagePrefix = packageName
        }
        if (serverpath) {
            compiler.viewPrefix = serverpath
        }
        if (encoding) {
            compiler.encoding = encoding
        }
        compiler.generatedViewsDirs = generatedViewDirectories()
        String optionalPages = System.getProperty(BuildSettings.OPTIONAL_GSP_PAGES)
        if (optionalPages) {
            for (String list : optionalPages.split(File.pathSeparator)) {
                File file = new File(list)
                if (file.isFile()) {
                    compiler.optionalPages.addAll(file.readLines('UTF-8')*.trim().findAll { String page -> page })
                }
            }
        }
        return compiler
    }

    /** The directories of generated pages the build asks to be compiled with the source, those that exist. */
    static List<File> generatedViewDirectories() {
        String dirs = System.getProperty(BuildSettings.GENERATED_GSP_VIEW_DIRECTORIES)
        (dirs ? dirs.split(File.pathSeparator).toList() : [])
                .collect { String dir -> new File(dir) }
                .findAll { File dir -> dir.isDirectory() }
    }

    private String[] extractValidConfigPaths(String[] configs) {
        configs
                .collect { new File(it) }
                .findAll { it.exists() }
                .collect { it.canonicalPath }
                .toArray(new String[0])
    }

    void compile(List<File> sources) {
        GroovyPageCompiler compiler = createPageCompiler()
        compiler.srcFiles = sources
        compiler.compile()
        // the build shows what this process prints, not what it logs
        compiler.leftOut.each { String page, String reason ->
            System.err.println("Left out the optional page ${page}, which does not compile; if it is rendered it " +
                    "is produced then instead, and fails the same way: ${reason}")
        }
    }

    static void main(String[] args) {
        run(args)
    }

    static void run(String[] args) {
        if (args.length != 8) {
            System.err.println("Invalid arguments: [${args.join(',')}]")
            System.err.println('''
Usage: java -cp CLASSPATH GroovyPageForkedCompiler [srcDir] [destDir] [tmpDir] [targetCompatibility] [packageName] [serverPath] [configFile] [encoding]
''')
            System.exit(1)
        }
        File srcDir = new File(args[0])
        File destinationDir = new File(args[1])
        File tmpDir = new File(args[2])
        String targetCompatibility = args[3]
        String packageName = args[4].trim()
        String serverpath = args[5]
        String[] configFiles = args[6].tokenize(',') as String[]
        File configFile = new File(args[6])
        String encoding = args[7] ?: 'UTF-8'

        GroovyPageForkedCompiler compiler = new GroovyPageForkedCompiler(srcDir, destinationDir, tmpDir)
        if (configFiles) {
            compiler.configs = configFiles
        }
        if (packageName) {
            compiler.packageName = packageName
        }
        if (encoding) {
            compiler.encoding = encoding
        }
        if (serverpath) {
            compiler.serverpath = serverpath
        }
        if (targetCompatibility) {
            compiler.targetCompatibility = targetCompatibility
        }

        List<File> allFiles = []
        ([srcDir] + generatedViewDirectories()).each { File dir ->
            if (dir.isDirectory()) {
                dir.eachFileRecurse(FileType.FILES) { File f ->
                    if (f.name.endsWith(fileExtension)) {
                        allFiles.add(f)
                    }
                }
            }
        }
        compiler.compile(allFiles)
    }
}
