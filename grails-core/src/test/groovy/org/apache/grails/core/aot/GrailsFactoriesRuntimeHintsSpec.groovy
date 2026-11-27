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
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Specification
import spock.lang.TempDir

import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.core.artefact.ServiceArtefactHandler

/**
 * Covers the classes named in {@code META-INF/grails.factories} being creatable in an image.
 *
 * <p>The framework reads the file and creates what it names through constructors, so an image built
 * without these stopped while the application was initialized, unable to create the first artefact
 * handler.</p>
 */
class GrailsFactoriesRuntimeHintsSpec extends Specification {

    @TempDir
    File classpathRoot

    RuntimeHints hints = new RuntimeHints()

    private ClassLoader withFactories(String declarations) {
        def factories = new File(classpathRoot, 'META-INF/grails.factories')
        factories.parentFile.mkdirs()
        factories.text = declarations
        new URLClassLoader([classpathRoot.toURI().toURL()] as URL[], getClass().classLoader)
    }

    private boolean creatable(Class<?> type) {
        RuntimeHintsPredicates.reflection().onType(type)
                .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
    }

    void 'where the factories are declared is carried'() {
        when:
            new GrailsFactoriesRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'it is read by name at run time'
            RuntimeHintsPredicates.resource().forResource('META-INF/grails.factories').test(hints)
    }

    void 'the artefact handlers the framework declares can be created'() {
        when:
            new GrailsFactoriesRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'the one an image failed on first, and the others declared beside it'
            creatable(DomainClassArtefactHandler)
            creatable(ServiceArtefactHandler)
    }

    void 'every class a declaration lists is covered, however it is written'() {
        given: 'two factories nothing else declares, listed across lines with spaces around the comma'
            def loader = withFactories(
                    "example.Factory=${FirstFactoryFixture.name} ,\\\n    ${SecondFactoryFixture.name}")

        when:
            new GrailsFactoriesRuntimeHints().registerHints(hints, loader)

        then:
            creatable(FirstFactoryFixture)
            creatable(SecondFactoryFixture)
    }

    void 'a class that cannot be loaded is skipped'() {
        given:
            def loader = withFactories('grails.core.ArtefactHandler=com.example.AbsentArtefactHandler')

        when:
            new GrailsFactoriesRuntimeHints().registerHints(hints, loader)

        then: 'registering it would fail the image build, which parses what it is asked to keep'
            hints.reflection().typeHints().noneMatch { it.type.name == 'com.example.AbsentArtefactHandler' }
    }
}

/** A factory no real declaration names, so finding it proves the declaration in the test was read. */
class FirstFactoryFixture {
}

/** The second entry of the same declaration. */
class SecondFactoryFixture {
}
