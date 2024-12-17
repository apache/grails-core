package specs

import grails.testing.mixin.integration.Integration
import pages.role.CreateRolePage
import pages.role.EditRolePage
import pages.role.ListRolePage
import pages.role.ShowRolePage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ !(
		System.getProperty('TESTCONFIG') == 'annotation' ||
        System.getProperty('TESTCONFIG') == 'basic' ||
        System.getProperty('TESTCONFIG') == 'basicCacheUsers' ||
        System.getProperty('TESTCONFIG') == 'requestmap' ||
        System.getProperty('TESTCONFIG') == 'static')
})
class RoleSpec extends AbstractSecuritySpec {

	void 'there are no roles initially'() {
		when:
		to ListRolePage

		then:
		roleRows.size() == 0
	}

	void 'add a role'() {
		when:
		to ListRolePage
		newRoleButton.click()

		then:
		at CreateRolePage

		when:
		authority = 'test'
		createButton.click()

		then:
		at ShowRolePage
		authority == 'test'
	}

	void 'edit the details'() {
		when:
		to ListRolePage
		roleRow(0).showLink.click()

		then:
		at ShowRolePage

		when:
		editButton.click()

		then:
		at EditRolePage

		when:
		authority = 'test_new'
		updateButton.click()

		then:
		at ShowRolePage

		when:
		to ListRolePage

		then:
		roleRows.size() == 1

		def row = roleRow(0)
		row.authority == 'test_new'
	}

	void 'show role'() {
		when:
		to ListRolePage
		roleRow(0).showLink.click()

		then:
		at ShowRolePage
	}

	void 'delete role'() {
		when:
		to ListRolePage
		roleRow(0).showLink.click()
		def deletedId = id

		then:
		at ShowRolePage

		when:
		withConfirm { deleteButton.click() }

		then:
		at ListRolePage

		message == "TestRole $deletedId deleted"
		roleRows.size() == 0
	}
}
