package com.test

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

@CompileStatic
@Secured('permitAll')
class LogoutController {

	static allowedMethods = [logout: 'POST']

	def index() {}

	@CompileDynamic
	def logout() {
		redirect uri: SpringSecurityUtils.securityConfig.logout.filterProcessesUrl
	}
}
