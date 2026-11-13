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
package org.grails.gradle.plugin.i18n

import spock.lang.Specification
import spock.lang.TempDir

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class GenerateI18nDescriptorTaskSpec extends Specification {

    @TempDir
    File projectDir

    private GenerateI18nDescriptorTask task(String type, String name, List<String> bundleNames,
            List<String> declared = []) {
        File bundles = new File(projectDir, 'grails-app/i18n')
        bundles.mkdirs()
        bundleNames.each { new File(bundles, it).text = 'a.code=value\n' }

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.tasks.register('generateI18nDescriptor', GenerateI18nDescriptorTask) { GenerateI18nDescriptorTask it ->
            it.bundleDirectory.set(bundles)
            it.artifactType.set(type)
            it.artifactName.set(name)
            it.artifactVersion.set('1.0.0')
            it.declaredBasenames.set(declared)
            it.outputDirectory.set(new File(projectDir, 'out'))
        }
        project.tasks.named('generateI18nDescriptor', GenerateI18nDescriptorTask).get()
    }

    private Properties descriptorOf(GenerateI18nDescriptorTask task) {
        task.generate()
        File file = new File(task.outputDirectory.get().asFile, GenerateI18nDescriptorTask.DESCRIPTOR_PATH)
        Properties properties = new Properties()
        if (file.exists()) {
            file.withInputStream { properties.load(it) }
        }
        properties
    }

    void 'an application descriptor records its base names and locales'() {
        when:
        Properties descriptor = descriptorOf(task(GenerateI18nDescriptorTask.TYPE_APPLICATION, 'my-app',
                ['messages.properties', 'messages_de.properties', 'messages_pt_BR.properties']))

        then:
        descriptor.'format.version' == GenerateI18nDescriptorTask.FORMAT_VERSION
        descriptor.'artifact.type' == 'application'
        descriptor.'artifact.name' == 'my-app'
        descriptor.'artifact.version' == '1.0.0'
        descriptor.basenames == 'messages'
        descriptor.locales == 'de,pt_BR'
    }

    void 'a plugin may ship several bundles inside its own namespace'() {
        when:
        Properties descriptor = descriptorOf(task(GenerateI18nDescriptorTask.TYPE_PLUGIN, 'spring-security-core',
                ['spring-security-core.properties', 'spring-security-core_fr.properties',
                 'spring-security-core-validation.properties']))

        then:
        descriptor.basenames == 'spring-security-core,spring-security-core-validation'
        descriptor.locales == 'fr'
    }

    void 'a plugin bundle outside the plugin namespace fails the build'() {
        when: 'Spring resolves a base name to the first match, so a colliding bundle is shadowed'
        descriptorOf(task(GenerateI18nDescriptorTask.TYPE_PLUGIN, 'spring-security-oauth2',
                ['messages.properties']))

        then:
        InvalidUserDataException e = thrown()
        e.message.contains("ships message bundles outside its own namespace")
        e.message.contains("'messages.properties'")
    }

    void 'an application is free to use any base name'() {
        when: 'there is only one application, so its base names cannot collide with a sibling'
        Properties descriptor = descriptorOf(task(GenerateI18nDescriptorTask.TYPE_APPLICATION, 'my-app',
                ['messages.properties', 'errors.properties']))

        then:
        descriptor.basenames == 'errors,messages'
    }

    void 'no bundles means no descriptor rather than an empty one'() {
        expect:
        descriptorOf(task(GenerateI18nDescriptorTask.TYPE_APPLICATION, 'my-app', [])).isEmpty()
    }

    void 'the descriptor carries no timestamp so the build stays reproducible'() {
        given:
        GenerateI18nDescriptorTask task = task(GenerateI18nDescriptorTask.TYPE_APPLICATION, 'my-app',
                ['messages.properties'])

        when:
        task.generate()
        String first = new File(task.outputDirectory.get().asFile, GenerateI18nDescriptorTask.DESCRIPTOR_PATH).text
        task.generate()
        String second = new File(task.outputDirectory.get().asFile, GenerateI18nDescriptorTask.DESCRIPTOR_PATH).text

        then:
        first == second
        !first.contains(new Date().format('yyyy'))
    }
}
