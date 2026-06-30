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

package org.apache.grails.buildsrc

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

import static org.apache.grails.buildsrc.GradleUtils.lookupPropertyByType

@CompileStatic
class CompilePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def initialized = new AtomicBoolean(false)
        project.plugins.withId('java') { // java (applied when groovy is applied) or java-library
            if (initialized.compareAndSet(false, true)) {
                configureCompile(project)
            }
        }
    }

    private static void configureCompile(Project project) {
        configureJavaVersion(project)
        configureJars(project)
        configureCompiler(project)
        configureReproducible(project)
    }

    private static void configureJavaVersion(Project project) {
        Integer javaVersion = lookupPropertyByType(project, 'javaVersion', Integer)
        project.tasks.withType(JavaCompile).configureEach {
            it.options.release.set(javaVersion)
        }
    }

    private static void configureJars(Project project) {
        project.extensions.configure(JavaPluginExtension) {
            it.withJavadocJar()
            it.withSourcesJar()
        }

        // Grails determines the grails version via the META-INF/MANIFEST.MF file
        // Note: we exclude attributes such as Built-By, Build-Jdk, Created-By to ensure the build is reproducible.
        project.tasks.withType(Jar).configureEach { Jar jar ->
            if (lookupPropertyByType(project, 'skipJavaComponent', Boolean)) {
                jar.enabled = false
                return
            }

            jar.manifest.attributes(
                    'Implementation-Title': 'Apache Grails',
                    'Implementation-Version': lookupPropertyByType(project, 'grailsVersion', String),
                    'Implementation-Vendor': 'grails.apache.org'
            )
            // Explicitly fail since duplicates indicate a double configuration that needs fixed
            jar.duplicatesStrategy = DuplicatesStrategy.FAIL
        }
    }

    private static void configureCompiler(Project project) {
        project.tasks.withType(JavaCompile).configureEach {
            // Preserve method parameter names in Groovy/Java classes for IDE parameter hints & bean reflection metadata.
            it.options.compilerArgs.add('-parameters')
            // encoding needs to be the same since it's different across platforms
            it.options.encoding = StandardCharsets.UTF_8.name()
            it.options.fork = true
            it.options.forkOptions.jvmArgs = ['-Xms128M', '-Xmx2G']
            if (System.getenv('SUPPRESS_DEPRECATION_WARNINGS') == 'true') {
                it.options.compilerArgs += ['-Xlint:-removal']
            }
        }

        project.plugins.withId('groovy') {
            project.tasks.withType(GroovyCompile).configureEach {
                // encoding needs to be the same since it's different across platforms
                it.groovyOptions.encoding = StandardCharsets.UTF_8.name()
                // Preserve method parameter names in Groovy/Java classes for IDE parameter hints & bean reflection metadata.
                it.groovyOptions.parameters = true
                // encoding needs to be the same since it's different across platforms
                it.options.encoding = StandardCharsets.UTF_8.name()
                it.options.fork = true
                // always set an isolated build to ensure grails.factories aren't accidentally merged since every project
                // in this mono repo should be an isolated projected
                it.options.forkOptions.jvmArgs = ['-Xms128M', '-Xmx2G', '-Dgrails.isolated.build=true']
                if (System.getenv('SUPPRESS_DEPRECATION_WARNINGS') == 'true') {
                    it.options.compilerArgs += ['-Xlint:-removal']
                }
            }
        }
    }

    private static void configureReproducible(Project project) {
        project.tasks.withType(Javadoc).configureEach { Javadoc it ->
            def options = it.options as StandardJavadocDocletOptions
            options.noTimestamp = true
            options.bottom = "Generated ${lookupPropertyByType(project, 'formattedBuildDate', String)} (UTC)"
        }

        // Any jar, zip, or archive should be reproducible
        // No longer needed after https://github.com/gradle/gradle/issues/30871
        project.tasks.withType(AbstractArchiveTask).configureEach {
            it.preserveFileTimestamps = false // to prevent timestamp mismatches
            it.reproducibleFileOrder = true // to keep the same ordering
            // to avoid platform specific defaults, set the permissions consistently
            it.filePermissions { permissions ->
                permissions.unix(0644)
            }
            it.dirPermissions { permissions ->
                permissions.unix(0755)
            }
        }
    }
}
