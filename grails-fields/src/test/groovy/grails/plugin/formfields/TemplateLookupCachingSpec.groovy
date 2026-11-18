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
package grails.plugin.formfields

import grails.plugin.formfields.mock.Person
import grails.plugins.GrailsPluginManager
import grails.testing.services.ServiceUnitTest
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.gsp.io.GroovyPageResourceScriptSource
import org.springframework.core.io.ByteArrayResource
import spock.lang.*

@Issue('https://github.com/grails/fields/issues/5')
class TemplateLookupCachingSpec extends BuildsAccessorFactory implements ServiceUnitTest<FormFieldsTemplateService> {

	GrailsConventionGroovyPageLocator mockGroovyPageLocator = Mock()

	@Shared
	BeanPropertyAccessorFactory beanPropertyAccessorFactory

	Person person = new Person(name: 'Bart Simpson', password: 'eatmyshorts')

	void setupSpec() {
		beanPropertyAccessorFactory = getFactory()
	}

	def setup() {
		service.pluginManager = applicationContext.getBean(GrailsPluginManager)
		service.groovyPageLocator = mockGroovyPageLocator
	}

	void 'a template is looked up the first time it is required'() {
		given:
		def templateResource = new GroovyPageResourceScriptSource('/_fields/person/name/_widget.gsp', new ByteArrayResource('PERSON NAME TEMPLATE'.getBytes('UTF-8')))

		and:
		def property = beanPropertyAccessorFactory.accessorFor(person, 'name')

		when:
		def template = service.findTemplate(property, 'input', null, null)

		then:
		template.path == '/_fields/person/name/input'

		and:
		1 * mockGroovyPageLocator.findTemplateByPath(_) >> templateResource
	}

	void 'the next time the template is cached'() {
		given:
		def templateResource = new GroovyPageResourceScriptSource('/_fields/person/name/_widget.gsp', new ByteArrayResource('PERSON NAME TEMPLATE'.getBytes('UTF-8')))

		and:
		def property = beanPropertyAccessorFactory.accessorFor(person, 'name')

		when: 'calling it the first time'
		def template = service.findTemplate(property, 'input', null, null)

		then: 'the template path is correct'
		template.path == '/_fields/person/name/input'

		and: 'the template was found by the service'
		1 * mockGroovyPageLocator.findTemplateByPath(_) >> templateResource

		when: 'calling it the second time'
		template = service.findTemplate(property, 'input', null, null)

		then: 'the template path is still correct'
		template.path == '/_fields/person/name/input'

		and: 'The mockGroovyPageLocator is only called the first time'
		0 * mockGroovyPageLocator.findTemplateByPath(_)
	}

	void 'a template for a different property is cached separately'() {
		given:
		def templateResource = new GroovyPageResourceScriptSource('/_fields/person/password/_widget.gsp', new ByteArrayResource('PERSON PASSWORD TEMPLATE'.getBytes('UTF-8')))

		and:
		def property = beanPropertyAccessorFactory.accessorFor(person, 'password')

		when:
		def template = service.findTemplate(property, 'input', null, null)

		then:
		template.path == '/_fields/person/password/input'

		and:
		1 * mockGroovyPageLocator.findTemplateByPath(_) >> templateResource
	}

	void 'a different template for the same property is cached separately'() {
		given:
		def templateResource = new GroovyPageResourceScriptSource('/_fields/person/name/_widget.gsp', new ByteArrayResource('PERSON NAME TEMPLATE 2'.getBytes('UTF-8')))

		and:
		def property = beanPropertyAccessorFactory.accessorFor(person, 'name')

		when:
		def template = service.findTemplate(property, 'field', null, null)

		then:
		template.path == '/_fields/person/name/field'

		and:
		1 * mockGroovyPageLocator.findTemplateByPath(_) >> templateResource
	}

	@Issue('https://github.com/apache/grails-core/issues/16162')
	void 'distinct accessors for the same property shape share the cache'() {
		given:
		def templateResource = new GroovyPageResourceScriptSource('/_fields/templateLookupCachingCommand/stringProperty/_widget.gsp', new ByteArrayResource('BEAN PROPERTY TEMPLATE'.getBytes('UTF-8')))

		and:
		def bean1 = new TemplateLookupCachingCommand(stringProperty: 'Bart Simpson')
		def bean2 = new TemplateLookupCachingCommand(stringProperty: 'Lisa Simpson')
		def property1 = beanPropertyAccessorFactory.accessorFor(bean1, 'stringProperty')
		def property2 = beanPropertyAccessorFactory.accessorFor(bean2, 'stringProperty')

		and: 'accessors retain distinct request beans, so accessor-keyed memoization would miss'
		assert !property1.is(property2)
		assert property1 != property2

		when: 'calling it the first time'
		def template = service.findTemplate(property1, 'input', null, null)

		then: 'the template path is correct'
		template.path == '/_fields/templateLookupCachingCommand/stringProperty/input'

		and: 'the template was found by the service'
		1 * mockGroovyPageLocator.findTemplateByPath(_) >> templateResource

		when: 'calling it with a different accessor for the same lookup shape'
		template = service.findTemplate(property2, 'input', null, null)

		then: 'the template path is still correct'
		template.path == '/_fields/templateLookupCachingCommand/stringProperty/input'

		and: 'the locator is only called for the first accessor'
		0 * mockGroovyPageLocator.findTemplateByPath(_)
	}

}

class TemplateLookupCachingCommand {
	String stringProperty
}
