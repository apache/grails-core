package grails.plugin.formfields.taglib

import grails.plugin.formfields.*
import grails.plugin.formfields.mock.*
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.*

@Unroll
class TemplateModelSpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

	def mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

	def setupSpec() {
		mockDomains(Person, Employee)
	}

	def setup() {
		mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
        mockFormFieldsTemplateService.getTemplateFor('wrapper') >> "wrapper"
        mockFormFieldsTemplateService.getTemplateFor('widget') >> "widget"
        mockFormFieldsTemplateService.getTemplateFor('displayWrapper') >> "displayWrapper"
        mockFormFieldsTemplateService.getTemplateFor('displayWidget') >> "displayWidget"
		tagLib.formFieldsTemplateService = mockFormFieldsTemplateService
	}

	void "bean and property attributes are passed to template"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${bean.getClass().simpleName}.${property}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == "Person.name"
	}

	void "constraints are passed to template"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'nullable=${constraints.nullable}, blank=${constraints.blank}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == "nullable=false, blank=false"
	}

	void "label is resolved by convention and passed to template"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<label>${label}</label>'

		and:
		messageSource.addMessage('person.name.label', request.locale, "Name of person")

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == "<label>Name of person</label>"
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/38')
	void "label is resolved by property path before property type and passed to template"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<label>${label}</label>'

		and:
		messageSource.addMessage('person.address.city.label', request.locale, "Label for property path")
		messageSource.addMessage('address.city.label', request.locale, "Label for property type")

		expect:
		applyTemplate('<f:field bean="personInstance" property="address.city"/>', [personInstance: personInstance]) == "<label>Label for property path</label>"
	}

    @Issue('https://github.com/grails-fields-plugin/grails-fields/issues/76')
	void "label is resolved by property type when property path message code does not exist"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<label>${label}</label>'

		and:
		messageSource.addMessage('address.city.label', request.locale, "Label for property type")

		expect:
		applyTemplate('<f:field bean="personInstance" property="address.city"/>', [personInstance: personInstance]) == "<label>Label for property type</label>"
	}

    @Issue('https://github.com/grails-fields-plugin/grails-fields/issues/76')
	void "label is not resolved by property type when property path label same as default label"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<label>${label}</label>'

		and:
		messageSource.addMessage('person.address.city.label', request.locale, "City")
		messageSource.addMessage('address.city.label', request.locale, "Label for property type")

		expect:
		applyTemplate('<f:field bean="personInstance" property="address.city"/>', [personInstance: personInstance]) == "<label>City</label>"
	}

	void "label is defaulted to natural property name"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<label>${label}</label>'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == "<label>Name</label>"
		applyTemplate('<f:field bean="personInstance" property="dateOfBirth"/>', [personInstance: personInstance]) == "<label>Date Of Birth</label>"
	}

	void "label can be overridden by label attribute"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<label>${label}</label>'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" label="Name of person"/>', [personInstance: personInstance]) == "<label>Name of person</label>"
	}

	void "label can be overridden by label key attribute"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<label>${label}</label>'

		and:
		messageSource.addMessage("custom.name.label", request.locale, "Name of person")

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" label="custom.name.label"/>', [personInstance: personInstance]) == "<label>Name of person</label>"
	}

	void "value is defaulted to property value"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<g:formatDate date="${value}" format="yyyy-MM-dd"/>'

		expect:
		applyTemplate('<f:field bean="personInstance" property="dateOfBirth"/>', [personInstance: personInstance]) == "1987-04-19"
	}
	
	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/55')
	void "numeric value of zero is not overridden by default"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<span>${value}</span>'
		
		and:
		def employee = new Employee(name: 'Monica Lewinsky', jobTitle: 'Intern', salary: 0)

		expect:
		applyTemplate('<f:field bean="personInstance" property="salary" default="50000"/>', [personInstance: employee]) == "<span>0</span>"
	}

	void "value is overridden by value attribute"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${value}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" value="Bartholomew J. Simpson"/>', [personInstance: personInstance]) == "Bartholomew J. Simpson"
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/46')
	void "value is overridden by #{value == null ? 'null' : 'empty'} value attribute"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<em>${value}</em>'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" value="${value}"/>', [personInstance: personInstance, value: value]) == '<em></em>'
		
		where:
		value << [null, '']
	}

	void "falsy string property value of '#value' falls back to default"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${value}'

		and:
		personInstance.name = value

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" default="A. N. Other"/>', [personInstance: personInstance]) == "A. N. Other"
		
		where:
		value << [null, '']
	}

	void "default attribute is ignored if property has non-null value"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${value}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" default="A. N. Other"/>', [personInstance: personInstance]) == "Bart Simpson"
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/46')
	void "default attribute is ignored if a non-null value override is specified"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${value}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" value="Bartholomew J. Simpson" default="A. N. Other"/>', [personInstance: personInstance]) == 'Bartholomew J. Simpson'
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/46')
	void "default attribute is ignored if a value override of '#value' is specified"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${value}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" value="${value}" default="A. N. Other"/>', [personInstance: personInstance, value: value]) == 'A. N. Other'

		where:
		value << [null, '']
	}

	void "errors passed to template is an empty collection for valid bean"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<g:each var="error" in="${errors}"><em>${error}</em></g:each>'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == ""
	}

	void "errors passed to template is a collection of strings"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '<g:each var="error" in="${errors}"><em>${error}</em></g:each>'

		and:
		personInstance.errors.rejectValue("name", "blank")
		personInstance.errors.rejectValue("name", "nullable")

		when:
		def renderedErrors = applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance])

		then:
		renderedErrors == "<em>blank</em><em>nullable</em>" || renderedErrors == "<em>blank.grails.plugin.formfields.mock.Person.name</em><em>nullable.grails.plugin.formfields.mock.Person.name</em>"
	}

	void "required flag is passed to template"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'required=${required}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == "required=true"
	}

	void "required flag can be forced with attribute"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'required=${required}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="minor" required="true"/>', [personInstance: personInstance]) == "required=true"
	}

	void "required flag can be forced off with attribute"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'required=${required}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" required="false"/>', [personInstance: personInstance]) == "required=false"
	}

	void "invalid flag is passed to template if bean has errors"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'invalid=${invalid}'

		and:
		personInstance.errors.rejectValue("name", "blank")

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == "invalid=true"
	}

	void "invalid flag is not passed to template if bean has no errors"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'invalid=${invalid}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == "invalid=false"
	}

	void "invalid flag can be overridden with attribute"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'invalid=${invalid}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" invalid="true"/>', [personInstance: personInstance]) == "invalid=true"
	}

	void "rendered input is passed to template"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = '${widget}'

		expect:
		applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == '<input type="text" name="name" value="Bart Simpson" required="" id="name" />'
	}

    @Issue('https://github.com/grails-fields-plugin/grails-fields/issues/80')
	def "correct value for Boolean"() {
		given:
    	views["/_fields/default/_wrapper.gsp"] = 'value=${value}'
	    def personWithBoolean = { personInstance.grailsDeveloper = it ; personInstance }

		expect:
		output == applyTemplate('<f:field bean="personInstance" property="grailsDeveloper"/>', [personInstance: personWithBoolean(value)])
        
        where:
        value | output
        null  | 'value='
        false | 'value=false'
        true  | 'value=true'
	}

    @Issue('https://github.com/grails-fields-plugin/grails-fields/issues/80')
	def "correct value for boolean"() {
		given:
    	views["/_fields/default/_wrapper.gsp"] = 'value=${value}'
	    def personWith_boolean = { personInstance.minor = it ; personInstance }

		expect:
		output == applyTemplate('<f:field bean="personInstance" property="minor"/>', [personInstance: personWith_boolean(value)])
        
        where:
        value | output
        false | 'value=false'
        true  | 'value=true'
	}
}
