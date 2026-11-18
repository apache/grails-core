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
package org.grails.taglib.index

import java.nio.file.Files
import java.nio.file.Path

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Two producers writing the index would put descriptors both in the directory the build generates and
 * in the class output. Both reach the classpath and both are packaged, so a tag library renamed or
 * deleted between builds could keep being described by the copy written class by class, which nothing
 * cleans. There is one producer per build.
 */
class SingleIndexProducerSpec extends Specification {

    private static final String TAG_LIB = '''
        import grails.gsp.TagLib
        @TagLib
        class ProducerCheckTagLib {
            static namespace = 'producercheck'
            def hello(Map attrs) { }
        }
    '''

    @TempDir
    Path tempDir

    void 'a tag library describes itself when nothing else has'() {
        given: 'compiling outside the Grails Gradle plugin, as a plain Groovy compilation does'
        Path output = compile(false)

        expect:
        descriptor(output, 'ProducerCheckTagLib').isFile()
        manifest(output).isFile()
    }

    void 'a tag library writes no descriptor when the build writes the index'() {
        given: 'the build described it from source before compiling it'
        Path output = compile(true)

        expect: 'nothing is written into the class output to be merged with it or packaged beside it'
        !descriptor(output, 'ProducerCheckTagLib').isFile()
        !manifest(output).isFile()
    }

    void 'nothing is written even for a tag library the index on the classpath does not name'() {
        given: 'a build that writes the index reads the source of what a tag library refers to, so it'
        Path output = compileWithIndexDescribing('some.other.TagLib', 'other', 'somethingElse')

        expect: 'describes all of them, and a copy here could only go stale beside it'
        !descriptor(output, 'ProducerCheckTagLib').isFile()
    }

    private static File descriptor(Path output, String className) {
        output.resolve(TagLibraryIndex.INDEX_LOCATION + className + '.properties').toFile()
    }

    private static File manifest(Path output) {
        output.resolve(TagLibraryIndex.INDEX_LOCATION + 'index.properties').toFile()
    }

    /**
     * @param described whether the build already described this tag library
     * @return the class output directory
     */
    private Path compile(boolean described) {
        described ? compileWithIndexDescribing('ProducerCheckTagLib', 'producercheck', 'hello') :
                compileAgainst(null, 'none')
    }

    /**
     * Compiles against a generated index that describes the given tag library, which is how the build
     * presents what it managed to describe before compilation.
     */
    private Path compileWithIndexDescribing(String className, String namespace, String tag) {
        Path generated = Files.createDirectories(tempDir.resolve('generated-' + className))
        Path indexDir = Files.createDirectories(generated.resolve(TagLibraryIndex.INDEX_LOCATION))
        indexDir.resolve('compile-settings.properties').toFile().text = 'strictTags=false\n'
        indexDir.resolve('index.properties').toFile().text = "${className}=\n"
        indexDir.resolve(className + '.properties').toFile().text =
                "version=${TagLibraryIndex.FORMAT_VERSION}\nclass=${className}\n" +
                        "namespace=${namespace}\ntags=${tag}\n"
        compileAgainst(generated, className)
    }

    private Path compileAgainst(Path generatedIndex, String label) {
        Path sourceFile = tempDir.resolve('ProducerCheckTagLib.groovy')
        sourceFile.toFile().text = TAG_LIB
        Path outputDir = Files.createDirectories(tempDir.resolve('classes-' + label))

        ClassLoader parent = generatedIndex != null ?
                new URLClassLoader([generatedIndex.toUri().toURL()] as URL[], getClass().classLoader) :
                getClass().classLoader

        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = outputDir.toFile()
        configuration.parameters = true
        CompilationUnit unit = new CompilationUnit(configuration, null,
                new GroovyClassLoader(parent, configuration))
        unit.addSource(sourceFile.toFile())
        unit.compile()

        outputDir
    }
}
