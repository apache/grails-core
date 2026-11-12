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
package org.apache.grails.web.http.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import grails.web.http.ContentSecurityPolicyNonce;

/**
 * Registers what reading the nonce of a nonce-based Content-Security-Policy reflects on.
 *
 * <p>Spring Security's nonce is a private class behind a public interface, and
 * {@link ContentSecurityPolicyNonce#nonceOf} reads it through that interface. An image has no
 * metadata for the private class, so only the interface can be registered, and the read must not
 * reflect on anything else.</p>
 *
 * @since 9.1
 */
public class ContentSecurityPolicyRuntimeHints implements RuntimeHintsRegistrar {

    /** What a hinted type is registered for: the members Groovy reads to choose an overload. */
    private static final MemberCategory[] DISPATCHED_MEMBERS = {
        MemberCategory.INVOKE_DECLARED_METHODS,
        MemberCategory.INVOKE_PUBLIC_METHODS,
        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
    };

    /**
     * The nonce Spring Security's nonce filter sets. Named as a string and registered only when
     * present, since an application need not use Spring Security.
     */
    private static final String SPRING_SECURITY_NONCE =
            "org.springframework.security.web.header.ContentSecurityPolicyNonce";

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // a controller, a service or a dynamically compiled page reads the nonce through Groovy's
        // dynamic dispatch
        hints.reflection().registerType(ContentSecurityPolicyNonce.class, DISPATCHED_MEMBERS);
        hints.reflection().registerTypeIfPresent(classLoader, SPRING_SECURITY_NONCE, DISPATCHED_MEMBERS);
    }
}
