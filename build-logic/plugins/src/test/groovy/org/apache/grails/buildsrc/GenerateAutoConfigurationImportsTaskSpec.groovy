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
package org.apache.grails.buildsrc

import groovy.io.FileType
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import spock.lang.TempDir

import spock.lang.Specification

/**
 * Compiles small fixture sources to real {@code .class} files (the same way {@code groovyc} would)
 * and scans them, rather than mocking the class-file reading {@link GenerateAutoConfigurationImportsTask#scan}
 * depends on.
 */
class GenerateAutoConfigurationImportsTaskSpec extends Specification {

    @TempDir
    File tempDir

    private static void compileFixtures(File srcDir, File destDir) {
        CompilerConfiguration config = new CompilerConfiguration()
        config.targetDirectory = destDir
        CompilationUnit unit = new CompilationUnit(config)
        srcDir.eachFileRecurse(FileType.FILES) { File source ->
            if (source.name.endsWith('.groovy')) {
                unit.addSource(source)
            }
        }
        unit.compile(Phases.OUTPUT)
    }

    def "finds a top-level class annotated @AutoConfiguration"() {
        given:
        File srcDir = new File(tempDir, 'src')
        srcDir.mkdirs()
        new File(srcDir, 'RealAutoConfig.groovy').text = '''
            package fixture

            import org.springframework.boot.autoconfigure.AutoConfiguration

            @AutoConfiguration
            class RealAutoConfig {
            }
        '''
        File destDir = new File(tempDir, 'classes')
        destDir.mkdirs()
        compileFixtures(srcDir, destDir)

        when:
        SortedSet<String> found = GenerateAutoConfigurationImportsTask.scan([destDir] as Set)

        then:
        found == ['fixture.RealAutoConfig'] as SortedSet
    }

    def "ignores classes with no @AutoConfiguration annotation"() {
        given:
        File srcDir = new File(tempDir, 'src')
        srcDir.mkdirs()
        new File(srcDir, 'PlainClass.groovy').text = '''
            package fixture

            class PlainClass {
            }
        '''
        File destDir = new File(tempDir, 'classes')
        destDir.mkdirs()
        compileFixtures(srcDir, destDir)

        when:
        SortedSet<String> found = GenerateAutoConfigurationImportsTask.scan([destDir] as Set)

        then:
        found.isEmpty()
    }

    def "ignores nested/inner classes even when annotated"() {
        given: "an @AutoConfiguration nested inside a plain outer class"
        File srcDir = new File(tempDir, 'src')
        srcDir.mkdirs()
        new File(srcDir, 'Outer.groovy').text = '''
            package fixture

            import org.springframework.boot.autoconfigure.AutoConfiguration

            class Outer {
                @AutoConfiguration
                static class Nested {
                }
            }
        '''
        File destDir = new File(tempDir, 'classes')
        destDir.mkdirs()
        compileFixtures(srcDir, destDir)

        expect: "compilation really did produce a \$-named class file, proving this isn't a vacuous pass"
        new File(destDir, 'fixture/Outer$Nested.class').exists()

        when:
        SortedSet<String> found = GenerateAutoConfigurationImportsTask.scan([destDir] as Set)

        then:
        found.isEmpty()
    }

    def "returns an empty set for a nonexistent classes directory"() {
        expect:
        GenerateAutoConfigurationImportsTask.scan([new File(tempDir, 'does-not-exist')] as Set).isEmpty()
    }

    def "detects an @AutoConfiguration whose supertype is not resolvable"() {
        given: "a compiled class whose superclass is deliberately removed afterwards - the shape that " +
                "used to be warned about and silently dropped from the generated file"
        File srcDir = new File(tempDir, 'src')
        srcDir.mkdirs()
        new File(srcDir, 'Missing.groovy').text = 'class Missing {}'
        new File(srcDir, 'RealAutoConfig.groovy').text = '''
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @AutoConfiguration
            class RealAutoConfig extends Missing {
            }
        '''
        File destDir = new File(tempDir, 'classes')
        destDir.mkdirs()
        compileFixtures(srcDir, destDir)
        assert new File(destDir, 'Missing.class').delete()

        expect: "reading the annotation table needs nothing but the class file itself"
        GenerateAutoConfigurationImportsTask.scan([destDir] as Set) == ['RealAutoConfig'] as SortedSet
    }

    def "finds a class whose package segment begins with the class-file extension"() {
        given: "a package named so that stripping '.class' by first occurrence would mangle the name"
        File srcDir = new File(tempDir, 'src')
        new File(srcDir, 'fixture/classloading').mkdirs()
        new File(srcDir, 'fixture/classloading/ScannedAutoConfig.groovy').text = '''
            package fixture.classloading

            import org.springframework.boot.autoconfigure.AutoConfiguration

            @AutoConfiguration
            class ScannedAutoConfig {
            }
        '''
        File destDir = new File(tempDir, 'classes')
        destDir.mkdirs()
        compileFixtures(srcDir, destDir)

        when:
        SortedSet<String> found = GenerateAutoConfigurationImportsTask.scan([destDir] as Set)

        then:
        found == ['fixture.classloading.ScannedAutoConfig'] as SortedSet
    }

    def "reports a hand-maintained imports file alongside the generated one"() {
        given: "a module resource directory that already contains the file this task writes"
        File resources = new File(tempDir, 'resources')
        File handMaintained = new File(resources, GenerateAutoConfigurationImportsTask.IMPORTS_RESOURCE_PATH)
        handMaintained.parentFile.mkdirs()
        handMaintained.text = 'fixture.HandMaintainedAutoConfig\n'

        and: "a second one that does not"
        File emptyResources = new File(tempDir, 'other-resources')
        emptyResources.mkdirs()

        expect:
        GenerateAutoConfigurationImportsTask.handMaintainedImportsFiles(
                [resources, emptyResources] as Set) == [handMaintained]

        and:
        GenerateAutoConfigurationImportsTask.handMaintainedImportsFiles([emptyResources] as Set).isEmpty()
    }

}
