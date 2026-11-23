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

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import spock.lang.Specification
import spock.lang.TempDir

class GeneratePluginDescriptorTaskSpec extends Specification {

    @TempDir
    File tempDir

    private static void writeClass(File classesDir, String internalName, Closure<?> customize) {
        writeClass(classesDir, internalName, Opcodes.ACC_PUBLIC, customize)
    }

    private static void writeClass(File classesDir, String internalName, int access = Opcodes.ACC_PUBLIC, Closure<?> customize = null) {
        ClassWriter cw = new ClassWriter(0)
        cw.visit(Opcodes.V21, access, internalName, null, 'java/lang/Object', null)
        if (customize) {
            customize.call(cw)
        }
        cw.visitEnd()
        File classFile = new File(classesDir, "${internalName}.class")
        classFile.parentFile.mkdirs()
        classFile.bytes = cw.toByteArray()
    }

    private static Properties loadProperties(File file) {
        Properties props = new Properties()
        file.withInputStream { props.load(it) }
        props
    }

    void 'scans legacy *GrailsPlugin classes and @Artefact classes into plugins.imports and plugin.properties'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        File outDir = new File(tempDir, 'out')
        writeClass(classesDir, 'com/example/SampleGrailsPlugin') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/plugins/metadata/PluginExcludes;', false)
            AnnotationVisitor arr = av.visitArray('value')
            arr.visit(null, 'com/example/Excluded*')
            arr.visitEnd()
            av.visitEnd()
        }
        writeClass(classesDir, 'com/example/BookController') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/artefact/Artefact;', true)
            av.visit('value', 'Controller')
            av.visitEnd()
        }
        writeClass(classesDir, 'com/example/ExcludedController') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/artefact/Artefact;', true)
            av.visit('value', 'Controller')
            av.visitEnd()
        }
        writeClass(classesDir, 'com/example/AbstractBaseGrailsPlugin', Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)

        when:
        def result = GeneratePluginDescriptorTask.scan([classesDir] as Set)
        GeneratePluginDescriptorTask.writeDescriptors(result, '9.0.0', outDir)

        then:
        new File(outDir, 'META-INF/grails/plugins.imports').text == 'sample\n'
        def props = loadProperties(new File(outDir, 'META-INF/grails/plugins/sample/plugin.properties'))
        props.getProperty('format.version') == '1'
        props.getProperty('name') == 'sample'
        props.getProperty('version') == '9.0.0'
        props.getProperty('class') == 'com.example.SampleGrailsPlugin'
        props.getProperty('metadata.complete') == 'false'
        props.getProperty('artefacts.Controller') == 'com.example.BookController'
    }

    void 'scans @GrailsPluginDescriptor-annotated class without GrailsPlugin suffix and records complete metadata'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        File outDir = new File(tempDir, 'out')
        writeClass(classesDir, 'com/example/MetricsStarter') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/plugins/metadata/GrailsPluginDescriptor;', true)
            av.visit('name', 'metrics-starter')
            av.visit('version', '2.3.4')
            av.visit('grailsVersion', '9.0.0 > *')
            AnnotationVisitor after = av.visitArray('loadAfter')
            after.visit(null, 'core')
            after.visitEnd()
            AnnotationVisitor deps = av.visitArray('dependsOn')
            deps.visit(null, 'i18n:9.0.0 > *')
            deps.visitEnd()
            av.visitEnd()
        }

        when:
        def result = GeneratePluginDescriptorTask.scan([classesDir] as Set)
        GeneratePluginDescriptorTask.writeDescriptors(result, '9.0.0', outDir)

        then:
        new File(outDir, 'META-INF/grails/plugins.imports').text == 'metricsStarter\n'
        def props = loadProperties(new File(outDir, 'META-INF/grails/plugins/metricsStarter/plugin.properties'))
        props.getProperty('name') == 'metricsStarter'
        props.getProperty('version') == '2.3.4'
        props.getProperty('class') == 'com.example.MetricsStarter'
        props.getProperty('grailsVersion') == '9.0.0 > *'
        props.getProperty('loadAfter') == 'core'
        props.getProperty('dependsOn.i18n') == '9.0.0 > *'
        props.getProperty('metadata.complete') == 'true'
    }

    void 'produces no descriptors when no plugin classes are present'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        writeClass(classesDir, 'com/example/PlainLibraryClass')

        when:
        def result = GeneratePluginDescriptorTask.scan([classesDir] as Set)

        then:
        result.plugins.isEmpty()
    }

    void 'ignores framework GrailsPlugin wrapper implementations such as DefaultGrailsPlugin and BinaryGrailsPlugin'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        ClassWriter cw1 = new ClassWriter(0)
        cw1.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, 'org/grails/plugins/DefaultGrailsPlugin', null, 'org/grails/plugins/AbstractGrailsPlugin', null)
        cw1.visitEnd()
        new File(classesDir, 'org/grails/plugins/DefaultGrailsPlugin.class').tap { parentFile.mkdirs(); bytes = cw1.toByteArray() }

        ClassWriter cw2 = new ClassWriter(0)
        cw2.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, 'org/grails/plugins/BinaryGrailsPlugin', null, 'org/grails/plugins/DefaultGrailsPlugin', null)
        cw2.visitEnd()
        new File(classesDir, 'org/grails/plugins/BinaryGrailsPlugin.class').tap { parentFile.mkdirs(); bytes = cw2.toByteArray() }

        when:
        def result = GeneratePluginDescriptorTask.scan([classesDir] as Set)

        then:
        result.plugins.isEmpty()
    }
}
