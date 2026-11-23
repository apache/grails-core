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
package org.apache.grails.cli

import java.util.jar.JarFile

import spock.lang.Specification

class ShadowJarMetadataSpec extends Specification {

    void "the #stage jar has usable merged Groovy extensions without a build-time comment"() {
        given:
        File file = new File(System.getProperty(property))
        String descriptor = new JarFile(file).withCloseable { JarFile jar ->
            assert jar.getJarEntry('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule') == null
            jar.getInputStream(jar.getJarEntry('META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule')).getText('ISO-8859-1')
        }
        Properties extensions = new Properties()
        extensions.load(new StringReader(descriptor))

        expect:
        extensions.stringPropertyNames() == ['moduleName', 'moduleVersion', 'extensionClasses', 'staticExtensionClasses'] as Set
        extensions.getProperty('moduleName')
        extensions.getProperty('extensionClasses')
        extensions.getProperty('extensionClasses').split(',').contains('org.grails.spring.context.ApplicationContextExtension')
        extensions.getProperty('extensionClasses').split(',').contains('org.apache.groovy.xml.extensions.XmlExtensions')
        !descriptor.contains('\r')
        descriptor.readLines().every { String line -> !line.startsWith('#') }

        where:
        stage         | property
        'intermediate' | 'grails.cli.intermediate.jar'
        'published'    | 'grails.cli.all.jar'
    }

    void "shadow packaging preserves every bundled pack, compiled page and index byte"() {
        given:
        Map<String, byte[]> intermediate = packEntries('grails.cli.intermediate.jar')
        Map<String, byte[]> published = packEntries('grails.cli.all.jar')

        expect:
        intermediate.containsKey('META-INF/grails-forge-bundled/packs')
        intermediate.containsKey('META-INF/grails-forge-pages/index')
        intermediate.keySet().any { String name -> name.startsWith('org/apache/grails/forge/core/pack/pages/') && name.endsWith('.class') }
        intermediate.keySet() == published.keySet()
        intermediate.every { String name, byte[] bytes -> Arrays.equals(bytes, published[name]) }
    }

    private static Map<String, byte[]> packEntries(String property) {
        new JarFile(new File(System.getProperty(property))).withCloseable { JarFile jar ->
            jar.entries().toList().findAll { entry ->
                !entry.directory && (entry.name.startsWith('META-INF/grails-forge-') || entry.name.startsWith('org/apache/grails/forge/core/pack/pages/'))
            }.collectEntries { entry -> [entry.name, jar.getInputStream(entry).bytes] }
        }
    }
}
