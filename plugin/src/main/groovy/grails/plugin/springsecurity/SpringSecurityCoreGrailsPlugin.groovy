/* Copyright 2006-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity

import grails.plugin.springsecurity.access.NullAfterInvocationProvider
import grails.plugin.springsecurity.access.intercept.NullAfterInvocationManager
import grails.plugin.springsecurity.access.vote.AuthenticatedVetoableDecisionManager
import grails.plugin.springsecurity.access.vote.ClosureVoter
import grails.plugin.springsecurity.authentication.GrailsAnonymousAuthenticationProvider
import grails.plugin.springsecurity.authentication.NullAuthenticationEventPublisher
import grails.plugin.springsecurity.cache.SpringUserCacheFactoryBean
import grails.plugin.springsecurity.userdetails.DefaultPostAuthenticationChecks
import grails.plugin.springsecurity.userdetails.DefaultPreAuthenticationChecks
import grails.plugin.springsecurity.userdetails.GormUserDetailsService
import grails.plugin.springsecurity.userdetails.NoStackUsernameNotFoundException
import grails.plugin.springsecurity.web.GrailsRedirectStrategy
import grails.plugin.springsecurity.web.NullFilterChainValidator
import grails.plugin.springsecurity.web.SecurityRequestHolderFilter
import grails.plugin.springsecurity.web.UpdateRequestContextHolderExceptionTranslationFilter
import grails.plugin.springsecurity.web.access.AjaxAwareAccessDeniedHandler
import grails.plugin.springsecurity.web.access.DefaultThrowableAnalyzer
import grails.plugin.springsecurity.web.access.GrailsWebInvocationPrivilegeEvaluator
import grails.plugin.springsecurity.web.access.expression.WebExpressionVoter
import grails.plugin.springsecurity.web.access.intercept.AnnotationFilterInvocationDefinition
import grails.plugin.springsecurity.web.access.intercept.ChannelFilterInvocationSecurityMetadataSourceFactoryBean
import grails.plugin.springsecurity.web.access.intercept.InterceptUrlMapFilterInvocationDefinition
import grails.plugin.springsecurity.web.access.intercept.RequestmapFilterInvocationDefinition
import grails.plugin.springsecurity.web.authentication.AjaxAwareAuthenticationEntryPoint
import grails.plugin.springsecurity.web.authentication.AjaxAwareAuthenticationFailureHandler
import grails.plugin.springsecurity.web.authentication.AjaxAwareAuthenticationSuccessHandler
import grails.plugin.springsecurity.web.authentication.FilterProcessUrlRequestMatcher
import grails.plugin.springsecurity.web.authentication.GrailsUsernamePasswordAuthenticationFilter
import grails.plugin.springsecurity.web.authentication.logout.MutableLogoutFilter
import grails.plugin.springsecurity.web.authentication.preauth.x509.ClosureX509PrincipalExtractor
import grails.plugin.springsecurity.web.authentication.preauth.x509.NullAuthenticationFailureHandler
import grails.plugin.springsecurity.web.authentication.preauth.x509.NullAuthenticationSuccessHandler
import grails.plugin.springsecurity.web.authentication.rememberme.GormPersistentTokenRepository
import grails.plugin.springsecurity.web.authentication.switchuser.NullSwitchUserAuthorityChanger
import grails.plugin.springsecurity.web.filter.DebugFilter
import grails.plugin.springsecurity.web.filter.GrailsAnonymousAuthenticationFilter

import grails.plugin.springsecurity.web.filter.GrailsRememberMeAuthenticationFilter
import grails.plugin.springsecurity.web.filter.IpAddressFilter
import grails.plugins.Plugin
import grails.util.Metadata
import groovy.util.logging.Slf4j
import org.grails.web.mime.HttpServletResponseExtension
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean
import org.springframework.cache.jcache.JCacheCacheManager
import org.springframework.core.Ordered
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.security.access.event.LoggerListener
import org.springframework.security.access.expression.DenyAllPermissionEvaluator
import org.springframework.security.access.hierarchicalroles.RoleHierarchyAuthoritiesMapper
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.access.intercept.AfterInvocationProviderManager
import org.springframework.security.access.intercept.NullRunAsManager
import org.springframework.security.access.vote.AuthenticatedVoter
import org.springframework.security.access.vote.RoleHierarchyVoter
import org.springframework.security.authentication.AccountStatusUserDetailsChecker
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.RememberMeAuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper
import org.springframework.security.core.userdetails.cache.NullUserCache
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
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.PortMapperImpl
import org.springframework.security.web.PortResolverImpl
import org.springframework.security.web.access.AccessDeniedHandlerImpl
import org.springframework.security.web.access.channel.ChannelDecisionManagerImpl
import org.springframework.security.web.access.channel.ChannelProcessingFilter
import org.springframework.security.web.access.channel.InsecureChannelProcessor
import org.springframework.security.web.access.channel.RetryWithHttpEntryPoint
import org.springframework.security.web.access.channel.RetryWithHttpsEntryPoint
import org.springframework.security.web.access.channel.SecureChannelProcessor
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.security.web.authentication.preauth.x509.SubjectDnX509PrincipalExtractor
import org.springframework.security.web.authentication.preauth.x509.X509AuthenticationFilter
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.authentication.www.DigestAuthenticationEntryPoint
import org.springframework.security.web.authentication.www.DigestAuthenticationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextPersistenceFilter
import org.springframework.security.web.firewall.DefaultHttpFirewall
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter
import org.springframework.security.web.session.HttpSessionEventPublisher
import org.springframework.security.web.util.matcher.AnyRequestMatcher
import org.springframework.web.filter.FormContentFilter

import jakarta.servlet.DispatcherType

/**
 * @author Burt Beckwith
 */
@Slf4j
class SpringSecurityCoreGrailsPlugin extends Plugin {

	public static final String ENCODING_ID_BCRYPT = "bcrypt"
	public static final String ENCODING_ID_LDAP = "ldap"
	public static final String ENCODING_ID_MD4 = "MD4"
	public static final String ENCODING_ID_MD5 = "MD5"
	public static final String ENCODING_ID_NOOP = "noop"
	public static final String ENCODING_ID_PBKDF2 = "pbkdf2"
	public static final String ENCODING_ID_SCRYPT = "scrypt"
	public static final String ENCODING_ID_ARGON2 = "argon2"
	public static final String ENCODING_ID_SHA1 = "SHA-1"
	public static final String ENCODING_IDSHA256 = "SHA-256"

