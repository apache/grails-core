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
package grails.plugin.springsecurity.acl

import grails.plugin.springsecurity.BeanTypeResolver
import grails.util.GrailsClassUtils as GCU
import grails.plugin.springsecurity.acl.cache.SpringAclCacheFactoryBean
import org.springframework.cache.jcache.JCacheCacheManager
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.security.access.annotation.SecuredAnnotationSecurityMetadataSource as SpringSecuredAnnotationSecurityMetadataSource
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.ExpressionBasedAnnotationAttributeFactory
import org.springframework.security.access.expression.method.ExpressionBasedPostInvocationAdvice
import org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice
import org.springframework.security.access.intercept.AfterInvocationProviderManager
import org.springframework.security.access.intercept.RunAsImplAuthenticationProvider
import org.springframework.security.access.intercept.RunAsManagerImpl
import org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor
import org.springframework.security.access.prepost.PostInvocationAdviceProvider
import org.springframework.security.access.prepost.PreInvocationAuthorizationAdviceVoter
import org.springframework.security.access.prepost.PrePostAnnotationSecurityMetadataSource
import org.springframework.security.access.vote.AffirmativeBased
import org.springframework.security.acls.AclEntryVoter
import org.springframework.security.acls.AclPermissionCacheOptimizer
import org.springframework.security.acls.AclPermissionEvaluator
import org.springframework.security.acls.afterinvocation.AclEntryAfterInvocationCollectionFilteringProvider
import org.springframework.security.acls.afterinvocation.AclEntryAfterInvocationProvider
import org.springframework.security.acls.domain.AclAuthorizationStrategyImpl
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.domain.DefaultPermissionFactory
import org.springframework.security.acls.domain.DefaultPermissionGrantingStrategy
import org.springframework.security.acls.domain.SpringCacheBasedAclCache
import org.springframework.security.acls.domain.SidRetrievalStrategyImpl
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.AuthorityUtils

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.acl.access.GroovyAwareAclVoter
import grails.plugin.springsecurity.acl.access.method.ProxyAwareDelegatingMethodSecurityMetadataSource
import grails.plugin.springsecurity.acl.access.method.SecuredAnnotationSecurityMetadataSource as GrailsSecuredAnnotationSecurityMetadataSource
import grails.plugin.springsecurity.acl.access.method.ServiceStaticMethodSecurityMetadataSource
import grails.plugin.springsecurity.acl.annotation.AclVoter
import grails.plugin.springsecurity.acl.annotation.AclVoters
import grails.plugin.springsecurity.acl.domain.NullAclAuditLogger
import grails.plugin.springsecurity.acl.jdbc.GormAclLookupStrategy
import grails.plugin.springsecurity.acl.model.GormObjectIdentityRetrievalStrategy
import grails.plugins.Plugin
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

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class SpringSecurityAclGrailsPlugin extends Plugin {

	public static final String ENCODING_ID_BCRYPT = 'bcrypt'
	public static final String ENCODING_ID_LDAP = 'ldap'
	public static final String ENCODING_ID_MD4 = 'MD4'
	public static final String ENCODING_ID_MD5 = 'MD5'
	public static final String ENCODING_ID_NOOP = 'noop'
	public static final String ENCODING_ID_PBKDF2 = 'pbkdf2'
	public static final String ENCODING_ID_SCRYPT = 'scrypt'
	public static final String ENCODING_ID_SHA1 = 'SHA-1'
	public static final String ENCODING_IDSHA256 = 'SHA-256'


	String grailsVersion = '7.0.0 > *'
	String author = 'Burt Beckwith'
	String authorEmail = ''
	String title = 'Spring Security ACL plugin'
	String description = 'ACL support for the Spring Security plugin'
	String documentation = 'https://apache.github.io/grails-spring-security'
	String license = 'APACHE'
	def organization = [name: 'Grails', url: 'https://www.grails.org']
	def issueManagement = [url: 'https://github.com/apache/grails-spring-security/issues']
	def scm = [url: 'https://github.com/apache/grails-spring-security']
	def loadAfter = ['springSecurityCore']
	def profiles = ['web']

	private beanTypeResolver

	Closure doWithSpring() {{ ->

		def conf = SpringSecurityUtils.securityConfig
		if (!conf || !conf.active) {
			return
		}

		SpringSecurityUtils.loadSecondaryConfig 'DefaultAclSecurityConfig'
		// have to get again after overlaying DefaultAclSecurityConfig
		conf = SpringSecurityUtils.securityConfig

		if (!conf.acl.active) {
			return
		}

		Class beanTypeResolverClass = conf.beanTypeResolverClass ?: BeanTypeResolver
		beanTypeResolver = beanTypeResolverClass.newInstance(conf, grailsApplication)

		boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true

		if (printStatusMessages) {
			println '\nConfiguring Spring Security ACL ...'
		}

		if (conf.useRunAs) {
			SpringSecurityUtils.registerProvider 'runAsAuthenticationProvider'
		}

		Map voterConfig = buildVoterConfig(conf)
		debug "voterConfig: $voterConfig"

		// core beans
		configureCoreBeans.delegate = delegate
		configureCoreBeans conf

		// expression support
		configureExpressionBeans.delegate = delegate
		configureExpressionBeans conf

		// secured beans
		configureSecuredBeans.delegate = delegate
		configureSecuredBeans conf

		// MetadataSource
		configureSecurityMetadataSource.delegate = delegate
		configureSecurityMetadataSource conf, voterConfig

		if (printStatusMessages) {
			println '... finished configuring Spring Security ACL\n'
		}
	}}

	void doWithApplicationContext() {

		def conf = SpringSecurityUtils.securityConfig
		if (!conf || !conf.active || !conf.acl.active) {
			return
		}

		applicationContext.aclSecurityMetadataSource.methodSecurityMetadataSources = [
			applicationContext.prePostAnnotationSecurityMetadataSource,
			applicationContext.springSecuredAnnotationSecurityMetadataSource,
			applicationContext.grailsSecuredAnnotationSecurityMetadataSource,
			applicationContext.serviceStaticMethodSecurityMetadataSource
		]
	}

	private configureCoreBeans = { conf ->

		sidRetrievalStrategy(SidRetrievalStrategyImpl, ref('roleHierarchy'))

		objectIdentityRetrievalStrategy(GormObjectIdentityRetrievalStrategy)

		// acl cache
		aclCacheManager(JCacheCacheManager)
		aclCache(SpringAclCacheFactoryBean) {
			cacheManager = ref('aclCacheManager')
			cacheName = 'aclCache'
			permissionGrantingStrategy = ref('aclPermissionGrantingStrategy')
			aclAuthorizationStrategy = ref('aclAuthorizationStrategy')
		}

		aclPermissionGrantingStrategy(DefaultPermissionGrantingStrategy, ref('aclAuditLogger'))

		aclAuthorizationStrategy(AclAuthorizationStrategyImpl,
				AuthorityUtils.createAuthorityList(
						conf.acl.authority.changeOwnership,
						conf.acl.authority.modifyAuditingDetails,
						conf.acl.authority.changeAclDetails) as GrantedAuthority[]) {
			sidRetrievalStrategy = ref('sidRetrievalStrategy')
		}

		aclAuditLogger(NullAclAuditLogger)

		def permissionClass = conf.acl.permissionClass
		if (permissionClass instanceof String) {
			permissionClass = classLoader.loadClass(permissionClass)
		}
		aclPermissionFactory(DefaultPermissionFactory, permissionClass ?: BasePermission)

		aclLookupStrategy(GormAclLookupStrategy) {
			aclAuthorizationStrategy = ref('aclAuthorizationStrategy')
			aclCache = ref('aclCache')
			permissionFactory = ref('aclPermissionFactory')
			permissionGrantingStrategy = ref('aclPermissionGrantingStrategy')
		}
		String algorithm = conf.password.algorithm
		passwordEncoder(classFor('passwordEncoder', DelegatingPasswordEncoder), algorithm, idToPasswordEncoder(conf))
	}

	private configureExpressionBeans = { conf ->

		permissionEvaluator(AclPermissionEvaluator, ref('aclService')) {
			objectIdentityRetrievalStrategy = ref('objectIdentityRetrievalStrategy')
			objectIdentityGenerator = ref('objectIdentityRetrievalStrategy')
			sidRetrievalStrategy = ref('sidRetrievalStrategy')
			permissionFactory = ref('aclPermissionFactory')
		}

		expressionParser(SpelExpressionParser)

		aclPermissionCacheOptimizer(AclPermissionCacheOptimizer, ref('aclService')) {
			objectIdentityRetrievalStrategy = ref('objectIdentityRetrievalStrategy')
			sidRetrievalStrategy = ref('sidRetrievalStrategy')
		}

		expressionHandler(DefaultMethodSecurityExpressionHandler) {
			permissionCacheOptimizer = ref('aclPermissionCacheOptimizer')
			expressionParser = ref('expressionParser')
			roleHierarchy = ref('roleHierarchy')
			permissionEvaluator = ref('permissionEvaluator')
		}
	}

	private configureSecuredBeans = { conf ->

		debug 'configuring secured services'

		if (conf.useRunAs) {
			runAsManager(RunAsManagerImpl) {
				key = conf.runAs.key
			}

			runAsAuthenticationProvider(RunAsImplAuthenticationProvider) {
				key = conf.runAs.key
			}
		}

		securedBeansInterceptor(AclAutoProxyCreator) {
			grailsApplication = grailsApplication
			interceptorNames = ['methodSecurityInterceptor']
			proxyTargetClass = true
		}
	}

	private configureSecurityMetadataSource = { conf, voterConfig ->

		// create decision voters

		groovyAwareAclVoter(GroovyAwareAclVoter)

		def aclAccessDecisionManagerDecisionVoters = [
			ref('roleVoter'),
			ref('authenticatedVoter'),
			ref('preInvocationVoter'),
			ref('groovyAwareAclVoter')]

		voterConfig.each { beanName, voterData ->
			"$beanName"(AclEntryVoter, ref('aclService'), voterData.configAttribute, voterData.permissions) {
				processDomainObjectClass = voterData.domainObjectClass
				internalMethod = voterData.internalMethod
				objectIdentityRetrievalStrategy = ref('objectIdentityRetrievalStrategy')
				sidRetrievalStrategy = ref('sidRetrievalStrategy')
			}
			aclAccessDecisionManagerDecisionVoters << ref(beanName)
			debug "created AclEntryVoter $beanName for domain class $voterData.domainObjectClass.name with configAttribute $voterData.configAttribute and permissions $voterData.permissions"
		}

		aclAccessDecisionManager(AffirmativeBased, aclAccessDecisionManagerDecisionVoters) {
			allowIfAllAbstainDecisions = false
		}

		// processes AFTER_ACL_COLLECTION_READ configuration settings;
		// filters out records you don't have access to
		afterAclCollectionRead(AclEntryAfterInvocationCollectionFilteringProvider, ref('aclService'),
				[BasePermission.READ]) {
			objectIdentityRetrievalStrategy = ref('objectIdentityRetrievalStrategy')
			sidRetrievalStrategy = ref('sidRetrievalStrategy')
		}

		// processes AFTER_ACL_READ configuration settings;
		// determines access to single instances returned
		afterAclRead(AclEntryAfterInvocationProvider, ref('aclService'),
				[BasePermission.READ]) {
			objectIdentityRetrievalStrategy = ref('objectIdentityRetrievalStrategy')
			sidRetrievalStrategy = ref('sidRetrievalStrategy')
		}

		preInvocationAdvice(ExpressionBasedPreInvocationAdvice) {
			expressionHandler = ref('expressionHandler')
		}

		preInvocationVoter(PreInvocationAuthorizationAdviceVoter, ref('preInvocationAdvice'))

		postInvocationAdvice(ExpressionBasedPostInvocationAdvice, ref('expressionHandler'))

		annotationInvocationFactory(ExpressionBasedAnnotationAttributeFactory, ref('expressionHandler'))

		Map<String, List<String>> classConfigNameMap = [:]
		Map<String, Map<String, List<String>>> methodConfigNameMap = [:]
		findConfigNames classConfigNameMap, methodConfigNameMap

		serviceStaticMethodSecurityMetadataSource(ServiceStaticMethodSecurityMetadataSource) {
			classConfigNames = classConfigNameMap
			methodConfigNames = methodConfigNameMap
		}

		prePostAnnotationSecurityMetadataSource(PrePostAnnotationSecurityMetadataSource, ref('annotationInvocationFactory'))
		grailsSecuredAnnotationSecurityMetadataSource(GrailsSecuredAnnotationSecurityMetadataSource) {
			serviceClassNames = grailsApplication.serviceClasses*.clazz.name
		}
		springSecuredAnnotationSecurityMetadataSource(SpringSecuredAnnotationSecurityMetadataSource)

		def metadataSources = [
			ref('prePostAnnotationSecurityMetadataSource'),
			ref('springSecuredAnnotationSecurityMetadataSource'),
			ref('serviceStaticMethodSecurityMetadataSource')]
		aclSecurityMetadataSource(ProxyAwareDelegatingMethodSecurityMetadataSource) {
			methodSecurityMetadataSources = metadataSources
		}

		aclPostInvocationProvider(PostInvocationAdviceProvider, ref('postInvocationAdvice'))
		aclAfterInvocationManager(AfterInvocationProviderManager) {
			providers = [
				ref('aclPostInvocationProvider'),
				ref('afterAclRead'),
				ref('afterAclCollectionRead')]
		}

		methodSecurityInterceptor(MethodSecurityInterceptor) {
			accessDecisionManager = ref('aclAccessDecisionManager')
			authenticationManager = ref('authenticationManager')
			afterInvocationManager = ref('aclAfterInvocationManager')
			securityMetadataSource = ref('aclSecurityMetadataSource')
			runAsManager = ref('runAsManager')
			validateConfigAttributes = false
		}

	}

	private void findConfigNames(Map<String, List<String>> classConfigNames,
			Map<String, Map<String, List<String>>> methodConfigNames) {

		/*
		Look for configurations like this in services:

		static springSecurityACL = [
			getReportName: ['ROLE_USER', 'ROLE_ADMIN'],
			getAllReports: ['ROLE_USER', 'AFTER_ACL_COLLECTION_READ'],
			getReport: ['ROLE_USER', 'AFTER_ACL_READ'],
			updateReport: ['ACL_REPORT_WRITE'],
			deleteReport: ['ACL_REPORT_DELETE']
		]
		*/

		for (serviceClass in grailsApplication.serviceClasses) {
			//methodConfigNames.put(serviceClass.clazz.name, [:])
			methodConfigNames[serviceClass.clazz.name] = [:]
		}

		for (serviceClass in grailsApplication.serviceClasses) {
			if (!GCU.isStaticProperty(serviceClass.clazz, 'springSecurityACL')) {
				continue
			}

			String className = serviceClass.clazz.name
			def springSecurityACL = serviceClass.clazz.springSecurityACL
			springSecurityACL.each { methodName, configNames ->
				if ('*'.equals(methodName)) {
					//classConfigNames.put(className,configNames)
					classConfigNames[className] = configNames
				}
				else {
					methodConfigNames[className][methodName] = configNames
				}
			}
		}
	}

	private Map buildVoterConfig(conf) {

		/*
		Look for annotations like this in domain classes:

		@AclVoters([
			@AclVoter(name='aclReportWriteVoter',
			          configAttribute='ACL_REPORT_WRITE',
			          permissions=['ADMINISTRATION', 'WRITE']),
			@AclVoter(name='aclReportDeleteVoter',
			          configAttribute='ACL_REPORT_DELETE',
			          permissions=['ADMINISTRATION', 'DELETE'])
		])

		In addition you can declare a config attribute 'acl.voters':

		grails.plugin.springsecurity.acl.voters = [
			aclReportWriteVoter: [
				domainObjectClass: Report,
				configAttribute: 'ACL_REPORT_WRITE',
				permissions: [BasePermission.ADMINISTRATION,
				              BasePermission.WRITE]
			],

			aclReportDeleteVoter: [
				domainObjectClass: Report,
				configAttribute: 'ACL_REPORT_DELETE',
				permissions: [BasePermission.ADMINISTRATION,
				              BasePermission.DELETE]
			]
		]
		*/

		Map config = conf.acl.voters.clone()

		for (dc in grailsApplication.domainClasses) {
			for (annotation in findAclVoterAnnotations(dc.clazz)) {
				def permissions = []
				for (String permissionName in annotation.permissions()) {
					permissions << BasePermission."$permissionName"
				}
				config[annotation.name()] = [
					configAttribute: annotation.configAttribute(),
					domainObjectClass: dc.clazz,
					permissions: permissions]
			}
		}

		config
	}

	/**
	 * Look for @AclVoter annotations in the specified domain class.
	 */
	private List<AclVoter> findAclVoterAnnotations(Class<?> domainClass) {
		List<AclVoter> annotations = []

		AclVoter aclVoterAnnotation = domainClass.getAnnotation(AclVoter)
		if (aclVoterAnnotation) {
			annotations << aclVoterAnnotation
		}

		AclVoters aclVotersAnnotation = domainClass.getAnnotation(AclVoters)
		if (aclVotersAnnotation) {
			annotations.addAll aclVotersAnnotation.value() as List
		}

		annotations
	}

	private void debug(message) {
//		log.debug message
//		println message
	}

	Map<String, PasswordEncoder> idToPasswordEncoder(ConfigObject conf) {

		MessageDigestPasswordEncoder messsageDigestPasswordEncoderMD5 = new MessageDigestPasswordEncoder(ENCODING_ID_MD5)
		messsageDigestPasswordEncoderMD5.encodeHashAsBase64 = conf.password.encodeHashAsBase64 // false
		messsageDigestPasswordEncoderMD5.iterations = conf.password.hash.iterations // 10000

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
		 (ENCODING_ID_MD5): messsageDigestPasswordEncoderMD5,
		 (ENCODING_ID_NOOP): NoOpPasswordEncoder.getInstance(),
		 (ENCODING_ID_PBKDF2): Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
		 (ENCODING_ID_SCRYPT): SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8(),
		 (ENCODING_ID_SHA1): messsageDigestPasswordEncoderSHA1,
		 (ENCODING_IDSHA256): messsageDigestPasswordEncoderSHA256,
		 "sha256": new StandardPasswordEncoder()]
	}

	private Class classFor(String beanName, Class defaultType) {
		beanTypeResolver.resolveType beanName, defaultType
	}


}
