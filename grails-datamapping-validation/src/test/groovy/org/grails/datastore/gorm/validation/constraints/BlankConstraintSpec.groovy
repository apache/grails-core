package org.grails.datastore.gorm.validation.constraints

import org.grails.datastore.mapping.validation.ValidationErrors
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.NoSuchMessageException
import spock.lang.Specification

import jakarta.persistence.Entity

/**
 * Created by gonmarques on 23/12/17.
 */
class BlankConstraintSpec extends Specification {

    void "Test a blank constraint that allows blank values"() {
        given: "A blank constraint that allows blank values"
        BlankConstraint blankConstraint
        blankConstraint = new BlankConstraint(Person.class, "name", true, messageSource)

        when: "The constraint validates a non-blank value"
        def name = "John"
        def nonBlankNamePerson = new Person(name: name)
        def nonBlankNamePersonErrors = new ValidationErrors(nonBlankNamePerson, Person.name)
        blankConstraint.processValidateWithVetoing(nonBlankNamePerson, name, nonBlankNamePersonErrors)

        then: "Errors is correct"
        !nonBlankNamePersonErrors.hasErrors()
        nonBlankNamePersonErrors.allErrors.size() == 0

        when: "The constraint validates a blank value"
        name = " "
        def blankNamePerson = new Person(name: name)
        def blankNamePersonErrors = new ValidationErrors(blankNamePerson, Person.name)
        blankConstraint.processValidateWithVetoing(blankNamePerson, name, blankNamePersonErrors)

        then: "Errors is correct"
        !blankNamePersonErrors.hasErrors()
        blankNamePersonErrors.allErrors.size() == 0

        when: "The constraint validates an empty value"
        name = ""
        def emptyNamePerson = new Person(name: name)
        def emptyNamePersonErrors = new ValidationErrors(emptyNamePerson, Person.name)
        blankConstraint.processValidateWithVetoing(emptyNamePerson, name, emptyNamePersonErrors)

        then: "Errors is correct"
        !emptyNamePersonErrors.hasErrors()
        emptyNamePersonErrors.allErrors.size() == 0
    }

    void "Test a blank constraint that does not allow blank values"() {
        given: "A blank constraint that does not allow blank values"
        BlankConstraint blankConstraint = new BlankConstraint(Person.class, "name", false, messageSource)

        when: "The constraint validates a non-blank value"
        def name = "John"
        def nonBlankNamePerson = new Person(name: name)
        def nonBlankNamePersonErrors = new ValidationErrors(nonBlankNamePerson, Person.name)
        blankConstraint.processValidateWithVetoing(nonBlankNamePerson, name, nonBlankNamePersonErrors)

        then: "Errors is correct"
        !nonBlankNamePersonErrors.hasErrors()
        nonBlankNamePersonErrors.allErrors.size() == 0

        when: "The constraint validates a blank value"
        name = " "
        def blankNamePerson = new Person(name: name)
        def blankNamePersonErrors = new ValidationErrors(blankNamePerson, Person.name)
        blankConstraint.processValidateWithVetoing(blankNamePerson, name, blankNamePersonErrors)

        then: "Errors is correct"
        blankNamePersonErrors.hasErrors()
        blankNamePersonErrors.allErrors.size() == 1

        when: "The constraint validates an empty value"
        name = ""
        def emptyNamePerson = new Person(name: name)
        def emptyNamePersonErrors = new ValidationErrors(emptyNamePerson, Person.name)
        blankConstraint.processValidateWithVetoing(emptyNamePerson, name, emptyNamePersonErrors)

        then: "Errors is correct"
        emptyNamePersonErrors.hasErrors()
        emptyNamePersonErrors.allErrors.size() == 1
    }

    private MessageSource messageSource = new MessageSource() {
        def message = "message"

        @Override
        String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
            return message
        }

        @Override
        String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
            return message
        }

        @Override
        String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
            return message
        }
    }
}

@Entity
class Person {
    String name
}

