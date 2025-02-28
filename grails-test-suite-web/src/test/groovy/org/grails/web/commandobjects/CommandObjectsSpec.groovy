package org.grails.web.commandobjects

import grails.artefact.Artefact
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.validation.Validateable
import spock.lang.Issue
import spock.lang.Specification

class CommandObjectsSpec extends Specification implements ControllerUnitTest<TestController>, DataTest {

    Closure doWithSpring() {{ ->
        theAnswer(Integer, 42)
    }}

    Closure doWithConfig() {{ config ->
        config['grails.gorm.default.constraints'] = {
            isProg inList: ['Emerson', 'Lake', 'Palmer']
        }
    }}

    void "Test command object with date binding"() {
        setup:
        def expectedCalendar = Calendar.instance
        expectedCalendar.clear()
        expectedCalendar.set Calendar.DAY_OF_MONTH, 3
        expectedCalendar.set Calendar.MONTH, Calendar.MAY
        expectedCalendar.set Calendar.YEAR, 1973
        def expectedDate = expectedCalendar.time


        when:
        controller.params.birthday = "struct"
        controller.params.birthday_day = "03"
        controller.params.birthday_month = "05"
        controller.params.birthday_year = "1973"
        def model = controller.methodActionWithDate()
        def birthday = model.command?.birthday

        then:
        model.command
        !model.command.hasErrors()
        birthday
        expectedDate == birthday
    }

    void 'Test that rejected binding value survives validation'() {
        when:
        controller.params.width = 'some bad value'
        controller.params.height = 42
        def model = controller.methodActionWithWidgetCommand()
        def widget = model.widget
        def err = widget.errors

        then:
        widget.height == 42
        widget.width == null
        widget.errors.errorCount == 2
        widget.errors.getFieldError('width').rejectedValue == 'some bad value'
    }

    @Issue('GRAILS-11218')
    void 'Test nested parameter names that match the command object parameter name'() {
        when: 'the top level of nested request parameters match the name of the command object class name minus the word "Command"'
        params.'widget.height' = 8
        def model = controller.methodActionWithWidgetCommand()

        then: 'everything below the top level of the request parameter name is used for binding to that command object'
        model.widget.height == 8
    }

    void 'Test non validateable command object'() {
        when:
        controller.params.name = 'Beardfish'
        def model = controller.methodActionWithNonValidateableCommandObject()

        then:
        model.commandObject.name == 'Beardfish'

    }

    void 'Test binding to a command object setter property'() {
        when:
        controller.params.someValue = 'My Value'
        def model = controller.methodActionWithSomeCommand()

        then:
        model.commandObject.someValue == 'My Value'
    }

    void "Test binding to multiple command objects"() {
        when:
        controller.params.name = 'Emerson'
        def model = controller.methodActionWithMultipleCommandObjects()

        then:
        model.person
        model.artist
        model.artist.name == 'Emerson'
        model.person.name == 'Emerson'

    }

    @Issue('GRAILS-11218')
    void "Test binding to multiple command objects with param name prefixes"() {
        when:
        controller.params.person = [name: 'Emerson']
        controller.params.artist = [name: 'Lake']
        def model = controller.methodActionWithMultipleCommandObjects()

        then:
        model.person
        model.artist
        model.artist.name == 'Lake'
        model.person.name == 'Emerson'

    }

    void "Test clearErrors"() {
        when:
        def model = controller.methodActionWithArtist()

        then:
        model.artist
        model.artist.name == null
        model.artist.hasErrors()
        model.artist.errors.errorCount == 1

        when:
        model.artist.clearErrors()

        then:
        !model.artist.hasErrors()
        model.artist.errors.errorCount == 0
    }

    void 'Test beforeValidate gets invoked'() {
        when:
        def model = controller.methodAction()
        def person = model.person

        then:
        1 == person.beforeValidateCounter
    }

    void 'Test constraints property'() {
        when:
        def model = controller.methodAction()
        def person = model.person
        def constrainedProperties = person.constraintsMap
        def nameConstrainedProperty = constrainedProperties.name
        def matchesProperty = nameConstrainedProperty.matches

        then:
        /[A-Z]+/ == matchesProperty
    }

    void "Test command object gets autowired"() {
        when:
        def model = controller.methodAction()

        then:
        model.person.theAnswer == 42

    }

    void 'Test bindable command object constraint'() {
        when:
        controller.params.name = 'JFK'
        controller.params.city = 'STL'
        controller.params.state = 'Missouri'
        def model = controller.methodAction()

        then:
        !model.person.hasErrors()
        model.person.name == 'JFK'
        model.person.state == 'Missouri'
        model.person.city == null
    }

    void "Test validation"() {
        when:
        controller.params.name = 'JFK'
        def model = controller.methodAction()

        then:
        !model.person.hasErrors()
        model.person.name == 'JFK'

        when:
        controller.params.name = 'Maynard'
        model = controller.methodAction()

        then:
        model.person.hasErrors()
        model.person.name == 'Maynard'
    }

    void "Test validation with inherited constraints"() {

        when:
        controller.params.name = 'Emerson'
        controller.params.bandName = 'Emerson Lake and Palmer'
        def model = controller.methodActionWithArtistSubclass()

        then:
        model.artist
        model.artist.name == 'Emerson'
        model.artist.bandName == 'Emerson Lake and Palmer'
        !model.artist.hasErrors()

        when:
        controller.params.clear()
        model = controller.methodActionWithArtistSubclass()

        then:
        model.artist
        model.artist.hasErrors()
        model.artist.errors.errorCount == 2
    }

    void "Test validation with shared constraints"() {

        when:
        controller.params.name = 'Emerson'
        def model = controller.methodActionWithArtist()

        then:
        model.artist
        !model.artist.hasErrors()

        when:
        controller.params.name = 'Hendrix'
        model = controller.methodActionWithArtist()

        then:
        model.artist
        model.artist.hasErrors()
    }

