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
import groovy.util.logging.Slf4j

import org.springframework.context.ApplicationContext
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * Blends user-defined Spring Security configuration components (the patterns
 * recommended by
 * <a href="https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter">
 * Spring Security without the WebSecurityConfigurerAdapter</a>) into the
 * Grails Spring Security plugin's runtime structures.
 *
 * <p>The Grails plugin pre-dates the component-based model and owns the servlet
 * security stack via its own {@code FilterChainProxy}, {@code ProviderManager}
 * and {@code GormUserDetailsService}. This blender lets users keep using the
 * blog post's idioms ({@code @Bean SecurityFilterChain},
 * {@code @Bean AuthenticationProvider}, {@code spring.security.user.*}) and
 * have them coexist with the plugin's {@code grails.plugin.springsecurity.*}
 * configuration instead of being silently ignored.</p>
 *
 * <p>Each merge method is idempotent and safe to invoke multiple times.</p>
 *
 * <p>Each merge is enabled by default and can be disabled individually via
 * configuration:</p>
 *
 * <pre>
 * grails:
 *   plugin:
 *     springsecurity:
 *       componentBased:
 *         autoMergeSecurityFilterChain: false       # disable user @Bean SecurityFilterChain merge
 *         autoMergeAuthenticationProviders: false   # disable user @Bean AuthenticationProvider merge
 *         autoChainUserDetailsServices: false       # disable user @Bean UserDetailsService chaining
 *         bridgeSpringSecurityUserProperties: false # disable spring.security.user.* property bridge
 * </pre>
 *
 * @since 8.0.0
 */
@CompileStatic
@Slf4j
class ComponentBasedConfigBlender {

    /**
     * Adds user-defined {@link SecurityFilterChain} beans to the plugin's filter
     * chain list. User chains are <strong>prepended</strong> (higher precedence)
     * because their request matchers are typically more specific than the
     * plugin's catch-all chain.
     *
     * <p>The plugin's own chains are not registered as named beans (they are
     * appended directly to {@code securityFilterChains} in
     * {@code SpringSecurityUtils.buildFilterChains}), so every
     * {@code SecurityFilterChain} bean visible to the application context is
     * treated as user-defined.</p>
     *
     * @param applicationContext the application context to scan
     * @param pluginChains the plugin's mutable {@code securityFilterChains} list
     *        (the same list the plugin's {@code FilterChainProxy} references)
     * @return the number of user chains merged
     */
    static int mergeUserSecurityFilterChains(ApplicationContext applicationContext,
            List<SecurityFilterChain> pluginChains) {
        Map<String, SecurityFilterChain> beanMap = applicationContext.getBeansOfType(SecurityFilterChain)
        List<SecurityFilterChain> userChains = beanMap.values().toList()
        if (userChains) {
            pluginChains.addAll(0, userChains)
            log.info 'Auto-merged {} user-defined SecurityFilterChain beans into the plugin filter chain (prepended for precedence): {}',
                    userChains.size(), beanMap.keySet()
        }
        userChains.size()
    }

    /**
     * Adds user-defined {@link AuthenticationProvider} beans to the plugin's
     * {@link ProviderManager}. User providers are <strong>appended</strong> so
     * that the plugin's primary providers (typically the GORM-backed DAO
     * provider) are tried first.
     *
     * <p>Providers already present in the manager (for example, those declared
     * via {@code grails.plugin.springsecurity.providerNames}) are not
     * re-added.</p>
     *
     * @param applicationContext the application context to scan
     * @param authenticationManager the plugin's {@code authenticationManager}
     *        bean (a {@code ProviderManager})
     * @return the number of user providers merged
     */
    static int mergeUserAuthenticationProviders(ApplicationContext applicationContext,
            ProviderManager authenticationManager) {
        Map<String, AuthenticationProvider> beanMap = applicationContext.getBeansOfType(AuthenticationProvider)
        Set<AuthenticationProvider> existing = authenticationManager.providers as Set
        List<AuthenticationProvider> userProviders = beanMap.values().findAll { !(it in existing) }.toList()
        if (userProviders) {
            authenticationManager.providers.addAll(userProviders)
            Set<String> mergedNames = beanMap.findAll { it.value in userProviders }.keySet()
            log.info 'Auto-merged {} user-defined AuthenticationProvider beans into the plugin authenticationManager (appended): {}',
                    userProviders.size(), mergedNames
        }
        userProviders.size()
    }

