package com.test

import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

@CompileStatic
class SecureController {

	SpringSecurityService springSecurityService

	@Secured('ROLE_ADMIN')
	def admins() {
		renderRoles()
	}

	@Secured('ROLE_USER')
	def users() {
		renderRoles()
	}

	@Secured('ROLE_SUPERUSER')
	def superusers() {
		renderRoles()
	}

	@Secured('ROLE_FOO_BAR')
	def fooBar() {
		renderRoles()
	}

	@CompileDynamic
	private void renderRoles() {
		render 'You have these roles: ' + springSecurityService.principal.authorities.join(' ')
	}
}
