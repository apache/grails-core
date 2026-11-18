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
package grails.web.http;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;

import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

/**
 * The nonce of a nonce-based Content-Security-Policy, which a request carries under the
 * {@value #ATTRIBUTE} attribute.
 *
 * <p>The Grails security headers filter puts one on each request when
 * {@code grails.security.headers.content-security-policy.value} names the nonce with the
 * {@code {nonce}} placeholder. {@value #ATTRIBUTE} is also the attribute Spring Security's
 * {@code ContentSecurityPolicyNonceGeneratingFilter} sets by default, so
 * {@link #nonceOf(HttpServletRequest)} reads the nonce whichever of the two generated it.</p>
 *
 * @since 9.1
 */
public final class ContentSecurityPolicyNonce {

    /**
     * The request attribute that carries the nonce.
     */
    public static final String ATTRIBUTE = "_csp";

    /**
     * The public type of the nonce Spring Security generates, whose implementation is a private
     * class. It is read through this type, the one registered for reflection, since this module
     * does not depend on Spring Security.
     */
    private static final String SPRING_SECURITY_NONCE =
            "org.springframework.security.web.header.ContentSecurityPolicyNonce";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int NONCE_BYTES = 16;

    private final Supplier<String> generated = SingletonSupplier.of(ContentSecurityPolicyNonce::generate);

    /**
     * The nonce, generated the first time it is read: 128 bits from a {@link SecureRandom},
     * Base64-encoded without padding, as Spring Security generates one.
     *
     * @return the nonce
     */
    public String getNonce() {
        return this.generated.get();
    }

    /**
     * The nonce the request carries: one the Grails security headers filter generated, or one
     * Spring Security's {@code ContentSecurityPolicyNonceGeneratingFilter} generated.
     *
     * @param request the request
     * @return the nonce, or {@code null} when the request carries neither
     */
    public static @Nullable String nonceOf(HttpServletRequest request) {
        Object holder = request.getAttribute(ATTRIBUTE);
        if (holder instanceof ContentSecurityPolicyNonce nonce) {
            return nonce.getNonce();
        }
        Method getNonce = springSecurityGetNonce(holder);
        Object nonce = (getNonce != null) ? ReflectionUtils.invokeMethod(getNonce, holder) : null;
        return (nonce instanceof String value && StringUtils.hasText(value)) ? value : null;
    }

    private static @Nullable Method springSecurityGetNonce(@Nullable Object holder) {
        if (holder == null) {
            return null;
        }
        // the loader of the holder's own class, which loaded the interface it implements
        ClassLoader classLoader = holder.getClass().getClassLoader();
        if (!ClassUtils.isPresent(SPRING_SECURITY_NONCE, classLoader)) {
            return null;
        }
        Class<?> nonceType = ClassUtils.resolveClassName(SPRING_SECURITY_NONCE, classLoader);
        return nonceType.isInstance(holder) ? ClassUtils.getMethodIfAvailable(nonceType, "getNonce") : null;
    }

    private static String generate() {
        byte[] bytes = new byte[NONCE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
}
