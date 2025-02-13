package spring.security.cas

import grails.plugin.springsecurity.annotation.Secured

class SecureController {

	@Secured('ROLE_ADMIN')
	def admins() {
		render 'Logged in with ROLE_ADMIN'
	}

	@Secured('ROLE_USER')
	def users() {
		render 'Logged in with ROLE_USER'
	}
}
