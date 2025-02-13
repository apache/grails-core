package com.test

import grails.testing.mixin.integration.Integration
import pages.IndexPage
import pages.SecureSuperuserPage
import pages.SecureUserPage

@Integration
class RetrieveDbRolesFunctionalSpec extends AbstractSecurityFunctionalSpec {

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
		login 'gauss', 'password'

		then:
		at SecureUserPage

		and:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'

		when:
		to SecureSuperuserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'

		when:
		logout()

		then:
		at IndexPage
	}

	def "login with a user NOT in the database"() {
		when:
		go SecureUserPage.url

		then:
		assertContentContains 'Please Login'

		when:
		login 'euler', 'password'

		then:
		at SecureUserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentDoesNotContain 'ROLE_SUPERUSER'

		when:
		go SecureSuperuserPage.url

		then:
		assertContentContains "Sorry, you're not authorized to view this page."

		when:
		logout()

		then:
		at IndexPage
	}
}
