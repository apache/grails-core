package grails.plugin.formfields


import grails.plugin.formfields.mock.Gender
import grails.plugin.formfields.mock.Person
import grails.validation.Validateable
import spock.lang.Issue
import spock.lang.Unroll

@Unroll
class CommandPropertyAccessorSpec extends BuildsAccessorFactory {

	void setupSpec() {
		mockDomain(Person)
	}

	void 'resolves a basic property'() {
		given:
		TestCommand command = new TestCommand()
		command.password = 'correct horse battery staple'

		and:
		def propertyAccessor = factory.accessorFor(command, 'password')

		expect:
		propertyAccessor.value == command.password
		propertyAccessor.rootBeanType == TestCommand
		propertyAccessor.beanType == TestCommand
		propertyAccessor.pathFromRoot == "password"
		propertyAccessor.propertyName == "password"
		propertyAccessor.propertyType == String
		!propertyAccessor.invalid
		propertyAccessor.required
		!propertyAccessor.constraints.blank
		propertyAccessor.constraints.password
	}

	void 'property of #type.simpleName is nullable'() {
		given:
		def command = type.newInstance()

		and:
		def propertyAccessor = factory.accessorFor(command, 'stringProperty')

		expect:
		!propertyAccessor.constraints.nullable
		propertyAccessor.required
		!propertyAccessor.invalid

		where:
		type << [TestCommand, UnconstrainedCommand]
	}

	void 'resolves a basic property even when its value is null'() {
		given:
		TestCommand command = new TestCommand()

		and:
		def propertyAccessor = factory.accessorFor(command, 'password')

		expect:
		propertyAccessor.value == null
		propertyAccessor.rootBeanType == TestCommand
		propertyAccessor.beanType == TestCommand
		propertyAccessor.pathFromRoot == "password"
		propertyAccessor.propertyName == "password"
		propertyAccessor.propertyType == String
		!propertyAccessor.constraints.blank
		propertyAccessor.constraints.password
	}

	void 'resolves a simple indexed property'() {
		given:
		TestCommand command = new TestCommand()
		command.listOfStrings = ['correct', 'horse', 'battery', 'staple']

		and:
		def propertyAccessor = factory.accessorFor(command, 'listOfStrings[1]')

		expect:
		propertyAccessor.value == command.listOfStrings[1]
		propertyAccessor.rootBeanType == TestCommand
		propertyAccessor.beanType == TestCommand
		propertyAccessor.pathFromRoot == "listOfStrings[1]"
		propertyAccessor.propertyName == "listOfStrings"
		propertyAccessor.propertyType == String
	}

	void 'resolves a simple indexed property when the value at that index is null'() {
		given:
		TestCommand command = new TestCommand()
		command.listOfStrings = ['correct', null, 'battery', 'staple']

		and:
		def propertyAccessor = factory.accessorFor(command, 'listOfStrings[1]')

		expect:
		propertyAccessor.value == null
		propertyAccessor.pathFromRoot == "listOfStrings[1]"
		propertyAccessor.propertyName == "listOfStrings"
		propertyAccessor.propertyType == String
	}

	void 'resolves an untyped simple indexed property when the value at that index is null'() {
		given:
		TestCommand command = new TestCommand()
		command.untypedList = ['correct', null, 'battery', 'staple']

		and:
		def propertyAccessor = factory.accessorFor(command, 'untypedList[1]')

		expect:
		propertyAccessor.value == null
		propertyAccessor.pathFromRoot == "untypedList[1]"
		propertyAccessor.propertyName == "untypedList"
		propertyAccessor.propertyType == Object
	}

	void 'resolves a simple mapped property'() {
		given:
		TestCommand command = new TestCommand()
		def today = new Date()
		command.mapOfDates = [yesterday: today - 1, today: today, tomorrow: today + 1]

		and:
		def propertyAccessor = factory.accessorFor(command, 'mapOfDates[today]')

		expect:
		propertyAccessor.value == command.mapOfDates['today']
		propertyAccessor.rootBeanType == TestCommand
		propertyAccessor.beanType == TestCommand
		propertyAccessor.pathFromRoot == "mapOfDates[today]"
		propertyAccessor.propertyName == "mapOfDates"
		propertyAccessor.propertyType == Date
	}

	void 'resolves a simple mapped property when the value at that index is null'() {
		given:
		TestCommand command = new TestCommand()
		def today = new Date()
		command.mapOfDates = [yesterday: today - 1, today: null, tomorrow: today + 1]

		and:
		def propertyAccessor = factory.accessorFor(command, 'mapOfDates[today]')

		expect:
		propertyAccessor.value == null
		propertyAccessor.pathFromRoot == "mapOfDates[today]"
		propertyAccessor.propertyName == "mapOfDates"
		propertyAccessor.propertyType == Date
	}

