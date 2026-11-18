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
package org.apache.grails.gradle.plugin.aot

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.TempDir

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class GenerateNativeMetadataTaskSpec extends Specification {

    @TempDir
    File projectDir

    private File classesDir
    private File pageClassesDir

    void setup() {
        classesDir = new File(projectDir, 'classes')
        pageClassesDir = new File(projectDir, 'gsp-classes')
        classesDir.mkdirs()
        pageClassesDir.mkdirs()
    }

    private void writeClass(File root, String path) {
        File target = new File(root, path + '.class')
        target.parentFile.mkdirs()
        target.bytes = new byte[]{ (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE }
    }

    /** Writes the manifest the GSP compiler produces, mapping a view to the class it compiled to. */
    private void writeViewsManifest(File root, Map<String, String> views) {
        File manifest = new File(root, GenerateNativeMetadataTask.VIEWS_MANIFEST)
        manifest.parentFile.mkdirs()
        Properties properties = new Properties()
        views.each { String uri, String pageClass -> properties.setProperty(uri, pageClass) }
        manifest.withOutputStream { properties.store(it, null) }
    }

    /** A jar shaped like the one a plugin publishes, carrying its own pages. */
    private File writePluginJar(String name, Map<String, String> views) {
        File jar = new File(projectDir, name)
        Properties properties = new Properties()
        views.each { String uri, String pageClass -> properties.setProperty(uri, pageClass) }
        new JarOutputStream(jar.newOutputStream()).withCloseable { JarOutputStream out ->
            out.putNextEntry(new JarEntry(GenerateNativeMetadataTask.VIEWS_MANIFEST))
            properties.store(out, null)
            out.closeEntry()
            views.values().each { String pageClass ->
                out.putNextEntry(new JarEntry(pageClass + '.class'))
                out.write([0xCA, 0xFE, 0xBA, 0xBE] as byte[])
                out.closeEntry()
            }
        }
        jar
    }

    private GenerateNativeMetadataTask task(List<File> classpath = []) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.tasks.register('generateNativeMetadata', GenerateNativeMetadataTask) {
            GenerateNativeMetadataTask it ->
                it.classesDirs.from(classesDir)
                it.pageClassesDirs.from(pageClassesDir)
                it.pageClasspath.from(classpath)
                it.outputDirectory.set(new File(projectDir, 'out'))
        }
        project.tasks.named('generateNativeMetadata', GenerateNativeMetadataTask).get()
    }

    private List<String> recordedTypes(GenerateNativeMetadataTask task) {
        task.generate()
        File file = new File(task.outputDirectory.get().asFile, GenerateNativeMetadataTask.METADATA_PATH)
        file.exists() ? new JsonSlurper().parse(file).reflection.collect { it.type } : []
    }

    void 'the application classes are recorded'() {
        given:
            writeClass(classesDir, 'com/example/UserController')
            writeClass(classesDir, 'com/example/User')

        expect:
            recordedTypes(task()).containsAll(['com.example.UserController', 'com.example.User'])
    }

    void 'the closures an artefact declares are recorded'() {
        given: 'Groovy reads doCall reflectively to pick an overload, so these are reached too'
            writeClass(classesDir, 'com/example/BootStrap$_closure1')

        expect:
            recordedTypes(task()).contains('com.example.BootStrap$_closure1')
    }

    void 'a page is recorded under the class its view compiled to'() {
        given:
            writeViewsManifest(pageClassesDir, ['/WEB-INF/grails-app/views/index.gsp': 'gsp_app_index_gsp'])
            writeClass(pageClassesDir, 'gsp_app_index_gsp')

        expect:
            recordedTypes(task()).contains('gsp_app_index_gsp')
    }

    void 'the closures a page declares are recorded'() {
        given:
            writeViewsManifest(pageClassesDir, ['/WEB-INF/grails-app/views/index.gsp': 'gsp_app_index_gsp'])
            writeClass(pageClassesDir, 'gsp_app_index_gsp')
            writeClass(pageClassesDir, 'gsp_app_index_gsp$_run_closure1')

        expect:
            recordedTypes(task()).contains('gsp_app_index_gsp$_run_closure1')
    }

    void 'a page class left behind by an earlier build is not recorded'() {
        given: 'the manifest is the record of this build, the directory is not'
            writeViewsManifest(pageClassesDir, ['/WEB-INF/grails-app/views/index.gsp': 'gsp_app_index_gsp'])
            writeClass(pageClassesDir, 'gsp_app_index_gsp')
            writeClass(pageClassesDir, 'gsp_app_removed_gsp')

        expect:
            !recordedTypes(task()).contains('gsp_app_removed_gsp')
    }

    void 'the pages a plugin contributes are recorded'() {
        given: 'an application renders these as readily as its own, and they are not in its build'
            File pluginJar = writePluginJar('fields-plugin.jar',
                    ['/WEB-INF/grails-app/views/_fields/default/_field.gsp': 'gsp_fields_field_gsp'])

        expect:
            recordedTypes(task([pluginJar])).contains('gsp_fields_field_gsp')
    }

    void 'an artifact carrying no pages is passed over'() {
        given:
            File plainJar = new File(projectDir, 'plain.jar')
            new JarOutputStream(plainJar.newOutputStream()).withCloseable { JarOutputStream out ->
                out.putNextEntry(new JarEntry('com/example/Plain.class'))
                out.write([0xCA, 0xFE, 0xBA, 0xBE] as byte[])
                out.closeEntry()
            }
            writeClass(classesDir, 'com/example/UserController')

        when:
            def types = recordedTypes(task([plainJar]))

        then:
            noExceptionThrown()
            types == ['com.example.UserController']
    }

    void 'every recorded type is registered for the access Grails makes of it'() {
        given:
            writeClass(classesDir, 'com/example/UserController')

        when:
            def task = task()
            task.generate()
            def entries = new JsonSlurper()
                    .parse(new File(task.outputDirectory.get().asFile, GenerateNativeMetadataTask.METADATA_PATH))
                    .reflection

        then: 'an action is invoked by name, and a domain class has its properties read'
            entries.every { it.allDeclaredMethods && it.allDeclaredFields && it.allDeclaredConstructors }
    }
}
