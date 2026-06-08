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

import grails.core.GrailsApplication
import grails.plugin.springsecurity.web.GrailsSecurityFilterChain
import grails.plugin.springsecurity.web.SecurityRequestHolder
import grails.util.Environment
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.text.StringEscapeUtils
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserCache
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.WebAttributes
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority
import org.springframework.security.web.savedrequest.SavedRequest
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartHttpServletRequest

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY

/**
 * Helper methods.
 *
 * @author Burt Beckwith
 */
@CompileStatic
@Slf4j
final class SpringSecurityUtils {

	private static final String MULTIPART_HTTP_SERVLET_REQUEST_KEY = MultipartHttpServletRequest.name

	private static ConfigObject _securityConfig
	private static GrailsApplication application

	/** Ordered filter names. Plugins add or remove them, and can be overridden by config. */
	static Map<Integer, String> orderedFilters = [:]

	/** Set by SpringSecurityCoreGrailsPlugin contains the actual filter beans in order. */
	static SortedMap<Integer, Filter> configuredOrderedFilters = new TreeMap<Integer, Filter>()

	/** Authentication provider names. Plugins add or remove them, and can be overridden by config. */
	static List<String> providerNames = []

	/** Logout handler names. Plugins add or remove them, and can be overridden by config. */
	static List<String> logoutHandlerNames = []

	/** AfterInvocationProvider names. Plugins add or remove them, and can be overridden by config. */
	static List<String> afterInvocationManagerProviderNames = []

	/** Voter names. Plugins add or remove them and can be overridden by config. */
	static List<String> voterNames = []

	// HttpSessionRequestCache.SAVED_REQUEST is package-scope
	public static final String SAVED_REQUEST = 'SPRING_SECURITY_SAVED_REQUEST' // TODO use requestCache

	// UsernamePasswordAuthenticationFilter.SPRING_SECURITY_LAST_USERNAME_KEY is deprecated
	public static final String SPRING_SECURITY_LAST_USERNAME_KEY = 'SPRING_SECURITY_LAST_USERNAME'

	// AbstractAuthenticationTargetUrlRequestHandler.DEFAULT_TARGET_PARAMETER was removed
	public static final String DEFAULT_TARGET_PARAMETER = 'spring-security-redirect'

	/** Default value for the name of the Ajax header. */
	public static final String AJAX_HEADER = 'X-Requested-With'

	/**
	 * Used to ensure that all authenticated users have at least one granted authority to work
	 * around Spring Security code that assumes at least one. By granting this non-authority,
	 * the user can't do anything but gets past the somewhat arbitrary restrictions.
	 */
	public static final String NO_ROLE = 'ROLE_NO_ROLES'

	public static final String XML_HTTP_REQUEST = 'XMLHttpRequest'
	public static final String FILTERS_NONE = 'none'

	private SpringSecurityUtils() {
		// static only
	}

	/**
	 * Set at startup by plugin.
	 * @param app the application
	 */
	static void setApplication(GrailsApplication app) {
		application = app
		initializeContext()
	}

	/**
	 * Extract the role names from authorities.
	 * @param authorities the authorities (a collection or array of {@link GrantedAuthority}).
	 * @return the names
	 */
	static Set<String> authoritiesToRoles(authorities) {
		Set<String> roles = new HashSet<String>()
		for (authority in ReflectionUtils.asList(authorities)) {
			String authorityName = ((GrantedAuthority)authority).authority
			assert authorityName != null,
				"Cannot process GrantedAuthority objects which return null from getAuthority() - attempting to process $authority"
			roles << authorityName
		}

		roles
	}

