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
import grails.plugin.springsecurity.ui.strategy.DefaultAclStrategy
import grails.plugin.springsecurity.ui.strategy.DefaultErrorsStrategy
import grails.plugin.springsecurity.ui.strategy.DefaultPersistentLoginStrategy
import grails.plugin.springsecurity.ui.strategy.DefaultPropertiesStrategy
import grails.plugin.springsecurity.ui.strategy.DefaultQueryStrategy
import grails.plugin.springsecurity.ui.strategy.DefaultRegistrationCodeStrategy
import grails.plugin.springsecurity.ui.strategy.DefaultRequestmapStrategy
import grails.plugin.springsecurity.ui.strategy.DefaultRoleStrategy
import grails.plugin.springsecurity.ui.strategy.DefaultUserStrategy
import grails.plugin.springsecurity.ui.strategy.MailPluginMailStrategy
import grails.plugins.Plugin
import groovy.util.logging.Slf4j

/**
 * @author Burt Beckwith
 */
@Slf4j
class SpringSecurityUiGrailsPlugin extends Plugin {

	String grailsVersion = '7.0.0-SNAPSHOT > *'
	String author = 'Burt Beckwith'
	String authorEmail = ''
	String title = 'Spring Security UI plugin'
	String description = 'User interface extensions for the Spring Security plugin'
	String documentation = 'https://apache.github.io/grails-spring-security'
	String license = 'APACHE'
	def organization = [name: 'Grails', url: 'https://www.grails.org']
	def issueManagement = [url: 'https://github.com/apache/grails-spring-security/issues']
	def scm = [url: 'https://github.com/apache/grails-spring-security']
	def loadAfter = ['springSecurityCore', 'springSecurityAcl']
	def profiles = ['web']

	Closure doWithSpring() {{ ->

		def conf = SpringSecurityUtils.securityConfig
		if (!conf || !conf.active) {
			return
		}

		boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true

		if (printStatusMessages) {
			println '\nConfiguring Spring Security UI ...'
		}

		SpringSecurityUtils.loadSecondaryConfig 'DefaultUiSecurityConfig'

		def serviceRef = { springSecurityUiService = ref('springSecurityUiService') }
		uiAclStrategy DefaultAclStrategy, serviceRef
		uiErrorsStrategy DefaultErrorsStrategy, serviceRef
		uiPersistentLoginStrategy DefaultPersistentLoginStrategy, serviceRef
		uiPropertiesStrategy DefaultPropertiesStrategy, serviceRef
		uiQueryStrategy DefaultQueryStrategy, serviceRef
		uiRegistrationCodeStrategy DefaultRegistrationCodeStrategy, serviceRef
		uiRequestmapStrategy DefaultRequestmapStrategy, serviceRef
		uiRoleStrategy DefaultRoleStrategy, serviceRef
		uiUserStrategy DefaultUserStrategy, serviceRef

		uiMailStrategy(MailPluginMailStrategy) { bean ->
			// can't explicitly add a dependency for the mailService bean (mailService = ref('mailService'))
			// since the mail plugin might not be installed
			bean.autowire = 'byName'
		}

		springSecurityUiInterceptor(SpringSecurityUiInterceptor) {
			uiPropertiesStrategy = ref('uiPropertiesStrategy')
		}

		if (printStatusMessages) {
			println '... finished configuring Spring Security UI\n'
		}
	}}

	void doWithApplicationContext() {

		def conf = SpringSecurityUtils.securityConfig
		if (!conf || !conf.active) {
			return
		}

		if (log.traceEnabled) {
			// redisplay here to show the merged config
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

		// can't use InitializingBean because of circular references with the strategy classes
		applicationContext.springSecurityUiService.initialize()
	}
}
