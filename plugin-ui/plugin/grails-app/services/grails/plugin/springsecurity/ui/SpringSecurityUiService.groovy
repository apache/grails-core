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
package grails.plugin.springsecurity.ui

import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.ui.strategy.AclStrategy
import grails.plugin.springsecurity.ui.strategy.ErrorsStrategy
import grails.plugin.springsecurity.ui.strategy.MailStrategy
import grails.plugin.springsecurity.ui.strategy.PersistentLoginStrategy
import grails.plugin.springsecurity.ui.strategy.PropertiesStrategy
import grails.plugin.springsecurity.ui.strategy.QueryStrategy
import grails.plugin.springsecurity.ui.strategy.RegistrationCodeStrategy
import grails.plugin.springsecurity.ui.strategy.RequestmapStrategy
import grails.plugin.springsecurity.ui.strategy.RoleStrategy
import grails.plugin.springsecurity.ui.strategy.UserStrategy
import grails.util.GrailsNameUtils
import groovy.util.logging.Slf4j
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.security.core.userdetails.UserCache
import org.springframework.transaction.TransactionStatus
import org.springframework.util.ClassUtils

import java.text.SimpleDateFormat

/**
 * Helper methods for UI management.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Slf4j
class SpringSecurityUiService implements AclStrategy, ErrorsStrategy, PersistentLoginStrategy,
                                         PropertiesStrategy, QueryStrategy, RegistrationCodeStrategy,
                                         RequestmapStrategy, RoleStrategy, UserStrategy {

	public static final String DATE_FORMAT = 'd MMM yyyy HH:mm:ss'

	protected static final String ACL_CLASS_NAME           = 'grails.plugin.springsecurity.acl.AclClass'
	protected static final String ACL_ENTRY_NAME           = 'grails.plugin.springsecurity.acl.AclEntry'
	protected static final String ACL_OBJECT_IDENTITY_NAME = 'grails.plugin.springsecurity.acl.AclObjectIdentity'
	protected static final String ACL_SID_NAME             = 'grails.plugin.springsecurity.acl.AclSid'

	protected Map<Class<?>, Map<String, String>> classMappings

	/** Dependency injection for the 'uiErrorsStrategy' bean. */
	ErrorsStrategy uiErrorsStrategy

	/** Dependency injection for the 'uiMailStrategy' bean. */
	MailStrategy uiMailStrategy

	/** Dependency injection for the 'uiPropertiesStrategy' bean. */
	PropertiesStrategy uiPropertiesStrategy

	GrailsApplication grailsApplication
	MessageSource messageSource
	def springSecurityService
	UserCache userCache

	@Transactional
	def saveAclClass(Map properties) {
		save properties, AclClass, 'saveAclClass', transactionStatus
	}

	@Transactional
	void updateAclClass(Map properties, aclClass) {
		save properties, aclClass, 'updateAclClass', transactionStatus
	}

	@Transactional
	void deleteAclClass(aclClass) {
		// will fail if there are FK references
		delete aclClass, 'deleteAclClass', transactionStatus
	}

	@Transactional
	def saveAclEntry(Map properties) {
		save(properties, AclEntry, 'saveAclEntry', transactionStatus) { aclEntry ->
			removeIfTransient aclEntry, 'aclObjectIdentity', 'sid'
		}
	}

	@Transactional
	void updateAclEntry(Map properties, aclEntry) {
		save(properties, aclEntry, 'updateAclEntry', transactionStatus) {
			removeIfTransient aclEntry, 'aclObjectIdentity', 'sid'
		}
	}

	@Transactional
	void deleteAclEntry(aclEntry) {
		delete aclEntry, 'deleteAclEntry', transactionStatus
	}

	@Transactional
	def saveAclObjectIdentity(Map properties) {
		save(properties, AclObjectIdentity, 'saveAclObjectIdentity', transactionStatus) { aclObjectIdentity ->
			removeIfTransient aclObjectIdentity, 'aclClass', 'owner', 'parent'
		}
	}

	@Transactional
	void updateAclObjectIdentity(Map properties, aclObjectIdentity) {
		save properties, aclObjectIdentity, 'updateAclObjectIdentity', transactionStatus
	}

	@Transactional
	void deleteAclObjectIdentity(aclObjectIdentity) {
		// will fail if there are FK references
		delete aclObjectIdentity, 'deleteAclObjectIdentity', transactionStatus
	}

	@Transactional
	def saveAclSid(Map properties) {
		save properties, AclSid, 'saveAclSid', transactionStatus
	}

	@Transactional
	void updateAclSid(Map properties, aclSid) {
		save properties, aclSid, 'updateAclSid', transactionStatus
	}

	@Transactional
	void deleteAclSid(aclSid) {
		// will fail if there are FK references
		delete aclSid, 'deleteAclSid', transactionStatus
	}

	@Transactional
	void updatePersistentLogin(Map properties, persistentLogin) {
		properties = [:] + properties
		if (properties.lastUsed && properties.lastUsed instanceof String) {
			Calendar c = Calendar.instance
			c.time = new SimpleDateFormat(DATE_FORMAT).parse(properties.lastUsed)
			properties.lastUsed = c.time
		}

		save properties, persistentLogin, 'updatePersistentLogin', transactionStatus
	}

	@Transactional
	void deletePersistentLogin(persistentLogin) {
		delete persistentLogin, 'deletePersistentLogin', transactionStatus
	}

	@Transactional
	void updateRegistrationCode(Map properties, RegistrationCode registrationCode) {
		save properties, registrationCode, 'updateRegistrationCode', transactionStatus
	}

	@Transactional
	void deleteRegistrationCode(RegistrationCode registrationCode) {
		delete registrationCode, 'deleteRegistrationCode', transactionStatus
	}

	@Transactional
	RegistrationCode register(user, String password) {

		save password: encodePassword(password), user, 'register', transactionStatus
		if (user.hasErrors()) {
			return
		}

		String username = uiPropertiesStrategy.getProperty(user, 'username')
		save username: username, RegistrationCode, 'register', transactionStatus
	}

	def createUser(RegisterCommand command) {
		uiPropertiesStrategy.setProperties(
			email: command.email, username: command.username, accountLocked: true, enabled: true, User, null)
	}

	/*Abstracted so this method can be used in other applications and if we determine to use another matching stragery later we can update as needed
	 */
	boolean doesAnswerMatch(String val1, String val2) {
		springSecurityService.passwordEncoder.matches(val2, val1)
	}

	@Transactional
	def validateForgotPasswordExtraSecurity(params,user,forgotPasswordExtraValidationDomainClassName,forgotPasswordExtraValidation,String validationUserLookUpProperty) {
		Boolean isvalid = true
		String instance
        def domain =  grailsApplication.getClassForName(forgotPasswordExtraValidationDomainClassName)
        if(!(domain instanceof Object)) {
            return [false,[errorMsg:messageSource.getMessage('spring.security.ui.securityQuestions.extraValidationString.config',null,'Domain Not Found',LocaleContextHolder.getLocale())]]
        }
		List<HashMap> rtnValidations = []
		forgotPasswordExtraValidation?.eachWithIndex{it,idx->
			HashMap inst = forgotPasswordExtraValidation.getAt(idx)
				rtnValidations[idx] = [:]
					instance = this.getProperty(domain.findWhere((validationUserLookUpProperty): user),inst.prop)
					rtnValidations[idx].valueTxt = params.get(inst.prop)
					if (!instance || instance.size() == 0 || ! this.doesAnswerMatch(instance,params.get(inst.prop)?.toLowerCase())  ) {
						rtnValidations[idx].errorMsg = messageSource.getMessage('spring.security.ui.securityQuestions.extraValidationString.notequal',null,'Not Equal',LocaleContextHolder.getLocale())
						isvalid = false
					}
				idx++
			}

		[isvalid,rtnValidations]
	}

	@Transactional
	def verifyRegistration(String token) {
		def conf = SpringSecurityUtils.securityConfig
		RegistrationCode registrationCode = token ? RegistrationCode.findByToken(token) : null
		def registerPostRegisterUrl = conf.ui.register.postRegisterUrl ?: ''
		def successHandlerDefaultTargetUrl = conf.successHandler.defaultTargetUrl ?: '/'

		if (!registrationCode) {
			return [flashType:'error',flashmsg:'spring.security.ui.register.badCode',redirectmsg:successHandlerDefaultTargetUrl]
		}
		def user = this.finishRegistration(registrationCode)
		if (!user || user.hasErrors()) {
			return [flashType:'error',flashmsg:'spring.security.ui.register.badCode',redirectmsg:successHandlerDefaultTargetUrl]
		}
		[flashType:'message', flashmsg:'spring.security.ui.register.complete',redirectmsg:registerPostRegisterUrl ?: successHandlerDefaultTargetUrl]
	}

	@Transactional
	def finishRegistration(RegistrationCode registrationCode) {

		def user = findUserByUsername(registrationCode.username)
		if (!user) {
			return
		}

		save accountLocked: false, user, 'finishRegistration', transactionStatus
		if (!user.hasErrors()) {
			addRoles user, registerDefaultRoleNames
			delete registrationCode, 'finishRegistration', transactionStatus
			springSecurityService.reauthenticate registrationCode.username
		}

		user
	}

	@Transactional
	RegistrationCode sendForgotPasswordMail(String username) {
		sendForgotPasswordMail(username, null, null,false)
	}

	@Transactional
	RegistrationCode sendForgotPasswordMail(String username,  String emailAddress,  Boolean sendMail) {
		sendForgotPasswordMail(username, emailAddress, null,sendMail)
	}

	@Transactional
	RegistrationCode sendForgotPasswordMail(String username, String emailAddress, Closure emailBodyGenerator) {
		sendForgotPasswordMail(username, emailAddress, emailBodyGenerator,true)
	}

	@Transactional
	RegistrationCode sendForgotPasswordMail(String username, String emailAddress, Closure emailBodyGenerator, Boolean sendMail) {

		RegistrationCode registrationCode = save(username: username, RegistrationCode, 'sendForgotPasswordMail', transactionStatus)
		if (!registrationCode.hasErrors() && sendMail) {
			uiMailStrategy.sendForgotPasswordMail(
					to: emailAddress,
					from: forgotPasswordEmailFrom,
					subject: forgotPasswordEmailSubject,
					html: emailBodyGenerator(registrationCode.token))
		}

		registrationCode
	}

	@Transactional
	def resetPassword(ResetPasswordCommand command, RegistrationCode registrationCode) {


		def user = findUserByUsername(registrationCode.username)
		save password: encodePassword(command.password), user, 'resetPassword', transactionStatus

		if (!user.hasErrors()) {
			delete registrationCode, 'resetPassword', transactionStatus
			springSecurityService.reauthenticate registrationCode.username
		}

		user
	}

	@Transactional
	def saveRole(Map properties) {
		save properties, Role, 'saveRole', transactionStatus
	}

	@Transactional
	void updateRole(Map properties, role) {

		updateKeys properties, role

		try {
			springSecurityService.updateRole role, properties
		}
		catch (e) {
			uiErrorsStrategy.handleException e, role, properties, this, 'updateRole', transactionStatus
			return
		}

		if (role.hasErrors()) {
			uiErrorsStrategy.handleValidationErrors role, this, 'updateRole', transactionStatus
		}
	}

	@Transactional
	void deleteRole(role) {
		try {
			UserRole.removeAll role
			springSecurityService.deleteRole role
		}
		catch (e) {
			uiErrorsStrategy.handleException e, role, null, this, 'deleteRole', transactionStatus
		}
	}

	@Transactional
	def saveRequestmap(Map properties) {
		save properties, Requestmap, 'saveRequestmap', transactionStatus
	}

	@Transactional
	void updateRequestmap(Map properties, requestmap) {

		save properties, requestmap, 'updateRequestmap', transactionStatus

		if (!requestmap.hasErrors()) {
			springSecurityService.clearCachedRequestmaps()
		}
	}

	@Transactional
	void deleteRequestmap(requestmap) {
		delete requestmap, 'deleteRequestmap', transactionStatus
		springSecurityService.clearCachedRequestmaps()
	}

	@Transactional
	def saveUser(Map properties, List<String> roleNames, String password) {

		def user = uiPropertiesStrategy.setProperties(properties, User, transactionStatus)

		if (password) {
			updatePassword user, password, transactionStatus
		}

		save [:], user, 'saveUser', transactionStatus
		if (!user.hasErrors()) {
			addRoles user, roleNames
		}

		user
	}

	@Transactional
	void updateUser(Map properties, user, List<String> roleNames) {
		String oldPassword = uiPropertiesStrategy.getProperty(user, 'password')

		uiPropertiesStrategy.setProperties properties, user, transactionStatus
		if (properties.password && properties.password != oldPassword) {
			updatePassword user, properties.password, transactionStatus
		}

		save [:], user, 'updateUser', transactionStatus
		if (user.hasErrors()) {
			return
		}

		updateUserRoles user, roleNames, transactionStatus
		removeUserFromCache user
	}

	@Transactional
	void deleteUser(user) {
		UserRole.removeAll user
		delete user, 'deleteUser', transactionStatus
		removeUserFromCache user
	}

	protected void addRoles(user, List<String> roleNames) {
		String authorityNameField = uiPropertiesStrategy.paramNameToPropertyName('authority', 'role')

		try {
			for (String roleName in roleNames) {
				UserRole.create user, Role.findWhere((authorityNameField): roleName)
			}
		}
		catch (e) {
			uiErrorsStrategy.handleException e, user, null, this, 'addRoles', transactionStatus
		}
	}

	@Transactional
	protected void addUserRoles(user, Set rolesToAdd) {
		if (!user || !rolesToAdd) {
			return
		}

        List<String> roleNames = []

		rolesToAdd.each { role ->
            roleNames << role.authority
        }

        addRoles(user, roleNames)
	}

	protected void updateUserRoles(user, List<String> roleNames, TransactionStatus transactionStatus) {
		String authorityNameField = uiPropertiesStrategy.paramNameToPropertyName('authority', 'role')

		try {
			String dynamicFinder = "findAllBy".concat(authorityNameField.capitalize()).concat("InList")
			List selectedRoles = Role."${dynamicFinder}"(roleNames)
			Set originalRoles = user.authorities
			Set rolesToRemove = originalRoles - selectedRoles
			Set rolesToAdd = selectedRoles - originalRoles

			removeUserRoles(user, rolesToRemove)
			addUserRoles(user, rolesToAdd)
		}
		catch (e) {
			uiErrorsStrategy.handleException e, user, null, this, 'updateUserRoles', transactionStatus
		}
	}

	protected void removeUserRoles(user, Set rolesToRemove) {
		if (!user || !rolesToRemove) {
			return
		}

		try {
			rolesToRemove.each { role ->
			    removeUserRoleAndReturnBoolean(user, role)
			}
		}
		catch (e) {
			uiErrorsStrategy.handleException e, user, null, this, 'removeUserRoles', transactionStatus
		}
	}

	@Transactional
	boolean removeUserRoleAndReturnBoolean(def user, def role) {
		UserRole.remove user, role
	}

	@Transactional
	Number removeUserRole(def u, def r) {
		UserRole.where { user == u && role == r }.deleteAll()
	}

	protected void removeUserFromCache(user) {
		userCache.removeUserFromCache uiPropertiesStrategy.getProperty(user, 'username')
	}

	protected void delete(instance, String methodName, TransactionStatus transactionStatus) {

		assert transactionStatus

		try {
			instance.delete()
		}
		catch (e) {
			uiErrorsStrategy.handleException e, instance, null, this, methodName, transactionStatus
		}
	}

	Map<Class<?>, Map<String, String>> findClassMappings() {
		def conf = SpringSecurityUtils.securityConfig

		def mappings = [:]

		def userLookup = conf.userLookup
		mappings[User] = [
			username:        userLookup.usernamePropertyName ?: '',
			enabled:         userLookup.enabledPropertyName ?: '',
			password:        userLookup.passwordPropertyName ?: '',
			authorities:     userLookup.authoritiesPropertyName ?: '',
			accountExpired:  userLookup.accountExpiredPropertyName ?: '',
			accountLocked:   userLookup.accountLockedPropertyName ?: '',
			passwordExpired: userLookup.passwordExpiredPropertyName ?: ''].asImmutable()

		mappings[Role] = [authority: conf.authority.nameField ?: ''].asImmutable()

		def requestMap = conf.requestMap
		mappings[Requestmap] = [
			url:             requestMap.urlField ?: '',
			configAttribute: requestMap.configAttributeField ?: '',
			httpMethod:      requestMap.httpMethodField ?: ''].asImmutable()

		mappings
	}

	def getProperty(instance, String paramName) {
		if (!instance) return

		if (paramName.endsWith('.id')) {
			paramName = paramName[0..-4]
		}

		def value = instance
		String currentPath = ''
		for (String paramNamePart in paramName.split('\\.')) {
			currentPath += paramNamePart

			String classPropertyName = GrailsNameUtils.getPropertyName(
				unproxy(value.getClass()).name)

			String propertyName = uiPropertiesStrategy.paramNameToPropertyName(
				paramNamePart, classPropertyName)

			try {
				if (instance.metaClass.getMetaProperty(propertyName)?.getter) {
					value = value[propertyName]
					if (value == null) return
				}
				else {
					log.error 'Attempted to read non-existent property {} in {}', currentPath as String, instance as String
					return
				}
			}
			catch (e) {
				throw new InvalidValueException(propertyName, paramNamePart, value, e)
			}

			currentPath += '.'
		}

		value
	}

	String paramNameToPropertyName(String paramName, String controllerName) {
		// Properties in the ACL classes and RegistrationCode aren't currently
		// configurable, and the PersistentLogin class is generated but its
		// properties also aren't configurable. Since this method is only by
		// the controllers to be able to hard-code param names and lookup the
		// actual domain class property name we can short-circuit the logic here.
		// Additionally there's no need to support nested properties (e.g.
		// 'aclClass.className' for AclObjectIdentity search) since those are
		// not used in GSP for classes with configurable properties.

		if (!paramName) {
			return paramName
		}

		Class<?> clazz = domainClassesByControllerName[controllerName]
		String name
		if (clazz) {
			String key = paramName
			if (paramName.endsWith('.id')) {
				key = paramName[0..-4]
			}
			name = classMappings[clazz][key]
		}
		name ?: paramName
	}

	def setProperties(Map data, instanceOrClass, TransactionStatus transactionStatus) {
		if (instanceOrClass == null) return

		updateKeys data, instanceOrClass

		def instance
		try {
			if (instanceOrClass instanceof Class) {
				instance = instanceOrClass.newInstance(data)
			}
			else {
				instance = instanceOrClass
				instance.properties = data
			}
		}
		catch (e) {
			uiErrorsStrategy.handleException e, instance, data, this, 'setProperties', transactionStatus
		}

		instance
	}

	protected void updateKeys(Map data, instanceOrClass) {
		Class clazz
		if (instanceOrClass instanceof Class) {
			clazz = instanceOrClass
		}
		else {
			clazz = instanceOrClass.getClass()
		}

		Map<String, String> mappings = classMappings[unproxy(clazz)]
		if (!mappings) return

		def removed = [:]
		([] + data.keySet()).each { String paramName ->
			String mappedKey = mappings[paramName]
			if (mappedKey && mappedKey != paramName) {
				removed[mappedKey] = data.remove(paramName)
			}
		}
		data.putAll removed
	}

	void handleValidationErrors(bean, source, String operation, TransactionStatus transactionStatus) {
		log.warn('Problem in {} at "{}" {} {} : {} {}',
				source as String,
				operation as String,
				isAttached(bean) ? 'updating' : 'creating',
				bean.getClass().simpleName,
				bean as String,
				beanErrors(bean))
		rollbackAndDiscard bean, transactionStatus
	}

	String beanErrors(bean) {
		StringBuilder message = new StringBuilder()
		def locale = LocaleContextHolder.getLocale()
		for (fieldErrors in bean.errors) {
			for (error in fieldErrors.allErrors) {
				message << '\n\t' << messageSource.getMessage(error, locale)
			}
		}
		message.toString()
	}

	void handleException(Throwable t, bean, Map properties, source, String operation,
			TransactionStatus transactionStatus) {

		boolean attached = bean && !(bean instanceof CommandObject) && bean.attached

		def message = new StringBuilder()
		message << 'Problem in ' << source << ' at "' << operation

		if (bean) {
			message << '" with bean ' << bean.getClass().simpleName << ': ' << bean
		}

		log.error message.toString(), t

		if (transactionStatus) {
			rollbackAndDiscard bean, transactionStatus
		}
	}

	protected boolean isAttached(bean) {
		bean && !(bean instanceof CommandObject) && bean.attached
	}

	protected void rollbackAndDiscard(bean, TransactionStatus transactionStatus) {

		if (bean && !(bean instanceof CommandObject)) {
			bean.discard()
		}

		transactionStatus.setRollbackOnly()
	}

	String encodePassword(String password) {
		if (encodePassword) {
			password = springSecurityService.encodePassword(password)
		}
		password
	}

	Closure<?> buildProjection(String path, String criterionMethod, List args) {

		log.debug "Building projection for path '{}': {}({})", path, criterionMethod, args.toString()

		def invoker = { String projectionName, Closure<?> subcriteria ->
			log.trace 'Invoking projection {}', projectionName
			delegate."$projectionName"(subcriteria)
		}

		def closure = { ->
			log.trace 'Invoking criterion {}({})', criterionMethod, args as String
			delegate."$criterionMethod"(args)
		}

		for (String projectionName in (path.split('\\.').reverse())) {
			closure = invoker.clone().curry(projectionName, closure)
		}

		closure
	}

	def runCriteria(Class<?> clazz, List<Closure<?>> criterias, Map paginateParams) {
		clazz.createCriteria().list(paginateParams) {
			for (Closure<?> criteria in criterias) {
				criteria.delegate = delegate
				criteria()
			}
		}
	}

	protected void updatePassword(user, String password, TransactionStatus transactionStatus) {
		uiPropertiesStrategy.setProperties password: encodePassword(password), user, transactionStatus
	}

	protected Class<?> unproxy(Class<?> clazz) {
		Class<?> current = clazz
		while (isProxy(current)) {
			current = current.superclass
		}
		current
	}

	protected boolean isProxy(Class<?> clazz) {
		if (clazz.superclass == Object) {
			return false
		}

		if (ClassUtils.isCglibProxyClass(clazz)) {
			return true
		}

		// Javassist
		clazz.interfaces.any { it.name.contains('org.hibernate.proxy.HibernateProxy') }
	}

	protected save(Map data, instanceOrClass, String methodName, transactionStatus, Closure callback = null) {

		assert transactionStatus

		def instance
		try {
			instance = uiPropertiesStrategy.setProperties(data, instanceOrClass, transactionStatus)

			if (callback) {
				callback instance
			}

			instance.save()
			if (instance.hasErrors()) {
				uiErrorsStrategy.handleValidationErrors instance, this, methodName, transactionStatus
			}
		}
		catch (e) {
			uiErrorsStrategy.handleException e, instance, data, this, methodName, transactionStatus
		}

		instance
	}

	/**
	 * When databinding and an object reference id isn't valid, the property will be a new
	 * 'transient' instance. If there are no validation errors, Hibernate will complain when
	 * flushing (typically with committing the transaction) 'an unsaved transient instance',
	 * so we remove the instance here before calling save(). If the property is nullable the
	 * update will succeed and the caller can handle the problem, and if it's not nullable
	 * this will trigger a validation error which is easier to handle than the exception
	 * that Hibernate throws.
	 *
	 * @param bean the bean (in practice only an AclEntry or AclObjectIdentity) since the
	 *             other domain classes don't have FK references
	 * @param propertyName the property name of the child object
	 */
	protected void removeIfTransient(bean, String... propertyNames) {
		def discardedNames = []
		for (propertyName in propertyNames) {
			def property = bean[propertyName]
			if (property && !property.attached) {
				bean[propertyName] = null
				discardedNames << propertyName
			}
		}

		if ( discardedNames ) {
			log.warn 'Discarding transient properties {} from {}', discardedNames as String, bean as String
		}
	}

	protected findUserByUsername(String username) {
		User.findWhere((classMappings[User].username): username)
	}

	protected boolean encodePassword
	protected String forgotPasswordEmailFrom
	protected String forgotPasswordEmailSubject
	protected List<String> registerDefaultRoleNames

	protected Class<?> AclClass
	protected Class<?> AclEntry
	protected Class<?> AclObjectIdentity
	protected Class<?> AclSid
	protected Class<?> Requestmap
	protected Class<?> Role
	protected Class<?> User
	protected Class<?> UserRole

	protected Map<String, Class<?>> domainClassesByControllerName = [:]

	void initialize() {
		def conf = SpringSecurityUtils.securityConfig

		def encode = conf.ui.encodePassword
		encodePassword = encode instanceof Boolean ? encode : false

		forgotPasswordEmailFrom = conf.ui.forgotPassword.emailFrom ?: ''
		forgotPasswordEmailSubject = conf.ui.forgotPassword.emailSubject ?: messageSource ? messageSource.getMessage('spring.security.ui.forgotPassword.email.subject', [].toArray(), 'Password Reset', LocaleContextHolder.locale) : '' ?: ''

		registerDefaultRoleNames = conf.ui.register.defaultRoleNames ?: []

		def getDomainClassClass = { String name ->
			if (name) return grailsApplication.getDomainClass(name)?.clazz
		}

		AclClass = getDomainClassClass(ACL_CLASS_NAME)
		AclEntry = getDomainClassClass(ACL_ENTRY_NAME)
		AclObjectIdentity = getDomainClassClass(ACL_OBJECT_IDENTITY_NAME)
		AclSid = getDomainClassClass(ACL_SID_NAME)
		Requestmap = getDomainClassClass(conf.requestMap.className)
		Role = getDomainClassClass(conf.authority.className)
		User = getDomainClassClass(conf.userLookup.userDomainClassName)
		UserRole = getDomainClassClass(conf.userLookup.authorityJoinClassName)

		domainClassesByControllerName = [requestmap: Requestmap, role: Role, user: User]

		classMappings = uiPropertiesStrategy.findClassMappings().asImmutable()
	}
}
