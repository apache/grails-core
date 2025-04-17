package grails.plugin.formfields.taglib

import grails.plugin.formfields.mock.Person
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Issue
import grails.plugin.formfields.*

@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/30')
class EmbeddedPropertiesSpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

	def mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

	def setupSpec() {
		mockDomain(Person)
	}

	def setup() {
		mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
        mockFormFieldsTemplateService.getTemplateFor('wrapper') >> "wrapper"
        mockFormFieldsTemplateService.getTemplateFor('widget') >> "widget"
        mockFormFieldsTemplateService.getTemplateFor('displayWrapper') >> "displayWrapper"
        mockFormFieldsTemplateService.getTemplateFor('displayWidget') >> "displayWidget"
		mockFormFieldsTemplateService.getWidgetPrefix() >> 'input-'
		tagLib.formFieldsTemplateService = mockFormFieldsTemplateService

        mockEmbeddedSitemeshLayout tagLib
    }

	void "field tag renders individual fields for embedded properties"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${property} '

		when:
		def output = applyTemplate('<f:field bean="personInstance" property="address"/>', [personInstance: personInstance])

		then:
		output.contains('address.city address.country address.street')
	}

	void "field tag wraps embedded properties in a container"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${property} '

		expect:
		applyTemplate('<f:field bean="personInstance" property="address"/>', [personInstance: personInstance]) == '<fieldset class="embedded address"><legend>Address</legend>address.city address.country address.street </fieldset>'
	}

	void "embedded property label is resolved from message bundle"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${property} '

		and:
		messageSource.addMessage('person.address.label', request.locale, 'Address of person')

		when:
		def output = applyTemplate('<f:field bean="personInstance" property="address"/>', [personInstance: personInstance])

		then:
		output.contains('<legend>Address of person</legend>')
	}

}