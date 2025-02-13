package com.test

import grails.testing.mixin.integration.Integration
import grails.util.Environment
import pages.IndexPage
import pages.LoginPage
import pages.SecureSuperuserPage
import pages.SecureUserPage

@Integration
class RetrieveGroupRolesFunctionalSpec extends AbstractSecurityFunctionalSpec {

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

	void 'secured urls are visible when authenticated'() {
		when:
		login 'euler', 'password1'

		then:
		at LoginPage

		Environment.current

		when:
		go SecureUserPage.url
		login 'euler', 'password'

		then:
		at SecureUserPage
		assertContentContains 'ROLE_MATHEMATICIANS'
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

		when:
		go SecureUserPage.url

		then:
		assertContentContains 'Please Login'

		when: 'logging with a scientist'
		login 'tesla', 'password'

		then:
		at SecureUserPage

		and: 'it does not belong to group mathematicians, thus it does not have the role mathematician'
		assertContentDoesNotContain 'ROLE_MATHEMATICIANS'
		assertContentContains 'ROLE_USER'
		assertContentDoesNotContain 'ROLE_SUPERUSER'

		when:
		logout()

		then:
		at IndexPage

		when:
		go SecureUserPage.url

		then:
		assertContentContains 'Please Login'

		when:
		login 'gauss', 'password'

		then:
		at SecureUserPage

		and:
		assertContentContains 'ROLE_MATHEMATICIANS'
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'

		when:
		to SecureSuperuserPage

		then:
		assertContentContains 'ROLE_USER'
		assertContentContains 'ROLE_SUPERUSER'
	}
}
