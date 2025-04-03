package grails.validation

import spock.lang.Issue
import spock.lang.Specification

class ValidateableMockSpec extends Specification {

    @Issue('apache/grails-core#9761')
    void 'ensure command is mocked properly'(){
        given:
        SomeCommand command = GroovyMock()
        1 * command.validate() >> true
        1 * command.validate() >> false
        1 * command.validate() >> true
        1 * command.validate(_ as List) >> true
        1 * command.validate(_ as Map) >> false
        1 * command.validate(_ as List, _ as Map) >> true

        expect:
        command.validate()
        !command.validate()
        command.validate()
        command.validate([])
        !command.validate([:])
        command.validate([], [:])
    }
}

class SomeCommand implements Validateable {}
