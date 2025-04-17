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

import org.apache.tools.ant.BuildException
import org.apache.tools.ant.DirectoryScanner
import org.apache.tools.ant.taskdefs.MatchingTask
import org.apache.tools.ant.types.Path
import org.apache.tools.ant.types.Reference
import org.codehaus.groovy.control.CompilerConfiguration
import org.grails.gsp.compiler.GroovyPageCompiler

/**
 * An Ant task used for compiling GSP sources. Example:
 *
 * <pre><code>
 *    <gspc srcdir="grails-app/views"
 *          destdir="target-classes"
 *          classpathref="my.classpath"
 *          />
 * </code></pre>
 *
 * @author Graeme Rocher
 * @since 1.2
 */
class GroovyPageCompilerTask extends MatchingTask {

    File destdir
    Path classpath
    Path configs
    File srcdir
    File tmpdir
    String packagename = 'default'
    String serverpath
    String encoding

    boolean verbose

    @Delegate GroovyPageCompiler compiler = new GroovyPageCompiler()

    /**
     * Adds a path to the classpath.
     *
     * @return a class path to be configured
     */
    Path createClasspath() {
        if (classpath == null) {
            classpath = new Path(getProject())
        }
        return classpath.createPath()
    }

    /**
     * Adds a path to the classpath.
     *
     * @return a class path to be configured
     */
    Path createConfigs() {
        if (configs == null) {
            configs = new Path(getProject())
        }
        return configs.createPath()
    }


    /**
     * Adds a reference to a classpath defined elsewhere.
     *
     * @param r a reference to a classpath
     */
    void setClasspathRef(Reference r) {
        createClasspath().setRefid(r)
    }

    void execute() {
        def compiler = new GroovyPageCompiler()
        if (classpath) {
            CompilerConfiguration config = new CompilerConfiguration()
            config.classpath = classpath.toString()
            compiler.compilerConfig = config
        }

        if(configs) {
            compiler.configs = configs.list()
        }
        if (!destdir || !destdir.exists()) {
            throw new BuildException("destination [${destdir}] directory doesn't exist or is not set!", getLocation())
        }

        compiler.targetDir = destdir

        if (!srcdir || !srcdir.exists()) {
            throw new BuildException("source [${srcdir}] directory doesn't exist or is not set!", getLocation())
        }

        compiler.viewsDir = srcdir
        DirectoryScanner scanner = super.getDirectoryScanner(srcdir)
        scanner.includes = ['**/*.gsp'] as String[]
        scanner.scan()

        List gspFiles = scanner.includedFiles.collect { new File("${srcdir}/$it") }
        compiler.srcFiles = gspFiles

        int gspCount = gspFiles?.size()
        if (gspCount) {
            log("Compiling ${gspCount} GSP file${gspCount>1?'s':''} for package [${packagename}]")
        }

        if (tmpdir) {
            compiler.generatedGroovyPagesDirectory = tmpdir
        }

        if (packagename) {
            compiler.packagePrefix = packagename
        }
        if (serverpath) {
            compiler.viewPrefix=serverpath
        }
        if (encoding) {
            compiler.encoding = encoding
        }

        compiler.compile()
        compiler = null
    }
}
