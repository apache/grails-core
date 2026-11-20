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
package grails.plugin.springsecurity

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment

/**
 * Automatically excludes Spring Boot security auto-configuration classes that
 * conflict with the Grails Spring Security plugin.
 *
 * <h2>Why this filter exists</h2>
 *
 * <p>The Grails Spring Security plugin owns the servlet security stack of a
 * Grails application: it builds its own {@code FilterChainProxy}
 * ({@code springSecurityFilterChain}), {@code UserDetailsService}
 * ({@code GormUserDetailsService}), and request-mapping/access-decision
 * infrastructure from the {@code grails.plugin.springsecurity.*} configuration
 * namespace.</p>
 *
 * <p>Spring Boot ships its own auto-configurations that try to do the same job
 * from the {@code spring.security.*} configuration namespace. When both are
 * active, Spring Boot can register an additional {@code SecurityFilterChain},
 * an in-memory {@code UserDetailsService}, OAuth2 client/authorization-server
 * filter chains, etc., resulting in two parallel servlet security stacks with
 * no defined precedence between them.</p>
 *
 * <p><strong>Configuration contract:</strong> while this filter is enabled
 * (the default), {@code grails.plugin.springsecurity.*} is the authoritative
 * configuration source for the application's security. Boot's
 * {@code spring.security.*} properties are <em>not</em> merged into the plugin
 * configuration and are <em>not</em> applied by Boot's auto-configuration.
 * Use the plugin's keys, not Spring Boot's, to configure security when this
 * plugin is active.</p>
 *
 * <h2>Coexistence with the component-based Spring Security configuration model</h2>
 *
 * <p>Spring Security 5.7 deprecated and Spring Security 6 removed
 * {@code WebSecurityConfigurerAdapter}, replacing it with a component-based
 * configuration model that registers individual {@code @Bean} components
 * (see <a href="https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter">
 * Spring Security without the WebSecurityConfigurerAdapter</a>).</p>
 *
 * <p>This plugin pre-dates that model and provides equivalent functionality
 * through the {@code grails.plugin.springsecurity.*} configuration namespace,
 * but it now <strong>blends</strong> the most common component-based patterns
 * automatically via {@link grails.plugin.springsecurity.componentbased.ComponentBasedConfigBlender}.
 * This means you can configure security from either side (or both) and the
 * effective configuration is the union of both sources.</p>
 *
 * <p>The following table summarises how each component-based pattern is
 * blended with the plugin's configuration:</p>
 *
 * <ul>
 *   <li><strong>{@code @Bean SecurityFilterChain}</strong> - user-defined
 *       {@code SecurityFilterChain} beans are <strong>auto-merged</strong>
 *       into the plugin's {@code FilterChainProxy}. User chains are prepended
 *       (higher precedence) so their typically more-specific request matchers
 *       win against the plugin's catch-all chain. Disable via
 *       {@code grails.plugin.springsecurity.componentBased.autoMergeSecurityFilterChain: false}.</li>
 *   <li><strong>{@code @Bean WebSecurityCustomizer}</strong> - still a no-op.
 *       The plugin does not use Spring's {@code WebSecurity} builder. To
 *       exclude URLs from security checks, use
 *       {@code grails.plugin.springsecurity.ipRestrictions} or
 *       {@code grails.plugin.springsecurity.staticRules} with
 *       {@code permitAll} access.</li>
 *   <li><strong>{@code @Bean AuthenticationManager}</strong> - the plugin
 *       registers an {@code authenticationManager} bean (a
 *       {@code ProviderManager}). A user-defined bean with the same name
 *       will fail with a duplicate-bean error. To plug in custom
 *       authentication providers, define {@code @Bean AuthenticationProvider}
 *       beans (auto-merged - see the next entry) or add their bean names to
 *       {@code grails.plugin.springsecurity.providerNames}.</li>
 *   <li><strong>{@code @Bean AuthenticationProvider}</strong> - user-defined
 *       {@code AuthenticationProvider} beans are <strong>auto-merged</strong>
 *       into the plugin's {@code authenticationManager}. User providers are
 *       appended so the plugin's primary GORM-backed provider runs first;
 *       providers already in the manager (e.g. those declared via
 *       {@code providerNames}) are not re-added. Disable via
 *       {@code grails.plugin.springsecurity.componentBased.autoMergeAuthenticationProviders: false}.</li>
 *   <li><strong>{@code @Bean UserDetailsManager} /
 *       {@code InMemoryUserDetailsManager} /
 *       {@code JdbcUserDetailsManager}</strong> - for each additional
 *       {@code UserDetailsService} bean, a new
 *       {@code DaoAuthenticationProvider} is created and appended to the
 *       plugin's {@code authenticationManager} providers list. The plugin's
 *       primary GORM-backed provider runs first; if it does not authenticate
 *       the user, each additional provider is tried in turn. (We cannot
 *       rewire the existing {@code daoAuthenticationProvider} because Spring
 *       Security 7 made its {@code userDetailsService} a final
 *       constructor-only field.) Disable via
 *       {@code grails.plugin.springsecurity.componentBased.autoChainUserDetailsServices: false}.</li>
 *   <li><strong>{@code spring.security.user.name} /
 *       {@code spring.security.user.password} /
 *       {@code spring.security.user.roles}</strong> - if
 *       {@code spring.security.user.name} is set, an
 *       {@code InMemoryUserDetailsManager} is created from those properties
 *       (mimicking what Spring Boot's
 *       {@code UserDetailsServiceAutoConfiguration} would have done), wrapped
 *       in a {@code DaoAuthenticationProvider} and added to the plugin's
 *       authenticationManager. Disable via
 *       {@code grails.plugin.springsecurity.componentBased.bridgeSpringSecurityUserProperties: false}.</li>
 *   <li><strong>LDAP factory beans
 *       ({@code EmbeddedLdapServerContextSourceFactoryBean},
 *       {@code LdapBindAuthenticationManagerFactory},
 *       {@code LdapPasswordComparisonAuthenticationManagerFactory})</strong>
 *       - the {@code grails-spring-security-ldap} plugin provides equivalent
 *       configuration through {@code grails.plugin.springsecurity.ldap.*}.
 *       User-defined LDAP factory beans coexist but are not wired into the
 *       plugin's authentication providers.</li>
 * </ul>
 *
 * <p>To delegate the entire servlet security stack to Spring Boot's
 * component-based model (and stop using the plugin's
 * {@code grails.plugin.springsecurity.*} configuration), disable this filter
 * - see the "Opt-out" section below.</p>
 *
 * <h2>What this filter excludes</h2>
 *
 * <p>In Spring Boot 4 the security auto-configurations were moved out of
 * {@code spring-boot-autoconfigure} into dedicated {@code spring-boot-security*}
 * modules and re-packaged under {@code org.springframework.boot.security.*}.
 * Adding any of those starters/modules to a Grails application would otherwise
 * re-introduce the conflicting servlet auto-configurations. This filter excludes
 * them during Spring Boot's auto-configuration discovery phase, so users do not
 * need to maintain a manual {@code spring.autoconfigure.exclude} list.</p>
 *
 * <h2>Opt-out</h2>
 *
 * <p>To disable this filter and allow Spring Boot's security auto-configurations
 * to run (for example, to delegate the entire servlet security stack to Spring
 * Boot instead of the plugin), set the following property in
 * {@code application.yml}:</p>
 *
 * <pre>
 * grails:
 *   plugin:
 *     springsecurity:
 *       excludeSpringSecurityAutoConfiguration: false
 * </pre>
 *
 * <p>Disabling this filter is intentionally a footgun: the plugin can no longer
 * guarantee that its filter chain is the only servlet security stack in the
 * application context, and a startup {@code WARN} is logged when it is turned
 * off.</p>
 *
 * <p>Registered via {@code META-INF/spring.factories} as an
 * {@link AutoConfigurationImportFilter}. This runs before auto-configuration
 * bytecode is loaded, so there is no performance overhead from excluded classes.</p>
 *
 * @since 7.0.2
 * @see AutoConfigurationImportFilter
 */