	String grailsVersion = '6.0.0 > *'
	List observe = ['controllers']
	List loadAfter = ['controllers', 'hibernate', 'hibernate4', 'hibernate5', 'services']
	String author = 'Burt Beckwith'
	String authorEmail = 'burt@burtbeckwith.com'
	String title = 'Spring Security Core Plugin'
	String description = 'Spring Security Core plugin'
	String documentation = 'https://grails.github.io/grails-spring-security-core/'
	String license = 'APACHE'
	def organization = [name: 'Grails', url: 'https://www.grails.org/']
	def issueManagement = [url: 'https://github.com/grails/grails-spring-security-core/issues']
	def scm = [url: 'https://github.com/grails/grails-spring-security-core']
	def profiles = ['web']

	private beanTypeResolver

	Closure doWithSpring() {{ ->
		ReflectionUtils.application = SpringSecurityUtils.application = grailsApplication

		SpringSecurityUtils.resetSecurityConfig()
		ConfigObject conf = SpringSecurityUtils.securityConfig
		boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true
		if (!conf || !conf.active) {
			if (printStatusMessages) {
				String message = '\n\nSpring Security is disabled, not loading\n\n'
				log.info message
				println message
			}
			return
		}

		log.trace 'doWithSpring'

		if (printStatusMessages) {
			String message = '\nConfiguring Spring Security Core ...'
			log.info message
			println message
		}

		if (log.traceEnabled) {
			def sb = new StringBuilder('Spring Security configuration:\n')
			def flatConf = conf.flatten()
			for (key in flatConf.keySet().sort()) {
				def value = flatConf[key]
				sb << '\t' << key << ': '
				if (value instanceof Closure) {
					sb << '(closure)'
				}
				else {
					try {
						sb << value.toString() // eagerly convert to string to catch individual exceptions
					}
					catch (e) {
						sb << '(an error occurred: ' << e.message << ')'
					}
				}
				sb << '\n'
			}
			log.trace sb.toString()
		}

		Class beanTypeResolverClass = conf.beanTypeResolverClass ?: BeanTypeResolver
		beanTypeResolver = beanTypeResolverClass.newInstance(conf, grailsApplication)

		springSecurityBeanFactoryPostProcessor(classFor('springSecurityBeanFactoryPostProcessor', SpringSecurityBeanFactoryPostProcessor))

		// configure the filter and optionally the listener

		springSecurityFilterChainRegistrationBean(classFor('springSecurityFilterChainRegistrationBean', FilterRegistrationBean)) {
			filter = ref('springSecurityFilterChain')
			urlPatterns = ['/*']
			dispatcherTypes = EnumSet.of(DispatcherType.ERROR, DispatcherType.REQUEST)
			order = SecurityProperties.DEFAULT_FILTER_ORDER
		}

		if (conf.useHttpSessionEventPublisher) {
			log.trace 'Configuring HttpSessionEventPublisher'
			httpSessionEventPublisher(classFor('httpSessionEventPublisher', ServletListenerRegistrationBean), new HttpSessionEventPublisher())
		}

		createRefList.delegate = delegate

		/** springSecurityFilterChain */
		configureFilterChain.delegate = delegate
		configureFilterChain conf

		// securityRequestHolderFilter
		securityRequestHolderFilter(classFor('securityRequestHolderFilter', SecurityRequestHolderFilter)) {
			useHeaderCheckChannelSecurity = conf.secureChannel.useHeaderCheckChannelSecurity
			secureHeaderName = conf.secureChannel.secureHeaderName // 'X-Forwarded-Proto'
			secureHeaderValue = conf.secureChannel.secureHeaderValue // 'http'
			insecureHeaderName = conf.secureChannel.insecureHeaderName // 'X-Forwarded-Proto'
			insecureHeaderValue = conf.secureChannel.insecureHeaderValue // 'https'
			portMapper = ref('portMapper')
			portResolver = ref('portResolver')
		}

		// logout
		configureLogout.delegate = delegate
		configureLogout conf

		/** securityContextRepository */
		securityContextRepository(classFor('securityContextRepository', HttpSessionSecurityContextRepository)) {
			allowSessionCreation = conf.scr.allowSessionCreation // true
			disableUrlRewriting = conf.scr.disableUrlRewriting // true
			springSecurityContextKey = conf.scr.springSecurityContextKey // SPRING_SECURITY_CONTEXT
			trustResolver = ref('authenticationTrustResolver')
		}

		/** securityContextPersistenceFilter */
		securityContextPersistenceFilter(classFor('securityContextPersistenceFilter', SecurityContextPersistenceFilter), ref('securityContextRepository')) {
			forceEagerSessionCreation = conf.scpf.forceEagerSessionCreation // false
		}

		/** authenticationProcessingFilter */
		configureAuthenticationProcessingFilter.delegate = delegate
		configureAuthenticationProcessingFilter conf

		/** securityContextHolderAwareRequestFilter */
		securityContextHolderAwareRequestFilter(classFor('securityContextHolderAwareRequestFilter', SecurityContextHolderAwareRequestFilter)) {
			authenticationEntryPoint = ref('authenticationEntryPoint')
			authenticationManager = ref('authenticationManager')
			logoutHandlers = ref('logoutHandlers')
			trustResolver = ref('authenticationTrustResolver')
		}

		/** rememberMeAuthenticationFilter */
		rememberMeAuthenticationFilter(classFor('rememberMeAuthenticationFilter', GrailsRememberMeAuthenticationFilter),
				ref('authenticationManager'), ref('rememberMeServices'), ref('requestCache')) {
			authenticationSuccessHandler = ref('authenticationSuccessHandler')
			createSessionOnSuccess = conf.rememberMe.createSessionOnSuccess // true
		}

		userDetailsChecker(classFor('userDetailsChecker', AccountStatusUserDetailsChecker))

		authoritiesMapper(classFor('authoritiesMapper', RoleHierarchyAuthoritiesMapper), ref('roleHierarchy'))

		/** rememberMeServices */
		if (conf.rememberMe.persistent) {
			log.trace 'Configuring persistent remember-me'
			rememberMeServices(classFor('rememberMeServices', PersistentTokenBasedRememberMeServices),
					conf.rememberMe.key, ref('userDetailsService'), ref('tokenRepository')) {
				cookieName = conf.rememberMe.cookieName
				if (conf.rememberMe.cookieDomain) {
					cookieDomain = conf.rememberMe.cookieDomain
				}
				alwaysRemember = conf.rememberMe.alwaysRemember
				tokenValiditySeconds = conf.rememberMe.tokenValiditySeconds
				parameter = conf.rememberMe.parameter
				if (conf.rememberMe.useSecureCookie instanceof Boolean) {
					useSecureCookie = conf.rememberMe.useSecureCookie // null
				}
				authenticationDetailsSource = ref('authenticationDetailsSource')
				userDetailsChecker = ref('userDetailsChecker')
				authoritiesMapper = ref('authoritiesMapper')

				seriesLength = conf.rememberMe.persistentToken.seriesLength // 16
				tokenLength = conf.rememberMe.persistentToken.tokenLength // 16
			}

			tokenRepository(classFor('tokenRepository', GormPersistentTokenRepository))
		}
		else {
			log.trace 'Configuring non-persistent remember-me'
			rememberMeServices(classFor('rememberMeServices', TokenBasedRememberMeServices), conf.rememberMe.key, ref('userDetailsService')) {
				cookieName = conf.rememberMe.cookieName
				if (conf.rememberMe.cookieDomain) {
					cookieDomain = conf.rememberMe.cookieDomain
				}
				alwaysRemember = conf.rememberMe.alwaysRemember
				tokenValiditySeconds = conf.rememberMe.tokenValiditySeconds
				parameter = conf.rememberMe.parameter
				if (conf.rememberMe.useSecureCookie instanceof Boolean) {
					useSecureCookie = conf.rememberMe.useSecureCookie // null
				}
				authenticationDetailsSource = ref('authenticationDetailsSource')
				userDetailsChecker = ref('userDetailsChecker')
				authoritiesMapper = ref('authoritiesMapper')
			}

			// register a lightweight impl so there's a bean in either case
			tokenRepository(classFor('tokenRepository', InMemoryTokenRepositoryImpl))
		}

		/** anonymousAuthenticationFilter */
		anonymousAuthenticationFilter(classFor('anonymousAuthenticationFilter', GrailsAnonymousAuthenticationFilter)) {
			authenticationDetailsSource = ref('authenticationDetailsSource')
			key = conf.anon.key
		}

		throwableAnalyzer(classFor('throwableAnalyzer', DefaultThrowableAnalyzer))

		/** exceptionTranslationFilter */
		exceptionTranslationFilter(classFor('exceptionTranslationFilter', UpdateRequestContextHolderExceptionTranslationFilter),
				ref('authenticationEntryPoint'), ref('requestCache')) {
			accessDeniedHandler = ref('accessDeniedHandler')
			authenticationTrustResolver = ref('authenticationTrustResolver')
			throwableAnalyzer = ref('throwableAnalyzer')
		}
		accessDeniedHandler(classFor('accessDeniedHandler', AjaxAwareAccessDeniedHandler)) {
			errorPage = conf.adh.errorPage == 'null' ? null : conf.adh.errorPage // '/login/denied' or 403
			ajaxErrorPage = conf.adh.ajaxErrorPage
			useForward = conf.adh.useForward
			portResolver = ref('portResolver')
			authenticationTrustResolver = ref('authenticationTrustResolver')
			requestCache = ref('requestCache')
		}

		/** authenticationTrustResolver */
		authenticationTrustResolver(classFor('authenticationTrustResolver', AuthenticationTrustResolverImpl)) {
			anonymousClass = conf.atr.anonymousClass
			rememberMeClass = conf.atr.rememberMeClass
		}

		// default 'authenticationEntryPoint'
		authenticationEntryPoint(classFor('authenticationEntryPoint', AjaxAwareAuthenticationEntryPoint), conf.auth.loginFormUrl) { // '/login/auth'
			ajaxLoginFormUrl = conf.auth.ajaxLoginFormUrl // '/login/authAjax'
			forceHttps = conf.auth.forceHttps // false
			useForward = conf.auth.useForward // false
			portMapper = ref('portMapper')
			portResolver = ref('portResolver')
			redirectStrategy = ref('redirectStrategy')
		}

		/** filterInvocationInterceptor */

		// TODO doc new
		if (conf.afterInvocationManagerProviderNames) {
			log.trace 'Configuring AfterInvocationProviderManager'
			afterInvocationManager(classFor('afterInvocationManager', AfterInvocationProviderManager)) {
				providers = [new NullAfterInvocationProvider()] // will be replaced in doWithApplicationContext
			}
		}
		else {
			// register a lightweight impl so there's a bean in either case
			afterInvocationManager(classFor('afterInvocationManager', NullAfterInvocationManager))
		}

		filterInvocationInterceptor(classFor('filterInvocationInterceptor', FilterSecurityInterceptor)) {
			authenticationManager = ref('authenticationManager')
			accessDecisionManager = ref('accessDecisionManager')
			securityMetadataSource = ref('objectDefinitionSource')
			runAsManager = ref('runAsManager')
			afterInvocationManager = ref('afterInvocationManager')
			alwaysReauthenticate = conf.fii.alwaysReauthenticate // false
			rejectPublicInvocations = conf.fii.rejectPublicInvocations // true
			validateConfigAttributes = conf.fii.validateConfigAttributes // true
			publishAuthorizationSuccess = conf.fii.publishAuthorizationSuccess // false
			observeOncePerRequest = conf.fii.observeOncePerRequest // true
		}

		String securityConfigType = SpringSecurityUtils.securityConfigType
		log.trace "Using security config type '{}'", securityConfigType
		if (securityConfigType != 'Annotation' &&
				securityConfigType != 'Requestmap' &&
				securityConfigType != 'InterceptUrlMap') {

			String message = """
ERROR: the 'securityConfigType' property must be one of
'Annotation', 'Requestmap', or 'InterceptUrlMap' or left unspecified
to default to 'Annotation'; setting value to 'Annotation'
"""
			println message
			log.warn message

			securityConfigType = 'Annotation'
		}

		httpServletResponseExtension(classFor('httpServletResponseExtension', HttpServletResponseExtension)) // used to be responseMimeTypesApi

		if (securityConfigType == 'Annotation') {
			objectDefinitionSource(classFor('objectDefinitionSource', AnnotationFilterInvocationDefinition)) {
				application = grailsApplication
				grailsUrlConverter = ref('grailsUrlConverter')
				httpServletResponseExtension = ref('httpServletResponseExtension')
				if (conf.rejectIfNoRule instanceof Boolean) {
					rejectIfNoRule = conf.rejectIfNoRule
				}
			}
		}
		else if (securityConfigType == 'Requestmap') {
			objectDefinitionSource(classFor('objectDefinitionSource', RequestmapFilterInvocationDefinition)) {
				if (conf.rejectIfNoRule instanceof Boolean) {
					rejectIfNoRule = conf.rejectIfNoRule
				}
			}
		}
		else if (securityConfigType == 'InterceptUrlMap') {
			objectDefinitionSource(classFor('objectDefinitionSource', InterceptUrlMapFilterInvocationDefinition)) {
				if (conf.rejectIfNoRule instanceof Boolean) {
					rejectIfNoRule = conf.rejectIfNoRule
				}
			}
		}

		webInvocationPrivilegeEvaluator(classFor('webInvocationPrivilegeEvaluator', GrailsWebInvocationPrivilegeEvaluator), ref('filterInvocationInterceptor'))

		// voters
		configureVoters.delegate = delegate
		configureVoters conf

		/** anonymousAuthenticationProvider */
		anonymousAuthenticationProvider(classFor('anonymousAuthenticationProvider', GrailsAnonymousAuthenticationProvider))

		/** rememberMeAuthenticationProvider */
		rememberMeAuthenticationProvider(classFor('rememberMeAuthenticationProvider', RememberMeAuthenticationProvider), conf.rememberMe.key)

		// authenticationManager
		configureAuthenticationManager.delegate = delegate
		configureAuthenticationManager conf

		/** daoAuthenticationProvider */
		preAuthenticationChecks(classFor('preAuthenticationChecks', DefaultPreAuthenticationChecks))
		postAuthenticationChecks(classFor('postAuthenticationChecks', DefaultPostAuthenticationChecks))

		daoAuthenticationProvider(classFor('daoAuthenticationProvider', DaoAuthenticationProvider)) {
			userDetailsService = ref('userDetailsService')
			passwordEncoder = ref('passwordEncoder')
			userCache = ref('userCache')
			preAuthenticationChecks = ref('preAuthenticationChecks')
			postAuthenticationChecks = ref('postAuthenticationChecks')
			authoritiesMapper = ref('authoritiesMapper')
			hideUserNotFoundExceptions = conf.dao.hideUserNotFoundExceptions // true
		}

		/** passwordEncoder */
		String algorithm = conf.password.algorithm

		log.trace 'Using {} algorithm', algorithm
		passwordEncoder(classFor('passwordEncoder', DelegatingPasswordEncoder), algorithm, idToPasswordEncoder(conf))

		/** userDetailsService */
		userDetailsService(classFor('userDetailsService', GormUserDetailsService)) {
			grailsApplication = grailsApplication
		}

		/** authenticationUserDetailsService */
		authenticationUserDetailsService(classFor('authenticationUserDetailsService', UserDetailsByNameServiceWrapper), ref('userDetailsService'))

		// port mappings for channel security, etc.
		portMapper(classFor('portMapper', PortMapperImpl)) {
			portMappings = [(conf.portMapper.httpPort.toString()) : conf.portMapper.httpsPort.toString()] // 8080, 8443

		}
		portResolver(classFor('portResolver', PortResolverImpl)) {
			portMapper = ref('portMapper')
		}

		// SecurityEventListener
		if (conf.useSecurityEventListener) {
			log.trace 'Configuring SecurityEventListener'
			securityEventListener(classFor('authenticationEventPublisher', SecurityEventListener))

			authenticationEventPublisher(classFor('authenticationEventPublisher', DefaultAuthenticationEventPublisher)) {
				additionalExceptionMappings =
						([(NoStackUsernameNotFoundException.name): AuthenticationFailureBadCredentialsEvent.name] as Properties)
			}
		}
		else {
			authenticationEventPublisher(classFor('authenticationEventPublisher', NullAuthenticationEventPublisher))
		}

		// Basic Auth
		if (conf.useBasicAuth) {
			log.trace 'Configuring Basic auth'
			configureBasicAuth.delegate = delegate
			configureBasicAuth conf
		}

		// Digest Auth
		if (conf.useDigestAuth) {
			log.trace 'Configuring Digest auth'
			configureDigestAuth.delegate = delegate
			configureDigestAuth conf
		}

		// Switch User
		if (conf.useSwitchUserFilter) {

			log.trace 'Configuring SwitchUserFilter'

			// TODO doc new
			switchUserAuthorityChanger(classFor('switchUserAuthorityChanger', NullSwitchUserAuthorityChanger))

			switchUserProcessingFilter(classFor('switchUserProcessingFilter', SwitchUserFilter)) {
				userDetailsService = ref('userDetailsService')
				userDetailsChecker = ref('userDetailsChecker')
				authenticationDetailsSource = ref('authenticationDetailsSource')
				switchUserAuthorityChanger = ref('switchUserAuthorityChanger')
				switchUserUrl = conf.switchUser.switchUserUrl // '/login/impersonate'
				exitUserUrl = conf.switchUser.exitUserUrl // '/logout/impersonate'
				usernameParameter = conf.switchUser.usernameParameter // 'username'
				if (conf.switchUser.targetUrl) {
					targetUrl = conf.switchUser.targetUrl
				}
				else {
					successHandler = ref('authenticationSuccessHandler')
				}
				if (conf.switchUser.switchFailureUrl) {
					switchFailureUrl = conf.switchUser.switchFailureUrl
				}
				else {
					failureHandler = ref('authenticationFailureHandler')
				}
				if (conf.switchUser.switchUserMatcher) {
					switchUserMatcher = conf.switchUser.switchUserMatcher
				}
				if (conf.switchUser.exitUserMatcher) {
					exitUserMatcher = conf.switchUser.exitUserMatcher
				}
			}
		}

		// per-method run-as, defined here so it can be overridden
		runAsManager(classFor('runAsManager', NullRunAsManager))

		// X.509
		if (conf.useX509) {
			log.trace 'Configuring X.509'
			configureX509.delegate = delegate
			configureX509 conf
		}

		// channel (http/https) security
		if (conf.secureChannel.definition) {
			log.trace 'Configuring channel security'
			configureChannelProcessingFilter.delegate = delegate
			configureChannelProcessingFilter conf
		}

		// IP filter
		if (conf.ipRestrictions) {
			log.trace 'Configuring IP restrictions'
			configureIpFilter.delegate = delegate
			configureIpFilter conf
		}

		// user details cache
		if (conf.cacheUsers) {
			log.trace 'Configuring user cache'
			userCache(classFor('userCache', SpringUserCacheFactoryBean)) {
				cacheManager = ref('cacheManager')
				cacheName = 'userCache'
			}
			cacheManager(classFor('cacheManager', JCacheCacheManager))
		}
		else {
			userCache(classFor('userCache', NullUserCache))
		}

		/** loggerListener */
		if (conf.registerLoggerListener) {
			log.trace 'Register LoggerListener'
			loggerListener(classFor('loggerListener', LoggerListener))
		}

		if (conf.debug.useFilter) {
			log.trace 'Register DebugFilter'
			securityDebugFilter(classFor('securityDebugFilter', DebugFilter), ref('springSecurityFilterChain'))
		}

		permissionEvaluator(classFor('permissionEvaluator', DenyAllPermissionEvaluator))

		if (printStatusMessages) {
			String message = '... finished configuring Spring Security Core\n'
			log.info message
			println message
		}

		formContentFilter(classFor('formContentFilter', FormContentFilter))
	}}

