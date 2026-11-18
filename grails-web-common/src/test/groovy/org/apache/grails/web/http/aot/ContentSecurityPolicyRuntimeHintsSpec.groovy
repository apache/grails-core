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
package org.apache.grails.web.http.aot

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.core.io.support.SpringFactoriesLoader
import spock.lang.Specification

import grails.web.http.ContentSecurityPolicyNonce

/**
 * Covers reading the nonce of a nonce-based Content-Security-Policy in an ahead-of-time image, where
 * only registered types can be reflected on.
 */
class ContentSecurityPolicyRuntimeHintsSpec extends Specification {

    private static final String SPRING_SECURITY_NONCE = 'org.springframework.security.web.header.ContentSecurityPolicyNonce'

    RuntimeHints hints = new RuntimeHints()

    void 'Spring Security\'s nonce can be read through its public interface'() {
        when:
        new ContentSecurityPolicyRuntimeHints().registerHints(hints, getClass().classLoader)

        then:
        RuntimeHintsPredicates.reflection().onMethodInvocation(SPRING_SECURITY_NONCE, 'getNonce').test(hints)
    }

    void 'the Grails nonce can be read by Groovy\'s dynamic dispatch'() {
        when:
        new ContentSecurityPolicyRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'a controller or a dynamically compiled page calling the reader, or the getter'
        RuntimeHintsPredicates.reflection().onMethodInvocation(ContentSecurityPolicyNonce, 'nonceOf').test(hints)
        RuntimeHintsPredicates.reflection().onMethodInvocation(ContentSecurityPolicyNonce, 'getNonce').test(hints)
    }

    void 'an application without Spring Security still registers the Grails nonce'() {
        when: 'a class loader that resolves nothing, as an application without Spring Security has'
        new ContentSecurityPolicyRuntimeHints().registerHints(hints, new URLClassLoader(new URL[0], null))

        then:
        hints.reflection().getTypeHint(TypeReference.of(SPRING_SECURITY_NONCE)) == null
        hints.reflection().getTypeHint(TypeReference.of(ContentSecurityPolicyNonce)) != null
    }

    void 'the hints are registered for an ahead-of-time build'() {
        expect:
        SpringFactoriesLoader.forResourceLocation('META-INF/spring/aot.factories')
                .load(RuntimeHintsRegistrar)*.class.contains(ContentSecurityPolicyRuntimeHints)
    }
}
