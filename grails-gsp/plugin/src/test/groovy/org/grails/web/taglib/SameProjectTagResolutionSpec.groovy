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
package org.grails.web.taglib

import java.nio.file.Files
import java.nio.file.Path

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.grails.taglib.index.TagLibraryIndexGenerator
import spock.lang.Specification
import spock.lang.TempDir

/**
 * A tag declared by the project being compiled has to be resolvable while that project compiles, not
 * only once it is packaged.
 *
 * <p>This is what generating the index from source before compilation is for, and it is checked here
 * end to end: an index is generated from a tag library source, placed on a compile classpath, and a
 * controller calling that namespace is compiled against it. Whether the build wires the directory onto
 * compileGroovy is asserted separately, in GenerateTagLibraryIndexTaskSpec; what is proved here is
 * that doing so is sufficient for the compiler to resolve the call.
 */
class SameProjectTagResolutionSpec extends Specification {

    @TempDir
    Path tempDir

    Path indexDir

    def setup() {
        Path taglibSources = Files.createDirectories(tempDir.resolve('grails-app/taglib/demo'))
        taglibSources.resolve('LocalTagLib.groovy').toFile().text = '''
            package demo

            import grails.gsp.TagLib

            @TagLib
            class LocalTagLib {
                static namespace = 'local'
                def greeting(Map attrs) { 'hello' }
            }
        '''
        indexDir = Files.createDirectories(tempDir.resolve('build/generated/grails-taglibs'))
        TagLibraryIndexGenerator.generate(
                tempDir.resolve('grails-app/taglib').toFile(), indexDir.toFile(), true, 'UTF-8')
    }

    void 'the index describes the tag library the project declares'() {
        expect: 'otherwise the compilation below would pass for the wrong reason'
        new File(indexDir.toFile(), 'META-INF/grails/taglibs/demo.LocalTagLib.properties').exists()
    }

    void 'a controller resolves a tag its own project declares'() {
        when: 'the generated index is on the classpath the controller is compiled against'
        byte[] compiled = compileWithIndexOnClasspath('''
            package demo

            import grails.artefact.gsp.TagLibraryInvoker

            class LocalController implements TagLibraryInvoker {
                def index() {
                    local.greeting(name: 'world')
                }
            }
        ''', 'LocalController', 'demo')

        then: 'the call is compiled into an invocation rather than left to be dispatched'
        new String(compiled, 'ISO-8859-1').contains('org/grails/taglib/CompiledTagInvocation')
    }

    void 'without the index on the classpath the same call stays dynamic'() {
        when: 'the index is not visible to the compiler, as before it was generated ahead of time'
        byte[] compiled = compileWithoutIndex('''
            package demo

            import grails.artefact.gsp.TagLibraryInvoker

            class UnresolvedController implements TagLibraryInvoker {
                def index() {
                    local.greeting(name: 'world')
                }
            }
        ''', 'UnresolvedController', 'demo')

        then: 'which is what made a project unable to resolve its own tags'
        !new String(compiled, 'ISO-8859-1').contains('org/grails/taglib/CompiledTagInvocation')
    }

    private byte[] compileWithIndexOnClasspath(String source, String className, String packageName) {
        compile(source, className, packageName, new GroovyClassLoader(
                new URLClassLoader([indexDir.toUri().toURL()] as URL[], getClass().classLoader)))
    }

    private byte[] compileWithoutIndex(String source, String className, String packageName) {
        compile(source, className, packageName, new GroovyClassLoader(getClass().classLoader))
    }

    private byte[] compile(String source, String className, String packageName, GroovyClassLoader loader) {
        Path sourceFile = tempDir.resolve(className + '.groovy')
        sourceFile.toFile().text = source
        Path outputDir = Files.createDirectories(tempDir.resolve('out-' + className))

        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = outputDir.toFile()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration, null, loader)
        unit.addSource(sourceFile.toFile())
        unit.compile()

        Files.readAllBytes(outputDir.resolve(packageName.replace('.', '/')).resolve(className + '.class'))
    }
}
