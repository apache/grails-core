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
package org.apache.grails.e2e.beans

import spock.lang.Specification
import spock.lang.TempDir

abstract class PluginIndexFixture extends Specification {

    @TempDir
    File projectDir

    def setup() {
        new File(projectDir, 'gradle.properties').text = "grailsVersion=${System.getProperty('grails.e2e.version')}\n"
        writeSettings()
        writeBuild()
    }

    protected File snapshot() {
        File archive = new File(projectDir, 'build/libs/fixture.jar')
        File snapshot = File.createTempFile('plugin-', '.jar', projectDir)
        java.nio.file.Files.copy(archive.toPath(), snapshot.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        snapshot
    }

    protected void build(String... tasks) {
        List<String> args = [System.getProperty('grails.e2e.gradlew'), '-p', projectDir.absolutePath, '--max-workers=1']
        if (Boolean.getBoolean('grails.e2e.offline')) args.add('--offline')
        args.addAll(tasks)
        def process = new ProcessBuilder(args).redirectErrorStream(true).start()
        String output = process.inputStream.getText('UTF-8')
        assert process.waitFor() == 0 : output
    }

    protected void writeSettings() {
        String repo = System.getProperty('grails.e2e.localMavenRepo')
        String pluginRepositories = System.getProperty('grails.e2e.pluginRepositories')
        new File(projectDir, 'settings.gradle').text = """
            pluginManagement {
                apply from: file('${pluginRepositories.replace('\\\\', '/')}')
                repositories {
                    maven { url = uri('${repo.replace('\\\\', '/')}') }
                    configurePluginRepositories(delegate)
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri('${repo.replace('\\\\', '/')}') }
                    // the snapshots of the libraries Grails also works on, such as the asset pipeline
                    configurePluginRepositories(delegate)
                }
            }
            rootProject.name = 'plugin-index-fixture'
        """
    }

    protected void writeBuild() {
        String version = System.getProperty('grails.e2e.version')
        new File(projectDir, 'build.gradle').text = """
            plugins {
                id 'org.apache.grails.gradle.grails-app' version '${version}'
            }

            version = '0.1'
            tasks.named('jar') { enabled = true; archiveFileName = 'fixture.jar' }

            grails {
                bom = null
                cliAutoProvision = false
            }

            dependencies {
                implementation platform('org.apache.grails:grails-bom:${version}')
                implementation 'org.apache.grails:grails-core'
                implementation 'org.apache.grails.data:grails-datamapping-core'
            }

            tasks.named('compileGroovy') {
                options.incremental = true
            }
        """
    }
}