	void doWithApplicationContext() {
		ReflectionUtils.application = grailsApplication

		def conf = SpringSecurityUtils.securityConfig
		if (!conf || !conf.active) {
			return
		}

		log.trace 'doWithApplicationContext'

		if (SpringSecurityUtils.securityConfigType == 'Annotation') {
			initializeFromAnnotations conf
		}

		/**
		 * Specify the field of the role hierarchy bean
		 * if the role hierarchy is backed by a domain object use this instead of roleHierarchy config param
		 * @author fpape
		 */
		String roleHierarchy
		if (conf.roleHierarchyEntryClassName) {
			log.trace 'Loading persistent role hierarchy'
			Class roleHierarchyEntryClass = Class.forName(conf.roleHierarchyEntryClassName)
			roleHierarchyEntryClass.withTransaction {
				roleHierarchy = roleHierarchyEntryClass.list()*.entry.join('\n')
			}
		}
		else {
			roleHierarchy = conf.roleHierarchy
		}

		applicationContext.roleHierarchy.hierarchy = roleHierarchy

		def strategyName = conf.sch.strategyName
		if (strategyName instanceof CharSequence) {
			SCH.strategyName = strategyName.toString()
		}
		log.trace 'Using SecurityContextHolder strategy {}', SCH.strategyName

		// build filters here to give dependent plugins a chance to register some
		SortedMap<Integer, String> filterNames = ReflectionUtils.findFilterChainNames(conf)
		def securityFilterChains = applicationContext.securityFilterChains

		// if sitemesh 3 is installed, the filter should be applied a second time
		// as part of the security filter chain so that pages are decorated using the security context
		if (applicationContext.containsBean('sitemesh')) {
			filterNames[SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order - 5] = 'sitemesh'
		}

		SpringSecurityUtils.buildFilterChains filterNames, conf.filterChain.chainMap ?: [], securityFilterChains, applicationContext
		log.trace 'Filter chain: {}', securityFilterChains

		// build voters list here to give dependent plugins a chance to register some
		def voterNames = conf.voterNames ?: SpringSecurityUtils.voterNames
		def decisionVoters = applicationContext.accessDecisionManager.decisionVoters
		decisionVoters.clear()
		decisionVoters.addAll createBeanList(voterNames)
		log.trace 'AccessDecisionVoters: {}', decisionVoters

		// build providers list here to give dependent plugins a chance to register some
		def providerNames = []
		if (conf.providerNames) {
			providerNames.addAll conf.providerNames
		}
		else {
			providerNames.addAll SpringSecurityUtils.providerNames
			if (conf.useX509) {
				providerNames << 'x509AuthenticationProvider'
			}
		}
		applicationContext.authenticationManager.providers = createBeanList(providerNames)
		log.trace 'AuthenticationProviders: {}', applicationContext.authenticationManager.providers

		// build handlers list here to give dependent plugins a chance to register some
		def logoutHandlerNames = (conf.logout.handlerNames ?: SpringSecurityUtils.logoutHandlerNames) +
				(conf.logout.additionalHandlerNames ?: [])
		applicationContext.logoutHandlers.clear()
		applicationContext.logoutHandlers.addAll createBeanList(logoutHandlerNames)
		log.trace 'LogoutHandlers: {}', applicationContext.logoutHandlers

		// build after-invocation provider names here to give dependent plugins a chance to register some
		def afterInvocationManagerProviderNames = conf.afterInvocationManagerProviderNames ?: SpringSecurityUtils.afterInvocationManagerProviderNames
		if (afterInvocationManagerProviderNames) {
			applicationContext.afterInvocationManager.providers = createBeanList(afterInvocationManagerProviderNames)
			log.trace 'AfterInvocationProviders: {}', applicationContext.afterInvocationManager.providers
		}

		if (conf.debug.useFilter) {
			applicationContext.removeAlias 'springSecurityFilterChain'
			applicationContext.registerAlias 'securityDebugFilter', 'springSecurityFilterChain'
		}

		if (conf.useDigestAuth) {
			def passwordEncoder = applicationContext.passwordEncoder
// TODO			if (passwordEncoder instanceof DigestAuthPasswordEncoder) {
//				passwordEncoder.resetInitializing()
//			}
		}
	}

