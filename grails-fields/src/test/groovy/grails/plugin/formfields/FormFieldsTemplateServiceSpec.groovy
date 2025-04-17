package grails.plugin.formfields

import grails.plugin.formfields.mock.*
import grails.testing.services.ServiceUnitTest
import org.grails.web.servlet.mvc.GrailsWebRequest
import spock.lang.*

class FormFieldsTemplateServiceSpec extends BuildsAccessorFactory implements ServiceUnitTest<FormFieldsTemplateService> {

	Person personInstance
	Employee employeeInstance

	void setup() {
		webRequest.controllerName = 'foo'
		webRequest.actionName = 'bar'

		personInstance = new Person(name: "Bart Simpson", password: "bartman", gender: Gender.Male, dateOfBirth: new Date(87, 3, 19), minor: true, picture: "good looking".bytes)
		personInstance.address = new Address(street: "94 Evergreen Terrace", city: "Springfield", country: "USA")

		employeeInstance = new Employee(salutation: Salutation.MR, name: "Waylon Smithers", salary: 10)
	}

	void cleanup() {
		views.clear()
		applicationContext.getBean("groovyPagesTemplateEngine").clearPageCache()
		applicationContext.getBean("groovyPagesTemplateRenderer").clearCache()
	}

	void 'uses default template when no others exist'() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)
		then:

		template.path == '/_fields/default/wrapper'
		template.plugin == null
		render(template: template.path) == 'DEFAULT FIELD TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/default/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME DEFAULT FIELD TEMPLATE'

	}

	void "resolves template for property type"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/string/_wrapper.gsp"] = 'PROPERTY TYPE TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/string/_wrapper.gsp"] = 'THEME PROPERTY TYPE TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null)

		then:
		template.path == '/_fields/string/wrapper'
		template.plugin == null
		render(template: template.path) == 'PROPERTY TYPE TEMPLATE'

		when:"Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/string/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME PROPERTY TYPE TEMPLATE'

	}

	void "resolves template for password field"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/string/_wrapper.gsp"] = 'PROPERTY TYPE TEMPLATE'
		views["/_fields/password/_wrapper.gsp"] = 'WIDGET TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/string/_wrapper.gsp"] = 'THEME PROPERTY TYPE TEMPLATE'
		views["/_fields/_themes/test/password/_wrapper.gsp"] = 'THEME WIDGET TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'password')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', "password")

		then:
		template.path == '/_fields/password/wrapper'
		template.plugin == null
		render(template: template.path) == 'WIDGET TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', "password", "test")

		then:
		template.path == '/_fields/_themes/test/password/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME WIDGET TEMPLATE'
	}


	void "resolves template for password widget"() {
		given:
		views["/_fields/default/_widget.gsp"] = 'DEFAULT FIELD WIDGET'
		views["/_fields/string/_widget.gsp"] = 'PROPERTY TYPE WIDGET'
		views["/_fields/password/_widget.gsp"] = 'INPUT WIDGET'

		views["/_fields/_themes/test/default/_widget.gsp"] = 'THEME DEFAULT FIELD WIDGET'
		views["/_fields/_themes/test/string/_widget.gsp"] = 'THEME PROPERTY TYPE WIDGET'
		views["/_fields/_themes/test/password/_widget.gsp"] = 'THEME INPUT WIDGET'

		and:
		def property = factory.accessorFor(personInstance, 'password')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'widget', "password")

		then:
		template.path == '/_fields/password/widget'
		template.plugin == null
		render(template: template.path) == 'INPUT WIDGET'

		when: "Theme is specified"
		template = service.findTemplate(property, 'widget', "password", "test")

		then:
		template.path == '/_fields/_themes/test/password/widget'
		template.plugin == null
		render(template: template.path) == 'THEME INPUT WIDGET'
	}

    void "resolves template for password display"() {
		given:
		views["/_fields/default/_displayWrapper.gsp"] = 'DEFAULT DISPLAY TEMPLATE'
		views["/_fields/string/_displayWrapper.gsp"] = 'PROPERTY TYPE TEMPLATE'
		views["/_fields/password/_displayWrapper.gsp"] = 'OUTPUT TEMPLATE'

		views["/_fields/_themes/test/default/_displayWrapper.gsp"] = 'THEME DEFAULT DISPLAY TEMPLATE'
		views["/_fields/_themes/test/string/_displayWrapper.gsp"] = 'THEME PROPERTY TYPE TEMPLATE'
		views["/_fields/_themes/test/password/_displayWrapper.gsp"] = 'THEME OUTPUT TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'password')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'displayWrapper', "password")

		then:
		template.path == '/_fields/password/displayWrapper'
		template.plugin == null
		render(template: template.path) == 'OUTPUT TEMPLATE'

		when:"Theme is specified"
		template = service.findTemplate(property, 'displayWrapper', "password", "test")

		then:
		template.path == '/_fields/_themes/test/password/displayWrapper'
		template.plugin == null
		render(template: template.path) == 'THEME OUTPUT TEMPLATE'

	}

    void "resolves template for display password widget"() {
		given:
		views["/_fields/default/_displayWidget.gsp"] = 'DEFAULT FIELD FOR DISPLAY'
		views["/_fields/string/_displayWidget.gsp"] = 'PROPERTY TYPE FOR DISPLAY'
		views["/_fields/password/_displayWidget.gsp"] = 'OUTPUT FOR DISPLAY'

		views["/_fields/_themes/test/default/_displayWidget.gsp"] = 'THEME DEFAULT FIELD FOR DISPLAY'
		views["/_fields/_themes/test/string/_displayWidget.gsp"] = 'THEME PROPERTY TYPE FOR DISPLAY'
		views["/_fields/_themes/test/password/_displayWidget.gsp"] = 'THEME OUTPUT FOR DISPLAY'

		and:
		def property = factory.accessorFor(personInstance, 'password')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'displayWidget', "password")

		then:
		template.path == '/_fields/password/displayWidget'
		template.plugin == null
		render(template: template.path) == 'OUTPUT FOR DISPLAY'

		when: "Theme is specified"
		template = service.findTemplate(property, 'displayWidget', "password", "test")

		then:
		template.path == '/_fields/_themes/test/password/displayWidget'
		template.plugin == null
		render(template: template.path) == 'THEME OUTPUT FOR DISPLAY'

	}

	void "resolves wrapper template for textarea widget constraint"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/string/_wrapper.gsp"] = 'PROPERTY TYPE TEMPLATE'
		views["/_fields/textarea/_wrapper.gsp"] = 'WIDGET TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/string/_wrapper.gsp"] = 'THEME PROPERTY TYPE TEMPLATE'
		views["/_fields/_themes/test/textarea/_wrapper.gsp"] = 'THEME WIDGET TEMPLATE'


		and:
		def property = factory.accessorFor(personInstance, 'biography')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', "biography")

		then:
		template.path == '/_fields/textarea/wrapper'
		template.plugin == null
		render(template: template.path) == 'WIDGET TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', "biography", "test")

		then:
		template.path == '/_fields/_themes/test/textarea/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME WIDGET TEMPLATE'
	}


    void "resolves widget template for textarea widget constraint"() {
		given:
		views["/_fields/default/_widget.gsp"] = 'DEFAULT FIELD WIDGET'
		views["/_fields/string/_widget.gsp"] = 'PROPERTY TYPE WIDGET'
		views["/_fields/textarea/_widget.gsp"] = 'INPUT WIDGET'

		views["/_fields/_themes/test/default/_widget.gsp"] = 'THEME DEFAULT FIELD WIDGET'
		views["/_fields/_themes/test/string/_widget.gsp"] = 'THEME PROPERTY TYPE WIDGET'
		views["/_fields/_themes/test/textarea/_widget.gsp"] = 'THEME INPUT WIDGET'

		and:
		def property = factory.accessorFor(personInstance, 'biography')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'widget', "biography")

		then:
		template.path == '/_fields/textarea/widget'
		template.plugin == null
		render(template: template.path) == 'INPUT WIDGET'

		when: "Theme is specified"
		template = service.findTemplate(property, 'widget', "biography", "test")

		then:
		template.path == '/_fields/_themes/test/textarea/widget'
		template.plugin == null
		render(template: template.path) == 'THEME INPUT WIDGET'

	}


	void "resolves display wrapper template for textarea widget constraint"() {
		given:
		views["/_fields/default/_displayWrapper.gsp"] = 'DEFAULT DISPLAY TEMPLATE'
		views["/_fields/string/_displayWrapper.gsp"] = 'PROPERTY TYPE TEMPLATE'
		views["/_fields/textarea/_displayWrapper.gsp"] = 'OUTPUT TEMPLATE'

		views["/_fields/_themes/test/default/_displayWrapper.gsp"] = 'THEME DEFAULT DISPLAY TEMPLATE'
		views["/_fields/_themes/test/string/_displayWrapper.gsp"] = 'THEME PROPERTY TYPE TEMPLATE'
		views["/_fields/_themes/test/textarea/_displayWrapper.gsp"] = 'THEME OUTPUT TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'biography')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'displayWrapper', "biography")

		then:
		template.path == '/_fields/textarea/displayWrapper'
		template.plugin == null
		render(template: template.path) == 'OUTPUT TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'displayWrapper', "biography", "test")

		then:
		template.path == '/_fields/_themes/test/textarea/displayWrapper'
		template.plugin == null
		render(template: template.path) == 'THEME OUTPUT TEMPLATE'

	}

    void "resolves display widget template for textarea widget constraint"() {
		given:
		views["/_fields/default/_displayWidget.gsp"] = 'DEFAULT FIELD FOR DISPLAY'
		views["/_fields/string/_displayWidget.gsp"] = 'PROPERTY TYPE FOR DISPLAY'
		views["/_fields/textarea/_displayWidget.gsp"] = 'OUTPUT FOR DISPLAY'

		views["/_fields/_themes/test/default/_displayWidget.gsp"] = 'THEME DEFAULT FIELD FOR DISPLAY'
		views["/_fields/_themes/test/string/_displayWidget.gsp"] = 'THEME PROPERTY TYPE FOR DISPLAY'
		views["/_fields/_themes/test/textarea/_displayWidget.gsp"] = 'THEME OUTPUT FOR DISPLAY'

		and:
		def property = factory.accessorFor(personInstance, 'biography')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'displayWidget', "biography", null)

		then:
		template.path == '/_fields/textarea/displayWidget'
		template.plugin == null
		render(template: template.path) == 'OUTPUT FOR DISPLAY'

		when: "Theme is specified"
		template = service.findTemplate(property, 'displayWidget', "biography", "test")

		then:
		template.path == '/_fields/_themes/test/textarea/displayWidget'
		template.plugin == null
		render(template: template.path) == 'THEME OUTPUT FOR DISPLAY'
	}

	void "resolves template for domain class property"() {
		setup:
		setupCommonTemplates()
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null)

		then:
		template.path == '/_fields/person/name/wrapper'
		template.plugin == null
		render(template: template.path) == 'CLASS AND PROPERTY TEMPLATE'

		when: "theme is specified but theme template does not exist"
		template = service.findTemplate(property, 'wrapper', null, "missing")

		then: "it fall backs to default templates"
		template.path == '/_fields/person/name/wrapper'
		template.plugin == null
		render(template: template.path) == 'CLASS AND PROPERTY TEMPLATE'

		when: "Theme is specified and theme template exists"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/person/name/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME CLASS AND PROPERTY TEMPLATE'
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/88')
    void "resolves template from controller views directory"() {
		given:
		setupCommonTemplates()
		views["/$webRequest.controllerName/_wrapper.gsp"] = 'CONTROLLER DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER DEFAULT TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null)

		then:
		template.path == "/$webRequest.controllerName/wrapper"
		template.plugin == null
		render(template: template.path) == 'CONTROLLER DEFAULT TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerName/_themes/test/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME CONTROLLER DEFAULT TEMPLATE'
	}

