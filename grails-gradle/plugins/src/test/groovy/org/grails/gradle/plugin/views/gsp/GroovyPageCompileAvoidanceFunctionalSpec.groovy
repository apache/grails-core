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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Covers which changes to the project's classes have its pages compiled, and its scaffolded pages
 * expanded, again.
 *
 * <p>Both read the project's classes for what a class compiled against them sees: the classes, their
 * members and signatures, and the annotations on them. A change to a method body or a private member
 * leaves them as they were. The project's AST transformations run while the pages are compiled, so a
 * change to what one does, or to a resource of theirs that registers one, compiles the pages again.</p>
 *
 * @since 9.0
 */
class GroovyPageCompileAvoidanceFunctionalSpec extends GradleSpecification {

    private File projectDir

    def "a change to the project's classes that a page cannot see leaves the pages as they were"() {
        given:
        project()
        compile('compileGroovyPages')

        when: 'a method body changes, and a private method is added'
        write('src/main/groovy/Shelf.groovy', '''
            class Shelf {
                String label() { 'shelf ' + size() }
                private int size() { 3 }
            }
        ''')
        BuildResult result = compile('compileGroovyPages')

        then: 'the class is compiled again, and the pages are not'
        result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        result.task(':compileGroovyPages').outcome == TaskOutcome.UP_TO_DATE
    }

    def "a change to the project's classes that a page can see compiles the pages again"() {
        given:
        project()
        compile('compileGroovyPages')

        when: 'a public method is added'
        write('src/main/groovy/Shelf.groovy', '''
            class Shelf {
                String label() { 'shelf' }
                int capacity() { 3 }
            }
        ''')
        BuildResult result = compile('compileGroovyPages')

        then:
        result.task(':compileGroovyPages').outcome == TaskOutcome.SUCCESS
    }

    def "a change to what an AST transformation of the project does compiles the pages again"() {
        given: 'a transformation in an ast source set, copied into the main classes directory as a plugin build does'
        project()
        astSourceSet()
        write('src/ast/groovy/Stamp.groovy', '''
            class Stamp {
                String stamp() { 'one' }
            }
        ''')
        compile('compileGroovyPages')

        when: 'a method body of the transformation changes'
        write('src/ast/groovy/Stamp.groovy', '''
            class Stamp {
                String stamp() { 'two' }
            }
        ''')
        BuildResult result = compile('compileGroovyPages')

        then: 'its ABI is the same, and the pages are compiled again all the same'
        result.task(':compileAstGroovy').outcome == TaskOutcome.SUCCESS
        result.task(':compileGroovyPages').outcome == TaskOutcome.SUCCESS
    }

    def "a change to a resource of the ast source set compiles the pages again"() {
        given: 'transformations registered by a resource of the ast source set, copied into the main classes directory as a plugin build does'
        project()
        astSourceSet()
        write('src/ast/groovy/Stamp.groovy', "class Stamp { String stamp() { 'one' } }")
        write('src/ast/groovy/OtherStamp.groovy', "class OtherStamp { String stamp() { 'two' } }")
        write("src/ast/resources/${resource}", 'Stamp')
        write('src/main/webapp/index.gsp', 'a webapp page')
        BuildResult first = compile('compileGroovyPages')
        assert first.task(':compileWebappGroovyPages').outcome == TaskOutcome.SUCCESS

        when: 'only the resource changes, to name a class already compiled'
        write("src/ast/resources/${resource}", 'Stamp\nOtherStamp')
        BuildResult result = compile('compileGroovyPages')

        then: 'what the page compiler loads from the classpath has changed, so both page compilations run again'
        result.task(':processAstResources').outcome == TaskOutcome.SUCCESS
        result.task(':compileAstGroovy').outcome == TaskOutcome.UP_TO_DATE
        result.task(':compileGroovyPages').outcome == TaskOutcome.SUCCESS
        result.task(':compileWebappGroovyPages').outcome == TaskOutcome.SUCCESS

        where:
        resource << ['META-INF/services/org.codehaus.groovy.transform.ASTTransformation',
                     'META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule']
    }

