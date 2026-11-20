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
package org.apache.grails.forge.buildlogic.shadowjar

import java.util.zip.ZipFile

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class GrailsGroovyExtensionTransformerSpec extends Specification {

    private static final String DESCRIPTOR = 'META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule'

    @TempDir
    File projectDir

    void "shadowJar merges #count extension descriptors reproducibly with the configuration cache"() {
        given:
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'extensions'"
        new File(projectDir, 'build.gradle').text = '''
            import org.apache.grails.forge.buildlogic.shadowjar.GrailsGroovyExtensionTransformer

            plugins {
                id 'java'
                id 'com.gradleup.shadow'
            }

            tasks.named('shadowJar') {
                from('first')
                from('second')
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                preserveFileTimestamps = false
                transform(GrailsGroovyExtensionTransformer)
            }
        '''
        File unrelated = new File(projectDir, 'first/unrelated.properties')
        unrelated.parentFile.mkdirs()
        unrelated.text = 'value=unchanged\n'
        if (count > 0) {
            descriptor('first', 'first-module', '2.0', 'example.FirstExtensions', 'example.FirstStaticExtensions')
        }
        if (count > 1) {
            descriptor('second', 'second-module', '3.0', 'example.SecondExtensions', 'example.SecondStaticExtensions')
        }

        when:
        BuildResult stored = shadowJar(null)
        byte[] firstJar = new File(projectDir, 'build/libs/extensions-all.jar').bytes
        BuildResult reused = shadowJar('86400')
        File jar = new File(projectDir, 'build/libs/extensions-all.jar')

        then:
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')
        jar.bytes == firstJar

        and:
        new ZipFile(jar).withCloseable { ZipFile zip ->
            assert zip.getInputStream(zip.getEntry('unrelated.properties')).text == 'value=unchanged\n'
            def entry = zip.getEntry(DESCRIPTOR)
            if (count == 0) {
                assert entry == null
            } else {
                assert zip.entries().findAll { it.name == DESCRIPTOR }.size() == 1
                String text = zip.getInputStream(entry).getText('ISO-8859-1')
                assert !text.readLines().any { it.startsWith('#') }
                Properties properties = new Properties()
                properties.load(new StringReader(text))
                assert properties == [
                        moduleName: count == 1 ? 'first-module' : 'MergedByGrailsShadowJar',
                        moduleVersion: count == 1 ? '2.0' : '1.0.0',
                        extensionClasses: count == 1 ? 'example.FirstExtensions' : 'example.FirstExtensions,example.SecondExtensions',
                        staticExtensionClasses: count == 1 ? 'example.FirstStaticExtensions' : 'example.FirstStaticExtensions,example.SecondStaticExtensions'
                ]
            }
            true
        }

        where:
        count << [0, 1, 2]
    }

    private void descriptor(String directory, String name, String version, String extensions, String staticExtensions) {
        File file = new File(projectDir, "${directory}/${DESCRIPTOR}")
        file.parentFile.mkdirs()
        file.text = """moduleName=${name}
moduleVersion=${version}
extensionClasses=${extensions}
staticExtensionClasses=${staticExtensions}
ignored=value
"""
    }

    private BuildResult shadowJar(String epoch) {
        Map<String, String> environment = new HashMap<>(System.getenv())
        environment.remove('SOURCE_DATE_EPOCH')
        if (epoch != null) {
            environment.put('SOURCE_DATE_EPOCH', epoch)
        }
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('clean', 'shadowJar', '--configuration-cache', '--stacktrace')
                .withEnvironment(environment)
                .withPluginClasspath()
                .build()
    }
}