	void 'resolves an untyped simple mapped property when the value at that index is null'() {
		given:
		TestCommand command = new TestCommand()
		def today = new Date()
		command.untypedMap = [yesterday: today - 1, today: null, tomorrow: today + 1]

		and:
		def propertyAccessor = factory.accessorFor(command, 'untypedMap[today]')

		expect:
		propertyAccessor.value == null
		propertyAccessor.pathFromRoot == "untypedMap[today]"
		propertyAccessor.propertyName == "untypedMap"
		propertyAccessor.propertyType == Object
	}

	void 'resolves an enum property'() {
		given:
		TestCommand command = new TestCommand()
		command.gender = Gender.Male

		and:
		def propertyAccessor = factory.accessorFor(command, 'gender')

		expect:
		propertyAccessor.value == command.gender
		propertyAccessor.pathFromRoot == "gender"
		propertyAccessor.propertyName == "gender"
		propertyAccessor.propertyType == Gender
	}

	void 'resolves a nested property'() {
		given:
		TestCommand command = new TestCommand()
		command.person = new Person(name: 'Bart Simpson')

		and:
		def propertyAccessor = factory.accessorFor(command, 'person.name')

		expect:
		propertyAccessor.value == command.person.name
		propertyAccessor.rootBeanType == TestCommand
		propertyAccessor.beanType == Person
		propertyAccessor.pathFromRoot == "person.name"
		propertyAccessor.propertyName == "name"
		propertyAccessor.propertyType == String
	}

	void 'resolves a nested property even when the intervening object is null'() {
		given:
		TestCommand command = new TestCommand()

		and:
		def propertyAccessor = factory.accessorFor(command, 'person.name')

		expect:
		propertyAccessor.value == null
		propertyAccessor.rootBeanType == TestCommand
		propertyAccessor.beanType == Person
		propertyAccessor.pathFromRoot == "person.name"
		propertyAccessor.propertyName == "name"
		propertyAccessor.propertyType == String
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/37')
	void "resolves constraints of the '#property' property even when the intervening object is null"() {
		given:
		TestCommand command = new TestCommand()

		and:
		def propertyAccessor = factory.accessorFor(command, property)

		expect:
		propertyAccessor.isRequired() || !isRequired
		propertyAccessor.constraints?.nullable || isRequired
		propertyAccessor.constraints?.blank || isRequired

		where:
		property                      | isRequired
		'person.name'                 | true
		'person.id'                   | true
		'person.displayFalseProperty' | false
	}

	void 'if a nested property is a domain class then it is handled as one'() {
		given:
		TestCommand command = new TestCommand()
		command.person = new Person(name: 'Bart Simpson')

		and:
		def propertyAccessor = factory.accessorFor(command, 'person.name')

		expect:
		propertyAccessor.domainProperty
		propertyAccessor.domainProperty.name == 'name'
	}

	void 'constraints are defaulted for classes that have no constraints property'() {
		given:
		UnconstrainedCommand command = new UnconstrainedCommand()

		and:
		def propertyAccessor = factory.accessorFor(command, 'stringProperty')

		expect:
		propertyAccessor.constraints
		!propertyAccessor.constraints.nullable
		propertyAccessor.constraints.blank
	}

	@Issue('https://github.com/grails-fields-plugin/grails-fields/issues/218')
	void 'respect defaultNullable() when evaluating constraints of a Validateable'() {
		given:
        ValidateableCommand command = new ValidateableCommand()
        DefaultNullableValidateableCommand command2 = new DefaultNullableValidateableCommand()

		and:
		def propertyAccessor = factory.accessorFor(command, 'myNullableProperty')
		def propertyAccessor2 = factory.accessorFor(command2, 'myNullableProperty')

		expect:
		!propertyAccessor.constraints.nullable
		propertyAccessor2.constraints.nullable
	}
}

class TestCommand {

	String username
	String password
	String stringProperty
	List<String> listOfStrings
	List untypedList
	Map<String, Date> mapOfDates
	Map untypedMap
	Gender gender
	Person person

	static constraints = {
		username blank: false
		password blank: false, password: true
	}
}

class UnconstrainedCommand {
	String stringProperty
}

class ValidateableCommand implements Validateable {
    String myNullableProperty
}

class DefaultNullableValidateableCommand implements Validateable {
	String myNullableProperty

	static boolean defaultNullable() {
		return true
	}
}
