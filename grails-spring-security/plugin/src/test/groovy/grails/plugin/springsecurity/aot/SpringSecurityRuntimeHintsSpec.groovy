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
package grails.plugin.springsecurity.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.core.io.support.SpringFactoriesLoader
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

import grails.plugin.springsecurity.userdetails.GrailsUser

class SpringSecurityRuntimeHintsSpec extends Specification {

    void 'the default configuration can be created in an image'() {
        given:
            RuntimeHints hints = new RuntimeHints()

        when:
            new SpringSecurityRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'the script the plugin loads its defaults from is compiled, and so found by name'
            getClass().classLoader.loadClass('DefaultSecurityConfig').getConstructor()
            RuntimeHintsPredicates.reflection()
                    .onType(TypeReference.of('DefaultSecurityConfig'))
                    .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
                    .test(hints)
    }

    void 'the user cache can be created in an image, which with cacheUsers stores users by value'() {
        given:
            RuntimeHints hints = new RuntimeHints()

        when:
            new SpringSecurityRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'Ehcache creates the copier for the key reflectively'
            getClass().classLoader.loadClass('org.ehcache.jsr107.Eh107IdentityCopier').getConstructor()
            RuntimeHintsPredicates.reflection()
                    .onType(TypeReference.of('org.ehcache.jsr107.Eh107IdentityCopier'))
                    .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
                    .test(hints)
    }

    void 'every class a cached user is copied with is registered for serialization'() {
        given: 'a user as the plugin loads one, with a role and a GORM id'
            RuntimeHints hints = new RuntimeHints()
            new SpringSecurityRuntimeHints().registerHints(hints, getClass().classLoader)
            GrailsUser user = new GrailsUser('user', 'stored', true, true, true, true,
                    [new SimpleGrantedAuthority('ROLE_USER')], 42L)

        when: 'it is serialized, as the user cache copies it, noting each class written'
            Set<Class<?>> written = new LinkedHashSet<>()
            new ObjectOutputStream(new ByteArrayOutputStream()) {
                @Override
                protected void annotateClass(Class<?> type) {
                    written << type
                }
            }.withCloseable { it.writeObject(user) }

        then: 'a change in what Spring Security writes fails here, rather than in an image'
            written.containsAll([GrailsUser, Long, SimpleGrantedAuthority])
            written.findAll { Class<?> type -> !RuntimeHintsPredicates.reflection().onJavaSerialization(type, true).test(hints) } == [] as Set
    }

    void 'ahead-of-time processing finds the hints'() {
        expect:
            SpringFactoriesLoader.forResourceLocation('META-INF/spring/aot.factories')
                    .load(RuntimeHintsRegistrar)*.getClass()
                    .contains(SpringSecurityRuntimeHints)
    }
}
