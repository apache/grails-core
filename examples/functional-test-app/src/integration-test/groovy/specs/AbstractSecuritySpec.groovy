package specs

import com.testapp.TestDataService
import functional.test.app.Application
import geb.driver.CachingDriverFactory
import grails.plugin.geb.ContainerGebSpec
import grails.plugin.springsecurity.SpringSecurityCoreGrailsPlugin
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder
import pages.LoginPage
import spock.lang.Shared

@Integration(applicationClass = Application)
abstract class AbstractSecuritySpec extends ContainerGebSpec {

	private @Shared boolean databaseReset = false

	@Autowired
	TestDataService testDataService

	def setup() {
		logout()

		// call resetDatabase() once per suite, before the first test; would
		// be better in a setupSpec() method, but can't make go() calls there
		if (!databaseReset) {
			resetDatabase()
			databaseReset = true
		}
	}

	void cleanup() {
		CachingDriverFactory.clearCache()
	}

	void cleanupSpec() {
		databaseReset = false
	}

	protected void resetDatabase() {
		testDataService.returnToInitialState()
	}

	protected String getContent(String url) {
		go url
		$().text()
	}

	protected String getSessionValue(String name) {
		getContent 'hack/getSessionValue?name=' + name
	}

	protected String getPageSource() {
		browser.driver.pageSource
	}

	protected void login(String user, String pwd = 'password', boolean remember = false) {
		to LoginPage
		username = user
		password = pwd
		if (remember) {
			rememberMe.click()
		}
		loginButton.click()
	}

	protected void logout() {
		go SpringSecurityUtils.securityConfig.logout.filterProcessesUrl
		browser.clearCookies()
		browser.go("/")
	}

	protected void assertContentContains(String expected) {
		assert $().text().contains(expected)
	}

	protected void assertContentDoesNotContain(String unexpected) {
		assert !$().text().contains(unexpected)
	}

	protected MessageDigestPasswordEncoder createSha256Encoder() {
		MessageDigestPasswordEncoder passwordEncoder = new MessageDigestPasswordEncoder(SpringSecurityCoreGrailsPlugin.ENCODING_IDSHA256)
		passwordEncoder.iterations = 1
		passwordEncoder
	}
}
