package grails.plugin.formfields.taglib

import grails.plugin.formfields.mock.Person
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Issue
import grails.plugin.formfields.*

@Issue('https://github.com/grails-fields-plugin/grails-fields/pull/17')
class ExtraAttributesSpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

	def mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

	def setupSpec() {
        mockDomain(Person)
	}

	def setup() {
		mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
		mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']
        mockFormFieldsTemplateService.getTemplateFor('wrapper') >> "wrapper"
        mockFormFieldsTemplateService.getTemplateFor('widget') >> "widget"
        mockFormFieldsTemplateService.getTemplateFor('displayWrapper') >> "displayWrapper"
        mockFormFieldsTemplateService.getTemplateFor('displayWidget') >> "displayWidget"
        mockFormFieldsTemplateService.getWidgetPrefix() >> 'input-'
		tagLib.formFieldsTemplateService = mockFormFieldsTemplateService
	}

    void 'arbitrary attributes can be passed to the field template model for backward compatibility'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${foo}'

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == 'bar'
    }

    void 'arbitrary attributes are be passed to the field template model in "attrs"'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${attrs.foo}'

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == 'bar'
    }

    void 'arbitrary attributes on f:field are not passed to the widget template'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${widget}'
        views["/_fields/person/name/_widget.gsp"] = '<span>${foo}${attrs?.foo}</span>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'widget', null, null) >> [path: '/_fields/person/name/widget']

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == '<span></span>'
    }

    void 'arbitrary attributes on f:display are not passed to the widget template'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '${widget}'
        views["/_fields/person/name/_displayWidget.gsp"] = '<span>${foo}${attrs?.foo}</span>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/person/name/displayWidget']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == '<span></span>'
    }

    void 'arbitrary attributes prefixed with widget- are not passed to the widget template (if it is configured as the prefix)'() {
        given:
        mockFormFieldsTemplateService.getWidgetPrefix() >> 'widget-'
        views["/_fields/default/_wrapper.gsp"] = '<foo>${foo}</foo>'

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" widget-foo="bar" />', [personInstance: personInstance]) == '<foo></foo>'
    }

    void 'arbitrary attributes prefixed with widget- are passed to the wrapper template if it is configured with another prefix ("input-" in this case, see setup)'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '<foo>${attrs["widget-foo"]}</foo>'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" widget-foo="bar" />', [personInstance: personInstance]) == '<foo>bar</foo>'
    }

    void 'arbitrary attributes on f:field are not passed to the default widget'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${widget}'

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == '<input type="text" name="name" value="Bart Simpson" required="" id="name" />'
    }

    void 'arbitrary attributes on f:display are not passed to the default widget'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '${widget}'
        views["/_fields/default/_displayWidget.gsp"] = '${foo}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/default/displayWidget']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == ''
    }

    void 'arbitrary attributes can be passed to the display template model for backward compatibility'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '${foo}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == 'bar'
    }

    void 'arbitrary attributes are be passed to the display template model in "attrs"'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '${attrs.foo}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == 'bar'
    }

	void 'arbitrary attributes on f:input are passed to the input template'() {
		given:
		views["/_fields/person/name/_widget.gsp"] = '${foo}'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'widget', null, null) >> [path: '/_fields/person/name/widget']

		expect:
		applyTemplate('<f:input bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == 'bar'
	}

	void 'arbitrary attributes on f:displayWidget are passed to the displayWidget template'() {
		given:
		views["/_fields/person/name/_displayWidget.gsp"] = '${foo}'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/person/name/displayWidget']

		expect:
		applyTemplate('<f:displayWidget bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == 'bar'
	}

	void 'arbitrary attributes on f:input are passed to the default input'() {
		expect:
		applyTemplate('<f:input bean="personInstance" property="name" foo="bar"/>', [personInstance: personInstance]) == '<input type="text" foo="bar" name="name" value="Bart Simpson" required="" id="name" />'
	}

	@Issue("https://github.com/grails-fields-plugin/grails-fields/pull/49")
    void 'arbitrary attributes prefixed with input- are not passed to the field template'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '<foo>${foo}</foo>'

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" input-foo="bar" />', [personInstance: personInstance]) == '<foo></foo>'
    }

    void 'arbitrary attributes prefixed with input- are not passed to the display template'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '<foo>${foo}</foo>'

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" input-foo="bar" />', [personInstance: personInstance]) == '<foo></foo>'
    }

    void 'arbitrary attributes prefixed with widget- are not passed to the display template (if it is configured as the prefix)'() {
        given:
        mockFormFieldsTemplateService.getWidgetPrefix() >> 'widget-'
        views["/_fields/default/_displayWrapper.gsp"] = '<foo>${foo}</foo>'

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" widget-foo="bar" />', [personInstance: personInstance]) == '<foo></foo>'
    }

    void 'arbitrary attributes prefixed with widget- are passed to the display template if it is configured with another prefix ("input-" in this case, see setup)'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '<foo>${attrs["widget-foo"]}</foo>'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" widget-foo="bar" />', [personInstance: personInstance]) == '<foo>bar</foo>'
    }

    void 'arbitrary attributes prefixed with input- on f:field are added to the widget template model for backward compatibility'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${widget}'
        views["/_fields/person/name/_widget.gsp"] = '<span>${foo}</span>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'widget', null, null) >> [path: '/_fields/person/name/widget']

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" input-foo="bar"/>', [personInstance: personInstance]) == '<span>bar</span>'
    }

    void 'arbitrary attributes prefixed with input- on f:display are added to the display widget template model for backward compatibility'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '${widget}'
        views["/_fields/person/name/_displayWidget.gsp"] = '<span>${foo}</span>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/person/name/displayWidget']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" input-foo="bar"/>', [personInstance: personInstance]) == '<span>bar</span>'
    }

    void 'arbitrary attributes prefixed with input- on f:field are passed to the input template as attrs'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${widget}'
        views["/_fields/person/name/_widget.gsp"] = '<span>${attrs.foo}</span>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'widget', null, null) >> [path: '/_fields/person/name/widget']

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" input-foo="bar"/>', [personInstance: personInstance]) == '<span>bar</span>'
    }

    void 'arbitrary attributes prefixed with input- on f:display are passed to the widget template as attrs'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '${widget}'
        views["/_fields/person/name/_displayWidget.gsp"] = '<span>${attrs.foo}</span>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/person/name/displayWidget']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" input-foo="bar"/>', [personInstance: personInstance]) == '<span>bar</span>'
    }

	@Issue("https://github.com/grails-fields-plugin/grails-fields/pull/49")
    void 'arbitrary attributes prefixed with input- on f:field are passed to the default input'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '${widget}'

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" input-foo="bar"/>', [personInstance: personInstance]) == '<input type="text" foo="bar" name="name" value="Bart Simpson" required="" id="name" />'
    }

}