    def "a change to a file other than a class in the project's classes directory compiles the pages again"() {
        given: 'a transformation registration a task of the build copies into the main classes directory'
        project()
        new File(projectDir, 'build.gradle').append('''
            def copyRegistrations = tasks.register('copyRegistrations', Copy) {
                from('registrations')
                into(layout.buildDirectory.dir('classes/groovy/main'))
            }
            sourceSets.main.output.classesDirs.builtBy(copyRegistrations)
            tasks.named('classes') { dependsOn(copyRegistrations) }
        ''')
        write('registrations/META-INF/services/org.codehaus.groovy.transform.ASTTransformation', 'Shelf')
        write('src/main/webapp/index.gsp', 'a webapp page')
        BuildResult first = compile('compileGroovyPages')
        assert first.task(':compileWebappGroovyPages').outcome == TaskOutcome.SUCCESS

        when: 'only the registration changes'
        write('registrations/META-INF/services/org.codehaus.groovy.transform.ASTTransformation', 'Shelf\nBookController')
        BuildResult result = compile('compileGroovyPages')

        then: 'no class changed, and what the page compiler loads from the classpath did, so both page compilations run again'
        result.task(':copyRegistrations').outcome == TaskOutcome.SUCCESS
        result.task(':compileGroovy').outcome == TaskOutcome.UP_TO_DATE
        result.task(':compileGroovyPages').outcome == TaskOutcome.SUCCESS
        result.task(':compileWebappGroovyPages').outcome == TaskOutcome.SUCCESS
    }

    def "a change to a scaffolded controller that is not read for its pages leaves them as they were"() {
        given:
        project()
        compile('generateScaffoldedViews')

        when: 'a private method is added to the controller'
        write('grails-app/controllers/BookController.groovy', '''
            import grails.plugin.scaffolding.annotation.Scaffold
            @Scaffold(Integer)
            class BookController {
                private String title() { 'books' }
            }
        ''')
        BuildResult result = compile('generateScaffoldedViews')

        then:
        result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        result.task(':generateScaffoldedViews').outcome == TaskOutcome.UP_TO_DATE
    }

    def "a change to what is read of a scaffolded controller expands its pages again"() {
        given:
        project()
        compile('generateScaffoldedViews')

        when: 'the controller scaffolds another domain class'
        write('grails-app/controllers/BookController.groovy', '''
            import grails.plugin.scaffolding.annotation.Scaffold
            @Scaffold(Long)
            class BookController { }
        ''')
        BuildResult other = compile('generateScaffoldedViews')

        then: 'the pages are expanded for it'
        other.task(':generateScaffoldedViews').outcome == TaskOutcome.SUCCESS
        scaffolded('java.lang.Long').isDirectory()
        !scaffolded('java.lang.Integer').exists()

        when: 'it declares a namespace'
        write('grails-app/controllers/BookController.groovy', '''
            import grails.plugin.scaffolding.annotation.Scaffold
            @Scaffold(Long)
            class BookController { static namespace = 'admin' }
        ''')
        BuildResult namespaced = compile('generateScaffoldedViews')

        then: 'the namespace-specific template is expanded for its domain class'
        namespaced.task(':generateScaffoldedViews').outcome == TaskOutcome.SUCCESS
        // under the copy of the templates it was expanded from, as the stand-in writes it
        scaffolded('java.lang.Long').listFiles().any { File copy -> new File(copy, 'admin/show.gsp').isFile() }
    }

    def "a namespace a controller declares only in a private field expands its namespace's pages"() {
        given: 'a page for the admin namespace is already expanded, for another controller'
        project()
        write('grails-app/controllers/admin/EventController.groovy', '''
            package admin
            import grails.plugin.scaffolding.annotation.Scaffold
            @Scaffold(String)
            class EventController { static namespace = 'admin' }
        ''')
        compile('generateScaffoldedViews')
        assert !scaffolded('java.lang.Integer').listFiles().any { File copy -> new File(copy, 'admin/show.gsp').isFile() }

        when: 'a controller gains a private field naming that namespace, with no accessor, which its ABI leaves out'
        write('grails-app/controllers/BookController.groovy', '''
            import grails.plugin.scaffolding.annotation.Scaffold
            @Scaffold(Integer)
            class BookController { private static String namespace = 'admin' }
        ''')
        BuildResult result = compile('generateScaffoldedViews')

        then: 'the namespace is seen, and the page is expanded for the domain class it scaffolds'
        result.task(':generateScaffoldedViews').outcome == TaskOutcome.SUCCESS
        scaffolded('java.lang.Integer').listFiles().any { File copy -> new File(copy, 'admin/show.gsp').isFile() }
    }

