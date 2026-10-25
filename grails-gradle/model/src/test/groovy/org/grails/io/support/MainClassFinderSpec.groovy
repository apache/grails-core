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
import groovyjarjarasm.asm.Opcodes

import spock.lang.Specification
import spock.lang.TempDir

class MainClassFinderSpec extends Specification {

    private static final int PUBLIC_STATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
    private static final int PRIVATE_STATIC = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC

    @TempDir
    File tmp

    void cleanup() {
        MainClassFinder.mainClasses.clear()
    }

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

    void 'findMainClass finds a nested Grails application and its class file'() {
        given:
        File application = writeClass(tmp, 'example/nested/Application')
        new File(tmp, 'notes.txt').text = 'not bytecode'
        writeClass(tmp, 'example/PlainMain', 'java/lang/Object')

        when:
        def holder = MainClassFinder.findMainClass(tmp, false)

        then:
        holder.className == 'example.nested.Application'
        holder.classFile == application
    }

    void 'findMainClass rejects a candidate with superclass #superName access #access and signature #descriptor'() {
        given:
        writeClass(tmp, 'example/Application', superName, access, descriptor)

        expect:
        MainClassFinder.findMainClass(tmp, false) == null

        where:
        superName                                  | access             | descriptor
        'java/lang/Object'                          | PUBLIC_STATIC      | '([Ljava/lang/String;)V'
        'grails/boot/config/GrailsAutoConfiguration' | PRIVATE_STATIC     | '([Ljava/lang/String;)V'
        'grails/boot/config/GrailsAutoConfiguration' | Opcodes.ACC_PUBLIC | '([Ljava/lang/String;)V'
        'grails/boot/config/GrailsAutoConfiguration' | PUBLIC_STATIC      | '()V'
    }

    void 'findMainClass ignores hidden directories and classes without a main method'() {
        given:
        writeClass(tmp, '.hidden/Application')
        writeClass(tmp, 'example/NoMain', 'grails/boot/config/GrailsAutoConfiguration', 0, null)

        expect:
        MainClassFinder.findMainClass(tmp, false) == null
    }

    void 'findMainClass returns null for missing and empty directories'() {
        expect:
        MainClassFinder.findMainClass(new File(tmp, 'missing'), false) == null
        MainClassFinder.findMainClass(tmp, false) == null
    }

    void 'findMainClass rejects a file as the search root'() {
        given:
        File file = new File(tmp, 'file.txt')
        file.text = 'content'

        when:
        MainClassFinder.findMainClass(file, false)

        then:
        def error = thrown(IllegalArgumentException)
        error.message.contains('Invalid root folder')
    }

    void 'findMainClass can bypass its cache and rescan after a cached class is deleted'() {
        given:
        File application = writeClass(tmp, 'example/Application')
        def original = MainClassFinder.findMainClass(tmp)

        when:
        writeClass(tmp, 'example/Application', 'java/lang/Object')

        then:
        MainClassFinder.findMainClass(tmp).is(original)
        MainClassFinder.findMainClass(tmp, false) == null

        when:
        assert application.delete()
        File replacement = writeClass(tmp, 'example/Replacement')
        def result = MainClassFinder.findMainClass(tmp)

        then:
        result.className == 'example.Replacement'
        result.classFile == replacement
    }

    void 'searchMainClass discovers #classesPath using the #marker project marker'() {
        given:
        if (marker == 'grails-app') {
            assert new File(tmp, marker).mkdirs()
        } else {
            new File(tmp, marker).text = ''
        }
        File source = new File(tmp, 'src/main/groovy/example/Application.groovy')
        assert source.parentFile.mkdirs()
        source.text = 'package example'
        File classes = new File(tmp, classesPath)
        writeClass(classes, 'example/Application')

        expect:
        MainClassFinder.searchMainClass(source.toURI(), false) == 'example.Application'

        where:
        classesPath                 | marker
        'build/classes/main'        | 'build.gradle'
        'build/classes/groovy/main' | 'grails-app'
    }

    void 'searchMainClass caches by URI and invalidates deleted class files'() {
        given:
        new File(tmp, 'build.gradle').text = ''
        File source = new File(tmp, 'Application.groovy')
        source.text = 'package example'
        File classes = new File(tmp, 'build/classes/main')
        File application = writeClass(classes, 'example/Application')

        expect:
        MainClassFinder.searchMainClass(source.toURI()) == 'example.Application'
        MainClassFinder.searchMainClass(source.toURI()) == 'example.Application'

        when:
        writeClass(classes, 'example/Application', 'java/lang/Object')

        then:
        MainClassFinder.searchMainClass(source.toURI(), false) == null
        MainClassFinder.searchMainClass(source.toURI()) == 'example.Application'

        when:
        assert application.delete()
        writeClass(classes, 'example/Replacement')

        then:
        MainClassFinder.searchMainClass(source.toURI()) == 'example.Replacement'
    }

    void 'searchMainClass returns null for absent projects and unsupported URIs'() {
        expect:
        MainClassFinder.searchMainClass(null, false) == null
        MainClassFinder.searchMainClass(new URI('https://example.invalid/Application.groovy'), false) == null
        MainClassFinder.searchMainClass(new File(tmp, 'missing.groovy').toURI(), false) == null
    }

    private File project(String name) {
        File dir = new File(tmp, name)
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'build.gradle').text = ''
        dir
    }

    /** A class with a main method extending GrailsAutoConfiguration, which is what the finder looks for. */
    private static void writeMainClass(File classesDir, String className) {
        writeClass(classesDir, className.replace('.', '/'))
    }

    private static File writeClass(File root, String name,
                                   String superName = 'grails/boot/config/GrailsAutoConfiguration',
                                   int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                                   String descriptor = '([Ljava/lang/String;)V') {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superName, null)
        if (descriptor != null) {
            def method = writer.visitMethod(access, 'main', descriptor, null, null)
            method.visitCode()
            method.visitInsn(Opcodes.RETURN)
            method.visitMaxs(0, 2)
            method.visitEnd()
        }
        writer.visitEnd()
        File file = new File(root, name + '.class')
        assert file.parentFile.mkdirs() || file.parentFile.isDirectory()
        file.bytes = writer.toByteArray()
        file
    }
}
