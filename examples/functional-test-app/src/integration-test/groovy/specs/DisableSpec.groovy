package specs

import grails.testing.mixin.integration.Integration
import pages.IndexPage
import spock.lang.IgnoreIf


@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'misc' })
class DisableSpec extends AbstractHyphenatedSecuritySpec {

	void 'lock account'() {
		given:
		String username = 'admin'

		when:
		login username

		then:
		at IndexPage

		when:
		go 'secure-annotated'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()

		then:
		'false' == getUserProperty(username, 'accountLocked')

		when:
		setUserProperty username, 'accountLocked', true

		then:
		'true' == getUserProperty(username, 'accountLocked')

		when:
		login username

		then:
		assertContentContains 'accountLocked'

		// reset
		when:
		setUserProperty username, 'accountLocked', false

		then:
		'false' == getUserProperty(username, 'accountLocked')
	}

	void 'disable account'() {
		given:
		String username = 'admin'

		when:
		login username

		then:
		at IndexPage

		when:
		go 'secure-annotated'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()

		then:
		'true' == getUserProperty(username, 'enabled')

		when:
		setUserProperty username, 'enabled', false

		then:
		'false' == getUserProperty(username, 'enabled')

		when:
		login username

		then:
		assertContentContains 'accountDisabled'

		// reset
		when:
		setUserProperty username, 'enabled', true

		then:
		'true' == getUserProperty(username, 'enabled')
	}

	void 'expire account'() {
		given:
		String username = 'admin'

		when:
		login username

		then:
		at IndexPage

		when:
		go 'secure-annotated'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()

		then:
		'false' == getUserProperty(username, 'accountExpired')

		when:
		setUserProperty username, 'accountExpired', true

		then:
		'true' == getUserProperty(username, 'accountExpired')

		when:
		login username

		then:
		assertContentContains 'accountExpired'

		// reset
		when:
		setUserProperty username, 'accountExpired', false

		then:
		'false' == getUserProperty(username, 'accountExpired')
	}

	void 'expire password'() {
		given:
		String username = 'admin'

		when:
		login username

		then:
		at IndexPage

		when:
		go 'secure-annotated'

		then:
		assertContentContains 'you have ROLE_ADMIN'

		when:
		logout()

		then:
		'false' == getUserProperty(username, 'passwordExpired')

		when:
		setUserProperty username, 'passwordExpired', true

		then:
		'true' == getUserProperty(username, 'passwordExpired')

		when:
		login username

		then:
		assertContentContains 'passwordExpired'

		// reset
		when:
		setUserProperty username, 'passwordExpired', false

		then:
		'false' == getUserProperty(username, 'passwordExpired')
	}

	private void setUserProperty(String user, String propertyName, value) {
		go "hack/set-user-property?user=$user&$propertyName=$value"
	}
}