    /**
     * Wraps the plugin's primary {@code UserDetailsService} bean in a chain that
     * also queries every other user-defined {@link UserDetailsService} bean in
     * the application context. The plugin's bean is queried first; user beans
     * are queried in bean-name order if the plugin's bean throws
     * {@link org.springframework.security.core.userdetails.UsernameNotFoundException}.
     *
     * <p>This method does not modify the plugin's bean directly. Instead it
     * returns a {@link UserDetailsService} that callers can substitute in their
     * authentication providers if they want the chained behaviour. The Grails
     * plugin core does not currently rewire its providers to use the chained
     * UDS automatically; users who want this behaviour should declare the
     * returned service as a bean and reference it via
     * {@code grails.plugin.springsecurity.dao.userDetailsService} (or the
     * provider-specific equivalent).</p>
     *
     * @param applicationContext the application context to scan
     * @param primaryUserDetailsService the plugin's primary
     *        {@code userDetailsService} bean (typically
     *        {@code GormUserDetailsService})
     * @return a chained {@code UserDetailsService}, or the primary unchanged if
     *         no other user beans are present
     */
    static UserDetailsService chainUserDetailsServices(ApplicationContext applicationContext,
            UserDetailsService primaryUserDetailsService) {
        Map<String, UserDetailsService> beanMap = applicationContext.getBeansOfType(UserDetailsService)
        List<UserDetailsService> additional = beanMap.values()
                .findAll { it !== primaryUserDetailsService }
                .toList()
        if (!additional) {
            return primaryUserDetailsService
        }
        List<UserDetailsService> chain = [primaryUserDetailsService] + additional
        log.info 'Chaining {} additional UserDetailsService beans behind the plugin primary: {}',
                additional.size(), beanMap.findAll { it.value in additional }.keySet()
        new ChainedUserDetailsService(chain)
    }

    /**
     * If {@code spring.security.user.name} (and optionally
     * {@code spring.security.user.password} / {@code spring.security.user.roles})
     * are set, returns an {@link InMemoryUserDetailsManager} containing that
     * single user, mimicking what Spring Boot's
     * {@code UserDetailsServiceAutoConfiguration} would have created had it not
     * been excluded by {@code SecurityAutoConfigurationExcluder}.
     *
     * <p>Defaults follow Spring Boot's defaults:</p>
     * <ul>
     *   <li>{@code password} - {@code user} (literal)</li>
     *   <li>{@code roles} - {@code [USER]}</li>
     * </ul>
     *
     * <p>The returned manager is intended to be combined with
     * {@link #chainUserDetailsServices} so it coexists with the plugin's primary
     * {@code userDetailsService}. Returns {@code null} if
     * {@code spring.security.user.name} is not set.</p>
     *
     * @param userName the resolved {@code spring.security.user.name} value
     * @param userPassword the resolved {@code spring.security.user.password}
     *        value (may be {@code null})
     * @param userRoles the resolved {@code spring.security.user.roles} value
     *        (may be {@code null})
     * @return the bridged {@code InMemoryUserDetailsManager}, or {@code null} if
     *         the bridge is not applicable
     */
    static InMemoryUserDetailsManager bridgeSpringSecurityUserProperties(String userName,
            String userPassword, List<String> userRoles) {
        if (!userName) {
            return null
        }
        String password = userPassword ?: 'user'
        String[] roles = (userRoles ?: ['USER']) as String[]
        UserDetails user = User.builder()
                .username(userName)
                .password('{noop}' + password)
                .roles(roles)
                .build()
        log.info 'Bridging spring.security.user.* properties: created in-memory user "{}" with roles {}',
                userName, roles.toList()
        new InMemoryUserDetailsManager([user])
    }
}
