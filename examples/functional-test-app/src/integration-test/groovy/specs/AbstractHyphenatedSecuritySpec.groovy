package specs

import grails.testing.mixin.integration.Integration

@Integration
abstract class AbstractHyphenatedSecuritySpec extends AbstractSecuritySpec {

	protected void resetDatabase() {
		go 'test-data/reset'
		go 'test-data/add-test-users'
	}

	protected String getSessionValue(String name) {
		getContent 'hack/get-session-value?name=' + name
	}

	protected String getUserProperty(String user, String propertyName) {
		getContent "hack/get-user-property?user=$user&propName=$propertyName"
	}
}
