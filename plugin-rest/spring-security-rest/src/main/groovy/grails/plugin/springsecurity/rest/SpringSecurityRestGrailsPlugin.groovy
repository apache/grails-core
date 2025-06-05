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
package grails.plugin.springsecurity.rest

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWSAlgorithm
import grails.core.GrailsApplication
import grails.plugin.springsecurity.BeanTypeResolver
import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.rest.authentication.DefaultRestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.authentication.NullRestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.credentials.DefaultJsonPayloadCredentialsExtractor
import grails.plugin.springsecurity.rest.credentials.RequestParamsCredentialsExtractor
import grails.plugin.springsecurity.rest.error.DefaultCallbackErrorHandler
import grails.plugin.springsecurity.rest.oauth.DefaultOauthUserDetailsService
import grails.plugin.springsecurity.rest.token.bearer.BearerTokenAccessDeniedHandler
import grails.plugin.springsecurity.rest.token.bearer.BearerTokenAuthenticationEntryPoint
import grails.plugin.springsecurity.rest.token.bearer.BearerTokenAuthenticationFailureHandler
import grails.plugin.springsecurity.rest.token.bearer.BearerTokenReader
import grails.plugin.springsecurity.rest.token.generation.SecureRandomTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.TokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.AbstractJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.CustomClaimProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.DefaultRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.FileRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.IssuerClaimProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.reader.HttpHeaderTokenReader
import grails.plugin.springsecurity.rest.token.rendering.DefaultAccessTokenJsonRenderer
import grails.plugin.springsecurity.rest.token.storage.jwt.JwtTokenStorageService
import grails.plugins.Plugin
import groovy.util.logging.Slf4j
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.LdapShaPasswordEncoder
import org.springframework.security.crypto.password.Md4PasswordEncoder
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
import org.springframework.security.crypto.password.StandardPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.access.ExceptionTranslationFilter
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.savedrequest.NullRequestCache

