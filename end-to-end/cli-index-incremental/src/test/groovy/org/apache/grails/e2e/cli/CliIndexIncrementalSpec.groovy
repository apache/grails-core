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

package org.apache.grails.e2e.cli

import java.util.jar.JarFile

import spock.lang.Specification
import spock.lang.TempDir

import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ApplicationContextCommandRegistry

class CliIndexIncrementalSpec extends Specification {

    @TempDir
    File projectDir

    void 'renaming and deleting the final command updates the packaged CLI registry without a clean'() {
        given:
        new File(projectDir, 'settings.gradle').text = """
            pluginManagement {
                apply from: file(providers.gradleProperty('repositoryScript').get())
                repositories {
                    maven { url = uri(providers.gradleProperty('localRepo').get()) }
                    configurePluginRepositories(delegate)
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri(providers.gradleProperty('localRepo').get()) }
                    configurePluginRepositories(delegate)
                }
            }
            rootProject.name = 'command-index'
        """
        new File(projectDir, 'build.gradle').text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-plugin-cli' version '${System.getProperty('grails.e2e.version')}'
            }
            group = 'example'
            version = '1.0'
            cliArtifact.defaultDependencies = false
            dependencies {
                cliImplementation platform('org.apache.grails:grails-bom:${System.getProperty('grails.e2e.version')}')
                cliImplementation 'org.apache.grails:grails-core-cli'
            }
            tasks.named('compileCliGroovy') { options.incremental = true }
        """
        File sources = new File(projectDir, 'src/cli/groovy/example')
        sources.mkdirs()
        new File(sources, 'Plain.groovy').text = 'package example; class Plain {}'
        File original = writeCommand(sources, 'FirstProbeCommand')

        when:
        build('cliJar')

        then:
        verifyJar('example.FirstProbeCommand', 'first-probe')

        when:
        assert original.delete()
        File renamed = writeCommand(sources, 'RenamedProbeCommand')
        build('cliJar')

        then:
        verifyJar('example.RenamedProbeCommand', 'renamed-probe')

        when:
        build('clean', 'cliJar')

        then:
        verifyJar('example.RenamedProbeCommand', 'renamed-probe')

        when:
        assert renamed.delete()
        build('cliJar')

        then:
        verifyJar(null, null)

        when:
        build('clean', 'cliJar')

        then:
        verifyJar(null, null)
    }

    private static File writeCommand(File directory, String name) {
        new File(directory, name + '.groovy').tap {
            text = """
                package example
                import org.apache.grails.core.cli.ApplicationCommand
                import org.apache.grails.core.cli.ExecutionContext
                class ${name} implements ApplicationCommand {
                    boolean handle(ExecutionContext context) { true }
                }
            """
        }
    }

    private boolean verifyJar(String expectedClass, String expectedName) {
        File artifact = new File(projectDir, 'build/libs/command-index-cli-1.0.jar')
        new JarFile(artifact).withCloseable { jar ->
            def entry = jar.getJarEntry('META-INF/grails-cli.factories')
            if (expectedClass == null) {
                assert entry == null
            } else {
                Properties registrations = new Properties()
                jar.getInputStream(entry).withCloseable { registrations.load(it) }
                assert registrations.getProperty(ApplicationCommand.name) == expectedClass
            }
        }
        def thread = Thread.currentThread()
        def parent = thread.contextClassLoader
        // Each build represents a fresh CLI invocation. Give its JAR a distinct URL in this test JVM.
        File snapshot = File.createTempFile('commands-', '.jar', projectDir)
        java.nio.file.Files.copy(artifact.toPath(), snapshot.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        new URLClassLoader([snapshot.toURI().toURL()] as URL[], parent).withCloseable { loader ->
            try {
                thread.contextClassLoader = loader
                def registry = new ApplicationContextCommandRegistry()
                for (String name : ['first-probe', 'renamed-probe']) {
                    assert registry.findCommand(name)?.class?.name == (name == expectedName ? expectedClass : null)
                }
            } finally {
                thread.contextClassLoader = parent
            }
        }
        true
    }

    private void build(String... tasks) {
        List<String> args = [System.getProperty('grails.e2e.gradlew'), '--max-workers=1',
                '-PlocalRepo=' + System.getProperty('grails.e2e.repo'),
                '-PrepositoryScript=' + System.getProperty('grails.e2e.repositories')]
        if (Boolean.getBoolean('grails.e2e.offline')) {
            args.add('--offline')
        }
        args.addAll(tasks)
        def process = new ProcessBuilder(args).directory(projectDir).redirectErrorStream(true).start()
        String output = process.inputStream.getText('UTF-8')
        assert process.waitFor() == 0 : output
    }
}
