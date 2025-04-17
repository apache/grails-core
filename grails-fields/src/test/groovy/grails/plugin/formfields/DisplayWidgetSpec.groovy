package grails.plugin.formfields

import grails.plugin.formfields.mock.Person
import grails.plugin.formfields.taglib.AbstractFormFieldsTagLibSpec
import grails.testing.web.taglib.TagLibUnitTest

class DisplayWidgetSpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

	def mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

	def setupSpec() {
		mockDomain(Person)
	}

	def setup() {
        mockFormFieldsTemplateService.getTemplateFor('displayWidget') >> "displayWidget"
		tagLib.formFieldsTemplateService = mockFormFieldsTemplateService
	}

	void 'f:displayWidget without template and a date value renders the formatted date'() {
		expect:
		applyTemplate('<f:displayWidget bean="personInstance" property="dateOfBirth"/>', [personInstance: personInstance]) == applyTemplate('<g:formatDate date="${personInstance.dateOfBirth}"/>', [personInstance: personInstance])
	}

	void 'f:displayWidget without template and a boolean value renders the formatted boolean'() {
		expect:
		applyTemplate('<f:displayWidget bean="personInstance" property="minor"/>', [personInstance: personInstance]) == applyTemplate('<g:formatBoolean boolean="${personInstance.minor}"/>', [personInstance: personInstance])
	}

	void 'f:displayWidget without template and a Boolean value renders the formatted Boolean'() {
		expect:
		applyTemplate('<f:displayWidget bean="personInstance" property="grailsDeveloper"/>', [personInstance: personInstance]) == applyTemplate('<g:formatBoolean boolean="${personInstance.grailsDeveloper}"/>', [personInstance: personInstance])
	}

	void 'f:displayWidget without template and a regular value renders the field value'() {
		expect:
		applyTemplate('<f:displayWidget bean="personInstance" property="salutation"/>', [personInstance: personInstance]) == applyTemplate('<g:fieldValue bean="${personInstance}" field="salutation"/>', [personInstance: personInstance])
	}

	void 'f:displayWidget without template and a String value renders the String value'() {
		expect:
		applyTemplate('<f:displayWidget bean="personInstance" property="name"/>', [personInstance: personInstance]) == personInstance.name
	}

    void 'f:displayWidget with a template renders the template'() {
        given:
        views["/_fields/person/name/_displayWidget.gsp"] = 'Some displayWidget'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/person/name/displayWidget']

        expect:
		applyTemplate('<f:displayWidget bean="personInstance" property="name"/>', [personInstance: personInstance]) == 'Some displayWidget'
	}

	void 'f:displayWidget with a template and theme renders the template from theme'() {
		given:
		views["/_fields/person/name/_displayWidget.gsp"] = 'Some displayWidget'
		views["/_fields/_themes/test/person/name/_displayWidget.gsp"] = 'theme displayWidget'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, "test") >> [path: '/_fields/_themes/test/person/name/displayWidget']

		expect:
		applyTemplate('<f:displayWidget bean="personInstance" property="name" theme="test"/>', [personInstance: personInstance]) == 'theme displayWidget'
	}

	void 'f:displayWidget escapes values to avoid XSS atacks'() {
		expect:
		applyTemplate('<f:displayWidget bean="productInstance" property="name"/>', [productInstance: productInstance]) == "&lt;script&gt;alert(&#39;XSS&#39;);&lt;/script&gt;"
	}

}