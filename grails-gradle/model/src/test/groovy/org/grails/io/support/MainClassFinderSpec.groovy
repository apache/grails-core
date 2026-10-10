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
package org.grails.io.support

import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Specification
import spock.lang.TempDir

class MainClassFinderSpec extends Specification {

    @TempDir
    File tmp

    void 'the main class is found on the compile classpath when the build directory is not build/'() {
        given:
        File app = project('app')
        File classes = new File(app, 'build-parent/build-8070/classes/groovy/main')
        writeMainClass(classes, 'example.Application')
        URI spec = new File(app, 'src/integration-test/groovy/example/SomeSpec.groovy').toURI()

        expect: 'nothing under build/, so only the classpath finds it'
        MainClassFinder.searchMainClass(spec, false) == null
        MainClassFinder.searchMainClass(spec, [classes], null, false) == 'example.Application'
    }

    void 'a classes directory of another project on the classpath is not searched'() {
        given: 'a plugin subproject inside the application directory, with a main class of its own'
        File app = project('app')
        File plugin = new File(app, 'modules/plugin')
        plugin.mkdirs()
        new File(plugin, 'build.gradle').text = ''
        File pluginClasses = new File(plugin, 'build/classes/groovy/main')
        writeMainClass(pluginClasses, 'plugin.Application')
        File appClasses = new File(app, 'out/classes/groovy/main')
        writeMainClass(appClasses, 'example.Application')
        URI spec = new File(app, 'src/integration-test/groovy/example/SomeSpec.groovy').toURI()

        expect:
        MainClassFinder.searchMainClass(spec, [pluginClasses, appClasses], null, false) == 'example.Application'
        MainClassFinder.searchMainClass(spec, [pluginClasses], null, false) == null
    }

    void 'with the build directory at the root of the checkout, the main class is the one next to the compile output'() {
        given: 'rootProject.layout.buildDirectory.dir(project.name) for the application and a plugin subproject'
        File root = new File(tmp, 'checkout')
        File app = project('checkout/app')
        File appBuild = new File(root, 'build-out/app')
        File pluginBuild = new File(root, 'build-out/plugin')
        writeMainClass(new File(pluginBuild, 'classes/groovy/main'), 'plugin.Application')
        writeMainClass(new File(appBuild, 'classes/groovy/main'), 'example.Application')
        URI spec = new File(app, 'src/integration-test/groovy/example/SomeSpec.groovy').toURI()
        List<File> classpath = [new File(pluginBuild, 'classes/groovy/main'), new File(appBuild, 'classes/groovy/main')]

        expect: 'outside the project, so only the compile output says which build it belongs to'
        MainClassFinder.searchMainClass(spec, classpath, null, false) == null
        MainClassFinder.searchMainClass(spec, classpath, new File(appBuild, 'classes/groovy/integrationTest'), false) ==
                'example.Application'
    }

    void 'with the build directory outside the checkout, the main class is the one next to the compile output'() {
        given:
        File app = project('app')
        File elsewhere = new File(tmp, 'elsewhere/build')
        writeMainClass(new File(elsewhere, 'classes/groovy/main'), 'example.Application')
        URI spec = new File(app, 'src/integration-test/groovy/example/SomeSpec.groovy').toURI()

        expect:
        MainClassFinder.searchMainClass(spec, [new File(elsewhere, 'classes/groovy/main')],
                new File(elsewhere, 'classes/groovy/integrationTest'), false) == 'example.Application'
    }

    void 'a compile output not laid out as <build>/classes/<language>/<sourceSet> does not widen the search'() {
        given: 'a target whose grandparent is the project itself'
        File app = project('app')
        File plugin = new File(app, 'modules/plugin')
        plugin.mkdirs()
        new File(plugin, 'build.gradle').text = ''
        File pluginClasses = new File(plugin, 'build/classes/groovy/main')
        writeMainClass(pluginClasses, 'plugin.Application')
        URI spec = new File(app, 'src/integration-test/groovy/example/SomeSpec.groovy').toURI()

        expect:
        MainClassFinder.searchMainClass(spec, [pluginClasses], new File(app, 'out/integ'), false) == null
    }

    private File project(String name) {
        File dir = new File(tmp, name)
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'build.gradle').text = ''
        dir
    }

    /** A class with a main method extending GrailsAutoConfiguration, which is what the finder looks for. */
    private static void writeMainClass(File classesDir, String className) {
        String internalName = className.replace('.', '/')
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, 'grails/boot/config/GrailsAutoConfiguration', null)
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