	void onChange(Map<String, Object> event) {
		def conf = SpringSecurityUtils.securityConfig
		if (!conf || !conf.active) {
			return
		}

		if (event.source && grailsApplication.isControllerClass(event.source)) {

			log.trace 'onChange for controller {}', event.source.name

			if (SpringSecurityUtils.securityConfigType == 'Annotation') {
				initializeFromAnnotations conf
			}
		}
	}

	void onConfigChange(Map<String, Object> event) {
		SpringSecurityUtils.resetSecurityConfig()

		def conf = SpringSecurityUtils.securityConfig
		if (!conf || !conf.active) {
			return
		}

		log.trace 'onConfigChange'

		if (SpringSecurityUtils.securityConfigType == 'Annotation') {
			// might have changed controllerAnnotations.staticRules
			initializeFromAnnotations conf
		}
		else if (SpringSecurityUtils.securityConfigType == 'InterceptUrlMap') {
			event.ctx.objectDefinitionSource.reset()
		}
	}

	private void initializeFromAnnotations(conf) {
		AnnotationFilterInvocationDefinition afid = applicationContext.objectDefinitionSource
		afid.initialize conf.controllerAnnotations.staticRules,
				applicationContext.grailsUrlMappingsHolder, grailsApplication.controllerClasses,
				grailsApplication.domainClasses
	}