    void 'Test command object that is a precompiled @Validatable'() {
        when:
        def model = controller.methodActionWithValidateableParam()

        then:
        model.commandObject.hasErrors()

        when:
        controller.params.name = 'mixedCase'
        model = controller.methodActionWithValidateableParam()

        then:
        model.commandObject.hasErrors()

        when:
        controller.params.name = 'UPPERCASE'
        model = controller.methodActionWithValidateableParam()

        then:
        !model.commandObject.hasErrors()
    }

    void "Test a command object that does not have a validate method at compile time but does at runtime"() {
        when:
        def model = controller.methodActionWithNonValidateableCommandObjectWithAValidateMethod()

        then:
        0 == model.co.validationCounter

        when:
        ClassWithNoValidateMethod.metaClass.validate = { -> ++ delegate.validationCounter }
        model = controller.methodActionWithNonValidateableCommandObjectWithAValidateMethod()

        then:
        1 == model.co.validationCounter

    }

    @Issue('grails/grails-core#9172')
    void 'test that a non domain class command object with an id and version is not treated as a domain class'() {
        when:
        params.name = 'Jeffrey'
        params.id = '42'
        request.method = 'PUT'
        def model = controller.nonDomainCommandObject()
        def commandObject = model.commandObject

        then:
        commandObject?.name == 'Jeffrey'
        commandObject.id == 42l
        commandObject.version == null

    }

    @Issue('https://github.com/grails/grails-core/issues/11432')
    void 'Test binding to a generic-based field'() {
        when:
        params.firstName = 'Douglas'
        params.lastName = 'Mendes'
        def model = controller.methodActionWithGenericBasedCommand()
        def commandObject = model.commandObject

        then:
        commandObject.firstName == 'Douglas'
        commandObject.lastName == 'Mendes'
    }

    @Issue('https://github.com/grails/grails-core/issues/13945')
    void "calling actions involving inherited command objects - child command"() {
        given:
        params.testId = 1
        params.myId = 3
        params.pId = 2

        when:
        def model = controller.zMethodTakingChild()
        def commandObject = model.commandObject

        then:
        commandObject.testId == 1
        commandObject.myId == 3
        model.pId == 2
    }

    @Issue('https://github.com/grails/grails-core/issues/13945')
    void "calling actions involving inherited command objects - parent command"() {
        given:
        params.testId = 1

        when:
        def model = controller.methodTakingParent()
        def commandObject = model.commandObject

        then:
        commandObject.testId == 1
    }
}

@Artefact('Controller')
class TestController {
    def methodAction(Person p) {
        [person: p]
    }

    def methodActionWithDate(DateComamndObject co) {
        [command: co]
    }

    private seeIssue13486() {
        // the presence of this local variable could break
        // the compile-time generated no-arg methodActionWithDate()
        // see https://github.com/grails/grails-core/issues/13486
        String co
    }

    def methodActionWithArtist(Artist a) {
        [artist: a]
    }

    def methodActionWithArtistSubclass(ArtistSubclass a) {
        [artist: a]
    }

    def methodActionWithMultipleCommandObjects(Person person, Artist artist)  {
        [person: person, artist: artist]
    }

    def methodActionWithSomeCommand(SomeCommand co) {
        [commandObject: co]
    }

    def methodActionWithWidgetCommand(WidgetCommand widget) {
        [widget: widget]
    }

    def methodActionWithValidateableParam(SomeValidateableClass svc) {
        [commandObject: svc]
    }

    def methodActionWithNonValidateableCommandObjectWithAValidateMethod(ClassWithNoValidateMethod co) {
        [co: co]
    }

    def methodActionWithNonValidateableCommandObject(NonValidateableCommand co) {
        [commandObject: co]
    }

    def nonDomainCommandObject(NonDomainCommandObjectWithIdAndVersion co) {
        [commandObject: co]
    }

    def methodActionWithGenericBasedCommand(ConcreteGenericBased co) {
        [commandObject: co]
    }

    def zMethodTakingChild(ChildCommand command, long pId) {
        [commandObject: command, pId: pId]
    }

    def methodTakingParent(ParentCommand command) {
        [commandObject: command, pId: 2]
    }
}


class ParentCommand implements Validateable {
    int testId
}

class ChildCommand extends ParentCommand {
    int myId
}

class DateComamndObject {
    Date birthday
}

class WidgetCommand {
    Integer width
    Integer height

    static constraints = { height range: 1..10 }
}

class SomeCommand {
    private String someFieldWithNoSetter

    void setSomeValue(String val) {
        someFieldWithNoSetter = val
    }

    String getSomeValue() {
        someFieldWithNoSetter
    }
}

class Artist implements Validateable {
    String name
    static constraints = { name shared: 'isProg' }
}

class ArtistSubclass extends Artist {
    String bandName
    static constraints = { bandName matches: /[A-Z].*/ }
}

abstract class MyAbstractController {
    def index = { [name: 'Abstract Parent Controller'] }
}

class SubClassController extends MyAbstractController {
    def index = { [name: 'Subclass Controller'] }
}

class Person {
    String name
    def theAnswer
    def beforeValidateCounter = 0

    String city
    String state

    def beforeValidate() {
        ++beforeValidateCounter
    }

    static constraints = {
        name matches: /[A-Z]+/
        bindable: false
        city nullable: true, bindable: false
        state nullable: true
    }
}

class NonDomainCommandObjectWithIdAndVersion {
    Long id
    Long version
    String name
}

abstract class WithGeneric<G> implements Validateable {
    String firstName
    G lastName
}

class ConcreteGenericBased extends WithGeneric<String> {
}