	/**
	 * Get the current user's authorities.
	 * @return a list of authorities (empty if not authenticated).
	 */
	@CompileDynamic
	static Collection<GrantedAuthority> getPrincipalAuthorities() {
		Authentication authentication = getAuthentication()
		if (!authentication) {
			return Collections.emptyList()
		}

		Collection<? extends GrantedAuthority> authorities = authentication.authorities
		if (authorities == null) {
			return Collections.emptyList()
		}

		// remove the fake role if it's there
		Collection<GrantedAuthority> copy = ([] + authorities) as Collection<GrantedAuthority>
		for (Iterator<GrantedAuthority> iter = copy.iterator(); iter.hasNext();) {
			if (NO_ROLE == iter.next().authority) {
				iter.remove()
			}
		}

		copy
	}

	/**
	 * Split the role names and create {@link GrantedAuthority}s for each.
	 * @param roleNames comma-delimited role names
	 * @return authorities (possibly empty)
	 */
	static List<GrantedAuthority> parseAuthoritiesString(String roleNames) {
		List<GrantedAuthority> requiredAuthorities = []
		for (String auth in StringUtils.commaDelimitedListToStringArray(roleNames)) {
			auth = auth.trim()
			if (auth) {
				requiredAuthorities << new SimpleGrantedAuthority(auth)
			}
		}

		requiredAuthorities
	}

	/**
	 * Find authorities in <code>granted</code> that are also in <code>required</code>.
	 * @param granted the granted authorities (a collection or array of {@link GrantedAuthority}).
	 * @param required the required authorities (a collection or array of {@link GrantedAuthority}).
	 * @return the authority names
	 */
	static Set<String> retainAll(granted, required) {
		Set<String> grantedRoles = authoritiesToRoles(granted)
		grantedRoles.retainAll authoritiesToRoles(required)
		grantedRoles
	}

	/**
	 * Check if the current user has all of the specified roles.
	 * @param roles a comma-delimited list of role names
	 * @return <code>true</code> if the user is authenticated and has all the roles
	 */
	static boolean ifAllGranted(String roles) {
		ifAllGranted parseAuthoritiesString(roles)
	}

	static boolean ifAllGranted(Collection<? extends GrantedAuthority> roles) {
		authoritiesToRoles(findInferredAuthorities(principalAuthorities)).containsAll authoritiesToRoles(roles)
	}

	/**
	 * Check if the current user has none of the specified roles.
	 * @param roles a comma-delimited list of role names
	 * @return <code>true</code> if the user is authenticated and has none the roles
	 */
	static boolean ifNotGranted(String roles) {
		ifNotGranted parseAuthoritiesString(roles)
	}

	static boolean ifNotGranted(Collection<? extends GrantedAuthority> roles) {
		!retainAll(findInferredAuthorities(principalAuthorities), roles)
	}

	/**
	 * Check if the current user has any of the specified roles.
	 * @param roles a comma-delimited list of role names
	 * @return <code>true</code> if the user is authenticated and has any the roles
	 */
	static boolean ifAnyGranted(String roles) {
		ifAnyGranted parseAuthoritiesString(roles)
	}

	static boolean ifAnyGranted(Collection<? extends GrantedAuthority> roles) {
		retainAll findInferredAuthorities(principalAuthorities), roles
	}

	/**
	 * Parse and load the security configuration.
	 * @return the configuration
	 */
	static synchronized ConfigObject getSecurityConfig() {
		if (_securityConfig == null) {
			log.trace 'Building security config since there is no cached config'
			reloadSecurityConfig()
		}

		_securityConfig
	}

	/**
	 * For testing only.
	 * @param config the config
	 */
	static void setSecurityConfig(ConfigObject config) {
		_securityConfig = config
	}

	/** Reset the config for testing or after a dev mode Config.groovy change. */
	static synchronized void resetSecurityConfig() {
		_securityConfig = null
		log.trace 'reset security config'
	}

	/**
	 * Allow a secondary plugin to add config attributes.
	 * @param className the name of the config class.
	 */
	static synchronized void loadSecondaryConfig(String className) {
		mergeConfig securityConfig, className
		log.trace 'loaded secondary config {}', className
	}

	/** Force a reload of the security configuration. */
	static void reloadSecurityConfig() {
		mergeConfig ReflectionUtils.securityConfig, 'DefaultSecurityConfig'
		log.trace 'reloaded security config'
	}

