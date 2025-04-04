package com.test

import grails.testing.mixin.integration.Integration
import pages.IndexPage
import pages.SecureSuperuserPage
import pages.SecureUserPage

@Integration
class CustomUserDetailsContextMapperFunctionalSpec extends AbstractSecurityFunctionalSpec {

	void 'secured urls are not visible without auth'() {
		when:
		go SecureUserPage.url

		then:
		assertContentContains 'Please Login'

		when:
		go SecureSuperuserPage.url

		then:
		assertContentContains 'Please Login'
	}

	def "login with a user present in the database"() {
		when:
		go SecureUserPage.url

		then:
		assertContentContains 'Please Login'

		when:
		login 'galileo', 'password'

		then:
		at SecureUserPage

		and:
		assertContentContains('galileo@ldap.forumsys.com')

		when:
		logout()

		then:
		at IndexPage
	}

}