    def "a controller in two class directories that declares a namespace in only one expands its namespace's pages"() {
        given: 'the main classes declare the namespace in a private field, and another directory has the controller without it'
        project()
        new File(projectDir, 'build.gradle').append('''
            sourceSets { extra }
            dependencies { extraImplementation localGroovy(); extraImplementation sourceSets.main.output }
            tasks.named('generateScaffoldedViews') { classesDirs.from(sourceSets.extra.output.classesDirs) }
        ''')
        write('grails-app/controllers/BookController.groovy', '''
            import grails.plugin.scaffolding.annotation.Scaffold
            @Scaffold(Integer)
            class BookController { private static String namespace = 'admin' }
        ''')
        write('src/extra/groovy/BookController.groovy', '''
            import grails.plugin.scaffolding.annotation.Scaffold
            @Scaffold(Integer)
            class BookController { }
        ''')

        when:
        BuildResult result = compile('generateScaffoldedViews')

        then: 'the namespace is seen, as it was before the controllers were an input'
        result.task(':generateScaffoldedViews').outcome == TaskOutcome.SUCCESS
        scaffolded('java.lang.Integer').listFiles().any { File copy -> new File(copy, 'admin/show.gsp').isFile() }
    }

    def "with an application.groovy a change to a method body of the project's classes compiles the pages again"() {
        given: 'a configuration that can compute its settings with the classes of the project'
        project()
        write('grails-app/conf/application.groovy', "grails.views.gsp.codecs.expression = 'html'")
        compile('compileGroovyPages')

        when: 'a method body changes, which leaves the ABI as it was'
        write('src/main/groovy/Shelf.groovy', '''
            class Shelf {
                String label() { 'another shelf' }
            }
        ''')
        BuildResult result = compile('compileGroovyPages')

        then: 'what the configuration computes may have changed, so the pages are compiled again'
        result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        result.task(':compileGroovyPages').outcome == TaskOutcome.SUCCESS
    }

    private void project() {
        projectDir = setupTestResourceProject('gsp-compile-classpath').projectDir
        new File(projectDir, 'build.gradle').append('''
            dependencies {
                implementation localGroovy()
                implementation files('compiler')
                runtimeOnly files('generator')
            }
            sourceSets.main.groovy.srcDir('grails-app/controllers')
        ''')
        // the page compiler comes from grails-web-gsp and the generator from grails-scaffolding, which
        // this build cannot depend on: the tests' stand-ins record what they are handed instead
        [['generator', 'org/apache/grails/scaffolding/ScaffoldedPagesGenerator.class'],
         ['compiler', 'org/grails/web/pages/GroovyPageForkedCompiler.class'],
         ['compiler', 'org/grails/gradle/plugin/views/RecordingForkedCompiler.class']].each { String dir, String standIn ->
            File standInClass = new File(projectDir, "${dir}/${standIn}")
            standInClass.parentFile.mkdirs()
            standInClass.bytes = getClass().classLoader.getResource(standIn).bytes
        }
        write('src/main/groovy/grails/plugin/scaffolding/annotation/Scaffold.groovy', '''
            package grails.plugin.scaffolding.annotation
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            @Retention(RetentionPolicy.RUNTIME)
            @interface Scaffold { Class value() }
        ''')
        write('src/main/groovy/Shelf.groovy', '''
            class Shelf {
                String label() { 'shelf' }
            }
        ''')
        write('grails-app/controllers/BookController.groovy', '''
            import grails.plugin.scaffolding.annotation.Scaffold
            @Scaffold(Integer)
            class BookController { }
        ''')
        write('src/main/templates/scaffolding/show.gsp', 'show ${className}')
        write('src/main/templates/scaffolding/admin/show.gsp', 'admin show ${className}')
        write('grails-app/views/book/index.gsp', 'a page')
    }

    /** An ast source set whose output is copied into the main classes directory, as a plugin's build does. */
    private void astSourceSet() {
        new File(projectDir, 'build.gradle').append('''
            sourceSets { ast }
            dependencies { astImplementation localGroovy() }
            def copyAstClasses = tasks.register('copyAstClasses', Copy) {
                from(sourceSets.ast.output)
                into(layout.buildDirectory.dir('classes/groovy/main'))
            }
            sourceSets.main.output.classesDirs.builtBy(copyAstClasses)
            tasks.named('classes') { dependsOn(copyAstClasses) }
        ''')
    }

    private void write(String path, String content) {
        File file = new File(projectDir, path)
        file.parentFile.mkdirs()
        file.text = content.stripIndent()
    }

    private File scaffolded(String domain) {
        new File(projectDir, "build/generated/scaffolded-views/grails-scaffolded/${domain}")
    }

    private BuildResult compile(String taskName) {
        executeTask(taskName)
    }
}
