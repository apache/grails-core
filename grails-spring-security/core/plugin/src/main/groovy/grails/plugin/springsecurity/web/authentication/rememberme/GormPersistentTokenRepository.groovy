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
package grails.plugin.springsecurity.web.authentication.rememberme

import groovy.util.logging.Slf4j
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository

import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import grails.gorm.DetachedCriteria
import grails.plugin.springsecurity.SpringSecurityUtils

/**
 * GORM-based PersistentTokenRepository implementation, based on {@link org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl}.
 *
 * @author Burt Beckwith
 */
@Slf4j
class GormPersistentTokenRepository implements PersistentTokenRepository, GrailsApplicationAware {

	/** Dependency injection for grailsApplication. */
	GrailsApplication grailsApplication

	void createNewToken(PersistentRememberMeToken token) {
		def clazz = lookupDomainClass()
		if (!clazz) return

		// join an existing transaction if one is active
		clazz.withTransaction { status ->
			clazz.newInstance(username: token.username, series: token.series,
					token: token.tokenValue, lastUsed: token.date).save()
		}
	}

	PersistentRememberMeToken getTokenForSeries(String seriesId) {
		def persistentToken
		def clazz = lookupDomainClass()
		if (clazz) {
			// join an existing transaction if one is active
			clazz.withTransaction { status ->
				persistentToken = clazz.get(seriesId)
			}
		}
		if (!persistentToken) {
			return null
		}

		new PersistentRememberMeToken(persistentToken.username, persistentToken.series, persistentToken.token, persistentToken.lastUsed)
	}

	void removeUserTokens(String username) {
		def clazz = lookupDomainClass()
		if (!clazz) return

		clazz.withTransaction { status ->
			// can't use 'where' query with variable class, probably confuses the AST, so create the same DetachedCriteria
			new DetachedCriteria(clazz).eq('username', username).deleteAll()
		}
	}

	void updateToken(String series, String tokenValue, Date lastUsed) {
		def clazz = lookupDomainClass()
		if (!clazz) return

		// join an existing transaction if one is active
		clazz.withTransaction { status ->
			def persistentLogin = clazz.get(series)
			persistentLogin?.token = tokenValue
			persistentLogin?.lastUsed = lastUsed
		}
	}

	protected Class lookupDomainClass() {
		def conf = SpringSecurityUtils.securityConfig
		String domainClassName = conf.rememberMe.persistentToken.domainClassName ?: ''
		def clazz = SpringSecurityUtils.securityConfig.userLookup.useExternalClasses ?
			Class.forName(domainClassName) : grailsApplication.getClassForName(domainClassName)
		if (!clazz) {
			log.error "Persistent token class not found: '{}'", domainClassName
		}
		clazz
	}
}
