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

    void 'a static namespace #declaration is left to the runtime resolver'() {
        given:
            writeNamespacedClass('com/example/EventController', declaration)
            writeController('BookController', 'Book')
            def task = task()

        when:
            task.generate()

        then:
            !generated(task, 'event').exists()
            !generated(task, 'admin/event').exists()
            generated(task, 'book/show.gsp').text == 'show Book'

        where:
            declaration << ['field', 'getter']
    }

    void 'an inherited namespace from a #location also prevents shared views'() {
        given:
            writeNamespacedClass('com/example/AdminBase', 'field')
            File parent = new File(classesDir, 'com/example/AdminBase.class')
            def task = task()
            if (location == 'jar') {
                File dependency = new File(projectDir, 'base.jar')
                new JarOutputStream(dependency.newOutputStream()).withCloseable { out ->
                    out.putNextEntry(new JarEntry('com/example/AdminBase.class'))
                    out.write(parent.bytes)
                    out.closeEntry()
                }
                parent.delete()
                task.controllerClasspath.from(dependency)
            }
            writeNamespacedClass('com/example/EventController', null, 'com/example/AdminBase')

        when:
            task.generate()

        then:
            !generated(task, 'event').exists()

        where:
            location << ['directory', 'jar']
    }

    void 'a superclass on the template classpath alone is not searched for a namespace'() {
        given: 'the base class is reachable only where templates are read from'
            writeNamespacedClass('com/example/AdminBase', 'field')
            File parent = new File(classesDir, 'com/example/AdminBase.class')
            File dependency = new File(projectDir, 'base.jar')
            new JarOutputStream(dependency.newOutputStream()).withCloseable { out ->
                out.putNextEntry(new JarEntry('com/example/AdminBase.class'))
                out.write(parent.bytes)
                out.closeEntry()
            }
            parent.delete()
            writeNamespacedClass('com/example/EventController', null, 'com/example/AdminBase')
            def task = task()
            task.templateClasspath.from(dependency)

        when:
            task.generate()

        then: 'class resolution reads its own classpath, so the two inputs can be scoped separately'
            generated(task, 'event/show.gsp').exists()
    }

    void 'every controller extending one namespaced base is left to the runtime resolver'() {
        given:
            writeNamespacedClass('com/example/AdminBase', 'field')
            writeNamespacedClass('com/example/EventController', null, 'com/example/AdminBase')
            writeNamespacedClass('com/example/VenueController', null, 'com/example/AdminBase')
            writeController('BookController', 'Book')
            def task = task()

        when:
            task.generate()

        then:
            !generated(task, 'event').exists()
            !generated(task, 'venue').exists()
            generated(task, 'book/show.gsp').exists()
    }

    void 'a superclass with #damage bytecode is taken to declare no namespace'() {
        given: 'a dependency base class that ASM cannot read'
            ClassWriter writer = new ClassWriter(0)
            writer.visit(damage == 'future' ? 200 : Opcodes.V21, Opcodes.ACC_PUBLIC, 'com/example/FutureBase', null, 'java/lang/Object', null)
            writer.visitEnd()
            File dependency = new File(projectDir, 'future.jar')
            new JarOutputStream(dependency.newOutputStream()).withCloseable { out ->
                out.putNextEntry(new JarEntry('com/example/FutureBase.class'))
                byte[] bytes = writer.toByteArray()
                out.write(damage == 'empty' ? new byte[0] : damage == 'truncated' ? Arrays.copyOf(bytes, 12) : bytes)
                out.closeEntry()
            }
            writeNamespacedClass('com/example/EventController', null, 'com/example/FutureBase')
            def task = task()
            task.controllerClasspath.from(dependency)

        when:
            task.generate()

        then: 'the build carries on and the controller is precompiled'
            generated(task, 'event/show.gsp').exists()

        where:
            damage << ['future', 'truncated', 'empty']
    }

    void 'an I/O failure reading a superclass is taken to declare no namespace'() {
        given:
            InputStream damaged = new InputStream() {
                @Override
                int read() throws IOException {
                    throw new IOException('Damaged dependency entry')
                }
            }
            ClassLoader resources = new ClassLoader((ClassLoader) null) {
                @Override
                InputStream getResourceAsStream(String name) {
                    name == 'com/example/Base.class' ? damaged : null
                }
            }
            def method = GenerateScaffoldedViewsTask.getDeclaredMethod('ancestorHasNamespace', String, ClassLoader, Map)
            method.accessible = true

        expect:
            !method.invoke(task(), 'com/example/Base', resources, [:])
    }

    void 'an acronym-prefixed controller writes the view directory the runtime resolves'() {
        given:
            writeController('APIController', 'Widget')
            def task = task()

        when:
            task.generate()

        then: 'Grails keeps a name beginning with two capitals unchanged'
            generated(task, 'API/index.gsp').text == 'list of widget for Widget'

        and: 'by its listed name, which a case-insensitive file system would not tell apart by lookup'
            task.outputDirectory.get().asFile.list() as List == ['API']
    }

    void 'an acronym-prefixed domain is bound under the property name the runtime binds'() {
        given:
            writeController('MappingController', 'URLMapping')
            def task = task()

        when:
            task.generate()

        then:
            generated(task, 'mapping/index.gsp').text == 'list of URLMapping for URLMapping'
    }

    void 'an acronym-prefixed controller respects a view the application declares'() {
        given:
            writeController('APIController', 'Widget')
            File views = new File(projectDir, 'grails-app/views/API')
            views.mkdirs()
            File declared = new File(views, 'index.gsp')
            declared.text = 'hand written'
            def task = task([], [declared])

        when:
            task.generate()

        then:
            !generated(task, 'API/index.gsp').exists()
            generated(task, 'API/show.gsp').exists()
    }

    void 'an acronym-prefixed controller respects a view a plugin declares'() {
        given:
            writeController('APIController', 'Widget')
            def task = task()
            task.viewClasspath.from(pluginViews('jar', '/WEB-INF/grails-app/views/API/show.gsp=api_show\n'))

        when:
            task.generate()

        then:
            !generated(task, 'API/show.gsp').exists()
            generated(task, 'API/index.gsp').exists()
    }

    void 'a namespaced claimant prevents another controller from filling the shared directory'() {
        given:
            writeControllerIn('com/example', 'EventController', 'com/example/Event')
            writeNamespacedClass('com/example/admin/EventController', 'field')
            def task = task()

        when:
            task.generate()

        then: 'even the same domain can use different namespace-specific templates'
            !generated(task, 'event').exists()
    }

    void 'an instance namespace property does not opt out of precompilation'() {
        given:
            writeNamespacedClass('com/example/EventController', 'instance')
            def task = task()

        when:
            task.generate()

        then:
            generated(task, 'event/show.gsp').exists()
    }

    void 'a plugin view in a #location takes precedence over scaffold generation'() {
        given:
            writeController('EventController', 'Event')
            def task = task()
            task.generate()
            assert generated(task, 'event/show.gsp').exists()
            File dependency = pluginViews(location, '/WEB-INF/grails-app/views/event/show.gsp=custom_event_show\n')
            task.viewClasspath.from(dependency)

        when:
            task.generate()

        then: 'a stale generated view must also disappear when a plugin starts providing it'
            !generated(task, 'event/show.gsp').exists()
            generated(task, 'event/index.gsp').exists()

        when: 'the plugin stops providing that view'
            task.viewClasspath.setFrom([])
            task.generate()

        then:
            generated(task, 'event/show.gsp').exists()

        where:
            location << ['directory', 'jar']
    }

    void 'a namespaced plugin page does not suppress an unrelated unqualified view'() {
        given:
            writeController('EventController', 'Event')
            def task = task()
            task.viewClasspath.from(pluginViews('jar', '/WEB-INF/grails-app/views/admin/event/show.gsp=admin_show\n'))

        when:
            task.generate()

        then:
            generated(task, 'event/show.gsp').exists()
    }

    void 'a plugin view indexed beside its descriptor in a #location takes precedence'() {
        given:
            writeController('EventController', 'Event')
            def task = task()
            task.viewClasspath.from(pluginViews(location, '/WEB-INF/grails-app/views/event/show.gsp=custom_event_show\n',
                    'META-INF/views.properties'))

        when:
            task.generate()

        then:
            !generated(task, 'event/show.gsp').exists()
            generated(task, 'event/index.gsp').exists()

        where:
            location << ['directory', 'jar']
    }

    void 'the index beside the descriptor is read instead of the compiled one, as the runtime reads it'() {
        given: 'a plugin carrying both indexes'
            writeController('EventController', 'Event')
            File dependency = new File(projectDir, 'plugin.jar')
            new JarOutputStream(dependency.newOutputStream()).withCloseable { out ->
                out.putNextEntry(new JarEntry('META-INF/grails-plugin.xml'))
                out.write('<plugin name="calendar" version="1.0"/>'.getBytes('UTF-8'))
                out.closeEntry()
                out.putNextEntry(new JarEntry('META-INF/views.properties'))
                out.write('/WEB-INF/grails-app/views/event/show.gsp=descriptor_show\n'.getBytes('ISO-8859-1'))
                out.closeEntry()
                out.putNextEntry(new JarEntry('gsp/views.properties'))
                out.write('/WEB-INF/grails-app/views/event/index.gsp=compiled_index\n'.getBytes('ISO-8859-1'))
                out.closeEntry()
            }
            def task = task()
            task.viewClasspath.from(dependency)

        when:
            task.generate()

        then: 'the runtime never serves the second index, so it must not suppress anything here'
            !generated(task, 'event/show.gsp').exists()
            generated(task, 'event/index.gsp').exists()
    }

    void 'a #indexPath index without a descriptor in a #location suppresses scaffolding only for directory outputs'() {
        given:
            writeController('EventController', 'Event')
            def task = task()
            task.viewClasspath.from(pluginViews(location, '/WEB-INF/grails-app/views/event/show.gsp=custom_show\n',
                    indexPath, false))

        when:
            task.generate()

        then:
            generated(task, 'event/show.gsp').exists() == (location == 'jar')
            generated(task, 'event/index.gsp').exists()

        where:
            location    | indexPath
            'jar'       | 'gsp/views.properties'
            'jar'       | 'META-INF/views.properties'
            'directory' | 'gsp/views.properties'
            'directory' | 'META-INF/views.properties'
    }

    private File pluginViews(String location, String index, String indexPath = 'gsp/views.properties', boolean descriptor = true) {
        File dependency = new File(projectDir, "plugin-${location}")
        if (location == 'jar') {
            dependency = new File(projectDir, 'plugin.jar')
            new JarOutputStream(dependency.newOutputStream()).withCloseable { out ->
                if (descriptor) {
                    out.putNextEntry(new JarEntry('META-INF/grails-plugin.xml'))
                    out.write('<plugin name="calendar" version="1.0"/>'.getBytes('UTF-8'))
                    out.closeEntry()
                }
                out.putNextEntry(new JarEntry(indexPath))
                out.write(index.getBytes('ISO-8859-1'))
                out.closeEntry()
            }
        }
        else {
            File registry = new File(dependency, indexPath)
            registry.parentFile.mkdirs()
            registry.text = index
        }
        dependency
    }

    private void writeNamespacedClass(String name, String declaration, String superclass = 'java/lang/Object') {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, superclass, null)
        AnnotationVisitor annotation = writer.visitAnnotation('Lgrails/plugin/scaffolding/annotation/Scaffold;', true)
        annotation.visit('domain', Type.getObjectType('com/example/Event'))
        annotation.visitEnd()
        if (declaration == 'field' || declaration == 'instance') {
            int access = declaration == 'field' ? Opcodes.ACC_STATIC : Opcodes.ACC_PUBLIC
            writer.visitField(access, 'namespace', 'Ljava/lang/String;', null, null).visitEnd()
        }
        if (declaration == 'getter') {
            def method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, 'getNamespace', '()Ljava/lang/String;', null, null)
            method.visitCode()
            method.visitLdcInsn('admin')
            method.visitInsn(Opcodes.ARETURN)
            method.visitMaxs(1, 0)
            method.visitEnd()
        }
        writer.visitEnd()
        File target = new File(classesDir, "${name}.class")
        target.parentFile.mkdirs()
        target.bytes = writer.toByteArray()
    }

}
