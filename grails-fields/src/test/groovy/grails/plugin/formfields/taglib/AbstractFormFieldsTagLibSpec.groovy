/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

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

	protected void mockEmbeddedGrailsLayout(taglib) {
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
