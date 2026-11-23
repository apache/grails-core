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
package org.grails.gradle.plugin.core

import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.Opcodes
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
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

    void 'generates descriptor-less starter plugin metadata, artefacts, and namespaced plugin.yml'() {
        given:
        File classesDir = new File(tempDir, 'classes').tap { mkdirs() }
        File outDir = new File(tempDir, 'out')
        File configFile = new File(tempDir, 'plugin.yml')
        configFile.text = 'starter:\n  enabled: true\n'

        writeClass(classesDir, 'com/example/HelloService') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/artefact/Artefact;', true)
            av.visit('value', 'Service')
            av.visitEnd()
        }

        Project project = ProjectBuilder.builder().withProjectDir(tempDir).withName('my-starter').build()
        GeneratePluginDescriptorTask task = project.tasks.register('generatePluginDescriptor', GeneratePluginDescriptorTask) {
            GeneratePluginDescriptorTask t ->
                t.classesDirs.from(classesDir)
                t.pluginConfigFile.set(configFile)
                t.projectName.set('my-starter')
                t.projectVersion.set('1.2.3')
                t.pluginOptions.grailsVersion.set('9.0.0 > *')
                t.pluginOptions.loadAfter.set(['core'])
                t.pluginOptions.dependsOn.set(['i18n': '9.0.0 > *'])
                t.outputDirectory.set(outDir)
        }.get()

        when:
        task.generate()

        then:
        new File(outDir, 'META-INF/grails/plugins.imports').text == 'myStarter\n'
        new File(outDir, 'META-INF/grails/plugins/myStarter/plugin.yml').text == 'starter:\n  enabled: true\n'

        def props = loadProperties(new File(outDir, 'META-INF/grails/plugins/myStarter/plugin.properties'))
        props.getProperty('format.version') == '1'
        props.getProperty('name') == 'myStarter'
        props.getProperty('version') == '1.2.3'
        props.getProperty('class') == null
        props.getProperty('metadata.complete') == 'true'
        props.getProperty('grailsVersion') == '9.0.0 > *'
        props.getProperty('loadAfter') == 'core'
        props.getProperty('dependsOn.i18n') == '9.0.0 > *'
        props.getProperty('artefacts.Service') == 'com.example.HelloService'
    }

    void 'generates descriptor for @GrailsPluginDescriptor-annotated class and applies pluginExcludes'() {
        given:
        File classesDir = new File(tempDir, 'classes').tap { mkdirs() }
        File outDir = new File(tempDir, 'out')

        writeClass(classesDir, 'com/example/AuditPlugin') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/plugins/metadata/GrailsPluginDescriptor;', true)
            av.visit('name', 'audit-trail')
            av.visit('version', '4.0.0')
            AnnotationVisitor excludes = av.visitArray('pluginExcludes')
            excludes.visit(null, 'com/example/Internal*')
            excludes.visitEnd()
            av.visitEnd()
        }
        writeClass(classesDir, 'com/example/AuditController') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/artefact/Artefact;', true)
            av.visit('value', 'Controller')
            av.visitEnd()
        }
        writeClass(classesDir, 'com/example/InternalController') { ClassWriter cw ->
            AnnotationVisitor av = cw.visitAnnotation('Lgrails/artefact/Artefact;', true)
            av.visit('value', 'Controller')
            av.visitEnd()
        }

        Project project = ProjectBuilder.builder().withProjectDir(tempDir).withName('audit-plugin').build()
        GeneratePluginDescriptorTask task = project.tasks.register('generatePluginDescriptor', GeneratePluginDescriptorTask) {
            GeneratePluginDescriptorTask t ->
                t.classesDirs.from(classesDir)
                t.projectName.set('audit-plugin')
                t.projectVersion.set('1.0.0')
                t.outputDirectory.set(outDir)
        }.get()

        when:
        task.generate()

        then:
        new File(outDir, 'META-INF/grails/plugins.imports').text == 'auditTrail\n'
        def props = loadProperties(new File(outDir, 'META-INF/grails/plugins/auditTrail/plugin.properties'))
        props.getProperty('name') == 'auditTrail'
        props.getProperty('version') == '4.0.0'
        props.getProperty('class') == 'com.example.AuditPlugin'
        props.getProperty('metadata.complete') == 'true'
        props.getProperty('artefacts.Controller') == 'com.example.AuditController'
    }
}
