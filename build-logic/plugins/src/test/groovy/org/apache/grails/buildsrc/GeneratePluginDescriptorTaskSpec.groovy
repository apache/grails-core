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

import org.gradle.testfixtures.ProjectBuilder

import org.apache.grails.plugin.descriptor.PluginDescriptorWriter
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class GeneratePluginDescriptorTaskSpec extends Specification {

    @TempDir
    File tempDir

    private static void writeClass(File classesDir, String internalName, Closure<?> customize) {
        writeClass(classesDir, internalName, Opcodes.ACC_PUBLIC, customize)
    }

    private static void writeClass(File classesDir, String internalName, int access = Opcodes.ACC_PUBLIC, Closure<?> customize = null) {
        ClassWriter cw = new ClassWriter(0)
        cw.visit(Opcodes.V21, access, internalName, null, 'java/lang/Object', null)
        if ((access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION)) == 0) {
            cw.visitMethod(Opcodes.ACC_PUBLIC, '<init>', '()V', null, null).visitEnd()
        }
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

    void 'regeneration removes renamed artefacts and deleted plugins from the same output directory'() {
        given:
        File classes = new File(tempDir, 'classes')
        File output = new File(tempDir, 'descriptor')
        def task = ProjectBuilder.builder().withProjectDir(tempDir).build().tasks.create('generate', GeneratePluginDescriptorTask)
        task.classesDirs.from(classes)
        task.outputDirectory.set(output)
        task.projectVersion.set('1')
        def controller = { ClassWriter cw ->
            def annotation = cw.visitAnnotation('Lgrails/artefact/Artefact;', true)
            annotation.visit('value', 'Controller')
            annotation.visitEnd()
        }
        writeClass(classes, 'example/OldController', controller)
        writeClass(classes, 'example/AlphaGrailsPlugin')
        writeClass(classes, 'example/BetaGrailsPlugin')
        task.generate()

        when:
        assert new File(classes, 'example/BetaGrailsPlugin.class').delete()
        assert new File(classes, 'example/OldController.class').delete()
        writeClass(classes, 'example/NewController', controller)
        task.generate()

        then:
        new File(output, 'META-INF/grails/plugins.imports').text == 'alpha\n'
        !new File(output, 'META-INF/grails/plugins/beta').exists()
        loadProperties(new File(output, 'META-INF/grails/plugins/alpha/plugin.properties'))['artefacts.Controller'] == 'example.NewController'
    }

    void 'scans legacy *GrailsPlugin classes and @Artefact classes into plugins.imports and plugin.properties'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        File outDir = new File(tempDir, 'out')
        writeClass(classesDir, 'com/example/SampleGrailsPlugin') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/plugins/metadata/CompiledPluginMetadata;', false)
            AnnotationVisitor arr = av.visitArray('pluginExcludes')
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
        def result = PluginDescriptorWriter.scan([classesDir] as Set)
        PluginDescriptorWriter.writeDescriptors(result.plugins, result.artefactsByType, '9.0.0', null, outDir)

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
        def result = PluginDescriptorWriter.scan([classesDir] as Set)
        PluginDescriptorWriter.writeDescriptors(result.plugins, result.artefactsByType, '9.0.0', null, outDir)

        then:
        new File(outDir, 'META-INF/grails/plugins.imports').text == 'metricsStarter\n'
        def props = loadProperties(new File(outDir, 'META-INF/grails/plugins/metricsStarter/plugin.properties'))
        props.getProperty('name') == 'metricsStarter'
        def xml = new groovy.xml.XmlSlurper().parse(new File(outDir, 'META-INF/grails-plugin.xml'))
        xml.@grailsVersion.text() == '9.0.0 > *'
        xml.type.text() == 'com.example.MetricsStarter'
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
        def result = PluginDescriptorWriter.scan([classesDir] as Set)

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
        def result = PluginDescriptorWriter.scan([classesDir] as Set)

        then:
        result.plugins.isEmpty()
    }

    void 'ignores generic @Artefact base classes unless stamped with @GrailsPlugin, while keeping constructor-injected artefacts'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        writeClass(classesDir, 'grails/rest/RestResponderGrailsPlugin')

        ClassWriter restfulBase = new ClassWriter(0)
        restfulBase.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, 'grails/rest/RestfulController', '<T:Ljava/lang/Object;>Ljava/lang/Object;', 'java/lang/Object', null)
        restfulBase.visitMethod(Opcodes.ACC_PUBLIC, '<init>', '(Ljava/lang/Class;)V', null, null).visitEnd()
        AnnotationVisitor av1 = restfulBase.visitAnnotation('Lgrails/artefact/Artefact;', true)
        av1.visit('value', 'Controller')
        av1.visitEnd()
        restfulBase.visitEnd()
        new File(classesDir, 'grails/rest/RestfulController.class').tap { parentFile.mkdirs(); bytes = restfulBase.toByteArray() }

        ClassWriter injectedService = new ClassWriter(0)
        injectedService.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, 'com/example/InjectedService', null, 'java/lang/Object', null)
        injectedService.visitMethod(Opcodes.ACC_PUBLIC, '<init>', '(Ljava/lang/String;)V', null, null).visitEnd()
        AnnotationVisitor av2 = injectedService.visitAnnotation('Lgrails/artefact/Artefact;', true)
        av2.visit('value', 'Service')
        av2.visitEnd()
        injectedService.visitEnd()
        new File(classesDir, 'com/example/InjectedService.class').tap { parentFile.mkdirs(); bytes = injectedService.toByteArray() }

        when:
        def result = PluginDescriptorWriter.scan([classesDir] as Set)

        then:
        result.artefactsByType['Controller'] == null
        result.artefactsByType['Service'] == ['com.example.InjectedService'] as SortedSet
    }

    void 'applies Ant glob patterns with double-star, single-char wildcard, and regex metacharacters'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        File outDir = new File(tempDir, 'out')
        writeClass(classesDir, 'com/example/GlobGrailsPlugin') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/plugins/metadata/CompiledPluginMetadata;', false)
            AnnotationVisitor arr = av.visitArray('pluginExcludes')
            arr.visit(null, '**/internal/**')
            arr.visit(null, 'com/example/V?Controller')
            arr.visit(null, 'com/example/Special$Controller')
            arr.visitEnd()
            av.visitEnd()
        }
        ['com/example/internal/HiddenController',
         'com/example/sub/internal/deep/AlsoHiddenController',
         'com/example/V1Controller',
         'com/example/Special$Controller',
         'com/example/PublicController',
         'com/example/V10Controller'].each { String internalName ->
            writeClass(classesDir, internalName) { ClassWriter cw ->
                AnnotationVisitor av = cw.visitAnnotation('Lgrails/artefact/Artefact;', true)
                av.visit('value', 'Controller')
                av.visitEnd()
            }
        }

        when:
        def result = PluginDescriptorWriter.scan([classesDir] as Set)
        PluginDescriptorWriter.writeDescriptors(result.plugins, result.artefactsByType, '9.0.0', null, outDir)
        def props = loadProperties(new File(outDir, 'META-INF/grails/plugins/glob/plugin.properties'))

        then:
        props.getProperty('artefacts.Controller') == 'com.example.PublicController,com.example.V10Controller'
    }

    void 'fails when two plugin classes resolve to the same logical plugin name'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        File outDir = new File(tempDir, 'out')
        writeClass(classesDir, 'com/one/DuplicateGrailsPlugin')
        writeClass(classesDir, 'com/two/DuplicateGrailsPlugin')

        when:
        def result = PluginDescriptorWriter.scan([classesDir] as Set)
        PluginDescriptorWriter.writeDescriptors(result.plugins, result.artefactsByType, '9.0.0', null, outDir)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('duplicate')
    }

    void 'ignores enums and bare GrailsPlugin classes during scanning'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        writeClass(classesDir, 'com/example/ModeGrailsPlugin', Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM)
        writeClass(classesDir, 'com/example/GrailsPlugin')

        when:
        def result = PluginDescriptorWriter.scan([classesDir] as Set)

        then:
        result.plugins.isEmpty()
    }

    @Unroll
    void "normalizes plugin name '#rawName' to '#expected'"() {
        given:
        File classesDir = new File(tempDir, "classes-${expected}")
        File outDir = new File(tempDir, "out-${expected}")
        writeClass(classesDir, 'com/example/NamedStarter') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/plugins/metadata/GrailsPluginDescriptor;', true)
            av.visit('name', rawName)
            av.visitEnd()
        }

        when:
        def result = PluginDescriptorWriter.scan([classesDir] as Set)
        PluginDescriptorWriter.writeDescriptors(result.plugins, result.artefactsByType, '9.0.0', null, outDir)

        then:
        new File(outDir, 'META-INF/grails/plugins.imports').text == "${expected}\n"

        where:
        rawName                || expected
        'a-b'                  || 'AB'
        'e-commerce'           || 'ECommerce'
        '-foo'                 || 'foo'
        'spring-security-core' || 'springSecurityCore'
    }

    void 'escapes leading whitespace and control characters in generated properties files'() {
        given:
        File classesDir = new File(tempDir, 'classes')
        File outDir = new File(tempDir, 'out')
        writeClass(classesDir, 'com/example/EscapedStarter') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/plugins/metadata/GrailsPluginDescriptor;', true)
            av.visit('name', 'escaped')
            av.visit('grailsVersion', '  9.0.0\t>\n*')
            av.visitEnd()
        }

        when:
        def result = PluginDescriptorWriter.scan([classesDir] as Set)
        PluginDescriptorWriter.writeDescriptors(result.plugins, result.artefactsByType, '9.0.0', null, outDir)
        def props = loadProperties(new File(outDir, 'META-INF/grails/plugins/escaped/plugin.properties'))

        then:
        props.getProperty('grailsVersion') == '9.0.0\t>\n*'
    }
}