	private createRefList = { names -> names.collect { name -> ref(name) } }

	private createBeanList(names) { names.collect { name -> applicationContext.getBean(name) } }

	private configureLogout = { conf ->

		securityContextLogoutHandler(classFor('securityContextLogoutHandler', SecurityContextLogoutHandler)) {
			clearAuthentication = conf.logout.clearAuthentication // true
			invalidateHttpSession = conf.logout.invalidateHttpSession // true
		}

		// create an initially empty list here, will be populated in doWithApplicationContext
		logoutHandlers(classFor('logoutHandlers', ArrayList))

		logoutSuccessHandler(classFor('logoutSuccessHandler', SimpleUrlLogoutSuccessHandler)) {
			redirectStrategy = ref('redirectStrategy')
			defaultTargetUrl = conf.logout.afterLogoutUrl // '/'
			alwaysUseDefaultTargetUrl = conf.logout.alwaysUseDefaultTargetUrl // false
			targetUrlParameter = conf.logout.targetUrlParameter // null
			useReferer = conf.logout.redirectToReferer // false
		}

		/** logoutFilter */
		logoutFilter(classFor('logoutFilter', MutableLogoutFilter), ref('logoutSuccessHandler')) {
			filterProcessesUrl = conf.logout.filterProcessesUrl // '/logoff'
			handlers = ref('logoutHandlers')
		}
	}