//	@IgnoreIf({ GrailsApplicationAttributes.metaClass.hasProperty(GrailsApplicationAttributes, "CONTROLLER_NAMESPACE_ATTRIBUTE") == false })
	@IgnoreIf({ GrailsWebRequest.metaClass.respondsTo(GrailsWebRequest, "getControllerNamespace").size() == 0 })
	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/168')
	void "resolves template from namespaced controller views directory"() {
		given:
		webRequest.setControllerNamespace('namespace')
		setupCommonTemplates()
		views["/$webRequest.controllerNamespace/$webRequest.controllerName/_wrapper.gsp"] = 'CONTROLLER DEFAULT TEMPLATE'
		views["/$webRequest.controllerNamespace/$webRequest.controllerName/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER DEFAULT TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null)

		then:
		template.path == "/$webRequest.controllerNamespace/$webRequest.controllerName/wrapper"
		template.plugin == null
		render(template: template.path) == 'CONTROLLER DEFAULT TEMPLATE'

		when: "Theme is not specified but theme template does not exist"
		template = service.findTemplate(property, 'wrapper', "missing")

		then: "Fallbacks to default template"
		template.path == "/$webRequest.controllerNamespace/$webRequest.controllerName/wrapper"
		template.plugin == null
		render(template: template.path) == 'CONTROLLER DEFAULT TEMPLATE'

		when: "Theme is specified and exists"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerNamespace/$webRequest.controllerName/_themes/test/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME CONTROLLER DEFAULT TEMPLATE'
	}



	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/39')
	void "resolves template by property type from controller views directory"() {
		given:
		setupCommonTemplates()

		views["/$webRequest.controllerName/_wrapper.gsp"] = 'CONTROLLER DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/string/_wrapper.gsp"] = 'CONTROLLER FIELD TYPE TEMPLATE'

		views["/$webRequest.controllerName/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/_themes/test/string/_wrapper.gsp"] = 'THEME CONTROLLER FIELD TYPE TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == "/$webRequest.controllerName/string/wrapper"
		template.plugin == null
		render(template: template.path) == 'CONTROLLER FIELD TYPE TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerName/_themes/test/string/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME CONTROLLER FIELD TYPE TEMPLATE'
	}

	void "resolves template by property name from controller views directory"() {
		given:
		setupCommonTemplates()

        views["/$webRequest.controllerName/_wrapper.gsp"] = 'CONTROLLER DEFAULT TEMPLATE'
        views["/$webRequest.controllerName/string/_wrapper.gsp"] = 'CONTROLLER FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/name/_wrapper.gsp"] = 'CONTROLLER FIELD NAME TEMPLATE'

		views["/$webRequest.controllerName/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/_themes/test/string/_wrapper.gsp"] = 'THEME CONTROLLER FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/name/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER FIELD NAME TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == "/$webRequest.controllerName/name/wrapper"
		template.plugin == null
		render(template: template.path) == 'CONTROLLER FIELD NAME TEMPLATE'

		when:"Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerName/name/_themes/test/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME CONTROLLER FIELD NAME TEMPLATE'

	}


	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/88')
    void "resolves template by from controller and action views directory"() {
        given:
		setupCommonTemplates()

        views["/$webRequest.controllerName/_wrapper.gsp"] = 'CONTROLLER DEFAULT TEMPLATE'
        views["/$webRequest.controllerName/string/_wrapper.gsp"] = 'CONTROLLER FIELD TYPE TEMPLATE'
        views["/$webRequest.controllerName/name/_wrapper.gsp"] = 'CONTROLLER FIELD NAME TEMPLATE'
        views["/$webRequest.controllerName/$webRequest.actionName/_wrapper.gsp"] = 'ACTION DEFAULT TEMPLATE'

		views["/$webRequest.controllerName/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/_themes/test/string/_wrapper.gsp"] = 'THEME CONTROLLER FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/_themes/test/name/_wrapper.gsp"] = 'THEME CONTROLLER FIELD NAME TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/_themes/test/_wrapper.gsp"] = 'THEME ACTION DEFAULT TEMPLATE'

		and:
        def property = factory.accessorFor(personInstance, 'name')

        when: "Theme is not specified"
        Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
        template.path == "/$webRequest.controllerName/$webRequest.actionName/wrapper"
        template.plugin == null
        render(template: template.path) == 'ACTION DEFAULT TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerName/$webRequest.actionName/_themes/test/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME ACTION DEFAULT TEMPLATE'
    }


    @Issue('https://github.com/grails-fields-plugin/grails-fields/issues/39')
	void "resolves template by property type from controller and action views directory"() {
		given:
		setupCommonTemplates()

        views["/$webRequest.controllerName/_wrapper.gsp"] = 'CONTROLLER DEFAULT TEMPLATE'
        views["/$webRequest.controllerName/string/_wrapper.gsp"] = 'CONTROLLER FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/name/_wrapper.gsp"] = 'CONTROLLER FIELD NAME TEMPLATE'
        views["/$webRequest.controllerName/$webRequest.actionName/_wrapper.gsp"] = 'ACTION DEFAULT TEMPLATE'
        views["/$webRequest.controllerName/$webRequest.actionName/string/_wrapper.gsp"] = 'ACTION FIELD TYPE TEMPLATE'

		views["/$webRequest.controllerName/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/_themes/test/string/_wrapper.gsp"] = 'THEME CONTROLLER FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/name/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER FIELD NAME TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/_themes/test/_wrapper.gsp"] = 'THEME ACTION DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/_themes/test/string/_wrapper.gsp"] = 'THEME ACTION FIELD TYPE TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == "/$webRequest.controllerName/$webRequest.actionName/string/wrapper"
		template.plugin == null
		render(template: template.path) == 'ACTION FIELD TYPE TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerName/$webRequest.actionName/_themes/test/string/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME ACTION FIELD TYPE TEMPLATE'
	}


	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/33')
	void "resolves template by property name from controller and action views directory"() {
		given:
		setupCommonTemplates()

        views["/$webRequest.controllerName/_wrapper.gsp"] = 'CONTROLLER DEFAULT TEMPLATE'
        views["/$webRequest.controllerName/string/_wrapper.gsp"] = 'CONTROLLER FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/name/_wrapper.gsp"] = 'CONTROLLER FIELD NAME TEMPLATE'
        views["/$webRequest.controllerName/$webRequest.actionName/_wrapper.gsp"] = 'ACTION DEFAULT TEMPLATE'
        views["/$webRequest.controllerName/$webRequest.actionName/string/_wrapper.gsp"] = 'ACTION FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/name/_wrapper.gsp"] = 'ACTION FIELD NAME TEMPLATE'

		views["/$webRequest.controllerName/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/_themes/test/string/_wrapper.gsp"] = 'THEME CONTROLLER FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/name/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER FIELD NAME TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/_themes/test/_wrapper.gsp"] = 'THEME ACTION DEFAULT TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/_themes/test/string/_wrapper.gsp"] = 'THEME ACTION FIELD TYPE TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/name/_themes/test/_wrapper.gsp"] = 'THEME ACTION FIELD NAME TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == "/$webRequest.controllerName/$webRequest.actionName/name/wrapper"
		template.plugin == null
		render(template: template.path) == 'ACTION FIELD NAME TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerName/$webRequest.actionName/name/_themes/test/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME ACTION FIELD NAME TEMPLATE'

	}

	void "does not use controller if there isn't one in the current request"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/name/_wrapper.gsp"] = 'STRANGE TEMPLATE'

		and:
		webRequest.controllerName = null

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when:
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == '/_fields/default/wrapper'
		template.plugin == null
		render(template: template.path) == 'DEFAULT FIELD TEMPLATE'
	}

	@PendingFeature(reason = 'until domain class inheritance works in Groovy 4')
	def "resolves template for superclass property"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/person/name/_wrapper.gsp"] = 'SUPERCLASS TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/person/name/_wrapper.gsp"] = 'THEME SUPERCLASS TEMPLATE'

		and:
		def property = factory.accessorFor(employeeInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == '/_fields/person/name/wrapper'
		template.plugin == null
		render(template: template.path) == 'SUPERCLASS TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/person/name/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME SUPERCLASS TEMPLATE'
	}


	def "subclass property template overrides superclass property template"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/person/name/_wrapper.gsp"] = 'SUPERCLASS TEMPLATE'
		views["/_fields/employee/name/_wrapper.gsp"] = 'SUBCLASS TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/person/name/_wrapper.gsp"] = 'THEME SUPERCLASS TEMPLATE'
		views["/_fields/_themes/test/employee/name/_wrapper.gsp"] = 'THEME SUBCLASS TEMPLATE'

		and:
		def property = factory.accessorFor(employeeInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == '/_fields/employee/name/wrapper'
		template.plugin == null
		render(template: template.path) == 'SUBCLASS TEMPLATE'

		when: "theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/employee/name/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME SUBCLASS TEMPLATE'

		when: "theme is specified and theme template not found"
		template = service.findTemplate(property, 'wrapper', null, "missing")

		then:
		template.path == '/_fields/employee/name/wrapper'
		template.plugin == null
		render(template: template.path) == 'SUBCLASS TEMPLATE'
	}


	def "property template gets resolved by the property's superclass"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/enum/_wrapper.gsp"] = 'GENERIC ENUM TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/enum/_wrapper.gsp"] = 'THEME GENERIC ENUM TEMPLATE'

		and:
		def property = factory.accessorFor(employeeInstance, 'salutation')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == '/_fields/enum/wrapper'
		template.plugin == null
		render(template: template.path) == 'GENERIC ENUM TEMPLATE'

		when: "theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/enum/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME GENERIC ENUM TEMPLATE'

		when: "theme template not found"
		template = service.findTemplate(property, 'wrapper', null, "missing")

		then:
		template.path == '/_fields/enum/wrapper'
		template.plugin == null
		render(template: template.path) == 'GENERIC ENUM TEMPLATE'
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/19')
	def "property template gets resolved by the property's interface"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/charSequence/_wrapper.gsp"] = 'INTERFACE TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/charSequence/_wrapper.gsp"] = 'THEME INTERFACE TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == '/_fields/charSequence/wrapper'
		template.plugin == null
		render(template: template.path) == 'INTERFACE TEMPLATE'

		when: "theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/charSequence/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME INTERFACE TEMPLATE'
	}

	def "property template overrides property's superclass template"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/enum/_wrapper.gsp"] = 'ENUM TEMPLATE'
		views["/_fields/salutation/_wrapper.gsp"] = 'SALUTATION TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/enum/_wrapper.gsp"] = 'THEME ENUM TEMPLATE'
		views["/_fields/_themes/test/salutation/_wrapper.gsp"] = 'THEME SALUTATION TEMPLATE'

		and:
		def property = factory.accessorFor(employeeInstance, 'salutation')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == '/_fields/salutation/wrapper'
		template.plugin == null
		render(template: template.path) == 'SALUTATION TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/salutation/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME SALUTATION TEMPLATE'

	}

	void "resolves template for embedded class property"() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/string/_wrapper.gsp"] = 'PROPERTY TYPE TEMPLATE'
		views["/_fields/address/city/_wrapper.gsp"] = 'CLASS AND PROPERTY TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/string/_wrapper.gsp"] = 'THEME PROPERTY TYPE TEMPLATE'
		views["/_fields/_themes/test/address/city/_wrapper.gsp"] = 'THEME CLASS AND PROPERTY TEMPLATE'

		and:
		def property = factory.accessorFor(personInstance, 'address.city')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == '/_fields/address/city/wrapper'
		template.plugin == null
		render(template: template.path) == 'CLASS AND PROPERTY TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/address/city/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME CLASS AND PROPERTY TEMPLATE'

	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/pull/16')
	void 'resolves template without a bean just based on property path'() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'

		and:
		def property = factory.accessorFor(null, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == '/_fields/default/wrapper'
		template.plugin == null
		render(template: template.path) == 'DEFAULT FIELD TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/default/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME DEFAULT FIELD TEMPLATE'

	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/pull/16')
	void 'resolves controller template without a bean just based on property path'() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/$webRequest.controllerName/name/_wrapper.gsp"] = 'CONTROLLER FIELD TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/$webRequest.controllerName/name/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER FIELD TEMPLATE'

		and:
		def property = factory.accessorFor(null, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == "/$webRequest.controllerName/name/wrapper"
		template.plugin == null
		render(template: template.path) == 'CONTROLLER FIELD TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerName/name/_themes/test/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME CONTROLLER FIELD TEMPLATE'
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/pull/33')
	void 'resolves controller action template without a bean just based on property path'() {
		given:
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/$webRequest.controllerName/name/_wrapper.gsp"] = 'CONTROLLER FIELD TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/name/_wrapper.gsp"] = 'ACTION FIELD TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/$webRequest.controllerName/name/_themes/test/_wrapper.gsp"] = 'THEME CONTROLLER FIELD TEMPLATE'
		views["/$webRequest.controllerName/$webRequest.actionName/name/_themes/test/_wrapper.gsp"] = 'THEME ACTION FIELD TEMPLATE'

		and:
		def property = factory.accessorFor(null, 'name')

		when: "Theme is not specified"
		Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
		template.path == "/$webRequest.controllerName/$webRequest.actionName/name/wrapper"
		template.plugin == null
		render(template: template.path) == 'ACTION FIELD TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == "/$webRequest.controllerName/$webRequest.actionName/name/_themes/test/wrapper"
		template.plugin == null
		render(template: template.path) == 'THEME ACTION FIELD TEMPLATE'
	}

    @Issue('https://github.com/gpc/grails-fields/issues/144')
    void "resolves template for property type object Byte array"() {
        given:
        views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
        views["/_fields/byteArray/_wrapper.gsp"] = 'PROPERTY BYTE ARRAY TYPE TEMPLATE'
        views["/_fields/byte;/_wrapper.gsp"] = 'PROPERTY WRONG BYTE ARRAY TYPE TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/byteArray/_wrapper.gsp"] = 'THEME PROPERTY BYTE ARRAY TYPE TEMPLATE'
		views["/_fields/_themes/test/byte;/_wrapper.gsp"] = 'THEME PROPERTY WRONG BYTE ARRAY TYPE TEMPLATE'

		and:
        def property = factory.accessorFor(personInstance, 'picture')

        when: "Theme is not specified"
        Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
        template.path == '/_fields/byteArray/wrapper'
        template.plugin == null
        render(template: template.path) == 'PROPERTY BYTE ARRAY TYPE TEMPLATE'

		when: "Theme is specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/byteArray/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME PROPERTY BYTE ARRAY TYPE TEMPLATE'

	}

    @Issue('https://github.com/grails-fields-plugin/grails-fields/pull/164')
    void "does not fail if constrained property is null"() {
        expect:
        null == service.getWidget(null, Object)
    }

    @Issue('https://github.com/gpc/grails-fields/issues/183')
    void "resolves template for property type simple type byte array"() {
        given:
        views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
        views["/_fields/byteArray/_wrapper.gsp"] = 'PROPERTY SIMPLE BYTE ARRAY TYPE TEMPLATE'
        views["/_fields/byte;/_wrapper.gsp"] = 'PROPERTY WRONG BYTE ARRAY TYPE TEMPLATE'
        views["/_fields/[B/_wrapper.gsp"] = 'PROPERTY WRONG BYTE ARRAY TYPE TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/byteArray/_wrapper.gsp"] = 'THEME PROPERTY SIMPLE BYTE ARRAY TYPE TEMPLATE'
		views["/_fields/_themes/test/byte;/_wrapper.gsp"] = 'THEME PROPERTY WRONG BYTE ARRAY TYPE TEMPLATE'
		views["/_fields/_themes/test/[B/_wrapper.gsp"] = 'THEME PROPERTY WRONG BYTE ARRAY TYPE TEMPLATE'

		and:
        def property = factory.accessorFor(personInstance, 'anotherPicture')

        when: "Theme is not specified"
        Map template = service.findTemplate(property, 'wrapper', null, null)

		then:
        template.path == '/_fields/byteArray/wrapper'
        template.plugin == null
        render(template: template.path) == 'PROPERTY SIMPLE BYTE ARRAY TYPE TEMPLATE'

		when: "Theme is  specified"
		template = service.findTemplate(property, 'wrapper', null, "test")

		then:
		template.path == '/_fields/_themes/test/byteArray/wrapper'
		template.plugin == null
		render(template: template.path) == 'THEME PROPERTY SIMPLE BYTE ARRAY TYPE TEMPLATE'

	}

	private void setupCommonTemplates() {
		views["/_fields/default/_wrapper.gsp"] = 'DEFAULT FIELD TEMPLATE'
		views["/_fields/string/_wrapper.gsp"] = 'PROPERTY TYPE TEMPLATE'
		views["/_fields/person/name/_wrapper.gsp"] = 'CLASS AND PROPERTY TEMPLATE'

		views["/_fields/_themes/test/default/_wrapper.gsp"] = 'THEME DEFAULT FIELD TEMPLATE'
		views["/_fields/_themes/test/string/_wrapper.gsp"] = 'THEME PROPERTY TYPE TEMPLATE'
		views["/_fields/_themes/test/person/name/_wrapper.gsp"] = 'THEME CLASS AND PROPERTY TEMPLATE'
	}


}
