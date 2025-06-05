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

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.web.GrailsSecurityFilterChain
import grails.plugin.springsecurity.web.access.intercept.AbstractFilterInvocationDefinition
import org.springframework.security.access.AccessDecisionManager
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserCache
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource
import org.springframework.security.web.authentication.logout.LogoutHandler

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class SecurityInfoController {

	AccessDecisionManager accessDecisionManager
	AuthenticationManager authenticationManager
	FilterInvocationSecurityMetadataSource channelFilterInvocationSecurityMetadataSource
	List<LogoutHandler> logoutHandlers
	AbstractFilterInvocationDefinition objectDefinitionSource
	List<GrailsSecurityFilterChain> securityFilterChains
	UserCache userCache

	def config() {
		[conf: new TreeMap(conf.flatten())]
	}

	def mappings() {
		// List<InterceptedUrl>
		[configAttributes: objectDefinitionSource.configAttributeMap,
		 securityConfigType: conf.securityConfigType]
	}

	def currentAuth() {
		[auth: SecurityContextHolder.context.authentication]
	}

	def usercache() {
		[cache: conf.cacheUsers ? userCache.cache.nativeCache : false]
	}

	def filterChains() {
		[securityFilterChains: securityFilterChains]
	}

	def logoutHandlers() {
		[handlers: logoutHandlers]
	}

	def voters() {
		[voters: accessDecisionManager.decisionVoters]
	}

	def providers() {
		[providers: authenticationManager.providers]
	}

	def secureChannel() {
		[requestMap: channelFilterInvocationSecurityMetadataSource?.requestMap]
	}

	protected ConfigObject getConf() {
		SpringSecurityUtils.securityConfig
	}
}