	private configureBasicAuth = { conf ->

		basicAuthenticationEntryPoint(classFor('basicAuthenticationEntryPoint', BasicAuthenticationEntryPoint)) {
			realmName = conf.basic.realmName // 'Grails Realm'
		}

		basicAuthenticationFilter(classFor('basicAuthenticationFilter', BasicAuthenticationFilter),
				ref('authenticationManager'), ref('basicAuthenticationEntryPoint')) {
			authenticationDetailsSource = ref('authenticationDetailsSource')
			rememberMeServices = ref('rememberMeServices')
			credentialsCharset = conf.basic.credentialsCharset // 'UTF-8'
		}

		basicAccessDeniedHandler(classFor('basicAccessDeniedHandler', AccessDeniedHandlerImpl))

		basicRequestCache(classFor('basicRequestCache', NullRequestCache))

		basicExceptionTranslationFilter(classFor('basicExceptionTranslationFilter', UpdateRequestContextHolderExceptionTranslationFilter),
				ref('basicAuthenticationEntryPoint'), ref('basicRequestCache')) {
			accessDeniedHandler = ref('basicAccessDeniedHandler')
			authenticationTrustResolver = ref('authenticationTrustResolver')
			throwableAnalyzer = ref('throwableAnalyzer')
		}
	}

	private configureDigestAuth = { conf ->

		if (conf.digest.useCleartextPasswords) {
			passwordEncoder(classFor('passwordEncoder', DelegatingPasswordEncoder), ENCODING_ID_NOOP, idToPasswordEncoder(conf))
		}
		else {
			conf.digest.passwordAlreadyEncoded = true
			conf.dao.reflectionSaltSourceProperty = conf.userLookup.usernamePropertyName
			passwordEncoder(classFor('passwordEncoder', DigestAuthPasswordEncoder)) {
				realm = conf.digest.realmName
			}
		}

		digestAuthenticationEntryPoint(classFor('digestAuthenticationEntryPoint', DigestAuthenticationEntryPoint)) {
			realmName = conf.digest.realmName // 'Grails Realm'
			key = conf.digest.key // 'changeme'
			nonceValiditySeconds = conf.digest.nonceValiditySeconds // 300
		}

		digestAuthenticationFilter(classFor('digestAuthenticationFilter', DigestAuthenticationFilter)) {
			authenticationDetailsSource = ref('authenticationDetailsSource')
			authenticationEntryPoint = ref('digestAuthenticationEntryPoint')
			userCache = ref('userCache')
			userDetailsService = ref('userDetailsService')
			passwordAlreadyEncoded = conf.digest.passwordAlreadyEncoded // false
			createAuthenticatedToken = conf.digest.createAuthenticatedToken // false
		}

		digestAccessDeniedHandler(classFor('digestAccessDeniedHandler', AccessDeniedHandlerImpl))

		digestExceptionTranslationFilter(classFor('digestExceptionTranslationFilter', UpdateRequestContextHolderExceptionTranslationFilter),
				ref('digestAuthenticationEntryPoint'), ref('requestCache')) {
			accessDeniedHandler = ref('digestAccessDeniedHandler')
			authenticationTrustResolver = ref('authenticationTrustResolver')
			throwableAnalyzer = ref('throwableAnalyzer')
		}
	}

	private configureVoters = { conf ->

		// the hierarchy string is set in doWithApplicationContext to support building
		// from the database using GORM if roleHierarchyEntryClassName is set
		roleHierarchy(classFor('roleHierarchy', RoleHierarchyImpl))

		roleVoter(classFor('roleVoter', RoleHierarchyVoter), ref('roleHierarchy'))

		authenticatedVoter(classFor('authenticatedVoter', AuthenticatedVoter)) {
			authenticationTrustResolver = ref('authenticationTrustResolver')
		}

		voterExpressionParser(classFor('voterExpressionParser', SpelExpressionParser))

		webExpressionHandler(classFor('webExpressionHandler', DefaultWebSecurityExpressionHandler)) {
			expressionParser = ref('voterExpressionParser')
			permissionEvaluator = ref('permissionEvaluator')
			roleHierarchy = ref('roleHierarchy')
			trustResolver = ref('authenticationTrustResolver')
		}

		webExpressionVoter(classFor('webExpressionVoter', WebExpressionVoter)) {
			expressionHandler = ref('webExpressionHandler')
		}

		closureVoter(classFor('closureVoter', ClosureVoter))

		// create the default list here, will be replaced in doWithApplicationContext
		def voters = createRefList(SpringSecurityUtils.voterNames)

		/** accessDecisionManager */
		accessDecisionManager(classFor('accessDecisionManager', AuthenticatedVetoableDecisionManager), voters) {
			allowIfAllAbstainDecisions = false
		}
	}