	/**
	 * Check if the request was triggered by an Ajax call.
	 * @param request the request
	 * @return <code>true</code> if Ajax
	 */
	static boolean isAjax(HttpServletRequest request) {

		String ajaxHeaderName = (String)ReflectionUtils.getConfigProperty('ajaxHeader')

		// check the current request's headers
		if (XML_HTTP_REQUEST == request.getHeader(ajaxHeaderName)) {
			return true
		}

		def ajaxCheckClosure = ReflectionUtils.getConfigProperty('ajaxCheckClosure')
		if (ajaxCheckClosure instanceof Closure) {
			def result = ajaxCheckClosure(request)
			if (result instanceof Boolean && result) {
				return true
			}
		}

		// look for an ajax=true parameter
		if ('true' == request.getParameter('ajax')) {
			return true
		}

		// process multipart requests
		MultipartHttpServletRequest multipart = (MultipartHttpServletRequest)request.getAttribute(MULTIPART_HTTP_SERVLET_REQUEST_KEY)
		if ('true' == multipart?.getParameter('ajax')) {
			return true
		}

		// check the SavedRequest's headers
		HttpSession httpSession = request.getSession(false)
		if (httpSession) {
			SavedRequest savedRequest = (SavedRequest)httpSession.getAttribute(SAVED_REQUEST)
			if (savedRequest) {
				return savedRequest.getHeaderValues(ajaxHeaderName).contains(MULTIPART_HTTP_SERVLET_REQUEST_KEY)
			}
		}

		false
	}

	/**
	 * Register a provider bean name.
	 *
	 * Note - only for use by plugins during bean building.
	 *
	 * @param beanName the Spring bean name of the provider
	 */
	static void registerProvider(String beanName) {
		providerNames.add 0, beanName
		log.trace 'Registered bean "{}" as a provider', beanName
	}

	/**
	 * Register a logout handler bean name.
	 *
	 * Note - only for use by plugins during bean building.
	 *
	 * @param beanName the Spring bean name of the handler
	 */
	static void registerLogoutHandler(String beanName) {
		logoutHandlerNames.add 0, beanName
		log.trace 'Registered bean "{}" as a logout handler', beanName
	}

	/**
	 * Register an AfterInvocationProvider bean name.
	 *
	 * Note - only for use by plugins during bean building.
	 *
	 * @param beanName the Spring bean name of the provider
	 */
	static void registerAfterInvocationProvider(String beanName) {
		afterInvocationManagerProviderNames.add 0, beanName
		log.trace 'Registered bean "{}" as an AfterInvocationProvider', beanName
	}

	/**
	 * Register a voter bean name.
	 *
	 * Note - only for use by plugins during bean building.
	 *
	 * @param beanName the Spring bean name of the voter
	 */
	static void registerVoter(String beanName) {
		voterNames.add 0, beanName
		log.trace 'Registered bean "{}" as a voter', beanName
	}

	/**
	 * Register a filter bean name in a specified position in the chain.
	 *
	 * Note - only for use by plugins during bean building - to register at runtime
	 * (preferably in BootStrap) use <code>clientRegisterFilter</code>.
	 *
	 * @param beanName the Spring bean name of the filter
	 * @param position the position
	 */
	static void registerFilter(String beanName, SecurityFilterPosition position) {
		registerFilter beanName, position.order
	}

	/**
	 * Register a filter bean name in a specified position in the chain.
	 *
	 * Note - only for use by plugins during bean building - to register at runtime
	 * (preferably in BootStrap) use <code>clientRegisterFilter</code>.
	 *
	 * @param beanName the Spring bean name of the filter
	 * @param order the position (see {@link SecurityFilterPosition})
	 */
	static void registerFilter(String beanName, int order) {
		String oldName = orderedFilters[order]
		assert oldName == null, "Cannot register filter '$beanName' at position $order; '$oldName' is already registered in that position"
		orderedFilters[order] = beanName

		log.trace 'Registered bean "{}" as a filter at order {}', beanName, order
	}

