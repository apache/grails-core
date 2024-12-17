package specs

import com.testapp.TestDataService
import grails.testing.mixin.integration.Integration
import pages.requestmap.CreateRequestmapPage
import pages.requestmap.EditRequestmapPage
import pages.requestmap.ListRequestmapPage
import pages.requestmap.ShowRequestmapPage
import spock.lang.IgnoreIf

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'requestmap' })
class RequestmapSpec extends AbstractSecuritySpec {

	void 'test request maps are initially present'() {
		when:
		go 'testRequestmap/list?max=100'

		then:
		at ListRequestmapPage
		requestmapRows.size() == TestDataService.URIS_FOR_REQUESTMAPS.size()
	}

	void 'add a requestmap'() {
		when:
		to ListRequestmapPage
		newRequestmapButton.click()

		then:
		at CreateRequestmapPage

		when:
		$('form').url = '/nuevo/**'
		configAttribute = 'ROLE_ADMIN'
		createButton.click()

		then:
		at ShowRequestmapPage
		value('URL') == '/nuevo/**'
		configAttribute == 'ROLE_ADMIN'

		when:
		go 'testRequestmap/list?max=100'

		then:
		at ListRequestmapPage
		requestmapRows.size() == (TestDataService.URIS_FOR_REQUESTMAPS.size() + 1)
	}

	void 'edit the details'() {
		when:
		go 'testRequestmap/list?max=100'

		then:
		at ListRequestmapPage

		when:
		requestmapRow(19).showLink.click()

		then:
		at ShowRequestmapPage

		when:
		editButton.click()

		then:
		at EditRequestmapPage

		when:
		$('form').url = '/secure2/**'
		configAttribute = 'ROLE_ADMINX'
		updateButton.click()

		then:
		at ShowRequestmapPage
		value('URL') == '/secure2/**'
		configAttribute == 'ROLE_ADMINX'
	}

	void 'delete requestmap'() {
		when:
		go 'testRequestmap/list?max=100'

		then:
		at ListRequestmapPage

		when:
		requestmapRow(19).showLink.click()

		then:
		at ShowRequestmapPage

		when:
		def deletedId = id
		withConfirm { deleteButton.click() }

		then:
		at ListRequestmapPage
		message == "TestRequestmap $deletedId deleted"

		when:
		go 'testRequestmap/list?max=100'

		then:
		requestmapRows.size() == TestDataService.URIS_FOR_REQUESTMAPS.size()
	}
}
