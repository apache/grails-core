import grails.plugin.geb.ContainerGebSpec
import pages.LoginPage
import pages.LogoutPage

abstract class AbstractSecurityFunctionalSpec extends ContainerGebSpec {

	def setup() {
		logout()
	}

	protected void login(String user, String pwd) {
		to LoginPage
		username = user
		password = pwd
		loginButton.click()
	}

	protected void logout() {
		to LogoutPage
		logoutButton.click()
		browser.clearCookies()
		browser.go("/")
	}

	protected void assertContentContains(String expected) {
		assert $().text().contains(expected)
	}

	protected void assertContentDoesNotContain(String unexpected) {
		assert !$().text().contains(unexpected)
	}
}
