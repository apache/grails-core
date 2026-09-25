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

import org.gradle.api.GradleException
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

    private void writeNamespacedController(String name, String domainInternalName) {
        writeClass(classesDir, name, scaffolded(name, domainInternalName) { ClassWriter writer ->
            writer.visitField(Opcodes.ACC_STATIC, 'namespace', 'Ljava/lang/String;', null, null).visitEnd()
        })
    }

    /** A scaffolded class, with whatever else {@code extra} writes into it. */
    private static byte[] scaffolded(String name, String domainInternalName, String superName = 'java/lang/Object',
                                     Closure extra = {}) {
        plain(name, superName) { ClassWriter writer ->
            AnnotationVisitor annotation = writer.visitAnnotation('Lgrails/plugin/scaffolding/annotation/Scaffold;', true)
            annotation.visit('domain', Type.getObjectType(domainInternalName))
            annotation.visitEnd()
            extra.call(writer)
        }
    }

    private static byte[] scaffolded(String name, String domainInternalName, Closure extra) {
        scaffolded(name, domainInternalName, 'java/lang/Object', extra)
    }

    /** {@code interface AdminArea { String namespace = 'admin' }}, as Groovy compiles it. */
    private static byte[] adminAreaInterface() {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, 'com/example/AdminArea', null,
                'java/lang/Object', null)
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, 'namespace', 'Ljava/lang/String;', null, 'admin').visitEnd()
        writer.visitEnd()
        writer.toByteArray()
    }

    /** A scaffolded class implementing an interface and declaring nothing else. */
    private static byte[] implementing(String name, String domainInternalName, String interfaceName) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, 'java/lang/Object', [interfaceName] as String[])
        AnnotationVisitor annotation = writer.visitAnnotation('Lgrails/plugin/scaffolding/annotation/Scaffold;', true)
        annotation.visit('domain', Type.getObjectType(domainInternalName))
        annotation.visitEnd()
        writer.visitEnd()
        writer.toByteArray()
    }

    private static byte[] plain(String name, String superName, Closure extra) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, superName, null)
        extra.call(writer)
        writer.visitEnd()
        writer.toByteArray()
    }

    private static void writeClass(File root, String name, byte[] bytes) {
        File target = new File(root, "${name}.class")
        target.parentFile.mkdirs()
        target.bytes = bytes
    }

    /** A jar of the given entries, a plugin's when one of them is its descriptor. */
    private static void writeJar(File jar, Map<String, byte[]> entries) {
        new JarOutputStream(jar.newOutputStream()).withCloseable { JarOutputStream out ->
            entries.each { String name, byte[] bytes ->
                out.putNextEntry(new JarEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    private static final String PLUGIN_DESCRIPTOR = 'META-INF/grails-plugin.xml'

    private GenerateScaffoldedViewsTask task(Object overrides = []) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.tasks.register('generateScaffoldedViews', GenerateScaffoldedViewsTask) {
            GenerateScaffoldedViewsTask it ->
                it.classesDirs.from(classesDir)
                it.runtimeClasspath.from(templateJar)
                it.runtimeClasspath.from(testClasspath())
                it.templateOverrides.from(overrides)
                it.outputDirectory.set(new File(projectDir, 'out'))
                it.optionalPages.set(new File(projectDir, 'optional-pages.txt'))
        }
        project.tasks.named('generateScaffoldedViews', GenerateScaffoldedViewsTask).get()
    }

    /** This test's own classpath, which carries the stand-in generator. */
    private static List<String> testClasspath() {
        System.getProperty('java.class.path').split(File.pathSeparator).toList()
    }

    /** What the generator was handed: each domain class and template path, with every copy of the template. */
    private Map<String, List<String>> handed(GenerateScaffoldedViewsTask task) {
        File root = new File(task.outputDirectory.get().asFile, 'grails-scaffolded')
        Map<String, List<String>> pages = new TreeMap<>()
        if (root.isDirectory()) {
            root.eachFileRecurse { File f ->
                if (f.isFile()) {
                    // <domain>/<copy>/<template path>.gsp, as the stand-in writes it
                    List<String> parts = root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/' as char).tokenize('/')
                    String key = ([parts[0]] + parts.drop(2)).join('/') - '.gsp'
                    pages.computeIfAbsent(key) { [] } << f.getText('UTF-8')
                }
            }
        }
        pages.each { String key, List<String> copies -> copies.sort() }
        pages
    }

    /** Where the generator was told each template came from, by the template's text. */
    private Map<String, String> originsOf(GenerateScaffoldedViewsTask task) {
        File output = task.outputDirectory.get().asFile
        File root = new File(output, 'grails-scaffolded')
        Map<String, String> origins = [:]
        root.eachFileRecurse { File f ->
            if (f.isFile()) {
                String copy = root.toPath().relativize(f.toPath()).getName(1).toString()
                origins[f.getText('UTF-8')] = new File(output, "origins/${copy}").getText('UTF-8')
            }
        }
        origins
    }

    void 'each copy is handed over with where it came from, named without the paths of this machine'() {
        given:
            File classesTheme = new File(projectDir, 'theme-classes')
            new File(classesTheme, 'META-INF/templates/scaffolding').mkdirs()
            new File(classesTheme, 'META-INF/templates/scaffolding/edit.gsp').text = 'directory edit'
            File overrides = new File(projectDir, 'templates')
            new File(overrides, 'index.gsp').with { parentFile.mkdirs(); text = 'custom index' }
            File packaged = new File(projectDir, 'resources-output')
            new File(packaged, 'META-INF/templates/scaffolding/create.gsp').with { parentFile.mkdirs(); text = 'packaged create' }
            writeController('UserController', 'User')
            def task = task(ProjectBuilder.builder().build().fileTree(overrides))
            task.packagedTemplates.from(packaged)
            task.runtimeClasspath.from(classesTheme)

        when:
            task.generate()

        then:
            originsOf(task) == [
                    'custom index'                            : "the application's index.gsp",
                    'packaged create'                         : "the application's create.gsp",
                    'show ${className}'                       : 'templates.jar!/META-INF/templates/scaffolding/show.gsp',
                    'list of ${propertyName} for ${className}': 'templates.jar!/META-INF/templates/scaffolding/index.gsp',
                    'create ${className}'                     : 'templates.jar!/META-INF/templates/scaffolding/create.gsp',
                    'edit ${className}'                       : 'templates.jar!/META-INF/templates/scaffolding/edit.gsp',
                    'directory edit'                          : 'theme-classes/META-INF/templates/scaffolding/edit.gsp']
    }

    void 'every template is expanded for every scaffolded domain class'() {
        given:
            writeController('UserController', 'User')
            writeController('BookController', 'Book')
            writeTemplateJar(templateJar, [show: 'show ${className}', index: 'index ${className}'])
            def task = task()

        when:
            task.generate()

        then:
            handed(task) == ['com.example.Book/index': ['index ${className}'], 'com.example.Book/show': ['show ${className}'],
                             'com.example.User/index': ['index ${className}'], 'com.example.User/show': ['show ${className}']]
    }

    void 'a controller without the annotation is left alone'() {
        given:
            writePlainController('PlainController')
            def task = task()

        when:
            task.generate()

        then:
            handed(task).isEmpty()
    }

    void 'the domain class named by the annotation drives the pages, not the controller'() {
        given: 'a controller whose name does not match the domain it scaffolds'
            writeController('AccountController', 'Person')
            def task = task()

        when:
            task.generate()

        then:
            handed(task).keySet()*.tokenize('/')*.first().unique() == ['com.example.Person']
    }

    void 'the domain attribute is read in preference to the class the annotation extends'() {
        given: 'a controller written as @Scaffold(RestfulServiceController<User>)'
            writeSuperclassParameterizedController('UserController', 'User')
            def task = task()

        when:
            task.generate()

        then:
            handed(task).keySet()*.tokenize('/')*.first().unique() == ['com.example.User']
    }

    void 'controllers of one name scaffolding different domains each get their own pages'() {
        given: 'com.example.UserController and com.example.community.UserController'
            writeControllerIn('com/example', 'UserController', 'com/example/User')
            writeControllerIn('com/example/community', 'UserController', 'com/example/community/User')
            def task = task()

        when:
            task.generate()

        then: 'pages are kept by domain class, so sharing a view directory name is nothing to them'
            handed(task).keySet()*.tokenize('/')*.first().unique() == ['com.example.User', 'com.example.community.User']
    }

    void 'a domain scaffolded by several controllers is handed over once'() {
        given:
            writeControllerIn('com/example', 'UserController', 'com/example/User')
            writeControllerIn('com/example/admin', 'UserController', 'com/example/User')
            def task = task()

        when:
            task.generate()

        then:
            handed(task).keySet().findAll { it.endsWith('/index') } == ['com.example.User/index'] as Set
    }

    void 'a namespaced controller is precompiled like any other'() {
        given:
            writeNamespacedController('com/example/admin/EventController', 'com/example/Event')
            def task = task()

        when:
            task.generate()

        then:
            handed(task)['com.example.Event/show'] == ['show ${className}']
    }

    void 'an application template is expanded along with every copy it overrides'() {
        given:
            writeController('UserController', 'User')
            File overrides = new File(projectDir, 'templates')
            overrides.mkdirs()
            new File(overrides, 'index.gsp').text = 'custom ${className}'
            def task = task(ProjectBuilder.builder().build().fileTree(overrides))

        when:
            task.generate()

        then: 'which one the resolver finds depends on the classpath the application runs from, so both are ready'
            handed(task)['com.example.User/index'] == ['custom ${className}', 'list of ${propertyName} for ${className}']
            handed(task)['com.example.User/show'] == ['show ${className}']
    }

    void 'namespace-specific templates are expanded only for a domain class a namespaced controller scaffolds'() {
        given:
            writeTemplateJar(templateJar, [show: 'show ${className}', 'admin/show': 'admin show ${className}'])
            writeController('UserController', 'User')
            writeNamespacedController('com/example/admin/EventController', 'com/example/Event')
            File overrides = new File(projectDir, 'templates')
            new File(overrides, 'staff').mkdirs()
            new File(overrides, 'staff/show.gsp').text = 'staff show ${className}'
            def task = task(ProjectBuilder.builder().build().fileTree(overrides))

        when:
            task.generate()

        then: 'a controller without a namespace never asks for one'
            handed(task) == ['com.example.Event/admin/show': ['admin show ${className}'], 'com.example.Event/show': ['show ${className}'],
                             'com.example.Event/staff/show': ['staff show ${className}'], 'com.example.User/show': ['show ${className}']]
    }

    void 'a namespace is found however the controller comes by it'() {
        given:
            writeTemplateJar(templateJar, [show: 'show ${className}', 'admin/show': 'admin show ${className}'])
            writeClass(classesDir, 'com/example/AdminBase', plain('com/example/AdminBase', 'java/lang/Object') { ClassWriter writer ->
                writer.visitField(Opcodes.ACC_STATIC, 'namespace', 'Ljava/lang/String;', null, null).visitEnd()
            })
            writeClass(classesDir, 'com/example/InheritedController',
                    scaffolded('com/example/InheritedController', 'com/example/Inherited', 'com/example/AdminBase'))
            writeClass(classesDir, 'com/example/TraitController', scaffolded('com/example/TraitController', 'com/example/FromTrait') { ClassWriter writer ->
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, 'getNamespace', '()Ljava/lang/String;', null, null).visitEnd()
            })
            writeClass(classesDir, 'com/example/AdminArea', adminAreaInterface())
            writeClass(classesDir, 'com/example/InterfaceController', implementing('com/example/InterfaceController', 'com/example/FromInterface',
                    'com/example/AdminArea'))
            def task = task()

        when:
            task.generate()

        then: 'declared by a superclass, by a trait, which leaves a static accessor on the class, or as an interface constant'
            handed(task).keySet().findAll { it.endsWith('admin/show') } ==
                    ['com.example.FromInterface/admin/show', 'com.example.FromTrait/admin/show', 'com.example.Inherited/admin/show'] as Set
    }

    void 'a controller that cannot be read is left out, and the others are still expanded'() {
        given:
            new File(classesDir, 'com/example').mkdirs()
            new File(classesDir, 'com/example/DamagedController.class').bytes = [0xCA, 0xFE, 0xBA, 0xBE, 0, 0] as byte[]
            writeController('UserController', 'User')
            def task = task()

        when:
            task.generate()

        then:
            handed(task).keySet()*.tokenize('/')*.first().unique() == ['com.example.User']
    }

    void 'a superclass that cannot be read is taken to declare no namespace'() {
        given:
            writeTemplateJar(templateJar, [show: 'show ${className}', 'admin/show': 'admin show ${className}'])
            new File(classesDir, 'com/example').mkdirs()
            new File(classesDir, 'com/example/Damaged.class').bytes = [0xCA, 0xFE, 0xBA, 0xBE, 0, 0] as byte[]
            writeClass(classesDir, 'com/example/UserController',
                    scaffolded('com/example/UserController', 'com/example/User', 'com/example/Damaged'))
            def task = task()

        when:
            task.generate()

        then:
            handed(task) == ['com.example.User/show': ['show ${className}']]
    }

    void 'the templates the application packages are read from where they are packaged'() {
        given: 'the output of the application, where a template is kept under META-INF/templates/scaffolding'
            File output = new File(projectDir, 'resources-output')
            new File(output, 'META-INF/templates/scaffolding').mkdirs()
            new File(output, 'META-INF/templates/scaffolding/index.gsp').text = 'packaged index ${className}'
            new File(output, 'application.yml').text = 'not a template'
            writeController('UserController', 'User')
            def task = task()
            task.packagedTemplates.from(output)

        when:
            task.generate()

        then:
            handed(task)['com.example.User/index'] == ['list of ${propertyName} for ${className}', 'packaged index ${className}']
            !handed(task).keySet().any { it.contains('application') }
    }

    void 'templates are read from a classpath directory, namespace directories included'() {
        given:
            File resources = new File(projectDir, 'resources')
            new File(resources, 'META-INF/templates/scaffolding/admin').mkdirs()
            new File(resources, 'META-INF/templates/scaffolding/show.gsp').text = 'directory show'
            new File(resources, 'META-INF/templates/scaffolding/admin/show.gsp').text = 'directory admin show'
            writeNamespacedController('com/example/admin/UserController', 'com/example/User')
            def task = task()
            task.runtimeClasspath.setFrom([resources] + testClasspath())

        when:
            task.generate()

        then:
            handed(task) == ['com.example.User/admin/show': ['directory admin show'], 'com.example.User/show': ['directory show']]
    }

    void 'every copy of a template on the classpath is expanded'() {
        given:
            File later = new File(projectDir, 'later.jar')
            writeTemplateJar(later, [show: 'later show'])
            writeTemplateJar(templateJar, [show: 'show'])
            writeController('UserController', 'User')
            def task = task()
            task.runtimeClasspath.from(later)

        when:
            task.generate()

        then:
            handed(task) == ['com.example.User/show': ['later show', 'show']]
    }

    void 'an archive on the classpath is read whatever it is named'() {
        given:
            File zip = new File(projectDir, 'theme.zip')
            writeTemplateJar(zip, [show: 'zipped show'])
            File notAnArchive = new File(projectDir, 'notes.txt')
            notAnArchive.text = 'not an archive'
            writeController('UserController', 'User')
            def task = task()
            task.runtimeClasspath.from(zip, notAnArchive)

        when:
            task.generate()

        then:
            handed(task)['com.example.User/show'] == ['show ${className}', 'zipped show']
    }

    void 'identical copies of a template are expanded once'() {
        given:
            File same = new File(projectDir, 'same.jar')
            writeTemplateJar(same, [show: 'show'])
            writeTemplateJar(templateJar, [show: 'show'])
            writeController('UserController', 'User')
            def task = task()
            task.runtimeClasspath.from(same)

        when:
            task.generate()

        then:
            handed(task) == ['com.example.User/show': ['show']]
    }

    void 'a scaffolded controller a plugin provides has its pages expanded, from every copy of a template'() {
        given: 'a plugin carrying a scaffolded controller and its own show template'
            File plugin = new File(projectDir, 'plugin.jar')
            writeJar(plugin, [(PLUGIN_DESCRIPTOR): '<plugin/>'.bytes,
                              'com/plugin/WidgetController.class': scaffolded('com/plugin/WidgetController', 'com/plugin/Widget'),
                              'META-INF/templates/scaffolding/show.gsp': 'plugin show'.bytes])
            def task = task()
            task.runtimeClasspath.from(plugin)

        when:
            task.generate()

        then: 'a native image keeps no class files, so the copy beside the controller is not the one it is sure to find'
            handed(task)['com.plugin.Widget/show'] == ['plugin show', 'show ${className}']
            handed(task)['com.plugin.Widget/index'] == ['list of ${propertyName} for ${className}']
    }

    void "a scaffolded controller in a plugin's classes directory is found as in its jar"() {
        given:
            File plugin = new File(projectDir, 'plugin-classes')
            writeClass(plugin, 'com/plugin/WidgetController', scaffolded('com/plugin/WidgetController', 'com/plugin/Widget'))
            new File(plugin, PLUGIN_DESCRIPTOR).with { parentFile.mkdirs(); text = '<plugin/>' }
            def task = task()
            task.runtimeClasspath.from(plugin)

        when:
            task.generate()

        then:
            handed(task)['com.plugin.Widget/show'] == ['show ${className}']
    }

    void 'a scaffolded class in a jar that is not a plugin is not a controller of the application'() {
        given:
            File library = new File(projectDir, 'library.jar')
            writeJar(library, ['com/library/WidgetController.class': scaffolded('com/library/WidgetController', 'com/library/Widget')])
            def task = task()
            task.runtimeClasspath.from(library)

        when:
            task.generate()

        then:
            handed(task).isEmpty()
    }

    void 'the pages are written in the encoding they are compiled with'() {
        given:
            writeController('UserController', 'User')
            def task = task()

        expect:
            task.pageEncoding.get() == 'UTF-8'

        when:
            task.pageEncoding.set('ISO-8859-1')
            task.generate()

        then:
            new File(task.outputDirectory.get().asFile, 'encoding.txt').text == 'ISO-8859-1'
    }

    void 'a template a dependency supplies that cannot be expanded is left out, and the others are expanded'() {
        given:
            writeTemplateJar(templateJar, [show: 'show FAIL', index: 'index ${className}'])
            writeController('UserController', 'User')
            def task = task()

        when:
            task.generate()

        then: 'it may be a copy the resolver never chooses, and where it is, it is expanded when rendered'
            noExceptionThrown()
            handed(task) == ['com.example.User/index': ['index ${className}']]
    }

    void "only the pages expanded from a dependency's templates are optional to compile"() {
        given:
            writeTemplateJar(templateJar, [show: 'show ${className}', index: 'index ${className}'])
            writeController('UserController', 'User')
            File overrides = new File(projectDir, 'templates')
            new File(overrides, 'show.gsp').with { parentFile.mkdirs(); text = 'custom show' }
            def task = task(ProjectBuilder.builder().build().fileTree(overrides))

        when:
            task.generate()
            List<String> optional = task.optionalPages.get().asFile.readLines('UTF-8')

        then: 'the application\'s own has to compile, as a view does'
            optional.size() == 2
            optional.every { String page -> new File(task.outputDirectory.get().asFile, page).isFile() }
            optional.collect { String page -> new File(task.outputDirectory.get().asFile, page).text } as Set ==
                    ['show ${className}', 'index ${className}'] as Set
    }

    void "a template of the application's own that cannot be expanded fails the build, naming each"() {
        given:
            writeController('UserController', 'User')
            writeController('BookController', 'Book')
            File overrides = new File(projectDir, 'templates')
            new File(overrides, 'show.gsp').with { parentFile.mkdirs(); text = 'custom show FAIL' }
            def task = task(ProjectBuilder.builder().build().fileTree(overrides))

        when:
            task.generate()

        then: 'it is the application\'s code, as a view is'
            GradleException e = thrown()
            e.message.contains("Could not expand the scaffolding template show, from the application's show.gsp, for com.example.Book")
            e.message.contains("Could not expand the scaffolding template show, from the application's show.gsp, for com.example.User")
    }

    void 'a stale page from a previous run does not survive'() {
        given:
            writeController('UserController', 'User')
            def task = task()
            task.generate()
            File stale = new File(task.outputDirectory.get().asFile, 'grails-scaffolded/gone/stale.gsp')
            stale.parentFile.mkdirs()
            stale.text = 'stale'

        when:
            task.generate()

        then:
            !stale.exists()
    }

    void 'with no scaffolding library that generates pages, nothing is written and the build carries on'() {
        given:
            writeController('UserController', 'User')
            def task = task()
            task.runtimeClasspath.setFrom(templateJar)

        when:
            task.generate()

        then:
            handed(task).isEmpty()
    }

    void 'a generator packaged in a jar is found, as grails-scaffolding ships it'() {
        given:
            String entry = 'org/apache/grails/scaffolding/ScaffoldedPagesGenerator.class'
            File library = new File(projectDir, 'grails-scaffolding.jar')
            new JarOutputStream(library.newOutputStream()).withCloseable { JarOutputStream out ->
                out.putNextEntry(new JarEntry(entry))
                out.write(getClass().classLoader.getResource(entry).bytes)
                out.closeEntry()
            }
            writeController('UserController', 'User')
            def task = task()
            task.runtimeClasspath.setFrom(templateJar, library)

        when:
            task.generate()

        then:
            handed(task)['com.example.User/show'] == ['show ${className}']
    }

    /**
     * A jar holding a generator that declares the given version of its exchange, or none, and has no
     * main method, so that running it would fail the build.
     */
    private File generatorJar(String name, Integer protocol) {
        ClassWriter writer = new ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, 'org/apache/grails/scaffolding/ScaffoldedPagesGenerator', null,
                'java/lang/Object', null)
        if (protocol != null) {
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, 'PROTOCOL', 'I', null, protocol).visitEnd()
        }
        writer.visitEnd()
        File jar = new File(projectDir, name)
        writeJar(jar, ['org/apache/grails/scaffolding/ScaffoldedPagesGenerator.class': writer.toByteArray()])
        jar
    }

    void 'a generator for another version of the exchange is not run, and the build carries on'() {
        given: 'the one the application loads first, ahead of one that would do'
            writeController('UserController', 'User')
            def task = task()
            task.runtimeClasspath.setFrom([templateJar, generatorJar('other.jar', protocol)] + testClasspath())

        when:
            task.generate()

        then: 'the pages are left to be expanded when rendered, as with no generator at all'
            noExceptionThrown()
            handed(task).isEmpty()

        where:
            protocol << [GenerateScaffoldedViewsTask.GENERATOR_PROTOCOL + 1, null]
    }

    void 'with no templates on the classpath nothing is written'() {
        given:
            writeController('UserController', 'User')
            templateJar.delete()
            def task = task()

        when:
            task.generate()

        then:
            handed(task).isEmpty()
    }

}
