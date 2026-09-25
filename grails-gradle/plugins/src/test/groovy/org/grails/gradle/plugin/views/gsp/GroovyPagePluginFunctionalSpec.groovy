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
package org.grails.gradle.plugin.views.gsp

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.Type
import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Functional tests for the classpath {@link GroovyPagePlugin} assembles for GSP compilation.
 *
 * <p>Uses Gradle TestKit to apply {@code org.apache.grails.gradle.grails-gsp} to a project and
 * assert what the {@code compileGroovyPages} and {@code compileWebappGroovyPages} tasks compile
 * against. The plugin no longer registers a {@code gspCompile} configuration: it was introduced
 * as the classpath for the original Ant-based GSP compiler, and once compilation moved to a
 * forked task its only remaining content was a hardcoded servlet API dependency, which the
 * compile classpath already supplies transitively.</p>
 *
 * @since 8.0
 */
class GroovyPagePluginFunctionalSpec extends GradleSpecification {

    def "plugin does not register a gspCompile configuration"() {
        given:
        setupTestResourceProject('gsp-compile-classpath')

        when:
        def result = executeTask('inspectGspCompileClasspath')

        then:
        result.output.contains('HAS_GSP_COMPILE_CONFIGURATION=false')
    }

    def "GSP compile tasks still resolve the compile classpath, provided dependencies and compiled classes"() {
        given:
        setupTestResourceProject('gsp-compile-classpath')

        when:
        def result = executeTask('inspectGspCompileClasspath')

        then: 'compileGroovyPages sees everything it needs to compile a GSP'
        result.output.contains('MAIN_HAS_COMPILE_CLASSPATH=true')
        result.output.contains('MAIN_HAS_PROVIDED_COMPILE=true')
        result.output.contains('MAIN_HAS_CLASSES_DIR=true')

        and: 'compileWebappGroovyPages resolves the same classpath'
        result.output.contains('WEBAPP_HAS_COMPILE_CLASSPATH=true')
        result.output.contains('WEBAPP_HAS_PROVIDED_COMPILE=true')
        result.output.contains('WEBAPP_HAS_CLASSES_DIR=true')
    }

    def "the page opt-in reaches both the build's page compiler and the JVM running the application"() {
        given:
        setupTestResourceProject('gsp-compile-static')

        when:
        def result = executeTask('inspectGspCompileStatic')

        then: 'the pages the build compiles ahead of time'
        result.output.contains('PAGE_COMPILER=true')
        result.output.contains('WEBAPP_PAGE_COMPILER=true')

        and: 'and the pages compiled again while the application runs'
        result.output.contains('RUNNING_APPLICATION=true')

        and: 'strictness travels with it, to both'
        result.output.contains('PAGE_COMPILER_STRICT=true')
        result.output.contains('RUNNING_APPLICATION_STRICT=true')
    }

    def "pages compile the way configuration says where the opt-in is not set"() {
        given:
        setupTestResourceProject('gsp-compile-classpath')

        when:
        def result = executeTask('inspectGspCompileClasspath')

        then: 'the option is read only where the grails extension exists, so this project keeps the default'
        result.output.contains('PAGE_COMPILER_STATIC=false')
        result.output.contains('WEBAPP_PAGE_COMPILER_STATIC=false')
    }

    def "compiled pages are on the test runtime class path"() {
        given: 'a project whose pages the plugin compiles'
        setupTestResourceProject('gsp-compile-classpath')

        when:
        def result = executeTask('inspectGspRuntimeClasspath')

        then: 'a test of the application loads the pages it would ship, the view registry included'
        result.output.contains('TEST_RUNTIME_HAS_PAGES=true')
        result.output.contains('TEST_RUNTIME_HAS_WEBAPP_PAGES=true')

        and: 'they are off the main runtime class path, which a boot archive would package a second time'
        result.output.contains('MAIN_RUNTIME_HAS_PAGES=false')
        result.output.contains('MAIN_RUNTIME_HAS_WEBAPP_PAGES=false')

        and: 'running the tests compiles them first, rather than using whatever an earlier run left'
        result.output.contains('TEST_WAITS_FOR_PAGE_COMPILATION=true')
        result.output.contains('TEST_WAITS_FOR_WEBAPP_PAGE_COMPILATION=true')
    }

