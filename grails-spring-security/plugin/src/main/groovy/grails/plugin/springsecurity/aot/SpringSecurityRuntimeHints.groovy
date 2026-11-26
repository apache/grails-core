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

import groovy.transform.CompileStatic

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeHint
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User

import grails.plugin.springsecurity.userdetails.GrailsUser

/**
 * Registers what the plugin reaches reflectively before any bean exists.
 *
 * <p>{@code ConfigSlurper} creates the plugin's default configuration through Groovy's cached constructors, which
 * {@code traceNativeMetadata} does not see, though it records everything the script goes on to call.</p>
 *
 * <p>With {@code cacheUsers}, the user cache stores users by value, so Ehcache creates its copier for the key
 * reflectively when the cache is created, and copies each user by Java serialization. The users the plugin creates are
 * registered for that: a {@code User} or {@code GrailsUser} with a {@code Long} id, its {@code SimpleGrantedAuthority}
 * roles, and the sorted set {@code User} keeps them in. A user class or id type of the application's own is recorded
 * by {@code traceNativeMetadata} when a login form is listed in {@code forms}. These are registered whether or not
 * {@code cacheUsers} is set, as the configuration is not known here.</p>
 *
 * @since 9.0
 */
@CompileStatic
class SpringSecurityRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerTypeIfPresent(classLoader, 'DefaultSecurityConfig',
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
        hints.reflection().registerTypeIfPresent(classLoader, 'org.ehcache.jsr107.Eh107IdentityCopier',
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
        [User, GrailsUser, SimpleGrantedAuthority, String, Long, Number].each { Class<?> type ->
            hints.reflection().registerJavaSerialization(type)
        }
        ['org.springframework.security.core.userdetails.User$AuthorityComparator', 'java.util.TreeSet',
         'java.util.Collections$UnmodifiableSet', 'java.util.Collections$UnmodifiableCollection'].each { String name ->
            hints.reflection().registerTypeIfPresent(classLoader, name) { TypeHint.Builder type -> type.withJavaSerialization(true) }
        }
    }
}
