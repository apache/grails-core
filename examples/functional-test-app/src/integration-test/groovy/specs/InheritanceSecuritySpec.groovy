package specs

import grails.testing.mixin.integration.Integration
import pages.IndexPage
import pages.LoginPage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'annotation' })
class InheritanceSecuritySpec extends AbstractSecuritySpec {

	protected void resetDatabase() {
		super.resetDatabase()
		go 'testData/addTestUsers'
	}

	void 'should redirect to login page for anonymous'() {
		when:
		go uri

		then:
		at LoginPage

		where:
		uri << ['base/index', 'extended/index', 'base/delete', 'extended/delete', 'base/update', 'extended/update']
	}

	void 'verify security for testuser'() {
		when:
		login 'testuser', 'password'

		then:
		at IndexPage

		when:
		go 'base/index'

		then:
		pageSource =~ /BaseController/

		when:
		go 'base/delete'

		then:
		pageSource =~ /DELETED/

		when:
		go 'base/update'

		then:
		pageSource =~ /BaseController - UPDATED/

		when:
		go 'extended/index'

		then:
		pageSource =~ /ExtendedController/

		when:
		go 'extended/delete'

		then:
		pageSource =~ /DELETED/

		when:
		go 'extended/update'

		then:
		pageSource =~ /ExtendedController - UPDATED/
	}

	void 'verify security for other user'() {
		when:
		login 'testuser_books', 'password'

		then:
		at IndexPage

		when:
		go 'base/index'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'base/delete'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'base/update'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'extended/index'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'extended/delete'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'extended/update'

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."
	}
}
