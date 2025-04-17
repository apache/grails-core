package grails.plugin.formfields.taglib

import grails.plugin.formfields.FormFieldsTagLib
import grails.plugin.formfields.FormFieldsTemplateService
import grails.plugin.formfields.mock.Product
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll


@Unroll
@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/210')
class AttributesOfWithAndAllTagsArePropagatedSpec extends AbstractFormFieldsTagLibSpec  implements TagLibUnitTest<FormFieldsTagLib> {

	FormFieldsTemplateService mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

	def setupSpec() {
		mockDomain(Product)
	}

	def setup() {
		mockFormFieldsTemplateService.getWidgetPrefix() >> "widget-"
		mockFormFieldsTemplateService.getTemplateFor("wrapper") >> 'wrapper'
		mockFormFieldsTemplateService.getTemplateFor("displayWrapper") >> 'displayWrapper'
		mockFormFieldsTemplateService.getTemplateFor("widget") >> 'widget'
		mockFormFieldsTemplateService.getTemplateFor("displayWidget") >> 'displayWidget'

		mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
		mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']

		mockFormFieldsTemplateService.findTemplate(_, 'widget', null, null) >> [path: '/_fields/default/widget']
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/default/displayWidget']

		tagLib.formFieldsTemplateService = mockFormFieldsTemplateService

		mockEmbeddedSitemeshLayout(tagLib)

		views["/_fields/default/_wrapper.gsp"] = '<wrapper attr="${attribute}">${widget}</wrapper>'
		views["/_fields/default/_displayWrapper.gsp"] = '<displayWrapper attr="${attribute}">${widget}</displayWrapper>'
		views["/_fields/default/_widget.gsp"] = '<widget>${property}</widget>'
		views["/_fields/default/_displayWidget.gsp"] = '<displayWidget>${property}</displayWidget>'
	}

	void "An attribute of the <f:with> tag is present on a field tag"() {
		expect:
		applyTemplate(
				'<f:with bean="productInstance" attribute="cool">' +
						'<f:field property="name"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<wrapper attr="cool"><widget>name</widget></wrapper>'
	}

	void "An attribute of the <f:with> tag is present on an inner display tag"() {
		expect:
		applyTemplate(
				'<f:with bean="productInstance" attribute="cool">' +
						'<f:display property="name"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<displayWrapper attr="cool"><displayWidget>name</displayWidget></displayWrapper>'
	}

	void "An attribute of the <f:all> tag is present on inner fields tag"() {
		expect:
		applyTemplate(
				'<f:all bean="productInstance" attribute="cool"/>',
				[productInstance: productInstance]
		) == '<wrapper attr="cool"><widget>name</widget></wrapper>' +
				'<wrapper attr="cool"><widget>netPrice</widget></wrapper>' +
				'<wrapper attr="cool"><widget>taxRate</widget></wrapper>'
	}

	@Ignore("It is not possible to use the <f:all> tag for printing displays instead of fields")
	void "An attribute of the <f:displayAll> tag is present on inner displays tag"() {
		expect:
		applyTemplate(
				'<f:displayAll bean="productInstance" wrapper="cool"/>',
				[productInstance: productInstance]
		) == '<displayWrapper attr="cool"><displayWidget>name</displayWidget></displayWrapper>' +
				'<displayWrapper attr="cool"><displayWidget>netPrice</displayWidget></displayWrapper>' +
				'<displayWrapper attr="cool"><displayWidget>taxRate</displayWidget></displayWrapper>' +
				'<displayWrapper attr="cool"><displayWidget>tax</displayWidget></displayWrapper>'
	}

	//Test Override of attributes

	void "<f:field> attributes overrides <f:with> attributes"() {
		expect:
		applyTemplate(
				'<f:with bean="productInstance" attribute="general-attr">' +
						'<f:field property="name" attribute="override-attr"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<wrapper attr="override-attr"><widget>name</widget></wrapper>'
	}

	void "<f:display> attributes overrides <f:with> attributes"() {
		expect:
		applyTemplate(
				'<f:with bean="productInstance" attribute="general-attr">' +
						'<f:display property="name" attribute="override-attr"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<displayWrapper attr="override-attr"><displayWidget>name</displayWidget></displayWrapper>'
	}

	//Test cascade tags

	void "Test cascade of <f:with> tags"() {
		expect:
		applyTemplate(
				'<f:with bean="productInstance" attribute="general-attr">' +
						'<f:field property="name"/>' +
						'<f:field property="netPrice"/>' +
						'<f:with bean="productInstance" attribute="override-attr">' +
						'<f:field property="taxRate"/>' +
						'</f:with>' +
						'<f:display property="tax"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<wrapper attr="general-attr"><widget>name</widget></wrapper>' +
				'<wrapper attr="general-attr"><widget>netPrice</widget></wrapper>' +
				'<wrapper attr="override-attr"><widget>taxRate</widget></wrapper>' +
				'<displayWrapper attr="general-attr"><displayWidget>tax</displayWidget></displayWrapper>'
	}

	void "An attribute of the <f:with> tag (prefixed with 'widget-') is present on an inner <f:widget> tag"() {
		given:
		views["/_fields/default/_widget.gsp"] = '<widget attr="${attribute}">${property}</widget>'

		expect:
		applyTemplate(
				'<f:with bean="productInstance" widget-attribute="cool">' +
						'<f:widget property="name"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<widget attr="cool">name</widget>'
	}

	void "An attribute of the <f:with> tag (prefixed with 'widget-') is present on an inner <f:displayWidget> tag"() {
		given:
		views["/_fields/default/_displayWidget.gsp"] = '<displayWidget attr="${attribute}">${property}</displayWidget>'

		expect:
		applyTemplate(
				'<f:with bean="productInstance" widget-attribute="cool">' +
						'<f:displayWidget property="name"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<displayWidget attr="cool">name</displayWidget>'
	}

	void "An attribute inside an <f:widget> tag can override an extra attribute of his parent <f:with>"() {
		given:
		views["/_fields/default/_widget.gsp"] = '<widget attr="${attribute}">${property}</widget>'

		expect:
		applyTemplate(
				'<f:with bean="productInstance" widget-attribute="cool">' +
						'<f:widget property="name" attribute="very cool"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<widget attr="very cool">name</widget>'
	}

	void "An attribute inside an <f:displayWidget> tag can override an extra attribute of his parent <f:with>"() {
		given:
		views["/_fields/default/_displayWidget.gsp"] = '<displayWidget attr="${attribute}">${property}</displayWidget>'

		expect:
		applyTemplate(
				'<f:with bean="productInstance" widget-attribute="cool">' +
						'<f:displayWidget property="name" attribute="very cool"/>' +
						'</f:with>',
				[productInstance: productInstance]
		) == '<displayWidget attr="very cool">name</displayWidget>'
	}
}