	private configureAuthenticationManager = { conf ->

		// create the default list here, will be replaced in doWithApplicationContext
		def providerRefs = createRefList(SpringSecurityUtils.providerNames)

		/** authenticationManager */
		authenticationManager(classFor('authenticationManager', ProviderManager), providerRefs) {
			authenticationEventPublisher = ref('authenticationEventPublisher')
			eraseCredentialsAfterAuthentication = conf.providerManager.eraseCredentialsAfterAuthentication // true
		}
	}

	private configureFilterChain = { conf ->

		filterChainValidator(classFor('filterChainValidator', NullFilterChainValidator))

		httpFirewall(classFor('httpFirewall', DefaultHttpFirewall))

		securityFilterChains(classFor('securityFilterChains', ArrayList))

		springSecurityFilterChainProxy(classFor('springSecurityFilterChainProxy', FilterChainProxy), ref('securityFilterChains')) {
			filterChainValidator = ref('filterChainValidator')
			firewall = ref('httpFirewall')
		}

		springConfig.addAlias 'springSecurityFilterChain', 'springSecurityFilterChainProxy'
	}

	private configureChannelProcessingFilter = { conf ->

		retryWithHttpEntryPoint(classFor('retryWithHttpEntryPoint', RetryWithHttpEntryPoint)) {
			portMapper = ref('portMapper')
			portResolver = ref('portResolver')
			redirectStrategy = ref('redirectStrategy')
		}

		retryWithHttpsEntryPoint(classFor('retryWithHttpsEntryPoint', RetryWithHttpsEntryPoint)) {
			portMapper = ref('portMapper')
			portResolver = ref('portResolver')
			redirectStrategy = ref('redirectStrategy')
		}

		secureChannelProcessor(classFor('secureChannelProcessor', SecureChannelProcessor)) {
			entryPoint = ref('retryWithHttpsEntryPoint')
			secureKeyword = conf.secureChannel.secureConfigAttributeKeyword // 'REQUIRES_SECURE_CHANNEL'
		}

		insecureChannelProcessor(classFor('insecureChannelProcessor', InsecureChannelProcessor)) {
			entryPoint = ref('retryWithHttpEntryPoint')
			insecureKeyword = conf.secureChannel.insecureConfigAttributeKeyword // 'REQUIRES_INSECURE_CHANNEL'
		}

		channelDecisionManager(classFor('channelDecisionManager', ChannelDecisionManagerImpl)) {
			channelProcessors = [ref('insecureChannelProcessor'), ref('secureChannelProcessor')]
		}

		if (conf.secureChannel.definition instanceof Map) {
			throw new IllegalArgumentException('secureChannel.definition defined as a Map is not supported; must be specified as a ' +
					"List of Maps as described in section 'Channel Security' of the reference documentation")
		}
		channelFilterInvocationSecurityMetadataSource(classFor('channelFilterInvocationSecurityMetadataSource', ChannelFilterInvocationSecurityMetadataSourceFactoryBean)) {
			definition = conf.secureChannel.definition
		}
		channelProcessingFilter(classFor('channelProcessingFilter', ChannelProcessingFilter)) {
			channelDecisionManager = ref('channelDecisionManager')
			securityMetadataSource = ref('channelFilterInvocationSecurityMetadataSource')
		}
	}

	private configureIpFilter = { conf ->

		if (conf.ipRestrictions instanceof Map) {
			throw new IllegalArgumentException("ipRestrictions defined as a Map is not supported; must be specified as a " +
					"List of Maps as described in section 'IP Address Restrictions' of the reference documentation")
		}

		if (!(conf.ipRestrictions instanceof List)) {
			return
		}

		ipAddressFilter(classFor('ipAddressFilter', IpAddressFilter)) {
			ipRestrictions = conf.ipRestrictions
		}
	}

	private configureAuthenticationProcessingFilter = { conf ->

		// TODO create plugin version that overrides extractAttributes
		if (conf.useSessionFixationPrevention) {
			log.trace 'Configuring session fixation prevention'
			sessionAuthenticationStrategy(classFor('sessionAuthenticationStrategy', SessionFixationProtectionStrategy)) {
				migrateSessionAttributes = conf.sessionFixationPrevention.migrate // true
				alwaysCreateSession = conf.sessionFixationPrevention.alwaysCreateSession // false
			}
		}
		else {
			sessionAuthenticationStrategy(classFor('sessionAuthenticationStrategy', NullAuthenticatedSessionStrategy))
		}

		if (conf.failureHandler.exceptionMappings instanceof Map) {
			throw new IllegalArgumentException('failureHandler.exceptionMappings defined as a Map is not supported; ' +
					'''must be specified as a List of Maps, e.g.
[
   [exception: 'org.springframework.security.authentication.LockedException',             url: '/user/accountLocked'],
   [exception: 'org.springframework.security.authentication.DisabledException',           url: '/user/accountDisabled'],
   [exception: 'org.springframework.security.authentication.AccountExpiredException',     url: '/user/accountExpired'],
   [exception: 'org.springframework.security.authentication.CredentialsExpiredException', url: '/user/passwordExpired']
]
''')
		}
		authenticationFailureHandler(classFor('authenticationFailureHandler', AjaxAwareAuthenticationFailureHandler)) {
			redirectStrategy = ref('redirectStrategy')
			defaultFailureUrl = conf.failureHandler.defaultFailureUrl //'/login/authfail?login_error=1'
			useForward = conf.failureHandler.useForward // false
			ajaxAuthenticationFailureUrl = conf.failureHandler.ajaxAuthFailUrl // '/login/authfail?ajax=true'
			exceptionMappingsList = conf.failureHandler.exceptionMappings // []
			allowSessionCreation = conf.failureHandler.allowSessionCreation // true
		}

		filterProcessUrlRequestMatcher(classFor('filterProcessUrlRequestMatcher', FilterProcessUrlRequestMatcher), conf.apf.filterProcessesUrl) // '/login/authenticate'

		authenticationProcessingFilter(classFor('authenticationProcessingFilter', GrailsUsernamePasswordAuthenticationFilter)) {
			authenticationManager = ref('authenticationManager')
			sessionAuthenticationStrategy = ref('sessionAuthenticationStrategy')
			authenticationSuccessHandler = ref('authenticationSuccessHandler')
			authenticationFailureHandler = ref('authenticationFailureHandler')
			rememberMeServices = ref('rememberMeServices')
			authenticationDetailsSource = ref('authenticationDetailsSource')
			requiresAuthenticationRequestMatcher = ref('filterProcessUrlRequestMatcher')
			usernameParameter = conf.apf.usernameParameter // username
			passwordParameter = conf.apf.passwordParameter // password
			continueChainBeforeSuccessfulAuthentication = conf.apf.continueChainBeforeSuccessfulAuthentication // false
			allowSessionCreation = conf.apf.allowSessionCreation // true
			postOnly = conf.apf.postOnly // true
			storeLastUsername = conf.apf.storeLastUsername // false
		}

		authenticationDetailsSource(classFor('authenticationDetailsSource', WebAuthenticationDetailsSource))

		requestMatcher(classFor('requestMatcher', AnyRequestMatcher))

		requestCache(classFor('requestCache', HttpSessionRequestCache)) {
			portResolver = ref('portResolver')
			createSessionAllowed = conf.requestCache.createSession // true
			requestMatcher = ref('requestMatcher')
		}

		authenticationSuccessHandler(classFor('authenticationSuccessHandler', AjaxAwareAuthenticationSuccessHandler)) {
			requestCache = ref('requestCache')
			defaultTargetUrl = conf.successHandler.defaultTargetUrl // '/'
			alwaysUseDefaultTargetUrl = conf.successHandler.alwaysUseDefault // false
			targetUrlParameter = conf.successHandler.targetUrlParameter // 'spring-security-redirect'
			ajaxSuccessUrl = conf.successHandler.ajaxSuccessUrl // '/login/ajaxSuccess'
			useReferer = conf.successHandler.useReferer // false
			redirectStrategy = ref('redirectStrategy')
		}

		redirectStrategy(classFor('redirectStrategy', GrailsRedirectStrategy)) {
			useHeaderCheckChannelSecurity = conf.secureChannel.useHeaderCheckChannelSecurity // false
			portResolver = ref('portResolver')
		}
	}

