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
package org.apache.grails.core.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Specification

/**
 * Covers the classes that extend types Groovy did not declare being callable from an image.
 *
 * <p>A module names them in a descriptor rather than in any code, and Groovy calls them through the
 * metaclass, so nothing else asks an image to keep them. The failure is a request rather than the
 * start-up: the framework extends the servlet request and response this way, and those are reached
 * while a page is rendered.</p>
 */
class GroovyExtensionModuleRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new GroovyExtensionModuleRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    private Set<String> registeredTypes() {
        hints.reflection().typeHints().collect { it.type.name } as Set<String>
    }

    private boolean invocable(String className) {
        invocableIn(hints, className)
    }

    private boolean invocableIn(RuntimeHints target, String className) {
        def hint = target.reflection().getTypeHint(TypeReference.of(className))
        hint != null && hint.memberCategories.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'the extensions the framework declares are registered'() {
        expect: 'these are named only in a descriptor, so nothing else would ask for them'
            registeredTypes().any { it.endsWith('Extension') }
    }

    void 'both kinds of extension a descriptor names are registered'() {
        given: 'a descriptor of this spec\'s own, so the assertion does not rest on what happens to ' +
                'be on the classpath of the module the registrar lives in'
            File directory = File.createTempDir()
            new File(directory, 'META-INF/services').mkdirs()
            new File(directory, 'META-INF/services/org.codehaus.groovy.runtime.ExtensionModule').text = '''
                    moduleName=spec-module
                    moduleVersion=1.0
                    extensionClasses=java.lang.StringBuilder
                    staticExtensionClasses=java.lang.StringBuffer
                    '''.stripIndent()
            RuntimeHints declared = new RuntimeHints()

        when:
            new GroovyExtensionModuleRuntimeHints().registerHints(declared,
                    new URLClassLoader([directory.toURI().toURL()] as URL[], getClass().classLoader))

        then: 'one extends instances and the other the type; a call is made through either'
            invocableIn(declared, 'java.lang.StringBuilder')
            invocableIn(declared, 'java.lang.StringBuffer')

        cleanup:
            directory.deleteDir()
    }

    void 'every extension named by a descriptor can have its methods called'() {
        expect: 'registering the type without its methods still leaves the call refused'
            registeredTypes().findAll { it.endsWith('Extension') }.every { invocable(it) }
    }

    void 'what the Groovy runtime reads as it starts is carried'() {
        expect: 'without these it cannot build its metaclasses, and fails before any application code'
            resourceRegistered('META-INF/dgminfo')
            resourceRegistered('META-INF/groovy-release-info.properties')

        and: 'and the descriptors naming the extensions, which is how it finds them'
            resourceRegistered('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule')
            resourceRegistered('META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule')
    }

    private boolean resourceRegistered(String resource) {
        RuntimeHintsPredicates.resource().forResource(resource).test(hints)
    }

    void 'a class loader that resolves nothing yields no hints rather than failing'() {
        given:
            RuntimeHints empty = new RuntimeHints()

        when:
            new GroovyExtensionModuleRuntimeHints().registerHints(empty, new URLClassLoader(new URL[0], null))

        then:
            noExceptionThrown()
    }
}
