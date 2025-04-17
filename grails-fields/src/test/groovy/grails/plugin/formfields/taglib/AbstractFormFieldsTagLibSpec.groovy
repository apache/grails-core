package grails.plugin.formfields.taglib

import grails.core.support.proxy.DefaultProxyHandler
import grails.plugin.formfields.BeanPropertyAccessorFactory
import grails.plugin.formfields.FieldsGrailsPlugin
import grails.testing.gorm.DataTest
import grails.testing.web.GrailsWebUnitTest
import org.grails.datastore.mapping.model.MappingContext
import org.grails.plugins.web.DefaultGrailsTagDateHelper
import org.grails.scaffolding.model.DomainModelServiceImpl
import org.grails.scaffolding.model.property.DomainPropertyFactory
import org.grails.scaffolding.model.property.DomainPropertyFactoryImpl
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.springframework.context.support.StaticMessageSource
import spock.lang.Specification
import grails.plugin.formfields.mock.*

abstract class AbstractFormFieldsTagLibSpec extends Specification implements GrailsWebUnitTest, DataTest {

	Person personInstance
	Cyborg cyborgInstance
    Product productInstance

	def setup() {
		personInstance = new Person(name: "Bart Simpson", password: "bartman", gender: Gender.Male, dateOfBirth: new Date(87, 3, 19), minor: true)
		personInstance.address = new Address(street: "94 Evergreen Terrace", city: "Springfield", country: "USA")
		personInstance.emails = [home: "bart@thesimpsons.net", school: "bart.simpson@springfieldelementary.edu"]
        productInstance = new Product(netPrice: 12.33, name: "<script>alert('XSS');</script>")
		cyborgInstance = new Cyborg(name: "Hal", password: "monolith", gender: null)
	}

	def cleanup() {
		views.clear()
		applicationContext.getBean("groovyPagesTemplateEngine").clearPageCache()
		applicationContext.getBean("groovyPagesTemplateRenderer").clearCache()

		(messageSource as StaticMessageSource).messageMap.clear() // bit of a hack but messages don't get torn down otherwise
	}

	void setupSpec() {
		defineBeans { ->
			grailsTagDateHelper(DefaultGrailsTagDateHelper)
			//constraintsEvaluator(DefaultConstraintEvaluator)
			def dpf = new DomainPropertyFactoryImpl(grailsDomainClassMappingContext: applicationContext.getBean("grailsDomainClassMappingContext", MappingContext), trimStrings: true, convertEmptyStringsToNull: true)
			fieldsDomainPropertyFactory(InstanceFactoryBean, dpf, DomainPropertyFactory)

			domainModelService(DomainModelServiceImpl) {
				domainPropertyFactory = ref('fieldsDomainPropertyFactory')
			}
			beanPropertyAccessorFactory(BeanPropertyAccessorFactory) {
				constraintsEvaluator = ref(FieldsGrailsPlugin.CONSTRAINTS_EVALULATOR_BEAN_NAME)
				proxyHandler = new DefaultProxyHandler()
				grailsDomainClassMappingContext = ref("grailsDomainClassMappingContext")
				fieldsDomainPropertyFactory = ref('fieldsDomainPropertyFactory')
			}
		}
	}

	protected void mockEmbeddedSitemeshLayout(taglib) {
	 	taglib.metaClass.applyLayout = { Map attrs, Closure body ->
	 		if (attrs.name == '_fields/embedded') {
	 			out << '<fieldset class="embedded ' << attrs.params.type << '">'
	 			out << '<legend>' << attrs.params.legend << '</legend>'
	 			out << body()
	 			out << '</fieldset>'
	 		}
	 		null // stops default return
	 	}
	}

}