	/**
	 * Register a filter in a specified position in the chain.
	 *
	 * Note - this is for use in application code after the plugin has initialized,
	 * e.g. in BootStrap where you want to register a custom filter in the correct
	 * order without dealing with the existing configured filters.
	 *
	 * @param beanName the Spring bean name of the filter
	 * @param position the position
	 */
	static void clientRegisterFilter(String beanName, SecurityFilterPosition position) {
		clientRegisterFilter beanName, position.order
	}

	/**
	 * Register a filter in a specified position in the chain.
	 *
	 * Note - this is for use in application code after the plugin has initialized,
	 * e.g. in BootStrap where you want to register a custom filter in the correct
	 * order without dealing with the existing configured filters.
	 *
	 * @param beanName the Spring bean name of the filter
	 * @param order the position (see {@link SecurityFilterPosition})
	 */
	@SuppressWarnings('deprecation')
	static void clientRegisterFilter(String beanName, int order) {
		Filter oldFilter = configuredOrderedFilters.get(order)
		assert !oldFilter,
			"Cannot register filter '$beanName' at position $order; '$oldFilter' is already registered in that position"

		def filter = getBean(beanName)
		if (filter instanceof FilterRegistrationBean) {
			filter = ((FilterRegistrationBean) filter).filter
		}
		configuredOrderedFilters[order] = (Filter) filter

		List<GrailsSecurityFilterChain> filterChains = getBean('securityFilterChains', List)
		mergeFilterChains configuredOrderedFilters, (Filter) filter, beanName, order, filterChains

		log.trace 'Client registered bean "{}" as a filter at order {}', beanName, order
		log.trace 'Updated filter chain: {}', filterChains
	}

	private static void mergeFilterChains(Map<Integer, Filter> orderedFilters, Filter filter, String beanName,
	                                      int order, List<GrailsSecurityFilterChain> filterChains) {

		Map<Filter, Integer> filterToPosition = new HashMap<Filter, Integer>()
		orderedFilters.each { Integer position, Filter f -> filterToPosition[f] = position }

		List<Map<String, ?>> chainMap = (List)(ReflectionUtils.getConfigProperty('filterChain.chainMap') ?: [])
		for (GrailsSecurityFilterChain filterChain in filterChains) {

			if (noFilterIsApplied(chainMap, filterChain.matcherPattern) || filterIsExcluded(chainMap, filterChain.matcherPattern, beanName)) {
				continue
			}

			List<Filter> filters = filterChain.filters.collect() // copy
			int index = 0
			while (index < filters.size() && filterToPosition[filters[index]] < order) {
				index++
			}
			filters.add index, filter

			filterChain.filters.clear()
			filterChain.filters.addAll filters
		}
	}

	public static boolean noFilterIsApplied(List<Map<String, ?>> chainMap, String pattern) {
		Map<String, ?> entry = chainMap.find { Map<String, ?> entry -> entry.pattern == pattern }
		if (!entry) {
			return false
		}
		String filters = entry.filters ?: ''
		String[] filtersArray = filters.split(',')
		(filtersArray.size() == 1 && filtersArray[0] == FILTERS_NONE)
	}

	private static boolean filterIsExcluded(List<Map<String, ?>> chainMap, String pattern, String filterName) {
		for (Map<String, ?> entry in chainMap) {
			if (entry.pattern != pattern) {
				continue
			}

			String filters = entry.filters
			for (item in filters.split(',')) {
				item = item.toString().trim()
				if (item.startsWith('-') && item.substring(1) == filterName) {
					return true
				}
			}
			return false
		}

		return false
	}

	/**
	 * Check if the current user is switched to another user.
	 * @return <code>true</code> if logged in and switched
	 */
	static boolean isSwitched() {
		findInferredAuthorities(principalAuthorities).any { authority ->
			(authority instanceof SwitchUserGrantedAuthority) ||
			SwitchUserFilter.ROLE_PREVIOUS_ADMINISTRATOR == ((GrantedAuthority)authority).authority
		}
	}

