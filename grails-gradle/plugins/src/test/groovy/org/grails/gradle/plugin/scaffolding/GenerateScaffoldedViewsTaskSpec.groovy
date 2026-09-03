/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.scaffolding

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.Type
import spock.lang.Specification
import spock.lang.TempDir

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class GenerateScaffoldedViewsTaskSpec extends Specification {

    @TempDir
    File projectDir

    private File classesDir
    private File templateJar

    void setup() {
        classesDir = new File(projectDir, 'classes')
        classesDir.mkdirs()
        templateJar = new File(projectDir, 'templates.jar')
        writeTemplateJar(templateJar, [
                index : 'list of ${propertyName} for ${className}',
                create: 'create ${className}',
                edit  : 'edit ${className}',
                show  : 'show ${className}'])
    }

    /** A jar shaped like the one the scaffolding plugin publishes. */
    private void writeTemplateJar(File jar, Map<String, String> templates) {
        new JarOutputStream(jar.newOutputStream()).withCloseable { JarOutputStream out ->
            templates.each { String name, String body ->
                out.putNextEntry(new JarEntry("META-INF/templates/scaffolding/${name}.gsp"))
                out.write(body.bytes)
                out.closeEntry()
            }
        }
    }

    /**
     * Writes a class carrying {@code @Scaffold}, so the task reads a real annotation rather than a
     * stand-in for one.
     */
    private void writeController(String controllerName, String domainClassName) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/${controllerName}", null,
                'java/lang/Object', null)
        AnnotationVisitor annotation = writer.visitAnnotation(
                'Lgrails/plugin/scaffolding/annotation/Scaffold;', true)
        annotation.visit('value', Type.getObjectType("com/example/${domainClassName}"))
        annotation.visitEnd()
        writer.visitEnd()
        File target = new File(classesDir, "com/example/${controllerName}.class")
        target.parentFile.mkdirs()
        target.bytes = writer.toByteArray()
    }

    /**
     * Writes a class shaped like {@code @Scaffold(RestfulServiceController<Domain>)} after
     * ScaffoldingControllerInjector has run: the domain it extracted from the type argument is
     * written into {@code domain}, and {@code value} is left naming the class to extend. The two
     * are emitted in the order javac and groovyc actually emit them, domain first, which is what
     * makes reading whichever came last name the superclass as the domain.
     */
    private void writeSuperclassParameterizedController(String controllerName, String domainClassName) {
        String superclass = 'grails/plugin/scaffolding/RestfulServiceController'
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/${controllerName}",
                "L${superclass}<Lcom/example/${domainClassName};>;", superclass, null)
        AnnotationVisitor annotation = writer.visitAnnotation(
                'Lgrails/plugin/scaffolding/annotation/Scaffold;', true)
        annotation.visit('domain', Type.getObjectType("com/example/${domainClassName}"))
        annotation.visit('value', Type.getObjectType(superclass))
        annotation.visitEnd()
        writer.visitEnd()
        File target = new File(classesDir, "com/example/${controllerName}.class")
        target.parentFile.mkdirs()
        target.bytes = writer.toByteArray()
    }

    /**
     * Writes a scaffolded controller into a package of its own, so two controllers of the same
     * simple name can be put in one classes directory the way an application does it.
     */
    private void writeControllerIn(String packagePath, String controllerName, String domainInternalName) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "${packagePath}/${controllerName}", null,
                'java/lang/Object', null)
        AnnotationVisitor annotation = writer.visitAnnotation(
                'Lgrails/plugin/scaffolding/annotation/Scaffold;', true)
        annotation.visit('domain', Type.getObjectType(domainInternalName))
        annotation.visitEnd()
        writer.visitEnd()
        File target = new File(classesDir, "${packagePath}/${controllerName}.class")
        target.parentFile.mkdirs()
        target.bytes = writer.toByteArray()
    }

    private void writePlainController(String controllerName) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/${controllerName}", null,
                'java/lang/Object', null)
        writer.visitEnd()
        File target = new File(classesDir, "com/example/${controllerName}.class")
        target.parentFile.mkdirs()
        target.bytes = writer.toByteArray()
    }

    private GenerateScaffoldedViewsTask task(List<File> overrides = [], List<File> views = []) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.tasks.register('generateScaffoldedViews', GenerateScaffoldedViewsTask) {
            GenerateScaffoldedViewsTask it ->
                it.classesDirs.from(classesDir)
                it.templateClasspath.from(templateJar)
                it.templateOverrides.from(overrides)
                it.applicationViews.from(views)
                it.outputDirectory.set(new File(projectDir, 'out'))
        }
        project.tasks.named('generateScaffoldedViews', GenerateScaffoldedViewsTask).get()
    }

    private File generated(GenerateScaffoldedViewsTask task, String path) {
        new File(task.outputDirectory.get().asFile, path)
    }

    void 'a scaffolded controller gets the full set of views'() {
        given:
            writeController('UserController', 'User')
            def task = task()

        when:
            task.generate()

        then:
            ['index', 'create', 'edit', 'show'].every { generated(task, "user/${it}.gsp").exists() }
    }

    void 'the naming the templates read is substituted'() {
        given:
            writeController('UserController', 'User')
            def task = task()

        when:
            task.generate()

        then:
            generated(task, 'user/index.gsp').text == 'list of user for User'
    }

    void 'the qualified domain class is substituted, so a template can declare the type of its model'() {
        given:
            writeTemplateJar(templateJar, [
                    index : 'model="List<${fullName}> ${propertyName}List" in ${packageName}',
                    create: 'create ${className}',
                    edit  : 'edit ${className}',
                    show  : 'show ${className}'])
            writeController('UserController', 'User')
            def task = task()

        when:
            task.generate()

        then: 'the simple name alone would not resolve from the generated page'
            generated(task, 'user/index.gsp').text == 'model="List<com.example.User> userList" in com.example'
    }

    void 'a controller without the annotation is left alone'() {
        given:
            writePlainController('PlainController')
            def task = task()

        when:
            task.generate()

        then:
            !generated(task, 'plain').exists()
    }

    void 'the domain class named by the annotation drives the naming, not the controller'() {
        given: 'a controller whose name does not match the domain it scaffolds'
            writeController('AccountController', 'Person')
            def task = task()

        when:
            task.generate()

        then:
            generated(task, 'account/index.gsp').text == 'list of person for Person'
    }

    void 'an application template overrides the one a plugin contributes'() {
        given:
            writeController('UserController', 'User')
            File overrides = new File(projectDir, 'templates')
            overrides.mkdirs()
            File custom = new File(overrides, 'index.gsp')
            custom.text = 'custom ${className}'
            def task = task([custom])

        when:
            task.generate()

        then:
            generated(task, 'user/index.gsp').text == 'custom User'
    }

    void 'a view the application declares is not generated over'() {
        given: 'the application writes its own index page'
            writeController('UserController', 'User')
            File views = new File(projectDir, 'grails-app/views/user')
            views.mkdirs()
            File declared = new File(views, 'index.gsp')
            declared.text = 'hand written'
            def task = task([], [declared])

        when:
            task.generate()

        then: 'the runtime prefers the declared page, so generating one would only shadow it'
            !generated(task, 'user/index.gsp').exists()

        and: 'the views it does not declare are still generated'
            generated(task, 'user/create.gsp').exists()
    }

    void 'a stale view from a previous run does not survive'() {
        given:
            writeController('UserController', 'User')
            def task = task()
            task.generate()
            File stale = generated(task, 'gone/index.gsp')
            stale.parentFile.mkdirs()
            stale.text = 'stale'

        when:
            task.generate()

        then:
            !stale.exists()
    }

    void 'the domain attribute is read in preference to the class the annotation extends'() {
        given: 'a controller written as @Scaffold(RestfulServiceController<User>)'
            writeSuperclassParameterizedController('UserController', 'User')
            def task = task()

        when:
            task.generate()

        then: 'the views are named for the domain, not for the class the annotation names'
            generated(task, 'user/index.gsp').text == 'list of user for User'
            !generated(task, 'restfulServiceController/index.gsp').exists()
    }

    void 'a controller that names its superclass still yields a type a view can declare'() {
        given:
            writeTemplateJar(templateJar, [
                    index : 'model="List<${fullName}> ${propertyName}List" in ${packageName}',
                    create: 'create ${className}',
                    edit  : 'edit ${className}',
                    show  : 'show ${className}'])
            writeSuperclassParameterizedController('UserController', 'User')
            def task = task()

        when:
            task.generate()

        then:
            generated(task, 'user/index.gsp').text == 'model="List<com.example.User> userList" in com.example'
    }

    void 'two controllers of one name scaffolding different domains get no views at all'() {
        given: 'com.example.UserController and com.example.community.UserController'
            writeControllerIn('com/example', 'UserController', 'com/example/User')
            writeControllerIn('com/example/community', 'UserController', 'com/example/community/User')
            def task = task()

        when:
            task.generate()

        then: 'neither domain is guessed at - the resolver expands a template per request instead'
            !generated(task, 'user/index.gsp').exists()
            !generated(task, 'user/edit.gsp').exists()
    }

    void 'two controllers of one name scaffolding the same domain are precompiled'() {
        given: 'the one page they would share serves both, so there is nothing to be ambiguous about'
            writeControllerIn('com/example', 'UserController', 'com/example/User')
            writeControllerIn('com/example/admin', 'UserController', 'com/example/User')
            def task = task()

        when:
            task.generate()

        then:
            generated(task, 'user/index.gsp').text == 'list of user for User'
    }

    void 'a collision leaves every other controller precompiled'() {
        given:
            writeControllerIn('com/example', 'UserController', 'com/example/User')
            writeControllerIn('com/example/community', 'UserController', 'com/example/community/User')
            writeControllerIn('com/example', 'BookController', 'com/example/Book')
            def task = task()

        when:
            task.generate()

        then:
            !generated(task, 'user/index.gsp').exists()
            generated(task, 'book/index.gsp').text == 'list of book for Book'
    }
}
