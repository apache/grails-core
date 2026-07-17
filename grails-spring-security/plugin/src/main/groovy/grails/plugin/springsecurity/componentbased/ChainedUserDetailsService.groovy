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
package grails.plugin.springsecurity.componentbased

import groovy.transform.CompileStatic

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/**
 * A {@link UserDetailsService} that delegates to a fixed ordered list of
 * delegate services. The first delegate that successfully resolves the
 * username wins; if every delegate throws
 * {@link UsernameNotFoundException}, this service rethrows that exception.
 *
 * <p>Used by {@link ComponentBasedConfigBlender#chainUserDetailsServices} to
 * blend the Grails plugin's primary {@code userDetailsService} (the GORM-backed
 * {@code GormUserDetailsService}) with user-defined
 * {@link org.springframework.security.provisioning.InMemoryUserDetailsManager}
 * or {@link org.springframework.security.provisioning.JdbcUserDetailsManager}
 * beans defined per
 * <a href="https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter">
 * Spring Security without the WebSecurityConfigurerAdapter</a>.</p>
 *
 * @since 8.0.0
 */
@CompileStatic
class ChainedUserDetailsService implements UserDetailsService {

    final List<UserDetailsService> delegates

    ChainedUserDetailsService(List<UserDetailsService> delegates) {
        if (!delegates) {
            throw new IllegalArgumentException('delegates must not be empty')
        }
        this.delegates = delegates.asImmutable()
    }

    @Override
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UsernameNotFoundException lastException = null
        for (UserDetailsService delegate in delegates) {
            try {
                UserDetails details = delegate.loadUserByUsername(username)
                if (details != null) {
                    return details
                }
            }
            catch (UsernameNotFoundException ex) {
                lastException = ex
            }
        }
        throw lastException ?: new UsernameNotFoundException("User '${username}' not found in any chained UserDetailsService")
    }
}