@CompileStatic
@Slf4j
class SecurityAutoConfigurationExcluder implements AutoConfigurationImportFilter, EnvironmentAware {

    static final String ENABLED_PROPERTY = 'grails.plugin.springsecurity.excludeSpringSecurityAutoConfiguration'

    private boolean enabled = true

    @Override
    void setEnvironment(Environment environment) {
        this.enabled = environment.getProperty(ENABLED_PROPERTY, Boolean, true)
        if (!this.enabled) {
            log.warn(
                    'Spring Boot security auto-configuration exclusion is DISABLED via {}=false. ' +
                    'Spring Boot may now register a parallel servlet security stack alongside the ' +
                    'Grails Spring Security plugin (additional SecurityFilterChain, UserDetailsService, ' +
                    'or OAuth2/SAML2 filter chains). The plugin can no longer guarantee that its ' +
                    'filter chain is the only servlet security stack in the application context.',
                    ENABLED_PROPERTY)
        }
    }

    /**
     * Spring Boot 4 servlet security auto-configuration classes that conflict
     * with the Grails Spring Security plugin's servlet security stack. These
     * are excluded unconditionally when the plugin is on the classpath.
     *
     * <p>Verified against the
     * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
     * files in the following Spring Boot 4 modules:
     * {@code spring-boot-security}, {@code spring-boot-security-oauth2-client},
     * {@code spring-boot-security-oauth2-resource-server},
     * {@code spring-boot-security-saml2}, and
     * {@code spring-boot-security-oauth2-authorization-server}.</p>
     *
     * <p>Reactive variants (e.g. {@code ReactiveWebSecurityAutoConfiguration},
     * {@code ReactiveOAuth2ResourceServerAutoConfiguration}) are intentionally
     * NOT excluded here. They are guarded by
     * {@code @ConditionalOnWebApplication(REACTIVE)} and do not activate in a
     * standard servlet-based Grails application; mixed servlet/reactive
     * security is outside this plugin's threat model.</p>
     *
     * <ul>
     *   <li>{@code SecurityAutoConfiguration} - enables {@code SecurityProperties}
     *       and contributes {@code AuthenticationEventPublisher}/{@code SecurityDataConfiguration}
     *       that conflict with the plugin's wiring</li>
     *   <li>{@code UserDetailsServiceAutoConfiguration} - creates an in-memory
     *       {@code UserDetailsService} from {@code spring.security.user.*}
     *       properties that conflicts with the plugin's
     *       {@code GormUserDetailsService}</li>
     *   <li>{@code SecurityFilterAutoConfiguration} - registers a
     *       {@code DelegatingFilterProxyRegistrationBean} that duplicates the
     *       plugin's {@code springSecurityFilterChainRegistrationBean}</li>
     *   <li>{@code ServletWebSecurityAutoConfiguration} - new in Spring Boot 4;
     *       contributes the servlet {@code SecurityFilterChain} wiring that
     *       conflicts with the plugin's filter chain</li>
     *   <li>{@code ManagementWebSecurityAutoConfiguration} - Actuator security
     *       that conflicts when Actuator is on the classpath</li>
     *   <li>{@code OAuth2ClientAutoConfiguration} - registers the
     *       {@code ClientRegistrationRepository} and authorized-client services
     *       that the {@code spring-security-oauth2} plugin owns</li>
     *   <li>{@code OAuth2ClientWebSecurityAutoConfiguration} - registers the
     *       OAuth2 client servlet {@code SecurityFilterChain} that duplicates
     *       the plugin's OAuth2 client filter chain</li>
     *   <li>{@code OAuth2ResourceServerAutoConfiguration} (servlet) - configures
     *       a JWT/Opaque-token-based {@code SecurityFilterChain} that conflicts
     *       with the plugin's REST/token authentication wiring</li>
     *   <li>{@code Saml2RelyingPartyAutoConfiguration} - registers a SAML2
     *       relying-party {@code SecurityFilterChain} that conflicts with the
     *       plugin's SAML wiring</li>
     *   <li>{@code OAuth2AuthorizationServerAutoConfiguration} (servlet) -
     *       registers a high-precedence authorization-server
     *       {@code SecurityFilterChain} plus a default
     *       {@code anyRequest().authenticated()} chain that would override the
     *       plugin's request-mapping rules</li>
     *   <li>{@code OAuth2AuthorizationServerJwtAutoConfiguration} (servlet) -
     *       authorization-server JWT companion that pulls in additional
     *       authorization-server beans</li>
     * </ul>
     */
    private static final Set<String> EXCLUDED_AUTO_CONFIGURATIONS = [
            'org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration',
            'org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration',
            'org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration',
            'org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration',
            'org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration',
            'org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration',
            'org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration',
            'org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration',
            'org.springframework.boot.security.saml2.autoconfigure.Saml2RelyingPartyAutoConfiguration',
            'org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration',
            'org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration',
    ].toSet().asImmutable()

    @Override
    boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        autoConfigurationClasses.collect {
            !enabled || !(it in EXCLUDED_AUTO_CONFIGURATIONS)
        } as boolean[]
    }

    /**
     * Returns the set of auto-configuration class names that this filter excludes.
     * Exposed for testing and diagnostic purposes.
     *
     * @return unmodifiable set of excluded class names
     */
    static Set<String> getExcludedAutoConfigurations() {
        EXCLUDED_AUTO_CONFIGURATIONS
    }
}