    def "a Grails project gets no compiled pages on its test runtime class path"() {
        given: 'the same wiring in a Grails build'
        setupTestResourceProject('gsp-compile-classpath-grails')

        when:
        def result = executeTask('inspectGspRuntimeClasspath')

        then: 'a Grails application renders the views under grails-app/views, which its tests find as they are'
        result.output.contains('TEST_RUNTIME_HAS_PAGES=false')
        result.output.contains('TEST_RUNTIME_HAS_WEBAPP_PAGES=false')

        and: 'so its test task is not put behind compiling pages it does not read'
        result.output.contains('TEST_WAITS_FOR_PAGE_COMPILATION=false')
    }

    def "scaffolded pages are generated into a directory of their own and compiled with the application views"() {
        given:
        def runner = setupTestResourceProject('gsp-compile-classpath')
        File projectDir = runner.projectDir
        new File(projectDir, 'build.gradle').append("""
            dependencies {
                implementation localGroovy()
                implementation files('compiler')
                runtimeOnly files('generator')
                runtimeOnly files('theme')
                runtimeOnly files('damaged-plugin.jar')
                runtimeOnly files('notes.txt')
            }
            sourceSets.main.groovy.srcDir('grails-app/controllers')
            tasks.named('compileGroovyPages') {
                compileOptions.encoding.set('ISO-8859-1')
            }
            // templates the application packages by routes of its own
            processResources {
                from('extra-templates') { into 'META-INF/templates/scaffolding' }
            }
            def generateTemplates = tasks.register('generateTemplates') {
                def generated = layout.buildDirectory.dir('generated-templates')
                outputs.dir(generated)
                doLast {
                    def template = generated.get().file('META-INF/templates/scaffolding/create.gsp').asFile
                    template.parentFile.mkdirs()
                    template.text = 'generated create \${className}'
                }
            }
            sourceSets.main.resources.srcDir(generateTemplates)
        """)
        // The generator the task runs comes from grails-scaffolding, and the page compiler from
        // grails-web-gsp, which this build cannot depend on; the tests' stand-ins record what they
        // are handed instead.
        ['generator': 'org/apache/grails/scaffolding/ScaffoldedPagesGenerator.class',
         'compiler' : 'org/grails/web/pages/GroovyPageForkedCompiler.class'].each { String dir, String standIn ->
            File standInClass = new File(projectDir, "${dir}/${standIn}")
            standInClass.parentFile.mkdirs()
            standInClass.bytes = getClass().classLoader.getResource(standIn).bytes
        }
        // Only the annotation's bytecode is read by the task; no application is started.
        Map<String, String> sources = [
            'src/main/groovy/grails/plugin/scaffolding/annotation/Scaffold.groovy': """
                package grails.plugin.scaffolding.annotation
                import java.lang.annotation.Retention
                import java.lang.annotation.RetentionPolicy
                @Retention(RetentionPolicy.RUNTIME)
                @interface Scaffold { Class value() }
            """,
            'grails-app/controllers/admin/EventController.groovy': """
                package admin
                import grails.plugin.scaffolding.annotation.Scaffold
                @Scaffold(String)
                class EventController { static namespace = 'admin' }
            """,
            'grails-app/controllers/BookController.groovy': """
                import grails.plugin.scaffolding.annotation.Scaffold
                @Scaffold(Integer)
                class BookController { }
            """,
            'src/main/templates/scaffolding/show.gsp': 'show ${className}',
            'src/main/templates/scaffolding/admin/show.gsp': 'admin show ${className}',
            // a template the application keeps with its resources, which are packaged the same way
            'src/main/resources/META-INF/templates/scaffolding/edit.gsp': 'resources edit ${className}',
            'extra-templates/index.gsp': 'extra index ${className}',
            // templates from a dependency the application only has at runtime, one of them a copy of
            // a template the application has too
            'theme/META-INF/templates/scaffolding/list.gsp': 'theme list ${className}',
            'theme/META-INF/templates/scaffolding/show.gsp': 'theme show ${className}',
            'grails-app/views/book/index.gsp': 'handwritten index'
        ]
        sources.each { String path, String content ->
            File file = new File(projectDir, path)
            file.parentFile.mkdirs()
            file.text = content.stripIndent()
        }
        // a plugin whose controller cannot be read, and a classpath entry that holds nothing to read
        new JarOutputStream(new File(projectDir, 'damaged-plugin.jar').newOutputStream()).withCloseable { JarOutputStream out ->
            ['META-INF/grails-plugin.xml': '<plugin/>'.bytes,
             'com/plugin/DamagedController.class': [0xCA, 0xFE, 0xBA, 0xBE, 0, 0] as byte[]].each { String name, byte[] bytes ->
                out.putNextEntry(new JarEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        new File(projectDir, 'notes.txt').text = 'not an archive'
        File generated = new File(projectDir, 'build/generated/scaffolded-views')
        Closure<Map<String, Object>> pagesOf = { String domain ->
            File dir = new File(generated, "grails-scaffolded/${domain}")
            Map<String, List<String>> pages = [:]
            dir.eachFileRecurse { File f ->
                if (f.isFile()) {
                    // <copy>/<template path>, as the stand-in writes it
                    List<String> parts = dir.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/' as char).tokenize('/')
                    pages.computeIfAbsent(parts.drop(1).join('/')) { [] } << f.text
                }
            }
            // a template with one copy by its text, one with several by all of them
            pages.collectEntries { String path, List<String> copies -> [path, copies.size() == 1 ? copies[0] : copies.sort()] }
        }

        when:
        def result = executeTask('generateScaffoldedViews')

        then:
        assertTaskSuccess('generateScaffoldedViews', result)

        and: 'a controller that cannot be read is warned about, where a note would not be shown'
        result.output.contains('Could not read damaged-plugin.jar!/com/plugin/DamagedController.class')
        !result.output.contains('is not an archive')

        and: 'the pages have a directory of their own, where no controller view resolves from, and the views are not copied'
        new File(generated, 'grails-scaffolded').isDirectory()
        !new File(generated, 'book').exists()
        !new File(generated, 'event').exists()
        !new File(projectDir, 'build/generated/views').exists()

        and: 'every copy of every template the application runs with is expanded for every scaffolded domain class'
        pagesOf('java.lang.String') == ['show.gsp': ['show ${className}', 'theme show ${className}'],
                                        'admin/show.gsp': 'admin show ${className}',
                                        'edit.gsp': 'resources edit ${className}', 'list.gsp': 'theme list ${className}',
                                        'index.gsp': 'extra index ${className}', 'create.gsp': 'generated create ${className}']

        and: 'a namespace-specific one only for a domain class a namespaced controller scaffolds'
        pagesOf('java.lang.Integer') == ['show.gsp': ['show ${className}', 'theme show ${className}'],
                                         'edit.gsp': 'resources edit ${className}', 'list.gsp': 'theme list ${className}',
                                         'index.gsp': 'extra index ${className}', 'create.gsp': 'generated create ${className}']

        and: 'they are written in the encoding they are compiled with'
        new File(generated, 'encoding.txt').text == 'ISO-8859-1'

        when:
        def compilation = executeTask('compileGroovyPages')
        Map<String, String> recorded = new File(projectDir, 'build/gsp-classes/main/compiler.txt').readLines('UTF-8')
                .collectEntries { String line -> line.split('=', 2) as List }

        then: 'the application views are compiled where they are, and the generated pages with them, in one compilation'
        assertTaskSuccess('compileGroovyPages', compilation)
        new File(recorded.source).canonicalFile == new File(projectDir, 'grails-app/views').canonicalFile
        new File(recorded.generatedViews).canonicalFile == generated.canonicalFile
        recorded.encoding == 'ISO-8859-1'

        and: 'the page compiler is told which pages it may leave out: those from a dependency\'s templates, not the application\'s'
        List<String> optional = recorded.optionalPages.tokenize(',')
        optional.collect { String page -> new File(generated, page).text } as Set ==
                ['theme show ${className}', 'theme list ${className}'] as Set
        optional.size() == 4

        when: 'a template override is edited'
        new File(projectDir, 'src/main/templates/scaffolding/show.gsp').text = 'edited show ${className}'
        def rebuild = executeTask('generateScaffoldedViews')

        then: 'the pages expanded from it are replaced, not added to'
        assertTaskSuccess('generateScaffoldedViews', rebuild)
        pagesOf('java.lang.String') == ['show.gsp': ['edited show ${className}', 'theme show ${className}'],
                                        'admin/show.gsp': 'admin show ${className}',
                                        'edit.gsp': 'resources edit ${className}', 'list.gsp': 'theme list ${className}',
                                        'index.gsp': 'extra index ${className}', 'create.gsp': 'generated create ${className}']
    }

    def "a plugin's scaffolded controllers have their pages generated and compiled in an application that scaffolds none of its own"() {
        given: 'no controller of the application is scaffolded, so nothing it wrote says it scaffolds, and it has no views'
        def runner = setupTestResourceProject('gsp-compile-classpath')
        File projectDir = runner.projectDir
        new File(projectDir, 'build.gradle').append("""
            dependencies {
                implementation files('compiler')
                runtimeOnly files('generator')
                runtimeOnly files('widget-plugin.jar')
            }
        """)
        ['generator': 'org/apache/grails/scaffolding/ScaffoldedPagesGenerator.class',
         'compiler' : 'org/grails/web/pages/GroovyPageForkedCompiler.class'].each { String dir, String standIn ->
            File standInClass = new File(projectDir, "${dir}/${standIn}")
            standInClass.parentFile.mkdirs()
            standInClass.bytes = getClass().classLoader.getResource(standIn).bytes
        }
        assert !new File(projectDir, 'grails-app/views').exists()
        ClassWriter controller = new ClassWriter(0)
        controller.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, 'com/plugin/WidgetController', null, 'java/lang/Object', null)
        AnnotationVisitor scaffold = controller.visitAnnotation('Lgrails/plugin/scaffolding/annotation/Scaffold;', true)
        scaffold.visit('domain', Type.getObjectType('java/lang/Long'))
        scaffold.visitEnd()
        controller.visitEnd()
        new JarOutputStream(new File(projectDir, 'widget-plugin.jar').newOutputStream()).withCloseable { JarOutputStream out ->
            ['META-INF/grails-plugin.xml': '<plugin/>'.bytes,
             'com/plugin/WidgetController.class': controller.toByteArray(),
             'META-INF/templates/scaffolding/show.gsp': 'widget show ${className}'.bytes].each { String name, byte[] bytes ->
                out.putNextEntry(new JarEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }

        when:
        def result = executeTask('generateScaffoldedViews')

        then:
        assertTaskSuccess('generateScaffoldedViews', result)
        File pages = new File(projectDir, 'build/generated/scaffolded-views/grails-scaffolded/java.lang.Long')
        pages.isDirectory()
        pages.listFiles()*.listFiles().flatten()*.text == ['widget show ${className}']

        when: 'the pages are compiled, with no views of the application beside them'
        def compilation = executeTask('compileGroovyPages')

        then: 'the plugin\'s page is compiled, as optional since the application does not own its template'
        assertTaskSuccess('compileGroovyPages', compilation)
        Map<String, String> recorded = new File(projectDir, 'build/gsp-classes/main/compiler.txt').readLines('UTF-8')
                .collectEntries { String line -> line.split('=', 2) as List }
        new File(recorded.generatedViews).canonicalFile == new File(projectDir, 'build/generated/scaffolded-views').canonicalFile
        recorded.optionalPages.tokenize(',').size() == 1
    }

    def "a plugin the project has only at runtime has no pages generated here, since they could not be compiled"() {
        given: 'a plugin the project depends on, and one that reaches it only at runtime, each with its own domain class'
        def runner = setupTestResourceProject('gsp-compile-classpath')
        File projectDir = runner.projectDir
        new File(projectDir, 'build.gradle').append("""
            dependencies {
                implementation files('compiler')
                implementation files('widget-plugin.jar')
                runtimeOnly files('generator')
                runtimeOnly files('gadget-plugin.jar')
            }
        """)
        ['generator': 'org/apache/grails/scaffolding/ScaffoldedPagesGenerator.class',
         'compiler' : 'org/grails/web/pages/GroovyPageForkedCompiler.class'].each { String dir, String standIn ->
            File standInClass = new File(projectDir, "${dir}/${standIn}")
            standInClass.parentFile.mkdirs()
            standInClass.bytes = getClass().classLoader.getResource(standIn).bytes
        }
        for (String name : ['widget', 'gadget']) {
            String type = "com/${name}/${name.capitalize()}"
            ClassWriter controller = new ClassWriter(0)
            controller.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "${type}Controller", null, 'java/lang/Object', null)
            AnnotationVisitor scaffold = controller.visitAnnotation('Lgrails/plugin/scaffolding/annotation/Scaffold;', true)
            scaffold.visit('domain', Type.getObjectType(type))
            scaffold.visitEnd()
            controller.visitEnd()
            ClassWriter domain = new ClassWriter(0)
            domain.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, type, null, 'java/lang/Object', null)
            domain.visitEnd()
            new JarOutputStream(new File(projectDir, "${name}-plugin.jar").newOutputStream()).withCloseable { JarOutputStream out ->
                ['META-INF/grails-plugin.xml': '<plugin/>'.bytes,
                 ("${type}Controller.class".toString()): controller.toByteArray(),
                 ("${type}.class".toString()): domain.toByteArray(),
                 'META-INF/templates/scaffolding/show.gsp': "${name} show \${className}".bytes].each { String entry, byte[] bytes ->
                    out.putNextEntry(new JarEntry(entry))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
        File scaffolded = new File(projectDir, 'build/generated/scaffolded-views/grails-scaffolded')

        when:
        def result = executeTask('generateScaffoldedViews', ['--info'])

        then: 'the plugin on the classpath the pages are compiled against has its pages generated, from every copy of the template'
        assertTaskSuccess('generateScaffoldedViews', result)
        new File(scaffolded, 'com.widget.Widget').listFiles()*.listFiles().flatten()*.text.sort() ==
                ['gadget show ${className}', 'widget show ${className}']

        and: 'the one the project has only at runtime has none, and says why only where the build is asked for detail'
        !new File(scaffolded, 'com.gadget.Gadget').exists()
        result.output.contains('No page is expanded for gadget-plugin.jar!/com/gadget/GadgetController.class: ' +
                'the domain class it scaffolds, com.gadget.Gadget, is not on the classpath the pages are compiled against')

        when: 'the pages are compiled'
        def compilation = executeTask('compileGroovyPages')

        then: 'the compiler is handed no page it cannot compile, so it has none to leave out'
        assertTaskSuccess('compileGroovyPages', compilation)
        Map<String, String> recorded = new File(projectDir, 'build/gsp-classes/main/compiler.txt').readLines('UTF-8')
                .collectEntries { String line -> line.split('=', 2) as List }
        List<String> optional = recorded.optionalPages.tokenize(',')
        optional.size() == 2
        optional.every { String page -> page.startsWith('grails-scaffolded/com.widget.Widget/') }
    }

    def "a project that scaffolds nothing generates nothing"() {
        given:
        def runner = setupTestResourceProject('gsp-compile-classpath')

        when:
        def result = executeTask('generateScaffoldedViews')

        then:
        assertTaskSuccess('generateScaffoldedViews', result)
        !new File(runner.projectDir, 'build/generated/scaffolded-views/grails-scaffolded').exists()

        when: 'and with no views of its own either'
        def compilation = executeTask('compileGroovyPages')

        then: 'there is nothing to compile, as there was before any page was generated'
        compilation.task(':compileGroovyPages').outcome == TaskOutcome.NO_SOURCE
    }
}
