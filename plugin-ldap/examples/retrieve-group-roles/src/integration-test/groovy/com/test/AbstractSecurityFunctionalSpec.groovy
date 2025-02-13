package com.test

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import pages.LoginPage
import pages.LogoutPage

@Integration
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
	}

	protected void assertContentContains(String expected) {
		assert contentContains(expected)
	}

	boolean contentContains(String expected) {
		browser.driver.pageSource.contains(expected)
	}

	protected void assertContentDoesNotContain(String unexpected) {
		assert !contentContains(unexpected)
	}
}
