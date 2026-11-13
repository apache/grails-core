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
package org.grails.plugins.i18n

import spock.lang.Specification
import spock.lang.TempDir

class I18nDescriptorsSpec extends Specification {

    @TempDir
    File tempDir

    /** One descriptor per directory; two in one directory would collapse into a single lookup hit. */
    private ClassLoader classLoaderWith(Map<String, String> descriptorsByDirectory) {
        List<URL> urls = descriptorsByDirectory.collect { String directory, String content ->
            File descriptor = new File(new File(tempDir, directory), I18nDescriptors.DESCRIPTOR_PATH)
            descriptor.parentFile.mkdirs()
            descriptor.text = content
            descriptor.parentFile.parentFile.parentFile.toURI().toURL()
        }
        new URLClassLoader(urls as URL[], null)
    }

    private static String descriptor(Map<String, String> entries) {
        entries.collect { key, value -> "${key}=${value}" }.join('\n') + '\n'
    }

    void 'a well-formed descriptor is read into its parts'() {
        given:
        ClassLoader classLoader = classLoaderWith(plugin: descriptor([
                'format.version': '1', 'artifact.type': 'plugin', 'artifact.name': 'spring-security-core',
                'artifact.version': '8.0.0', basenames: 'spring-security-core,spring-security-core-validation',
                locales: 'de,fr']))

        when:
        List<I18nDescriptor> descriptors = I18nDescriptors.load(classLoader)

        then:
        descriptors.size() == 1
        with(descriptors.first()) {
            !application
            name == 'spring-security-core'
            version == '8.0.0'
            basenames == ['spring-security-core', 'spring-security-core-validation']
            locales == ['de', 'fr']
        }
    }

    void 'a descriptor with no locales is legal'() {
        given:
        ClassLoader classLoader = classLoaderWith(app: descriptor([
                'format.version': '1', 'artifact.type': 'application', 'artifact.name': 'app',
                basenames: 'messages']))

        expect:
        I18nDescriptors.load(classLoader).first().locales == []
    }

    void 'a descriptor from a newer Grails is rejected rather than half-understood'() {
        given:
        ClassLoader classLoader = classLoaderWith(app: descriptor([
                'format.version': '2', 'artifact.type': 'application', 'artifact.name': 'app',
                basenames: 'messages']))

        when:
        I18nDescriptors.load(classLoader)

        then:
        IllegalStateException e = thrown()
        e.message.contains('Unsupported i18n descriptor format version')
    }

    void 'a missing required field is reported with the offending descriptor'() {
        given:
        ClassLoader classLoader = classLoaderWith(app: descriptor([
                'format.version': '1', 'artifact.type': 'application', 'artifact.name': 'app']))

        when:
        I18nDescriptors.load(classLoader)

        then:
        IllegalStateException e = thrown()
        e.message.contains("missing 'basenames'")
    }

    void 'an unknown artifact type is rejected'() {
        given:
        ClassLoader classLoader = classLoaderWith(app: descriptor([
                'format.version': '1', 'artifact.type': 'library', 'artifact.name': 'app',
                basenames: 'messages']))

        when:
        I18nDescriptors.load(classLoader)

        then:
        IllegalStateException e = thrown()
        e.message.contains('Invalid artifact.type')
    }

    void 'two application descriptors are rejected rather than letting classpath order pick one'() {
        given:
        ClassLoader classLoader = classLoaderWith(
                one: descriptor(['format.version': '1', 'artifact.type': 'application',
                                 'artifact.name': 'app-one', basenames: 'messages']),
                two: descriptor(['format.version': '1', 'artifact.type': 'application',
                                 'artifact.name': 'app-two', basenames: 'messages']))

        when:
        I18nDescriptors.load(classLoader)

        then:
        IllegalStateException e = thrown()
        e.message.contains('application i18n descriptors')
    }

    void 'two descriptors for the same plugin are rejected'() {
        given: 'e.g. two versions of one plugin on the classpath'
        ClassLoader classLoader = classLoaderWith(
                one: descriptor(['format.version': '1', 'artifact.type': 'plugin',
                                 'artifact.name': 'alpha-plugin', basenames: 'alpha-plugin']),
                two: descriptor(['format.version': '1', 'artifact.type': 'plugin',
                                 'artifact.name': 'alpha-plugin', basenames: 'alpha-plugin']))

        when:
        I18nDescriptors.load(classLoader)

        then:
        IllegalStateException e = thrown()
        e.message.contains('More than one i18n descriptor for plugin(s) alpha-plugin')
    }

    void 'an empty classpath yields no descriptors rather than failing'() {
        expect:
        I18nDescriptors.load(new URLClassLoader([] as URL[], null)).isEmpty()
    }

    void 'two plugin names that differ only in spelling are still rejected'() {
        given: 'descriptors are matched to discovered plugins by normalised name'
        ClassLoader classLoader = classLoaderWith(
                hyphenated: descriptor(['format.version': '1', 'artifact.type': 'plugin',
                                        'artifact.name': 'spring-security-core',
                                        basenames: 'spring-security-core']),
                camelCase: descriptor(['format.version': '1', 'artifact.type': 'plugin',
                                       'artifact.name': 'springSecurityCore',
                                       basenames: 'springSecurityCore']))

        when:
        I18nDescriptors.load(classLoader)

        then: 'comparing the raw names would pass here, then drop one silently downstream'
        IllegalStateException e = thrown()
        e.message.contains('spring-security-core')
        e.message.contains('springSecurityCore')
        e.message.contains('normalised form')
    }

    void 'plugins whose names do not normalise together are accepted'() {
        given:
        ClassLoader classLoader = classLoaderWith(
                one: descriptor(['format.version': '1', 'artifact.type': 'plugin',
                                 'artifact.name': 'alpha-plugin', basenames: 'alpha-plugin']),
                two: descriptor(['format.version': '1', 'artifact.type': 'plugin',
                                 'artifact.name': 'beta-plugin', basenames: 'beta-plugin']))

        expect:
        I18nDescriptors.load(classLoader).size() == 2
    }
}
