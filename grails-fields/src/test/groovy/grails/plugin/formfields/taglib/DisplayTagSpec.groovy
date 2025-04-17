package grails.plugin.formfields.taglib

import grails.converters.XML
import grails.plugin.formfields.FormFieldsTagLib
import grails.plugin.formfields.FormFieldsTemplateService
import grails.plugin.formfields.mock.Author
import grails.plugin.formfields.mock.Book
import grails.plugin.formfields.mock.Person
import grails.plugin.formfields.mock.Product
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Issue

@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/45')
class DisplayTagSpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

	FormFieldsTemplateService mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

	def setupSpec() {
		mockDomains(Person, Author, Book, Product)
	}

	def setup() {
        mockFormFieldsTemplateService.getTemplateFor('wrapper') >> "wrapper"
        mockFormFieldsTemplateService.getTemplateFor('widget') >> "widget"
        mockFormFieldsTemplateService.getTemplateFor('displayWrapper') >> "displayWrapper"
        mockFormFieldsTemplateService.getTemplateFor('displayWidget') >> "displayWidget"

		tagLib.formFieldsTemplateService = mockFormFieldsTemplateService
	}

	void 'renders all properties as list'() {
		when:"A list is rendered"
			def result = applyTemplate('<f:display bean="personInstance" />', [personInstance: personInstance])

		then:"The result is a list"
			result.contains '<ol class="property-list person">'
			result.contains '<span id="gender-label" class="property-label">Gender</span>'
			result.contains '<div class="property-value" aria-labelledby="gender-label">Male</div>'
	}

	void 'display tag allows to specify order'() {
		when:"A list is rendered"
		def result = applyTemplate('<f:display bean="personInstance" order="salutation,name,gender"/>', [personInstance: personInstance])
		def ol = XML.parse(result)

		then:
		ol.li.span.collect {it.text().trim()} == ["Salutation", "Name", "Gender"]
		ol.li.div.collect {it.text().trim()} == ["", "Bart Simpson", "Male"]
	}

	void "display tag allows to specify the except"() {
		when:
		def result = applyTemplate('<f:display bean="personInstance" except="salutation,grailsDeveloper,picture,anotherPicture,password,dateOfBirth,emails"/>', [personInstance: personInstance])
		def ol = XML.parse(result)

		then:
		ol.li.span.collect {it.text().trim()}.sort() == ["Address", "Biography", "Gender", "Minor", "Name"]
		ol.li.div.collect {it.text().trim()}.sort() == ["", "Bart Simpson", "CitySpringfieldCountryUSAStreet94 Evergreen Terrace", "Male", "True"]
	}



	void 'renders value using g:fieldValue if no template is present'() {
		expect:
		applyTemplate('<f:display bean="personInstance" property="name"/>', [personInstance: personInstance]) == personInstance.name
	}

	void 'renders value that is an association but not to a grom entity'() {
		expect:
		applyTemplate('<f:display bean="personInstance" property="emails"/>', [personInstance: personInstance]) == personInstance.emails.toString()
	}

	void 'renders boolean values using g:formatBoolean'() {
		given:
		messageSource.addMessage('default.boolean.true', request.locale, 'Yes')

		expect:
		applyTemplate('<f:display bean="personInstance" property="minor"/>', [personInstance: personInstance]) == 'Yes'
	}

	void 'renders date values using g:formatDate'() {
		expect:
		applyTemplate('<f:display bean="personInstance" property="dateOfBirth"/>', [personInstance: personInstance]) ==~ /1987-04-19 00:00:00 [A-Z]{3,4}/
	}

	void 'displays using template if one is present'() {
		given:
		views["/_fields/default/_displayWrapper.gsp"] = '<dt>${label}</dt><dd>${value}</dd>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', _, null) >> [path: '/_fields/default/displayWrapper']

		expect:
		applyTemplate('<f:display bean="personInstance" property="name"/>', [personInstance: personInstance]) == '<dt>Name</dt><dd>Bart Simpson</dd>'
	}

	void 'supports theme for templates'() {
		given:
		views["/_fields/_themes/test/default/_displayWrapper.gsp"] = '<theme>${label}-${value}</theme>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', _, "test") >> [path: '/_fields/_themes/test/default/displayWrapper']

		expect:
		applyTemplate('<f:display bean="personInstance" property="name" theme="test"/>',
				[personInstance: personInstance]) == '<theme>Name-Bart Simpson</theme>'
	}

	void 'displayStyle attribute allows to use a specific template'() {
		given:
		views["/_fields/default/_display.gsp"] = '<dt>${label}</dt><dd>${value}</dd>'
		views["/_fields/default/_display-custom.gsp"] = 'Custom: ${value}'
		views["/_fields/_themes/test/default/_display.gsp"] = 'theme ${value}</dd>'
		views["/_fields/_themes/test/default/_display-custom.gsp"] = 'theme Custom: ${value}'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget',_, null) >> [path: '/_fields/default/display']
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget-custom',_, null) >> [path: '/_fields/default/display-custom']
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget',_, "test") >> [path: '/_fields/_themes/test/default/display']
		mockFormFieldsTemplateService.findTemplate(_, 'displayWidget-custom',_, "test") >> [path: '/_fields/_themes/test/default/display-custom']

		expect: "'default' displayStyle uses 'display' template"
		applyTemplate('<f:display bean="personInstance" property="name" displayStyle="default"/>', [personInstance: personInstance]) == '<dt>Name</dt><dd>Bart Simpson</dd>'

		and: "'custom' displayStyle uses 'display-custom' template"
		applyTemplate('<f:display bean="personInstance" property="name" displayStyle="custom"/>', [personInstance: personInstance]) == 'Custom: Bart Simpson'

		and: "supports theme"
		applyTemplate('<f:display bean="personInstance" property="name" displayStyle="default" theme="test"/>', [personInstance: personInstance]) == 'theme Bart Simpson</dd>'
		applyTemplate('<f:display bean="personInstance" property="name" displayStyle="custom" theme="test"/>', [personInstance: personInstance]) == 'theme Custom: Bart Simpson'
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/88')
	void 'display tag will use body for rendering value'() {
		given:
		views["/_fields/default/_displayWrapper.gsp"] = '<dt>${label}</dt><dd>${value}</dd>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']

		expect:
		applyTemplate('<f:display bean="personInstance" property="name">${value.reverse()}</f:display>', [personInstance: personInstance]) == '<dt>Name</dt><dd>nospmiS traB</dd>'
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/192')
	void 'display tag will use body for rendering value2'() {
		given:
		views["/_fields/default/_displayWrapper.gsp"] = '<dt>${label}</dt><dd>${value}</dd>'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']

		expect:
		applyTemplate('<f:display bean="personInstance" property="gender">${value.name()}</f:display>', [personInstance: personInstance]) == '<dt>Gender</dt><dd>Male</dd>'
	}

    @Issue('https://github.com/grails-fields-plugin/grails-fields/issues/135')
    void 'numeric properties are not converted to Strings in display template'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '<dt>${label}</dt><dd>${value}</dd>'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']

        expect:
        def expectedDisplayedPrice = productInstance.netPrice.round(1)
        applyTemplate('<f:display bean="productInstance" property="netPrice">${g.formatNumber(number: value, maxFractionDigits: 1)}</f:display>',
                [productInstance: productInstance]) == "<dt>Net Price</dt><dd>$expectedDisplayedPrice</dd>"
    }

    void 'can nest f:display inside f:with'() {
        expect:
        applyTemplate('<f:with bean="personInstance"><f:display property="name"/></f:with>', [personInstance: personInstance]) == personInstance.name
    }

    @Issue('https://github.com/grails-fields-plugin/grails-fields/issues/160')
    void 'renders transients using g:fieldValue'() {
        expect:
        applyTemplate('<f:display bean="personInstance" property="transientText"/>', [personInstance: personInstance]) == personInstance.transientText
    }

	void 'renders all embedded components properties'() {
		when: "display an embedded address"
		def result= applyTemplate('<f:display bean="personInstance" property="address"/>', [personInstance: personInstance])

		then: "the result contains all embedded address properties"
		result.contains('<ol class="property-list address">')
		result.contains('<span id="street-label" class="property-label">Street</span>')
		result.contains('<div class="property-value" aria-labelledby="street-label">94 Evergreen Terrace</div>')
		result.contains('<div class="property-value" aria-labelledby="city-label">Springfield</div>')
		result.contains('<div class="property-value" aria-labelledby="country-label">USA</div>')
	}

	void 'renders many-side associations as a list of links'() {
		given:
		def book1 = new Book(title: 'book 1')
		def book2 = new Book(title: 'book 2')
		def author = new Author().addToBooks(book1).addToBooks(book2)

		when:
		def result = applyTemplate('<f:display bean="author"/>', [author: author])

		then:
		result.contains('<div class="property-value" aria-labelledby="books-label"><ul><li><a href="/book/show">book 1</a></li><li><a href="/book/show">book 2</a></li></ul></div>')
	}

	void 'renders one-side associations as a link'() {
		given:
		def book=new Book(title: 'the title')
		new Author(name: 'Bart Simpson').addToBooks(book)

		when:
		def result = applyTemplate('<f:display bean="book"/>', [book: book])

		then:
		result.contains('<div class="property-value" aria-labelledby="author-label"><a href="/author/show">Bart Simpson</a></div>')
	}


    void 'render field template with the input inside of it'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '<dt>${label}</dt><dd>${widget}</dd>'
        views["/_fields/default/_widget.gsp"] = '${value.reverse()}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
        mockFormFieldsTemplateService.findTemplate(_, 'widget', null, null) >> [path: '/_fields/default/widget']

        expect:
        applyTemplate('<f:field bean="personInstance" property="name"/>', [personInstance: personInstance]) == '<dt>Name</dt><dd>nospmiS traB</dd>'
    }

    void 'render field template with the templates attribute'() {
        given:
        views["/_fields/widget/_wrapper.gsp"] = '<dt>WIDGET:</dt><dd>${widget}</dd>'
        views["/_fields/widget/_widget.gsp"] = '${value.reverse()}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'wrapper', "widget", null) >> [path: '/_fields/widget/wrapper']
        mockFormFieldsTemplateService.findTemplate(_, 'widget', "widget", null) >> [path: '/_fields/widget/widget']

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" templates="widget"/>', [personInstance: personInstance]) == '<dt>WIDGET:</dt><dd>nospmiS traB</dd>'
    }

	void 'render field template with the templates and theme attribute'() {
		given:
		views["/_fields/_themes/test/widget/_wrapper.gsp"] = '<dt>theme:</dt><dd>${widget}</dd>'
		views["/_fields/_themes/test/widget/_widget.gsp"] = 'theme ${value.reverse()}'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'wrapper', "widget", "test") >> [path: '/_fields/_themes/test/widget/wrapper']
		mockFormFieldsTemplateService.findTemplate(_, 'widget', "widget", "test") >> [path: '/_fields/_themes/test/widget/widget']

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" templates="widget" theme="test"/>',
				[personInstance: personInstance]) == '<dt>theme:</dt><dd>theme nospmiS traB</dd>'
	}


	void 'render field template with the field attribute'() {
        given:
        views["/_fields/widget/_wrapper.gsp"] = '<dt>WIDGET:</dt><dd>${widget}</dd>'
        views["/_fields/default/_widget.gsp"] = '${value}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'wrapper', "widget", null) >> [path: '/_fields/widget/wrapper']
        mockFormFieldsTemplateService.findTemplate(_, 'widget', null, null) >> [path: '/_fields/default/widget']

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" wrapper="widget"/>', [personInstance: personInstance]) == '<dt>WIDGET:</dt><dd>Bart Simpson</dd>'
    }

    void 'render field template with the widget attribute'() {
        given:
        views["/_fields/default/_wrapper.gsp"] = '<dt>${label}</dt><dd>${widget}</dd>'
        views["/_fields/widget/_widget.gsp"] = '${value.reverse()}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, null) >> [path: '/_fields/default/wrapper']
        mockFormFieldsTemplateService.findTemplate(_, 'widget', "widget", null) >> [path: '/_fields/widget/widget']

        expect:
        applyTemplate('<f:field bean="personInstance" property="name" widget="widget"/>', [personInstance: personInstance]) == '<dt>Name</dt><dd>nospmiS traB</dd>'
    }

	void 'render field template with the widget and theme attribute'() {
		given:
		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'theme <dt>${label}</dt><dd>${widget}</dd>'
		views["/_fields/_themes/test/widget/_widget.gsp"] = 'theme ${value.reverse()}'

		and:
		mockFormFieldsTemplateService.findTemplate(_, 'wrapper', null, "test") >> [path: '/_fields/_themes/test/default/wrapper']
		mockFormFieldsTemplateService.findTemplate(_, 'widget', "widget", "test") >> [path: '/_fields/_themes/test/widget/widget']

		expect:
		applyTemplate('<f:field bean="personInstance" property="name" widget="widget" theme="test"/>',
				[personInstance: personInstance]) == 'theme <dt>Name</dt><dd>theme nospmiS traB</dd>'
	}

	void 'render display template with the displayWidget inside of it'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '<dt>${label}</dt><dd>${widget}</dd>'
        views["/_fields/default/_displayWidget.gsp"] = '${value.reverse()}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']
        mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/default/displayWidget']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name"/>', [personInstance: personInstance]) == '<dt>Name</dt><dd>nospmiS traB</dd>'
    }

    void 'render display template with the templates attribute'() {
        given:
        views["/_fields/widget/_displayWrapper.gsp"] = '<dt>WIDGET:</dt><dd>${widget}</dd>'
        views["/_fields/widget/_displayWidget.gsp"] = '${value.reverse()}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', "widget", null) >> [path: '/_fields/widget/displayWrapper']
        mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', "widget", null) >> [path: '/_fields/widget/displayWidget']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" templates="widget"/>', [personInstance: personInstance]) == '<dt>WIDGET:</dt><dd>nospmiS traB</dd>'
    }

    void 'render display template with the field attribute'() {
        given:
        views["/_fields/widget/_displayWrapper.gsp"] = '<dt>WIDGET:</dt><dd>${widget}</dd>'
        views["/_fields/default/_displayWidget.gsp"] = '${value}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', "widget", null) >> [path: '/_fields/widget/displayWrapper']
        mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', null, null) >> [path: '/_fields/default/displayWidget']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" wrapper="widget"/>', [personInstance: personInstance]) == '<dt>WIDGET:</dt><dd>Bart Simpson</dd>'
    }

    void 'render display template with the widget attribute'() {
        given:
        views["/_fields/default/_displayWrapper.gsp"] = '<dt>${label}</dt><dd>${widget}</dd>'
        views["/_fields/widget/_displayWidget.gsp"] = '${value.reverse()}'

        and:
        mockFormFieldsTemplateService.findTemplate(_, 'displayWrapper', null, null) >> [path: '/_fields/default/displayWrapper']
        mockFormFieldsTemplateService.findTemplate(_, 'displayWidget', "widget", null) >> [path: '/_fields/widget/displayWidget']

        expect:
        applyTemplate('<f:display bean="personInstance" property="name" widget="widget"/>', [personInstance: personInstance]) == '<dt>Name</dt><dd>nospmiS traB</dd>'
    }

    void 'f:display escapes one property to avoid XSS atacks'() {
        expect:
        applyTemplate('<f:display bean="productInstance" property="name"/>', [productInstance: productInstance]) == "&lt;script&gt;alert(&#39;XSS&#39;);&lt;/script&gt;"
    }

    void 'f:display escapes values when rendering all properties to avoid XSS atacks'() {
        when:
        def result = applyTemplate('<f:display bean="productInstance" />', [productInstance: productInstance])

        then:
        result.contains('&lt;script&gt;alert(&#39;XSS&#39;);&lt;/script&gt;')
        !result.contains("<script>alert('XSS');</script>")
    }

}