@Slf4j
class SpringSecurityRestGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    String grailsVersion = '7.0.0 > *'
    List loadAfter = ['springSecurityCore']
    List pluginExcludes = [
            'grails-app/views/**'
    ]

    String title = 'Spring Security REST Plugin'
    String author = 'Alvaro Sanchez-Mariscal'
    String authorEmail = ''
    String description = 'Implements authentication for REST APIs based on Spring Security. It uses a token-based workflow'

    def profiles = ['web']

    // URL to the plugin's documentation
    String documentation = 'https://apache.github.io/grails-spring-security'

    // Extra (optional) plugin metadata
    String license = 'APACHE'
    def organization = [name: 'Grails', url: 'https://www.grails.org']

    def issueManagement = [system: 'GitHub', url: 'https://github.com/apache/grails-spring-security/issues']
    def scm = [ url: 'https://github.com/apache/grails-spring-security']
    GrailsApplication grailsApplication

    Closure doWithSpring() { {->
        if (!springSecurityPluginsAreActive()){
            return
        }

        def conf = SpringSecurityUtils.securityConfig
        SpringSecurityUtils.loadSecondaryConfig 'DefaultRestSecurityConfig'
        conf = SpringSecurityUtils.securityConfig

        boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true

        if (printStatusMessages) {
            println "\nConfiguring Spring Security REST ${plugin.version}..."
        }

        ///*
        SpringSecurityUtils.registerProvider 'restAuthenticationProvider'

        /* restAuthenticationFilter */
        if(conf.rest.login.active) {
            SpringSecurityUtils.registerFilter 'restAuthenticationFilter', SecurityFilterPosition.FORM_LOGIN_FILTER.order + 1

            restAuthenticationFilterRequestMatcher(SpringSecurityRestFilterRequestMatcher, conf.rest.login.endpointUrl)

            restAuthenticationFilter(RestAuthenticationFilter) {
                authenticationManager = ref('authenticationManager')
                authenticationSuccessHandler = ref('restAuthenticationSuccessHandler')
                authenticationFailureHandler = ref('restAuthenticationFailureHandler')
                authenticationDetailsSource = ref('authenticationDetailsSource')
                credentialsExtractor = ref('credentialsExtractor')
                endpointUrl = conf.rest.login.endpointUrl
                tokenGenerator = ref('tokenGenerator')
                tokenStorageService = ref('tokenStorageService')
                authenticationEventPublisher = ref('authenticationEventPublisher')
                requestMatcher = ref('restAuthenticationFilterRequestMatcher')
            }

            def paramsClosure = {
                usernamePropertyName = conf.rest.login.usernamePropertyName // username
                passwordPropertyName = conf.rest.login.passwordPropertyName // password
            }

            if (conf.rest.login.useRequestParamsCredentials) {
                credentialsExtractor(RequestParamsCredentialsExtractor, paramsClosure)
            } else if (conf.rest.login.useJsonCredentials) {
                credentialsExtractor(DefaultJsonPayloadCredentialsExtractor, paramsClosure)
            }

            /* restLogoutFilter */
            restLogoutFilterRequestMatcher(SpringSecurityRestFilterRequestMatcher, conf.rest.logout.endpointUrl)

            restLogoutFilter(RestLogoutFilter) {
                endpointUrl = conf.rest.logout.endpointUrl
                headerName = conf.rest.token.validation.headerName
                tokenStorageService = ref('tokenStorageService')
                tokenReader = ref('tokenReader')
                requestMatcher = ref('restLogoutFilterRequestMatcher')
            }
        }

        restAuthenticationSuccessHandler(RestAuthenticationSuccessHandler) {
            renderer = ref('accessTokenJsonRenderer')
        }

        accessTokenJsonRenderer(DefaultAccessTokenJsonRenderer) {
            usernamePropertyName = conf.rest.token.rendering.usernamePropertyName
            tokenPropertyName = conf.rest.token.rendering.tokenPropertyName
            authoritiesPropertyName = conf.rest.token.rendering.authoritiesPropertyName
            useBearerToken = conf.rest.token.validation.useBearerToken
        }

        if(conf.rest.token.validation.useBearerToken ) {
            tokenReader(BearerTokenReader)
            restAuthenticationFailureHandler(BearerTokenAuthenticationFailureHandler){
                tokenReader = ref('tokenReader')
            }
            restAuthenticationEntryPoint(BearerTokenAuthenticationEntryPoint) {
                tokenReader = ref('tokenReader')
            }
            restAccessDeniedHandler(BearerTokenAccessDeniedHandler) {
                errorPage = null //403
            }

        } else {
            restAuthenticationEntryPoint(Http403ForbiddenEntryPoint)
            tokenReader(HttpHeaderTokenReader) {
                headerName = conf.rest.token.validation.headerName
            }
            restAuthenticationFailureHandler(RestAuthenticationFailureHandler) {
                statusCode = conf.rest.login.failureStatusCode?:HttpServletResponse.SC_UNAUTHORIZED
            }
            restAccessDeniedHandler(AccessDeniedHandlerImpl) {
                errorPage = null //403
            }
        }

        /* restTokenValidationFilter */
        SpringSecurityUtils.registerFilter 'restTokenValidationFilter', SecurityFilterPosition.ANONYMOUS_FILTER.order + 1
        SpringSecurityUtils.registerFilter 'restExceptionTranslationFilter', SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order - 5

        restTokenValidationFilterRequestMatcher(SpringSecurityRestFilterRequestMatcher, conf.rest.token.validation.endpointUrl)

        restTokenValidationFilter(RestTokenValidationFilter) {
            headerName = conf.rest.token.validation.headerName
            validationEndpointUrl = conf.rest.token.validation.endpointUrl
            active = conf.rest.token.validation.active
            tokenReader = ref('tokenReader')
            enableAnonymousAccess = conf.rest.token.validation.enableAnonymousAccess
            authenticationSuccessHandler = ref('restAuthenticationSuccessHandler')
            authenticationFailureHandler = ref('restAuthenticationFailureHandler')
            restAuthenticationProvider = ref('restAuthenticationProvider')
            authenticationEventPublisher = ref('authenticationEventPublisher')
            requestMatcher = ref('restTokenValidationFilterRequestMatcher')
        }

        restExceptionTranslationFilter(ExceptionTranslationFilter, ref('restAuthenticationEntryPoint'), ref('restRequestCache')) {
            accessDeniedHandler = ref('restAccessDeniedHandler')
            authenticationTrustResolver = ref('authenticationTrustResolver')
            throwableAnalyzer = ref('throwableAnalyzer')
        }

        restRequestCache(NullRequestCache)

        /* tokenGenerator */
        tokenGenerator(SecureRandomTokenGenerator)

        callbackErrorHandler(DefaultCallbackErrorHandler)
        
        String jwtSecretValue = conf.rest.token.storage.jwt.secret

        /* tokenStorageService - defaults to JWT */
        jwtService(JwtService) {
            jwtSecret = jwtSecretValue
        }
        tokenStorageService(JwtTokenStorageService) {
            jwtService = ref('jwtService')
            userDetailsService = ref('userDetailsService')
        }

        issuerClaimProvider(IssuerClaimProvider) {
            issuerName = conf.rest.token.generation.jwt.issuer
        }

        if (conf.rest.token.storage.jwt.useEncryptedJwt) {
            jwtService(JwtService) {
                keyProvider = ref('keyProvider')
            }
            tokenGenerator(EncryptedJwtTokenGenerator) {
                jwtTokenStorageService = ref('tokenStorageService')
                keyProvider = ref('keyProvider')
                defaultExpiration = conf.rest.token.storage.jwt.expiration
                defaultRefreshExpiration = conf.rest.token.storage.jwt.refreshExpiration
                jweAlgorithm = JWEAlgorithm.parse(conf.rest.token.generation.jwt.jweAlgorithm)
                encryptionMethod = EncryptionMethod.parse(conf.rest.token.generation.jwt.encryptionMethod)
            }

            if (conf.rest.token.storage.jwt.privateKeyPath instanceof CharSequence &&
                    conf.rest.token.storage.jwt.publicKeyPath instanceof CharSequence) {
                keyProvider(FileRSAKeyProvider) {
                    privateKeyPath = conf.rest.token.storage.jwt.privateKeyPath
                    publicKeyPath = conf.rest.token.storage.jwt.publicKeyPath
                }
            } else {
                keyProvider(DefaultRSAKeyProvider)
            }

        } else if (conf.rest.token.storage.jwt.useSignedJwt) {
            checkJwtSecret(jwtSecretValue)

            tokenGenerator(SignedJwtTokenGenerator) {
                jwtTokenStorageService = ref('tokenStorageService')
                jwtSecret = jwtSecretValue
                defaultExpiration = conf.rest.token.storage.jwt.expiration
                defaultRefreshExpiration = conf.rest.token.storage.jwt.refreshExpiration
                jwsAlgorithm = JWSAlgorithm.parse(conf.rest.token.generation.jwt.algorithm)
            }
        }

        /* restAuthenticationProvider */
        restAuthenticationProvider(RestAuthenticationProvider) {
            tokenStorageService = ref('tokenStorageService')
            useJwt = true
            jwtService = ref('jwtService')
        }

        /* oauthUserDetailsService */
        oauthUserDetailsService(DefaultOauthUserDetailsService) {
            userDetailsService = ref('userDetailsService')
            preAuthenticationChecks = ref('preAuthenticationChecks')
        }

        // SecurityEventListener
        if (conf.useSecurityEventListener) {
            restSecurityEventListener(RestSecurityEventListener)

            authenticationEventPublisher(DefaultRestAuthenticationEventPublisher)
        } else {
            authenticationEventPublisher(NullRestAuthenticationEventPublisher)
        }

        String algorithm = conf.password.algorithm
        Class beanTypeResolverClass = conf.beanTypeResolverClass ?: BeanTypeResolver
        def beanTypeResolver = beanTypeResolverClass.newInstance(conf, grailsApplication)

        passwordEncoder(beanTypeResolver.resolveType('passwordEncoder', DelegatingPasswordEncoder), algorithm, idToPasswordEncoder(conf))

        if (printStatusMessages) {
            println '... finished configuring Spring Security REST\n'
        }

    }}

    @Override
    void doWithApplicationContext() {
        if (!springSecurityPluginsAreActive()){
            return
        }
        def customClaimProvidersList = applicationContext.getBeanNamesForType(CustomClaimProvider).collect {
            applicationContext.getBean(it, CustomClaimProvider)
        }
        log.debug 'customClaimProvidersList = {}', customClaimProvidersList

        TokenGenerator tokenGenerator = applicationContext.getBean('tokenGenerator') as TokenGenerator

        if (tokenGenerator instanceof AbstractJwtTokenGenerator) {
            tokenGenerator.customClaimProviders = customClaimProvidersList
        }

    }

    private void checkJwtSecret(String jwtSecretValue) {
        if (!jwtSecretValue &&
                !pluginManager.hasGrailsPlugin('springSecurityRestGorm') &&
                !pluginManager.hasGrailsPlugin('springSecurityRestGrailsCache') &&
                !pluginManager.hasGrailsPlugin('springSecurityRestRedis') &&
                !pluginManager.hasGrailsPlugin('springSecurityRestMemcached')) {
            throw new Exception('A JWT secret must be defined. Please provide a value for the config property: grails.plugin.springsecurity.rest.token.storage.jwt.secret')
        }
    }


    Map<String, PasswordEncoder> idToPasswordEncoder(ConfigObject conf) {

        final String ENCODING_ID_BCRYPT = 'bcrypt'
        final String ENCODING_ID_LDAP = 'ldap'
        final String ENCODING_ID_MD4 = 'MD4'
        final String ENCODING_ID_MD5 = 'MD5'
        final String ENCODING_ID_NOOP = 'noop'
        final String ENCODING_ID_PBKDF2 = 'pbkdf2'
        final String ENCODING_ID_SCRYPT = 'scrypt'
        final String ENCODING_ID_ARGON2 = 'argon2'
        final String ENCODING_ID_SHA1 = 'SHA-1'
        final String ENCODING_IDSHA256 = 'SHA-256'

        MessageDigestPasswordEncoder messageDigestPasswordEncoderMD5 = new MessageDigestPasswordEncoder(ENCODING_ID_MD5)
        messageDigestPasswordEncoderMD5.encodeHashAsBase64 = conf.password.encodeHashAsBase64 // false
        messageDigestPasswordEncoderMD5.iterations = conf.password.hash.iterations // 10000

        MessageDigestPasswordEncoder messsageDigestPasswordEncoderSHA1 = new MessageDigestPasswordEncoder(ENCODING_ID_SHA1)
        messsageDigestPasswordEncoderSHA1.encodeHashAsBase64 = conf.password.encodeHashAsBase64 // false
        messsageDigestPasswordEncoderSHA1.iterations = conf.password.hash.iterations // 10000

        MessageDigestPasswordEncoder messsageDigestPasswordEncoderSHA256 = new MessageDigestPasswordEncoder(ENCODING_IDSHA256)
        messsageDigestPasswordEncoderSHA256.encodeHashAsBase64 = conf.password.encodeHashAsBase64 // false
        messsageDigestPasswordEncoderSHA256.iterations = conf.password.hash.iterations // 10000

        int strength = conf.password.bcrypt.logrounds
        [(ENCODING_ID_BCRYPT): new BCryptPasswordEncoder(strength),
         (ENCODING_ID_LDAP): new LdapShaPasswordEncoder(),
         (ENCODING_ID_MD4): new Md4PasswordEncoder(),
         (ENCODING_ID_MD5): messageDigestPasswordEncoderMD5,
         (ENCODING_ID_NOOP): NoOpPasswordEncoder.getInstance(),
         (ENCODING_ID_PBKDF2): Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
         (ENCODING_ID_SCRYPT): SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8(),
         (ENCODING_ID_ARGON2): Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
         (ENCODING_ID_SHA1): messsageDigestPasswordEncoderSHA1,
         (ENCODING_IDSHA256): messsageDigestPasswordEncoderSHA256,
         'sha256': new StandardPasswordEncoder()]
    }

    private boolean springSecurityPluginsAreActive() {
        def conf = SpringSecurityUtils.securityConfig
        if (!conf || !conf.active) {
            return false
        }

        SpringSecurityUtils.loadSecondaryConfig 'DefaultRestSecurityConfig'
        conf = SpringSecurityUtils.securityConfig

        if (!conf.rest.active) {
            return false
        }
        return true
    }

}
