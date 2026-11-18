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
package org.grails.compiler.injection.testing

import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Specification
import spock.lang.TempDir

import org.springframework.test.context.ContextConfiguration

/**
 * An {@code @Integration} spec without {@code applicationClass} gets its application class when it is compiled, from
 * the main classes the build puts next to the spec's own output, wherever the build directory is. Compiled here as a
 * build compiles it: a source file in a project directory, compiled into the build's classes against a classpath.
 */
class IntegrationTestApplicationClassSpec extends Specification {

    @TempDir
    File tmp

    void 'the application class is found with the build directory #layout'() {
        given: 'the application, and a plugin subproject with a main class of its own on the classpath first'
        File app = project('checkout/app')
        File build = new File(tmp, buildDir)
        File appClasses = new File(build, 'classes/groovy/main')
        writeApplicationClass(appClasses, 'example.Application')
        File pluginClasses = new File(tmp, 'checkout/build-out/plugin/classes/groovy/main')
        writeApplicationClass(pluginClasses, 'plugin.Application')

        when:
        Class spec = compileSpec(app, new File(build, 'classes/groovy/integrationTest'), [pluginClasses, appClasses])

        then:
        spec.getAnnotation(ContextConfiguration)?.classes()*.name == ['example.Application']

        where:
        layout                              | buildDir
        'inside the project, not build/'    | 'checkout/app/build-parent/build-8070'
        'at the root of the checkout'       | 'checkout/build-out/app'
        'outside the checkout'              | 'elsewhere/app'
    }

    private File project(String path) {
        File dir = new File(tmp, path)
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'build.gradle').text = ''
        dir
    }

    private static Class compileSpec(File projectDir, File targetDir, List<File> classpath) {
        File source = new File(projectDir, 'src/integration-test/groovy/example/SomeSpec.groovy')
        source.parentFile.mkdirs()
        source.text = '''
            package example

            import grails.testing.mixin.integration.Integration
            import spock.lang.Specification

            @Integration
            class SomeSpec extends Specification {
            }
        '''
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = targetDir
        configuration.classpathList = classpath*.path
        GroovyClassLoader loader = new GroovyClassLoader(IntegrationTestApplicationClassSpec.classLoader, configuration)
        CompilationUnit unit = new CompilationUnit(configuration, null, loader)
        unit.addSource(source)
        unit.compile()
        URL[] urls = ([targetDir] + classpath).collect { File dir -> dir.toURI().toURL() } as URL[]
        new URLClassLoader(urls, IntegrationTestApplicationClassSpec.classLoader).loadClass('example.SomeSpec')
    }

    /** A class with a main method extending GrailsAutoConfiguration, which is what the lookup looks for. */
    private static void writeApplicationClass(File classesDir, String className) {
        String internalName = className.replace('.', '/')
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, 'grails/boot/config/GrailsAutoConfiguration', null)
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, '<init>', '()V', null, null)
        init.visitCode()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, 'grails/boot/config/GrailsAutoConfiguration', '<init>', '()V', false)
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(1, 1)
        init.visitEnd()
        MethodVisitor main = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, 'main', '([Ljava/lang/String;)V', null, null)
        main.visitCode()
        main.visitInsn(Opcodes.RETURN)
        main.visitMaxs(0, 1)
        main.visitEnd()
        writer.visitEnd()
        File file = new File(classesDir, "${internalName}.class")
        file.parentFile.mkdirs()
        file.bytes = writer.toByteArray()
    }
}
