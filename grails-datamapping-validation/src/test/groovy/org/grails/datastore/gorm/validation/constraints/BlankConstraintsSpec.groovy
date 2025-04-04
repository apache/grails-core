package org.grails.datastore.gorm.validation.constraints

import org.grails.datastore.mapping.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.springframework.validation.Errors
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BlankConstraintsSpec extends Specification {

    @Shared
    def constraint = new BlankConstraint(ClassWithSomeProperty, 'someProperty', false, null)

    @Shared
    def obj = new ClassWithSomeProperty()

    @Unroll
    @Issue('grails/grails-core#10846')
    void 'hasErrors() should return #shouldTriggerError for input ["#inputValue"]'() {
        setup:
        Errors errors = new ValidationErrors(obj)
        constraint.validate(obj, inputValue, errors)

        expect:
        errors.hasErrors() == shouldTriggerError

        where:
        inputValue   | shouldTriggerError
        ''           | true
        '  '         | true
        'some value' | false
    }
}


class ClassWithSomeProperty {
    String someProperty
}