	private configureX509 = { conf ->

		x509AuthenticationFailureHandler(NullAuthenticationFailureHandler)
		x509AuthenticationSuccessHandler(NullAuthenticationSuccessHandler)

		x509ProcessingFilter(classFor('x509ProcessingFilter', X509AuthenticationFilter)) {
			authenticationDetailsSource = ref('authenticationDetailsSource')
			authenticationFailureHandler = ref('x509AuthenticationFailureHandler')
			authenticationManager = ref('authenticationManager')
			authenticationSuccessHandler = ref('x509AuthenticationSuccessHandler')
			principalExtractor = ref('x509PrincipalExtractor')
			checkForPrincipalChanges = conf.x509.checkForPrincipalChanges // false
			continueFilterChainOnUnsuccessfulAuthentication = conf.x509.continueFilterChainOnUnsuccessfulAuthentication // true
			invalidateSessionOnPrincipalChange = conf.x509.invalidateSessionOnPrincipalChange // true
		}

		if (conf.x509.subjectDnClosure) {
			x509PrincipalExtractor(classFor('x509PrincipalExtractor', ClosureX509PrincipalExtractor))
		}
		else {
			x509PrincipalExtractor(classFor('x509PrincipalExtractor', SubjectDnX509PrincipalExtractor)) {
				subjectDnRegex = conf.x509.subjectDnRegex // 'CN=(.*?)(?:,|$)'
			}
		}

		x509AuthenticationProvider(classFor('x509AuthenticationProvider', PreAuthenticatedAuthenticationProvider)) {
			preAuthenticatedUserDetailsService = ref('authenticationUserDetailsService')
			userDetailsChecker = ref('userDetailsChecker')
			throwExceptionWhenTokenRejected = conf.x509.throwExceptionWhenTokenRejected // false
		}

		authenticationEntryPoint(classFor('authenticationEntryPoint', Http403ForbiddenEntryPoint))
	}

	private Class classFor(String beanName, Class defaultType) {
		beanTypeResolver.resolveType beanName, defaultType
	}


	static Map<String, PasswordEncoder> idToPasswordEncoder(ConfigObject conf) {

		MessageDigestPasswordEncoder messageDigestPasswordEncoderMD5 = new MessageDigestPasswordEncoder(ENCODING_ID_MD5)
		messageDigestPasswordEncoderMD5.encodeHashAsBase64 = conf.password.encodeHashAsBase64 // false
		messageDigestPasswordEncoderMD5.iterations = conf.password.hash.iterations // 10000

		MessageDigestPasswordEncoder messageDigestPasswordEncoderSHA1 = new MessageDigestPasswordEncoder(ENCODING_ID_SHA1)
		messageDigestPasswordEncoderSHA1.encodeHashAsBase64 = conf.password.encodeHashAsBase64 // false
		messageDigestPasswordEncoderSHA1.iterations = conf.password.hash.iterations // 10000

		MessageDigestPasswordEncoder messageDigestPasswordEncoderSHA256 = new MessageDigestPasswordEncoder(ENCODING_IDSHA256)
		messageDigestPasswordEncoderSHA256.encodeHashAsBase64 = conf.password.encodeHashAsBase64 // false
		messageDigestPasswordEncoderSHA256.iterations = conf.password.hash.iterations // 10000

		int strength = conf.password.bcrypt.logrounds
		[(ENCODING_ID_BCRYPT): new BCryptPasswordEncoder(strength),
		 (ENCODING_ID_LDAP): new LdapShaPasswordEncoder(),
		 (ENCODING_ID_MD4): new Md4PasswordEncoder(),
		 (ENCODING_ID_MD5): messageDigestPasswordEncoderMD5,
		 (ENCODING_ID_NOOP): NoOpPasswordEncoder.getInstance(),
		 (ENCODING_ID_PBKDF2): Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
		 (ENCODING_ID_SCRYPT): SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8(),
		 (ENCODING_ID_ARGON2): Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
		 (ENCODING_ID_SHA1): messageDigestPasswordEncoderSHA1,
		 (ENCODING_IDSHA256): messageDigestPasswordEncoderSHA256,
		 "sha256": new StandardPasswordEncoder()]
	}
}