	/**
	 * Get the username of the original user before switching to another.
	 * @return the original login name
	 */
	static String getSwitchedUserOriginalUsername() {
		if (isSwitched()) {
			((SwitchUserGrantedAuthority)authentication.authorities.find({ it instanceof SwitchUserGrantedAuthority }))?.source?.name
		}
	}

	/**
	 * Lookup the security type as a String to avoid dev mode reload issues.
	 * @return the name of the <code>SecurityConfigType</code>
	 */
	static String getSecurityConfigType() {
		securityConfig.securityConfigType
	}

	/**
	 * Rebuild an Authentication for the given username and register it in the security context.
	 * Typically used after updating a user's authorities or other auth-cached info.
	 *
	 * Also removes the user from the user cache to force a refresh at next login.
	 *
	 * @param username the user's login name
	 * @param password optional
	 */
	static void reauthenticate(String username, String password) {
		UserDetails userDetails = getBean('userDetailsService', UserDetailsService).loadUserByUsername(username)

		SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
				userDetails, password == null ? userDetails.password : password, userDetails.authorities)

		getBean('userCache', UserCache).removeUserFromCache username
	}

	/**
	 * Execute a closure with the current authentication. Assumes that there's an authentication in the
	 * http session and that the closure is running in a separate thread from the web request, so the
	 * context and authentication aren't available to the standard ThreadLocal.
	 *
	 * @param closure the code to run
	 * @return the closure's return value
	 */
	static doWithAuth(Closure<?> closure) {
		boolean set = false
		if (!authentication && SecurityRequestHolder.request) {
			HttpSession httpSession = SecurityRequestHolder.request.getSession(false)
			if (httpSession) {
				def securityContext = httpSession.getAttribute(SPRING_SECURITY_CONTEXT_KEY)
				if (securityContext instanceof SecurityContext) {
					SecurityContextHolder.context = (SecurityContext)securityContext
					set = true
				}
			}
		}

		try {
			closure()
		}
		finally {
			if (set) {
				SecurityContextHolder.clearContext()
			}
		}
	}

	/**
	 * Authenticate as the specified user and execute the closure with that authentication. Restores
	 * the authentication to the one that was active if it exists, or clears the context otherwise.
	 *
	 * This is similar to run-as and switch-user but is only local to a Closure.
	 *
	 * @param username the username to authenticate as
	 * @param closure the code to run
	 * @return the closure's return value
	 */
	static doWithAuth(String username, Closure<?> closure) {
		Authentication previousAuth = authentication
		reauthenticate username, null

		try {
			closure()
		}
		finally {
			if (!previousAuth) {
				SecurityContextHolder.clearContext()
			}
			else {
				SecurityContextHolder.context.authentication = previousAuth
			}
		}
	}

	static SecurityContext getSecurityContext(HttpSession session) {
		def securityContext = session.getAttribute(SPRING_SECURITY_CONTEXT_KEY)
		if (securityContext instanceof SecurityContext) {
			(SecurityContext)securityContext
		}
	}

	/**
	 * Get the last auth exception.
	 * @param session the session
	 * @return the exception
	 */
	static Throwable getLastException(HttpSession session) {
		(Throwable)session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION)
	}

	/**
	 * Get the last attempted username.
	 * @param session the session
	 * @return the username
	 */
	static String getLastUsername(HttpSession session) {
		String username = (String)session.getAttribute(SPRING_SECURITY_LAST_USERNAME_KEY)
		if (username) {
			username = StringEscapeUtils.unescapeHtml4(username)
		}
		username
	}

	/**
	 * Get the saved request from the session.
	 * @param session the session
	 * @return the saved request
	 */
	static SavedRequest getSavedRequest(HttpSession session) {
		(SavedRequest)session.getAttribute(SAVED_REQUEST)
	}

	/**
	 * Merge in a secondary config (provided by a plugin as defaults) into the main config.
	 * @param currentConfig the current configuration
	 * @param className the name of the config class to load
	 */
	private static void mergeConfig(ConfigObject currentConfig, String className) {
		ConfigObject secondary = new ConfigSlurper(Environment.current.name).parse(
				  new GroovyClassLoader(this.classLoader).loadClass(className))
		_securityConfig = ReflectionUtils.securityConfig = mergeConfig(currentConfig, secondary.security as ConfigObject)
	}

	/**
	 * Merge two configs together. The order is important if <code>secondary</code> is not null then
	 * start with that and merge the main config on top of that. This lets the <code>secondary</code>
	 * config act as default values but let user-supplied values in the main config override them.
	 *
	 * @param currentConfig the main config, starting from Config.groovy
	 * @param secondary new default values
	 * @return the merged configs
	 */
	private static ConfigObject mergeConfig(ConfigObject currentConfig, ConfigObject secondary) {
		(secondary ?: new ConfigObject()).merge(currentConfig ?: new ConfigObject()) as ConfigObject
	}

	private static Collection<? extends GrantedAuthority> findInferredAuthorities(Collection<GrantedAuthority> granted) {
		getBean('roleHierarchy', RoleHierarchy).getReachableGrantedAuthorities(granted) ?: ( Collections.emptyList() as Collection<? extends GrantedAuthority> )
	}

	@SuppressWarnings('unchecked')
	private static <T> T getBean(String name, Class<T> c = null) {
		(T)application.mainContext.getBean(name, c)
	}

	/**
	 * Called each time doWithApplicationContext() is invoked, so it's important to reset
	 * to default values when running integration and functional tests together.
	 */
	private static void initializeContext() {
		voterNames.clear()
		voterNames << 'authenticatedVoter' << 'roleVoter' << 'webExpressionVoter' << 'closureVoter'

		logoutHandlerNames.clear()
		logoutHandlerNames << 'rememberMeServices' << 'securityContextLogoutHandler'

		providerNames.clear()
		providerNames << 'daoAuthenticationProvider' << 'anonymousAuthenticationProvider' << 'rememberMeAuthenticationProvider'

		orderedFilters.clear()

		configuredOrderedFilters.clear()

		afterInvocationManagerProviderNames.clear()
	}

	private static Authentication getAuthentication() {
		SecurityContextHolder.context?.authentication
	}

	static SortedMap<Integer, String> findFilterChainNames(filterChainFilterNames, boolean useSecureChannel,
	                                                       boolean useIpRestrictions, boolean useX509, boolean useDigestAuth,
	                                                       boolean useBasicAuth, boolean useSwitchUserFilter) {

		SortedMap<Integer, String> orderedNames = new TreeMap()

		// if the user listed the names, use those
		if (filterChainFilterNames) {
			// cheat and put them in the map in order - the key values don't matter in this case since
			// the user has chosen the order and the map will be used to insert single filters, which
			// wouldn't happen if they've defined the order already
			filterChainFilterNames.eachWithIndex { String name, int index -> orderedNames[index] = name }
		}
		else {

			orderedNames[SecurityFilterPosition.FIRST.order + 10] = 'securityRequestHolderFilter'

			if (useSecureChannel) {
				orderedNames[SecurityFilterPosition.CHANNEL_FILTER.order] = 'channelProcessingFilter'
			}

			// CONCURRENT_SESSION_FILTER

			orderedNames[SecurityFilterPosition.SECURITY_CONTEXT_FILTER.order] = 'securityContextPersistenceFilter'

			orderedNames[SecurityFilterPosition.LOGOUT_FILTER.order] = 'logoutFilter'

			if (useIpRestrictions) {
				orderedNames[SecurityFilterPosition.LOGOUT_FILTER.order + 1] = 'ipAddressFilter'
			}

			if (useX509) {
				orderedNames[SecurityFilterPosition.X509_FILTER.order] = 'x509ProcessingFilter'
			}

			// PRE_AUTH_FILTER

			// CAS_FILTER

			orderedNames[SecurityFilterPosition.FORM_LOGIN_FILTER.order] = 'authenticationProcessingFilter'

			// OPENID_FILTER

			// facebook

			if (useDigestAuth) {
				orderedNames[SecurityFilterPosition.DIGEST_AUTH_FILTER.order] = 'digestAuthenticationFilter'
				orderedNames[SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order + 1] = 'digestExceptionTranslationFilter'
			}

			if (useBasicAuth) {
				orderedNames[SecurityFilterPosition.BASIC_AUTH_FILTER.order] = 'basicAuthenticationFilter'
				orderedNames[SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order + 1] = 'basicExceptionTranslationFilter'
			}

			// REQUEST_CACHE_FILTER

			orderedNames[SecurityFilterPosition.SERVLET_API_SUPPORT_FILTER.order] = 'securityContextHolderAwareRequestFilter'

			orderedNames[SecurityFilterPosition.REMEMBER_ME_FILTER.order] = 'rememberMeAuthenticationFilter'

			orderedNames[SecurityFilterPosition.ANONYMOUS_FILTER.order] = 'anonymousAuthenticationFilter'

			// SESSION_MANAGEMENT_FILTER

			orderedNames[SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order] = 'exceptionTranslationFilter'

			orderedNames[SecurityFilterPosition.FILTER_SECURITY_INTERCEPTOR.order] = 'filterInvocationInterceptor'

			if (useSwitchUserFilter) {
				orderedNames[SecurityFilterPosition.SWITCH_USER_FILTER.order] = 'switchUserProcessingFilter'
			}

			orderedNames[SecurityFilterPosition.EXCEPTION_TRANSLATION_FILTER.order-10] = 'formContentFilter'

			// add in filters contributed by secondary plugins
			orderedNames << SpringSecurityUtils.orderedFilters
		}

		orderedNames
	}

	static void buildFilterChains(SortedMap<Integer, String> filterNames, List<Map<String, ?>> chainMap,
	                              List<GrailsSecurityFilterChain> filterChains, ApplicationContext applicationContext) {

		filterChains.clear()

		def allConfiguredFilters = [:]
		filterNames.each { Integer order, String name ->
			def filter = applicationContext.getBean(name)
			if (filter instanceof FilterRegistrationBean) {
				filter = ((FilterRegistrationBean) filter).filter
			}
			allConfiguredFilters[name] = (Filter) filter
			SpringSecurityUtils.configuredOrderedFilters[order] = (Filter) filter
		}
		log.trace 'Ordered filters: {}', SpringSecurityUtils.configuredOrderedFilters

		if (chainMap) {
			for (Map<String, ?> entry in chainMap) {
				String value = (entry.filters ?: '').toString().trim()
				List<Filter> filters
				if (value.toLowerCase() == FILTERS_NONE) {
					filters = Collections.emptyList()
				}
				else if (value.contains('JOINED_FILTERS')) {
					// special case to use either the filters defined by conf.filterChain.filterNames or
					// the filters defined by config settings; can also remove one or more with a prefix of -
					def copy = [:] << allConfiguredFilters
					for (item in value.split(',')) {
						item = item.toString().trim()
						if (item == 'JOINED_FILTERS') continue
						if (item.startsWith('-')) {
							item = item.substring(1)
							copy.remove item
						}
						else {
							throw new IllegalArgumentException("Cannot add a filter to JOINED_FILTERS, can only remove: $item")
						}
					}
					filters = copy.values() as List<Filter>
				}
				else {
					// explicit filter names
					filters = value.toString().split(',').collect { String name -> applicationContext.getBean(name, Filter) }
				}
				filterChains << new GrailsSecurityFilterChain(entry.pattern as String, filters)
			}
		}
		else {
			filterChains << new GrailsSecurityFilterChain('/**', allConfiguredFilters.values() as List<Filter>)
		}
	}
}
