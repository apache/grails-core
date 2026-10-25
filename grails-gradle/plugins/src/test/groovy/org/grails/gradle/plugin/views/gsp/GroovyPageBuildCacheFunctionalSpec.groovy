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

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Covers what the build cache keeps of a project's compiled pages.
 *
 * <p>What a page compiles to depends on the Groovy that compiles it, the Java it is compiled for and
 * the classes it is compiled against. Each has to be part of the key, or a build is handed pages
 * compiled for another and fails the first time one is rendered. Nothing tied to where the checkout
 * is may be part of it, or every checkout compiles again what another already did.</p>
 *
 * @since 8.0.1
 */
class GroovyPageBuildCacheFunctionalSpec extends GradleSpecification {

    private GradleRunner runner

    def "pages compiled in one checkout are restored in another"() {
        given:
        File checkout = projectWithPages()
        compilePages()

        when: 'the same sources, in another directory'
        File elsewhere = copyOf(checkout, 'gsp-build-cache-elsewhere')
        BuildResult result = compilePages(elsewhere)

        then:
        result.task(':compileGroovyPages').outcome == TaskOutcome.FROM_CACHE
    }

    def "a build that resolves another Groovy compiles its pages again"() {
        given:
        File checkout = projectWithPages()
        compilePages()
        executeTask('clean')

        when: 'the classpath carries another Groovy, as a build that aligns on a newer one does'
        groovy(checkout, '6.0.0')
        BuildResult other = compilePages()

        then: 'the pages are compiled by it, not restored from a build that had the first'
        other.task(':compileGroovyPages').outcome == TaskOutcome.SUCCESS

        when: 'back on the first'
        executeTask('clean')
        groovy(checkout, '5.0.4')
        BuildResult first = compilePages()

        then: 'its pages are still there for it'
        first.task(':compileGroovyPages').outcome == TaskOutcome.FROM_CACHE
    }

    def "pages are compiled again for another Java"() {
        given:
        File checkout = projectWithPages()
        compilePages()
        executeTask('clean')

        when:
        new File(checkout, 'build.gradle').append('''
            tasks.named('compileGroovyPages') { targetCompatibility = '17' }
        ''')
        BuildResult result = compilePages()

        then:
        result.task(':compileGroovyPages').outcome == TaskOutcome.SUCCESS
    }

    def "the classpath the compiler runs from is part of the key"() {
        expect: 'as AbstractCompile declares it, and fingerprinted by content'
        GroovyPageForkCompileTask.isAnnotationPresent(CacheableTask)
        GroovyPageForkCompileTask.getMethod('getClasspath').isAnnotationPresent(Classpath)
    }

    private File projectWithPages() {
        runner = setupTestResourceProject('gsp-compile-classpath')
        File checkout = runner.projectDir
        File cache = new File(checkout.parentFile, 'build-cache')
        new File(checkout, 'settings.gradle').append("""
            buildCache { local { directory = file('${cache.absolutePath.replace('\\', '/')}') } }
        """)
        new File(checkout, 'build.gradle').append('''
            dependencies {
                implementation files('compiler')
                implementation files('groovy.jar')
            }
        ''')
        // the page compiler comes from grails-web-gsp, which this build cannot depend on: a stand-in
        // under its name, and the recorder it delegates to
        ['org/grails/web/pages/GroovyPageForkedCompiler.class', 'org/grails/gradle/plugin/views/RecordingForkedCompiler.class'].each { String resource ->
            File standIn = new File(checkout, "compiler/${resource}")
            standIn.parentFile.mkdirs()
            standIn.bytes = getClass().classLoader.getResource(resource).bytes
        }
        groovy(checkout, '5.0.4')
        File page = new File(checkout, 'grails-app/views/book/index.gsp')
        page.parentFile.mkdirs()
        page.text = 'a page'
        checkout
    }

    /** Stands in for the Groovy on the classpath, by the file Groovy states its version in. */
    private static void groovy(File checkout, String version) {
        new JarOutputStream(new File(checkout, 'groovy.jar').newOutputStream()).withCloseable { JarOutputStream out ->
            out.putNextEntry(new JarEntry('META-INF/groovy-release-info.properties'))
            out.write("ImplementationVersion=${version}\n".bytes)
            out.closeEntry()
        }
    }

    private BuildResult compilePages(File checkout = null) {
        if (checkout) {
            runner.withProjectDir(checkout)
        }
        executeTask('compileGroovyPages', ['--build-cache'])
    }

    private static File copyOf(File checkout, String name) {
        File copy = new File(checkout.parentFile, name)
        checkout.eachFileRecurse { File file ->
            def relative = checkout.toPath().relativize(file.toPath())
            if (file.isDirectory() || relative.getName(0).toString() in ['build', '.gradle']) {
                return
            }
            File target = new File(copy, relative.toString())
            target.parentFile.mkdirs()
            target.bytes = file.bytes
        }
        copy
    }
}
