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
package org.apache.grails.e2e.taglib

import java.util.zip.ZipFile

import spock.lang.Specification
import spock.lang.TempDir

/**
 * A tag library that has been renamed or deleted must not survive in what a build publishes.
 *
 * <p>The interesting case is the second build. Gradle does not recompile a source that has not
 * changed, so anything written per class as it compiled would never be revisited and would simply
 * stay - describing a tag library that no longer exists, and being packaged alongside the index that
 * no longer describes it. Only a build run twice, without a clean, shows that.
 *
 * <p>Built against published artifacts rather than project dependencies, so what is exercised is the
 * plugin an application actually applies.
 */
class TagLibraryIndexIncrementalSpec extends Specification {

    private static final String INDEX = 'META-INF/grails/taglibs'

    @TempDir
    File projectDir

    def setup() {
        writeSettings()
        writeBuild()
        writeTagLib('AlphaTagLib', 'alpha', 'alphaTag')
        writeTagLib('BetaTagLib', 'beta', 'betaTag')
    }

    void 'a deleted tag library is gone from the published index after a build with no clean'() {
        given: 'a first build describing both'
        build()

        expect:
        packagedDescriptor('demo.AlphaTagLib').isFile()
        packagedDescriptor('demo.BetaTagLib').isFile()
        packagedManifest().contains('demo.BetaTagLib')
        jarNames().contains("${INDEX}/demo.BetaTagLib.properties" as String)

        when: 'one is deleted and the project is built again, without a clean'
        new File(projectDir, 'grails-app/taglib/demo/BetaTagLib.groovy').delete()
        build()

        then: 'it is described nowhere: not beside the index, not in it, not in the artifact'
        !packagedDescriptor('demo.BetaTagLib').isFile()
        !packagedManifest().contains('demo.BetaTagLib')
        !jarNames().any { it.contains('BetaTagLib') }

        and: 'and the one that remains is still described'
        packagedDescriptor('demo.AlphaTagLib').isFile()
        packagedManifest().contains('demo.AlphaTagLib')
    }

    void 'a renamed tag library does not leave its old name behind'() {
        given:
        build()

        when: 'renamed in place, which to a build is a deletion and an addition'
        new File(projectDir, 'grails-app/taglib/demo/BetaTagLib.groovy').delete()
        writeTagLib('GammaTagLib', 'beta', 'betaTag')
        build()

        then:
        !packagedDescriptor('demo.BetaTagLib').isFile()
        packagedDescriptor('demo.GammaTagLib').isFile()
        packagedManifest().contains('demo.GammaTagLib')
        !packagedManifest().contains('demo.BetaTagLib')
    }

    void 'a tag removed from a tag library is gone from the index it is described by'() {
        given:
        build()

        expect:
        packagedDescriptor('demo.AlphaTagLib').text.contains('alphaTag')

        when: 'the tag is removed and the project built again'
        writeTagLib('AlphaTagLib', 'alpha', 'renamedTag')
        build()

        then:
        !packagedDescriptor('demo.AlphaTagLib').text.contains('alphaTag:')
        packagedDescriptor('demo.AlphaTagLib').text.contains('renamedTag')
    }

    void 'an executable archive carries the descriptors and not the settings'() {
        given: 'a war copies whole directories off the runtime classpath, which is how the settings'
        buildTask('war')

        when: 'used to escape an exclusion declared on the archive task'
        List<String> entries = archiveNames('build/libs', '.war')

        then: 'the descriptors are there, where a page compiled at runtime can read them'
        entries.any { it == "WEB-INF/classes/${INDEX}/demo.AlphaTagLib.properties" as String }
        entries.any { it == "WEB-INF/classes/${INDEX}/index.properties" as String }

        and: 'the settings are nowhere in it'
        !entries.any { it.endsWith('compile-settings.properties') }

        and: 'and no descriptor is carried twice, in two places that would then disagree'
        entries.findAll { it.endsWith('demo.AlphaTagLib.properties') }.size() == 1
    }

    void 'the settings this build declared reach no archive'() {
        given: 'they say how this project compiles; a consumer inheriting them would compile by them'
        build()

        expect: 'not in the jar'
        !jarNames().any { it.endsWith('compile-settings.properties') }

        and: 'and not anywhere in the tree an executable archive is built from, which copies whole'
        !new File(projectDir, 'build/generated/grails-taglibs-packaged')
                .listFiles({ File dir, String name -> name == 'META-INF' } as FilenameFilter)
                .collect { new File(it, 'grails/taglibs/compile-settings.properties') }
                .any { it.exists() }
    }

    void 'nothing writes a second index into the class output'() {
        given: 'a build that writes the index owns it, so a copy there could only compete and go stale'
        build()

        expect:
        !new File(projectDir, "build/classes/groovy/main/${INDEX}").exists()
    }

    private void build() {
        buildTask('jar')
    }

    private void buildTask(String task) {
        Process process = new ProcessBuilder(System.getProperty('grails.e2e.gradlew'),
                '-p', projectDir.absolutePath, task, '--stacktrace')
                .redirectErrorStream(true)
                .start()
        String output = process.inputStream.getText('UTF-8')
        int status = process.waitFor()
        assert status == 0 : "building the application failed:\n${output}"
    }

    private File packagedDescriptor(String className) {
        new File(projectDir, "build/generated/grails-taglibs-packaged/${INDEX}/${className}.properties")
    }

    private String packagedManifest() {
        File manifest = new File(projectDir,
                "build/generated/grails-taglibs-packaged/${INDEX}/index.properties")
        manifest.isFile() ? manifest.text : ''
    }

    private List<String> jarNames() {
        archiveNames('build/libs', '.jar')
    }

    private List<String> archiveNames(String directory, String extension) {
        File archive = new File(projectDir, directory).listFiles()?.find { it.name.endsWith(extension) }
        assert archive != null : "the project produced no ${extension} in ${directory}"
        new ZipFile(archive).withCloseable { zip -> zip.entries().collect { it.name } }
    }

    private void writeTagLib(String className, String namespace, String tagName) {
        File dir = new File(projectDir, 'grails-app/taglib/demo')
        dir.mkdirs()
        new File(dir, "${className}.groovy").text = """
            package demo

            import grails.gsp.TagLib

            @TagLib
            class ${className} {
                static namespace = '${namespace}'

                def ${tagName}(Map attrs) {
                    out << 'hello'
                }
            }
        """
    }

    private void writeSettings() {
        String repo = System.getProperty('grails.e2e.localMavenRepo')
        new File(projectDir, 'settings.gradle').text = """
            pluginManagement {
                repositories {
                    maven { url = uri('${repo.replace('\\\\', '/')}') }
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri('${repo.replace('\\\\', '/')}') }
                    mavenCentral()
                }
            }
            rootProject.name = 'taglib-index-incremental-app'
        """
    }

    private void writeBuild() {
        String version = System.getProperty('grails.e2e.version')
        new File(projectDir, 'build.gradle').text = """
            plugins {
                id 'groovy'
                id 'war'
                id 'org.apache.grails.gradle.grails-gsp' version '${version}'
            }

            version = '0.1'
            group = 'demo'

            dependencies {
                // The gsp plugin alone applies no BOM, so this names it the way an application does.
                implementation platform('org.apache.grails:grails-bom:${version}')
                implementation 'org.apache.grails.views:grails-web-taglib'
                implementation 'org.apache.grails.views:grails-taglib'
                implementation 'org.apache.grails.views:grails-gsp-core'
            }
        """
    }
}
