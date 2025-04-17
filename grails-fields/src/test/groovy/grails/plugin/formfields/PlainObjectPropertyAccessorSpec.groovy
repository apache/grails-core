package grails.plugin.formfields

import grails.plugin.formfields.mock.*

class PlainObjectPropertyAccessorSpec extends BuildsAccessorFactory {

	void setupSpec() {
		mockDomain(Person)
	}

	void 'resolves a basic property'() {
		given:
		TestBean bean = new TestBean()
		bean.stringProperty = 'a string value'

		and:
		def propertyAccessor = factory.accessorFor(bean, 'stringProperty')

		expect:
		propertyAccessor.value == bean.stringProperty
		propertyAccessor.rootBeanType == TestBean
		propertyAccessor.beanType == TestBean
		propertyAccessor.pathFromRoot == 'stringProperty'
		propertyAccessor.propertyName == 'stringProperty'
		propertyAccessor.propertyType == String
		propertyAccessor.errors == []
		!propertyAccessor.invalid
		propertyAccessor.required
		propertyAccessor.constraints.blank
		!propertyAccessor.constraints.nullable
	}

	void 'resolves a basic property even when its value is null'() {
		given:
		TestBean bean = new TestBean()

		and:
		def propertyAccessor = factory.accessorFor(bean, 'stringProperty')

		expect:
		propertyAccessor.value == null
		propertyAccessor.rootBeanType == TestBean
		propertyAccessor.beanType == TestBean
		propertyAccessor.pathFromRoot == 'stringProperty'
		propertyAccessor.propertyName == 'stringProperty'
		propertyAccessor.propertyType == String
	}

	void 'resolves a simple indexed property'() {
		given:
		TestBean bean = new TestBean()
		bean.listOfStrings = ['correct', 'horse', 'battery', 'staple']

		and:
		def propertyAccessor = factory.accessorFor(bean, 'listOfStrings[1]')

		expect:
		propertyAccessor.value == bean.listOfStrings[1]
		propertyAccessor.rootBeanType == TestBean
		propertyAccessor.beanType == TestBean
		propertyAccessor.pathFromRoot == "listOfStrings[1]"
		propertyAccessor.propertyName == "listOfStrings"
		propertyAccessor.propertyType == String
	}

	void 'resolves a simple indexed property when the value at that index is null'() {
		given:
		TestBean bean = new TestBean()
		bean.listOfStrings = ['correct', null, 'battery', 'staple']

		and:
		def propertyAccessor = factory.accessorFor(bean, 'listOfStrings[1]')

		expect:
		propertyAccessor.value == null
		propertyAccessor.pathFromRoot == "listOfStrings[1]"
		propertyAccessor.propertyName == "listOfStrings"
		propertyAccessor.propertyType == String
	}

	void 'resolves an untyped simple indexed property when the value at that index is null'() {
		given:
		TestBean bean = new TestBean()
		bean.untypedList = ['correct', null, 'battery', 'staple']

		and:
		def propertyAccessor = factory.accessorFor(bean, 'untypedList[1]')

		expect:
		propertyAccessor.value == null
		propertyAccessor.pathFromRoot == "untypedList[1]"
		propertyAccessor.propertyName == "untypedList"
		propertyAccessor.propertyType == Object
	}

	void 'resolves a simple mapped property'() {
		given:
		TestBean bean = new TestBean()
		def today = new Date()
		bean.mapOfDates = [yesterday: today -1, today: today, tomorrow: today + 1]

		and:
		def propertyAccessor = factory.accessorFor(bean, 'mapOfDates[today]')

		expect:
		propertyAccessor.value == bean.mapOfDates['today']
		propertyAccessor.rootBeanType == TestBean
		propertyAccessor.beanType == TestBean
		propertyAccessor.pathFromRoot == "mapOfDates[today]"
		propertyAccessor.propertyName == "mapOfDates"
		propertyAccessor.propertyType == Date
	}

	void 'resolves a simple mapped property when the value at that index is null'() {
		given:
		TestBean bean = new TestBean()
		def today = new Date()
		bean.mapOfDates = [yesterday: today -1, today: null, tomorrow: today + 1]

		and:
		def propertyAccessor = factory.accessorFor(bean, 'mapOfDates[today]')

		expect:
		propertyAccessor.value == null
		propertyAccessor.pathFromRoot == "mapOfDates[today]"
		propertyAccessor.propertyName == "mapOfDates"
		propertyAccessor.propertyType == Date
	}

	void 'resolves an untyped simple mapped property when the value at that index is null'() {
		given:
		TestBean bean = new TestBean()
		def today = new Date()
		bean.untypedMap = [yesterday: today -1, today: null, tomorrow: today + 1]

		and:
		def propertyAccessor = factory.accessorFor(bean, 'untypedMap[today]')

		expect:
		propertyAccessor.value == null
		propertyAccessor.pathFromRoot == "untypedMap[today]"
		propertyAccessor.propertyName == "untypedMap"
		propertyAccessor.propertyType == Object
	}

	void 'resolves an enum property'() {
		given:
		TestBean bean = new TestBean()
		bean.enumProperty = Gender.Male

		and:
		def propertyAccessor = factory.accessorFor(bean, 'enumProperty')

		expect:
		propertyAccessor.value == bean.enumProperty
		propertyAccessor.pathFromRoot == 'enumProperty'
		propertyAccessor.propertyName == 'enumProperty'
		propertyAccessor.propertyType == Gender
	}

	void 'resolves a nested property'() {
		given:
		TestBean bean = new TestBean()
		bean.person = new Person(name: 'Bart Simpson')

		and:
		def propertyAccessor = factory.accessorFor(bean, 'person.name')

		expect:
		propertyAccessor.value == bean.person.name
		propertyAccessor.rootBeanType == TestBean
		propertyAccessor.beanType == Person
		propertyAccessor.pathFromRoot == "person.name"
		propertyAccessor.propertyName == "name"
		propertyAccessor.propertyType == String
	}

	void 'resolves a nested property even when the intervening object is null'() {
		given:
		TestBean bean = new TestBean()

		and:
		def propertyAccessor = factory.accessorFor(bean, 'person.name')

		expect:
		propertyAccessor.value == null
		propertyAccessor.rootBeanType == TestBean
		propertyAccessor.beanType == Person
		propertyAccessor.pathFromRoot == "person.name"
		propertyAccessor.propertyName == "name"
		propertyAccessor.propertyType == String
	}

	void 'if a nested property is a domain class then it is handled as one'() {
		given:
		TestBean bean = new TestBean()
		bean.person = new Person(name: 'Bart Simpson')

		and:
		def propertyAccessor = factory.accessorFor(bean, 'person.name')

		expect:
		propertyAccessor.domainProperty
		propertyAccessor.domainProperty.name == 'name'
	}

}

class TestBean {
	String stringProperty
	List<String> listOfStrings
	List untypedList
	Map<String, Date> mapOfDates
	Map untypedMap
	Gender enumProperty
	Person person
}