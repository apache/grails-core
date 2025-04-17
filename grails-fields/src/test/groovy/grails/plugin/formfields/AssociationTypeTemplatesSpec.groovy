package grails.plugin.formfields

import grails.plugin.formfields.mock.*
import grails.testing.services.ServiceUnitTest
import spock.lang.*

@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/39')
class AssociationTypeTemplatesSpec extends BuildsAccessorFactory implements ServiceUnitTest<FormFieldsTemplateService> {

	Author authorInstance

	void setupSpec() {
		mockDomains(Book, Author)
	}

	void setup() {
		authorInstance = new Author(name: 'William Gibson')
		authorInstance.addToBooks new Book(title: 'Pattern Recognition')
		authorInstance.addToBooks new Book(title: 'Spook Country')
		authorInstance.addToBooks new Book(title: 'Zero History')
		authorInstance.save(failOnError: true, flush: true)
	}

	void 'resolves template for association type'() {
		given:
		views['/_fields/default/_wrapper.gsp'] = 'DEFAULT FIELD TEMPLATE'
		views['/_fields/list/_wrapper.gsp'] = 'PROPERTY TYPE TEMPLATE'
		views['/_fields/oneToMany/_wrapper.gsp'] = 'ASSOCIATION TYPE TEMPLATE'

		and:
		def property = factory.accessorFor(authorInstance, 'books')

		expect:
		def template = service.findTemplate(property, 'wrapper', null, null)
		render(template: template.path) == 'ASSOCIATION TYPE TEMPLATE'
	}

	void 'theme: resolves template for association type'() {
		given:
		views['/_fields/_themes/test/default/_wrapper.gsp'] = 'THEME DEFAULT FIELD TEMPLATE'
		views['/_fields/_themes/test/list/_wrapper.gsp'] = 'THEME PROPERTY TYPE TEMPLATE'
		views['/_fields/_themes/test/oneToMany/_wrapper.gsp'] = 'THEME ASSOCIATION TYPE TEMPLATE'

		and:
		def property = factory.accessorFor(authorInstance, 'books')

		expect:
		def template = service.findTemplate(property, 'wrapper', null, "test")
		render(template: template.path) == 'THEME ASSOCIATION TYPE TEMPLATE'
	}

	void 'property name trumps association type'() {
		given:
		views['/_fields/default/_wrapper.gsp'] = 'DEFAULT FIELD TEMPLATE'
		views['/_fields/list/_wrapper.gsp'] = 'PROPERTY TYPE TEMPLATE'
		views['/_fields/oneToMany/_wrapper.gsp'] = 'ASSOCIATION TYPE TEMPLATE'
		views['/_fields/author/books/_wrapper.gsp'] = 'PROPERTY NAME TEMPLATE'

		and:
		def property = factory.accessorFor(authorInstance, 'books')

		expect:
		def template = service.findTemplate(property, 'wrapper', null, null)
		render(template: template.path) == 'PROPERTY NAME TEMPLATE'
	}

	void 'theme: property name trumps association type'() {
		given:
		views['/_fields/_themes/test/default/_wrapper.gsp'] = 'DEFAULT FIELD TEMPLATE'
		views['/_fields/_themes/test/list/_wrapper.gsp'] = 'PROPERTY TYPE TEMPLATE'
		views['/_fields/_themes/test/oneToMany/_wrapper.gsp'] = 'ASSOCIATION TYPE TEMPLATE'
		views['/_fields/_themes/test/author/books/_wrapper.gsp'] = 'THEME PROPERTY NAME TEMPLATE'

		and:
		def property = factory.accessorFor(authorInstance, 'books')

		expect:
		def template = service.findTemplate(property, 'wrapper', null, "test")
		render(template: template.path) == 'THEME PROPERTY NAME TEMPLATE'
	}

}
