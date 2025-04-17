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
package org.grails.web.pages

import groovy.io.FileType
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilerConfiguration
import org.grails.gsp.compiler.GroovyPageCompiler

/**
 * A Forked Compiler Task for use (typically by Gradle)
 *
 * @author David Estes
 */

@CompileStatic
class GroovyPageCompilerForkTask {

    private static final String fileExtension = '.gsp'

    @Delegate
    CompilerConfiguration configuration = new CompilerConfiguration()

    String packageName = ""
    File sourceDir
    File destDir
    File tmpdir
    String serverpath
    String encoding
    String targetCompatibility
    String[] configs

    GroovyPageCompilerForkTask(File sourceDir, File destDir, File tmpdir) {
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
        return compiler
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
    }

    static void main(String[] args) {
        run(args)
    }

    static void run(String[] args) {
        if (args.length != 8) {
            System.err.println("Invalid arguments: [${args.join(',')}]")
            System.err.println("""
Usage: java -cp CLASSPATH GroovyPageCompilerForkTask [srcDir] [destDir] [tmpDir] [targetCompatibility] [packageName] [serverPath] [configFile] [encoding]
""")
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

        GroovyPageCompilerForkTask compiler = new GroovyPageCompilerForkTask(srcDir, destinationDir, tmpDir)
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
        srcDir.eachFileRecurse(FileType.FILES) { File f ->
            if (f.name.endsWith(fileExtension)) {
                allFiles.add(f)
            }
        }
        compiler.compile(allFiles)
    }
}